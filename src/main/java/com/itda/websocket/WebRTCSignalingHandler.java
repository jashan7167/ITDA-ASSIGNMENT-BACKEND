package com.itda.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.itda.dto.ParticipantState;
import com.itda.entity.Room;
import com.itda.repository.RoomRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebRTCSignalingHandler extends TextWebSocketHandler {

    // roomId -> Map of sessionId -> WebSocketSession
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    // roomId -> Map of userId -> ParticipantState
    private final Map<String, Map<Long, ParticipantState>> participantStates = new ConcurrentHashMap<>();
    // sessionId -> roomId mapping
    private final Map<String, String> sessionRoomMap = new ConcurrentHashMap<>();
    // sessionId -> userId mapping
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();
    // roomId -> hostId mapping (for authorization)
    private final Map<String, Long> roomHostMap = new ConcurrentHashMap<>();

    private final RoomRepository roomRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebRTCSignalingHandler(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public void setRoomHost(String roomId, Long hostId) {
        roomHostMap.put(roomId, hostId);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = extractRoomId(session);
        String userId = extractUserId(session);
        String username = extractUsername(session);
        
        if (roomId == null || userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Room room = roomRepository.findByRoomId(roomId).orElse(null);
        if (room == null || Boolean.FALSE.equals(room.getIsActive())) {
            session.close(new CloseStatus(1008, "Room is closed"));
            return;
        }

        long userIdLong = Long.parseLong(userId);
        
        // Add to sessions
        roomSessions.putIfAbsent(roomId, new ConcurrentHashMap<>());
        roomSessions.get(roomId).put(session.getId(), session);
        
        // Create participant state
        ParticipantState state = ParticipantState.builder()
                .userId(userIdLong)
                .sessionId(session.getId())
                .audioMuted(false)
                .videoDisabled(false)
                .joinedAt(System.currentTimeMillis())
                .username(username != null ? username : "User" + userIdLong)
                .build();
        
        participantStates.putIfAbsent(roomId, new ConcurrentHashMap<>());
        participantStates.get(roomId).put(userIdLong, state);
        
        sessionRoomMap.put(session.getId(), roomId);
        sessionUserMap.put(session.getId(), userIdLong);

        log.info("User {} ({}) joined room {}", userIdLong, username, roomId);

        // Send participant list to new user
        sendParticipants(session, roomId);

        // Notify others that a new user joined
        broadcastToRoom(roomId, Map.of(
                "type", "user-joined",
                "participant", objectMapper.readTree(objectMapper.writeValueAsString(state))
        ), session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomId = sessionRoomMap.get(session.getId());
        Long userId = sessionUserMap.get(session.getId());
        
        if (roomId == null || userId == null) {
            return;
        }

        try {
                Map<String, Object> data = objectMapper.readValue(
                    message.getPayload(),
                    new TypeReference<Map<String, Object>>() {}
                );
            String type = (String) data.get("type");

            switch (type) {
                case "offer":
                case "answer":
                case "ice-candidate":
                    // Forward SDP/ICE to target peer
                    String targetSessionId = (String) data.get("targetSessionId");
                    if (targetSessionId != null) {
                        forwardToPeer(roomId, session.getId(), targetSessionId, data);
                    }
                    break;
                    
                case "audio-state-changed":
                    handleAudioStateChange(roomId, userId, (Boolean) data.get("muted"));
                    break;
                    
                case "video-state-changed":
                    handleVideoStateChange(roomId, userId, (Boolean) data.get("disabled"));
                    break;
                    
                case "get-participants":
                    sendParticipants(session, roomId);
                    break;
                    
                case "mute-participant":
                    handleHostMuteRequest(roomId, userId, (Long) data.get("targetUserId"));
                    break;
                    
                case "kick-participant":
                    handleHostKickRequest(roomId, userId, (Long) data.get("targetUserId"));
                    break;
                    
                default:
                    log.warn("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = sessionRoomMap.remove(session.getId());
        Long userId = sessionUserMap.remove(session.getId());

        if (roomId != null) {
            // Remove from sessions
            Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) {
                    roomSessions.remove(roomId);
                    participantStates.remove(roomId);
                    roomHostMap.remove(roomId);
                }
            }

            // Remove from participant states
            if (userId != null) {
                Map<Long, ParticipantState> states = participantStates.get(roomId);
                if (states != null) {
                    states.remove(userId);
                }

                log.info("User {} left room {}", userId, roomId);
                broadcastToRoom(roomId, Map.of(
                        "type", "user-left",
                        "userId", userId,
                        "sessionId", session.getId()
                ));
            }
        }
    }

    public int closeRoom(String roomId) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            roomHostMap.remove(roomId);
            participantStates.remove(roomId);
            return 0;
        }

        int closedConnections = 0;
        List<WebSocketSession> sessionsToClose = new ArrayList<>(sessions.values());

        try {
            broadcastToRoom(roomId, Map.of(
                    "type", "room-closed",
                    "roomId", roomId,
                    "message", "Room has been closed by the host"
            ));
        } catch (IOException e) {
            log.warn("Failed to broadcast room close event for room {}: {}", roomId, e.getMessage());
        }

        for (WebSocketSession ws : sessionsToClose) {
            try {
                if (ws.isOpen()) {
                    ws.close(new CloseStatus(1000, "Room closed by host"));
                    closedConnections++;
                }
            } catch (IOException e) {
                log.warn("Failed to close session {} in room {}: {}", ws.getId(), roomId, e.getMessage());
            }
        }

        roomSessions.remove(roomId);
        participantStates.remove(roomId);
        roomHostMap.remove(roomId);
        return closedConnections;
    }

    private void forwardToPeer(String roomId, String fromSessionId, String toSessionId, Map<String, Object> data) 
            throws IOException {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        WebSocketSession targetSession = sessions.get(toSessionId);
        if (targetSession != null && targetSession.isOpen()) {
            String payload = objectMapper.writeValueAsString(data);
            targetSession.sendMessage(new TextMessage(payload));
        }
    }

    private void broadcastToRoom(String roomId, Map<String, Object> message) throws IOException {
        broadcastToRoom(roomId, message, null);
    }

    private void broadcastToRoom(String roomId, Map<String, Object> message, String excludeSessionId) 
            throws IOException {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        String payload = objectMapper.writeValueAsString(message);
        for (Map.Entry<String, WebSocketSession> entry : sessions.entrySet()) {
            if (excludeSessionId != null && entry.getKey().equals(excludeSessionId)) {
                continue;
            }
            
            if (entry.getValue().isOpen()) {
                try {
                    entry.getValue().sendMessage(new TextMessage(payload));
                } catch (IOException e) {
                    log.warn("Failed to send message to session {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }
    }

    private void sendParticipants(WebSocketSession session, String roomId) throws IOException {
        Map<Long, ParticipantState> states = participantStates.get(roomId);
        List<ParticipantState> participants = new ArrayList<>();
        
        if (states != null) {
            participants = new ArrayList<>(states.values());
        }

        Map<String, Object> response = Map.of(
                "type", "participants",
                "count", participants.size(),
                "participants", participants
        );
        
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }

    private void handleAudioStateChange(String roomId, Long userId, Boolean muted) throws IOException {
        Map<Long, ParticipantState> states = participantStates.get(roomId);
        if (states != null && states.containsKey(userId)) {
            ParticipantState state = states.get(userId);
            state.setAudioMuted(muted != null ? muted : false);
            
            broadcastToRoom(roomId, Map.of(
                    "type", "state-changed",
                    "userId", userId,
                    "audioMuted", state.getAudioMuted()
            ));
        }
    }

    private void handleVideoStateChange(String roomId, Long userId, Boolean disabled) throws IOException {
        Map<Long, ParticipantState> states = participantStates.get(roomId);
        if (states != null && states.containsKey(userId)) {
            ParticipantState state = states.get(userId);
            state.setVideoDisabled(disabled != null ? disabled : false);
            
            broadcastToRoom(roomId, Map.of(
                    "type", "state-changed",
                    "userId", userId,
                    "videoDisabled", state.getVideoDisabled()
            ));
        }
    }

    private void handleHostMuteRequest(String roomId, Long hostUserId, Long targetUserId) throws IOException {
        // Verify host
        Long roomHost = roomHostMap.get(roomId);
        if (roomHost == null || !roomHost.equals(hostUserId)) {
            log.warn("Non-host user {} attempted to mute in room {}", hostUserId, roomId);
            return;
        }

        Map<Long, ParticipantState> states = participantStates.get(roomId);
        if (states != null && states.containsKey(targetUserId)) {
            ParticipantState state = states.get(targetUserId);
            state.setAudioMuted(true);
            
            broadcastToRoom(roomId, Map.of(
                    "type", "host-muted",
                    "userId", targetUserId,
                    "audioMuted", true
            ));
            
            log.info("Host {} muted user {} in room {}", hostUserId, targetUserId, roomId);
        }
    }

    private void handleHostKickRequest(String roomId, Long hostUserId, Long targetUserId) throws IOException {
        // Verify host
        Long roomHost = roomHostMap.get(roomId);
        if (roomHost == null || !roomHost.equals(hostUserId)) {
            log.warn("Non-host user {} attempted to kick in room {}", hostUserId, roomId);
            return;
        }

        // Find and close target user's session
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            String targetSessionId = null;
            for (Map.Entry<String, Long> entry : sessionUserMap.entrySet()) {
                if (entry.getValue().equals(targetUserId)) {
                    String sId = entry.getKey();
                    if (sessionRoomMap.get(sId).equals(roomId)) {
                        targetSessionId = sId;
                        break;
                    }
                }
            }

            if (targetSessionId != null) {
                WebSocketSession targetSession = sessions.get(targetSessionId);
                if (targetSession != null && targetSession.isOpen()) {
                    targetSession.close(CloseStatus.POLICY_VIOLATION.withReason("Kicked by host"));
                }
                
                log.info("Host {} kicked user {} from room {}", hostUserId, targetUserId, roomId);
            }
        }
    }

    public Map<Long, ParticipantState> getRoomParticipants(String roomId) {
        return participantStates.getOrDefault(roomId, new ConcurrentHashMap<>());
    }

    public void muteParticipant(String roomId, Long hostUserId, Long targetUserId) throws IOException {
        handleHostMuteRequest(roomId, hostUserId, targetUserId);
    }

    public void unmuteParticipant(String roomId, Long hostUserId, Long targetUserId) throws IOException {
        // Verify host
        Long roomHost = roomHostMap.get(roomId);
        if (roomHost == null || !roomHost.equals(hostUserId)) {
            log.warn("Non-host user {} attempted to unmute in room {}", hostUserId, roomId);
            return;
        }

        Map<Long, ParticipantState> states = participantStates.get(roomId);
        if (states != null && states.containsKey(targetUserId)) {
            ParticipantState state = states.get(targetUserId);
            state.setAudioMuted(false);
            
            broadcastToRoom(roomId, Map.of(
                    "type", "host-unmuted",
                    "userId", targetUserId,
                    "audioMuted", false
            ));
            
            log.info("Host {} unmuted user {} in room {}", hostUserId, targetUserId, roomId);
        }
    }

    public void kickParticipant(String roomId, Long hostUserId, Long targetUserId) throws IOException {
        handleHostKickRequest(roomId, hostUserId, targetUserId);
    }

    public boolean isRoomActive(String roomId) {
        return roomRepository.findByRoomId(roomId)
                .map(Room::getIsActive)
                .orElse(false);
    }

    private String extractRoomId(WebSocketSession session) {
        String uri = session.getUri().toString();
        String[] parts = uri.split("/");
        return parts.length > 0 ? parts[parts.length - 1] : null;
    }

    private String extractUserId(WebSocketSession session) {
        return session.getHandshakeHeaders().getFirst("X-User-Id");
    }

    private String extractUsername(WebSocketSession session) {
        return session.getHandshakeHeaders().getFirst("X-Username");
    }
}
