package com.itda.room;

import com.itda.dto.CreateRoomRequest;
import com.itda.dto.ParticipantListResponse;
import com.itda.dto.RoomResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@RequestBody CreateRoomRequest request, Authentication auth) {
        Long userId = (Long) auth.getDetails();
        RoomResponse response = roomService.createRoom(request, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId) {
        RoomResponse response = roomService.getRoomById(roomId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/join-link/{roomId}")
    public ResponseEntity<Map<String, String>> getJoinLink(@PathVariable String roomId) {
        String link = roomService.getJoinLink(roomId);
        return ResponseEntity.ok(Map.of("joinLink", link));
    }

    @GetMapping("/{roomId}/participants")
    public ResponseEntity<ParticipantListResponse> getParticipants(@PathVariable String roomId) {
        ParticipantListResponse response = roomService.getParticipants(roomId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-rooms")
    public ResponseEntity<List<RoomResponse>> getMyRooms(Authentication auth) {
        Long userId = (Long) auth.getDetails();
        List<RoomResponse> rooms = roomService.getUserRooms(userId);
        return ResponseEntity.ok(rooms);
    }

    @PostMapping("/{roomId}/close")
    public ResponseEntity<Map<String, String>> closeRoom(@PathVariable String roomId, Authentication auth) {
        Long userId = (Long) auth.getDetails();
        roomService.verifyHostWithRoom(roomId, userId);
        roomService.closeRoom(roomId);
        return ResponseEntity.ok(Map.of("message", "Room closed"));
    }

    @PostMapping("/{roomId}/mute/{userId}")
    public ResponseEntity<Map<String, String>> muteParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId,
            Authentication auth) {
        Long hostId = (Long) auth.getDetails();
        roomService.verifyHostWithRoom(roomId, hostId);
        roomService.muteParticipant(roomId, hostId, userId);
        return ResponseEntity.ok(Map.of("message", "Participant muted"));
    }

    @PostMapping("/{roomId}/unmute/{userId}")
    public ResponseEntity<Map<String, String>> unmuteParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId,
            Authentication auth) {
        Long hostId = (Long) auth.getDetails();
        roomService.verifyHostWithRoom(roomId, hostId);
        roomService.unmuteParticipant(roomId, hostId, userId);
        return ResponseEntity.ok(Map.of("message", "Participant unmuted"));
    }

    @PostMapping("/{roomId}/kick/{userId}")
    public ResponseEntity<Map<String, String>> kickParticipant(
            @PathVariable String roomId,
            @PathVariable Long userId,
            Authentication auth) {
        Long hostId = (Long) auth.getDetails();
        roomService.verifyHostWithRoom(roomId, hostId);
        roomService.kickParticipant(roomId, hostId, userId);
        return ResponseEntity.ok(Map.of("message", "Participant kicked"));
    }
}
