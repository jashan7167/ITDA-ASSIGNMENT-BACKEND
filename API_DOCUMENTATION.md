# ITDA Video Conference Backend API Reference

This document explains the backend APIs for the ITDA WebRTC video conference application. It is written as a practical reference for testing, integration, and submission review.

## 1. Quick Overview

The backend is responsible for:

- host authentication
- room creation and room state
- public invite link generation
- WebSocket signaling for WebRTC
- participant tracking
- host moderation actions such as mute, unmute, kick, and close room

The backend does **not** capture camera or microphone input. That part belongs to the browser frontend.

## 2. Base URL

All REST endpoints are served under:

```text
http://localhost:8080/api
```

The WebSocket endpoint is:

```text
ws://localhost:8080/api/ws/rooms/{roomId}
```

## 3. Authentication

Authentication is handled with JWT.

### How it works

1. A user registers or logs in.
2. The backend returns a JWT token.
3. The frontend sends that token in the `Authorization` header for protected routes.

Example header:

```http
Authorization: Bearer <token>
```

### Important detail

Only the host needs authentication for room creation and moderation actions. Guests can join rooms using the invite link.

## 4. Authentication APIs

### 4.1 Register

Creates a new user and returns a JWT token.

**Endpoint**

```http
POST /auth/register
```

**Request body**

```json
{
  "email": "host@example.com",
  "password": "password123",
  "username": "hostuser"
}
```

**Response**

```json
{
  "token": "eyJhbGciOi...",
  "email": "host@example.com",
  "username": "hostuser",
  "userId": 1
}
```

**Notes**

- `email` must be unique.
- `password` is stored as a BCrypt hash in the database.

---

### 4.2 Login

Authenticates an existing user and returns a JWT token.

**Endpoint**

```http
POST /auth/login
```

**Request body**

```json
{
  "email": "host@example.com",
  "password": "password123"
}
```

**Response**

```json
{
  "token": "eyJhbGciOi...",
  "email": "host@example.com",
  "username": "hostuser",
  "userId": 1
}
```

**Common errors**

- wrong password
- user not found
- invalid request payload

## 5. Room APIs

### 5.1 Create Room

Creates a new room and assigns the authenticated user as host.

**Endpoint**

```http
POST /rooms
```

**Auth required**

Yes. Send a JWT token in the `Authorization` header.

**Request body**

```json
{
  "roomName": "Daily Standup"
}
```

**Response**

```json
{
  "roomId": "a1b2c3d4e5f6",
  "roomName": "Daily Standup",
  "hostId": 1,
  "joinLink": "http://localhost:3000/join/a1b2c3d4e5f6",
  "createdAt": 1744480000000,
  "isActive": true
}
```

**What happens internally**

- a unique room ID is generated
- the room is saved in PostgreSQL
- the host is registered in the WebSocket handler
- a public join link is returned

---

### 5.2 Get Room Details

Returns the room metadata.

**Endpoint**

```http
GET /rooms/{roomId}
```

**Auth required**

No.

**Example response**

```json
{
  "roomId": "a1b2c3d4e5f6",
  "roomName": "Daily Standup",
  "hostId": 1,
  "joinLink": "http://localhost:3000/join/a1b2c3d4e5f6",
  "createdAt": 1744480000000,
  "isActive": true
}
```

---

### 5.3 Get Join Link

Returns the public invite link for the room.

**Endpoint**

```http
GET /rooms/join-link/{roomId}
```

**Auth required**

No.

**Response**

```json
{
  "joinLink": "http://localhost:3000/join/a1b2c3d4e5f6"
}
```

**Notes**

- If the room is closed, this endpoint returns an error.
- The join link is public and does not require guest login.

---

### 5.4 Get My Rooms

Returns the list of active rooms created by the authenticated host.

**Endpoint**

```http
GET /rooms/my-rooms
```

**Auth required**

Yes.

**Response**

```json
[
  {
    "roomId": "a1b2c3d4e5f6",
    "roomName": "Daily Standup",
    "hostId": 1,
    "joinLink": "http://localhost:3000/join/a1b2c3d4e5f6",
    "createdAt": 1744480000000,
    "isActive": true
  }
]
```

---

### 5.5 Get Participants

Returns the current participants in a room along with their media state.

**Endpoint**

```http
GET /rooms/{roomId}/participants
```

**Auth required**

No.

**Response**

```json
{
  "roomId": "a1b2c3d4e5f6",
  "count": 2,
  "participants": [
    {
      "userId": 1,
      "sessionId": "8b8f5c2f-1",
      "audioMuted": false,
      "videoDisabled": false,
      "joinedAt": 1744480000000,
      "username": "hostuser"
    },
    {
      "userId": 2,
      "sessionId": "8b8f5c2f-2",
      "audioMuted": true,
      "videoDisabled": false,
      "joinedAt": 1744480010000,
      "username": "guestuser"
    }
  ]
}
```

