package com.itda.room;

import com.itda.dto.CreateRoomRequest;
import com.itda.dto.ParticipantListResponse;
import com.itda.dto.ParticipantState;
import com.itda.dto.RoomResponse;
import com.itda.entity.Room;
import com.itda.repository.RoomRepository;
import com.itda.websocket.WebRTCSignalingHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final WebRTCSignalingHandler webRTCSignalingHandler;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @Value("${server.port:8080}")
    private String serverPort;

    public RoomResponse createRoom(CreateRoomRequest request, Long hostId) {
        String roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        
        Room room = Room.builder()
                .roomId(roomId)
                .hostId(hostId)
                .roomName(request.getRoomName())
                .build();

        room = roomRepository.save(room);
        
        // Register room host in WebSocket handler
        webRTCSignalingHandler.setRoomHost(roomId, hostId);
        
        return mapToResponse(room);
    }

    public RoomResponse getRoomById(String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        return mapToResponse(room);
    }

    public String getJoinLink(String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (Boolean.FALSE.equals(room.getIsActive())) {
            throw new RuntimeException("Room is closed");
        }

        return String.format("http://localhost:3000/join/%s", roomId);
    }

    public List<RoomResponse> getUserRooms(Long hostId) {
        return roomRepository.findByHostIdAndIsActiveTrue(hostId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void closeRoom(String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        room.setIsActive(false);
        roomRepository.save(room);

        webRTCSignalingHandler.closeRoom(roomId);
    }

    public ParticipantListResponse getParticipants(String roomId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (Boolean.FALSE.equals(room.getIsActive())) {
            throw new RuntimeException("Room is closed");
        }
        
        // Get participants from WebSocket handler
        Map<Long, ParticipantState> participants = webRTCSignalingHandler.getRoomParticipants(roomId);
        List<ParticipantState> participantList = new ArrayList<>(participants.values());
        
        return ParticipantListResponse.builder()
                .roomId(roomId)
                .count(participantList.size())
                .participants(participantList)
                .build();
    }

    public void verifyHostWithRoom(String roomId, Long userId) {
        Room room = roomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        if (!room.getHostId().equals(userId)) {
            throw new RuntimeException("Only room host can perform this action");
        }
    }

    public void muteParticipant(String roomId, Long hostId, Long targetUserId) {
        try {
            webRTCSignalingHandler.muteParticipant(roomId, hostId, targetUserId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to mute participant: " + e.getMessage());
        }
    }

    public void unmuteParticipant(String roomId, Long hostId, Long targetUserId) {
        try {
            webRTCSignalingHandler.unmuteParticipant(roomId, hostId, targetUserId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to unmute participant: " + e.getMessage());
        }
    }

    public void kickParticipant(String roomId, Long hostId, Long targetUserId) {
        try {
            webRTCSignalingHandler.kickParticipant(roomId, hostId, targetUserId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to kick participant: " + e.getMessage());
        }
    }

    private RoomResponse mapToResponse(Room room) {
        String joinLink = String.format("http://localhost:3000/join/%s", room.getRoomId());
        
        return RoomResponse.builder()
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .hostId(room.getHostId())
                .joinLink(joinLink)
                .createdAt(room.getCreatedAt())
                .isActive(room.getIsActive())
                .build();
    }
}