**Notes**

- This is backed by in-memory WebSocket participant state.
- If the room is closed, the request fails.

---

### 5.6 Close Room

Marks the room as inactive and closes live WebSocket connections.

**Endpoint**

```http
POST /rooms/{roomId}/close
```

**Auth required**

Yes. Only the host can close the room.

**Response**

```json
{
  "message": "Room closed"
}
```

**Behavior**

- the room is marked inactive in PostgreSQL
- all active WebSocket sessions in the room are closed
- a `room-closed` event is broadcast
- new joins are rejected

---

### 5.7 Mute Participant

Forces a participant to be muted.

**Endpoint**

```http
POST /rooms/{roomId}/mute/{userId}
```

**Auth required**

Yes. Host only.

**Response**

```json
{
  "message": "Participant muted"
}
```

**Broadcast event**

```json
{
  "type": "host-muted",
  "userId": 2,
  "audioMuted": true
}
```

---

### 5.8 Unmute Participant

Forces a participant to be unmuted.

**Endpoint**

```http
POST /rooms/{roomId}/unmute/{userId}
```

**Auth required**

Yes. Host only.

**Response**

```json
{
  "message": "Participant unmuted"
}
```

**Broadcast event**

```json
{
  "type": "host-unmuted",
  "userId": 2,
  "audioMuted": false
}
```

---

### 5.9 Kick Participant

Disconnects a participant from the room.

**Endpoint**

```http
POST /rooms/{roomId}/kick/{userId}
```

**Auth required**

Yes. Host only.

**Response**

```json
{
  "message": "Participant kicked"
}
```

**Behavior**

- the target participant’s WebSocket session is closed
- the room broadcasts a `user-left` event
- the target client should leave the room UI immediately

## 6. WebSocket Signaling

The WebSocket layer is used for WebRTC signaling and room events.

### Connection URL

```text
ws://localhost:8080/api/ws/rooms/{roomId}
```

### Required handshake headers

```http
X-User-Id: <userId>
X-Username: <username>
```

### Why these headers are used

The backend needs to identify the participant when the WebSocket connects. In this demo flow, the user ID and username are sent in handshake headers.

## 7. WebSocket Message Types

### 7.1 Offer

Sent when a peer starts WebRTC negotiation.

```json
{
  "type": "offer",
  "targetSessionId": "target-session-id",
  "sdp": "v=0..."
}
```

### 7.2 Answer

Sent as a reply to an offer.

```json
{
  "type": "answer",
  "targetSessionId": "target-session-id",
  "sdp": "v=0..."
}
```

### 7.3 ICE Candidate

Sent while discovering network paths for the peer connection.

```json
{
  "type": "ice-candidate",
  "targetSessionId": "target-session-id",
  "candidate": "candidate:..."
}
```

### 7.4 Get Participants

Requests the current participant list.

```json
{
  "type": "get-participants"
}
```

### 7.5 Audio State Changed

Used by a participant to update their own audio state.

```json
{
  "type": "audio-state-changed",
  "muted": true
}
```

### 7.6 Video State Changed

Used by a participant to update their own video state.

```json
{
  "type": "video-state-changed",
  "disabled": true
}
```

### 7.7 Mute Participant

Host moderation action sent through WebSocket.

```json
{
  "type": "mute-participant",
  "targetUserId": 2
}
```

### 7.8 Kick Participant

Host moderation action sent through WebSocket.

```json
{
  "type": "kick-participant",
  "targetUserId": 2
}
```

## 8. WebSocket Events Broadcast By Server

These events are sent to participants in the room.

### 8.1 User Joined

```json
{
  "type": "user-joined",
  "participant": {
    "userId": 2,
    "sessionId": "8b8f5c2f-2",
    "audioMuted": false,
    "videoDisabled": false,
    "joinedAt": 1744480010000,
    "username": "guestuser"
  }
}
```

### 8.2 Participants List

```json
{
  "type": "participants",
  "count": 2,
  "participants": [
    {
      "userId": 1,
      "sessionId": "8b8f5c2f-1",
      "audioMuted": false,
      "videoDisabled": false,
      "joinedAt": 1744480000000,
      "username": "hostuser"
    }
  ]
}
```

### 8.3 State Changed

Used when a participant changes their own audio or video state.

```json
{
  "type": "state-changed",
  "userId": 2,
  "audioMuted": true
}
```

or

```json
{
  "type": "state-changed",
  "userId": 2,
  "videoDisabled": true
}
```

### 8.4 Host Muted / Unmuted

```json
{
  "type": "host-muted",
  "userId": 2,
  "audioMuted": true
}
```

```json
{
  "type": "host-unmuted",
  "userId": 2,
  "audioMuted": false
}
```

### 8.5 User Left

```json
{
  "type": "user-left",
  "userId": 2,
  "sessionId": "8b8f5c2f-2"
}
```

### 8.6 Room Closed

```json
{
  "type": "room-closed",
  "roomId": "a1b2c3d4e5f6",
  "message": "Room has been closed by the host"
}
```

## 9. Error Responses

The backend currently throws runtime exceptions for invalid flows, so the exact error body depends on Spring’s default error handling or any global handler you add later.

Typical cases:

- `401 Unauthorized` when auth is missing or invalid
- `403 Forbidden` when a non-host tries to moderate the room
- `404 Not Found` when a room does not exist
- `400 Bad Request` when payloads are invalid
- `500 Internal Server Error` for unexpected failures

Example response shape you may see:

```json
{
  "timestamp": "2026-04-13T10:00:00.000+00:00",
  "status": 403,
  "error": "Forbidden",
  "path": "/api/rooms/a1b2c3d4e5f6/close"
}
```

## 10. Common End-to-End Flow

This is the usual sequence when testing the app manually.

### Host flow

1. Register or log in.
2. Create a room.
3. Copy the invite link.
4. Open a WebSocket connection as host.
5. Watch participants join.
6. Moderate users if needed.
7. Close the room when done.

### Guest flow

1. Open the invite link.
2. Join the room without authentication.
3. Allow microphone and camera access in the browser.
4. Open the WebSocket connection.
5. Exchange WebRTC signaling messages.
6. Leave the room or wait for the host to close it.

## 11. Local Testing Examples

### Register a user

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"host@example.com","password":"password123","username":"hostuser"}'
```

### Create a room

```bash
curl -X POST http://localhost:8080/api/rooms \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"roomName":"Daily Standup"}'
```

### Get participants

```bash
curl http://localhost:8080/api/rooms/a1b2c3d4e5f6/participants
```

### Close a room

```bash
curl -X POST http://localhost:8080/api/rooms/a1b2c3d4e5f6/close \
  -H "Authorization: Bearer <TOKEN>"
```

## 12. Important Notes

- Room IDs are generated randomly and are hard to guess.
- The backend uses raw WebSocket, not STOMP, to keep the signaling layer simple.
- The media stream itself stays browser-to-browser through WebRTC.
- The backend only relays control messages and room state.
- The `join-link` endpoint is public, but moderation routes are protected.

## 13. DTO Reference

### AuthRequest

```json
{
  "email": "string",
  "password": "string",
  "username": "string"
}
```

### AuthResponse

```json
{
  "token": "string",
  "email": "string",
  "username": "string",
  "userId": 1
}
```

### CreateRoomRequest

```json
{
  "roomName": "string"
}
```

### RoomResponse

```json
{
  "roomId": "string",
  "roomName": "string",
  "hostId": 1,
  "joinLink": "string",
  "createdAt": 1744480000000,
  "isActive": true
}
```

### ParticipantState

```json
{
  "userId": 1,
  "sessionId": "string",
  "audioMuted": false,
  "videoDisabled": false,
  "joinedAt": 1744480000000,
  "username": "string"
}
```

### ParticipantListResponse

```json
{
  "roomId": "string",
  "count": 1,
  "participants": []
}
```

## 14. Files This Documentation Matches

- [src/main/java/com/itda/auth/AuthController.java](src/main/java/com/itda/auth/AuthController.java)
- [src/main/java/com/itda/room/RoomController.java](src/main/java/com/itda/room/RoomController.java)
- [src/main/java/com/itda/websocket/WebRTCSignalingHandler.java](src/main/java/com/itda/websocket/WebRTCSignalingHandler.java)
- [src/main/java/com/itda/dto/AuthRequest.java](src/main/java/com/itda/dto/AuthRequest.java)
- [src/main/java/com/itda/dto/AuthResponse.java](src/main/java/com/itda/dto/AuthResponse.java)
- [src/main/java/com/itda/dto/CreateRoomRequest.java](src/main/java/com/itda/dto/CreateRoomRequest.java)
- [src/main/java/com/itda/dto/RoomResponse.java](src/main/java/com/itda/dto/RoomResponse.java)
- [src/main/java/com/itda/dto/ParticipantState.java](src/main/java/com/itda/dto/ParticipantState.java)
- [src/main/java/com/itda/dto/ParticipantListResponse.java](src/main/java/com/itda/dto/ParticipantListResponse.java)
