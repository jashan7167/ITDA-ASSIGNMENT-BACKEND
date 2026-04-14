# ITDA Video Conference Application

This repository contains the backend for the ITDA hiring assignment: a WebRTC-based video conference application with authenticated room creation, public invite links, and live room moderation.

## What Is Implemented

- JWT authentication for host registration and login
- Room creation with unique room IDs and public join links
- WebSocket-based signaling for WebRTC peer connection setup
- Participant state tracking for audio and video controls
- Host moderation actions: mute, unmute, kick, and close room
- PostgreSQL persistence with Docker setup and SQL scripts
- Submission screenshots included in [Pictures](Pictures)

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Security with JWT
- Spring WebSocket
- Spring Data JPA
- PostgreSQL
- Docker Compose for local database setup

## Project Structure

```text
backend/
├── Pictures/                    # Screenshots for submission evidence
├── sql/                         # Database scripts
├── src/main/java/com/itda/
│   ├── auth/                    # Authentication APIs and service
│   ├── config/                  # Security and WebSocket configuration
│   ├── dto/                     # Request/response models
│   ├── entity/                  # JPA entities
│   ├── repository/              # JPA repositories
│   ├── room/                    # Room APIs and service
│   ├── util/                    # JWT helper
│   └── websocket/               # WebRTC signaling handler
├── src/main/resources/
│   └── application.properties
├── build.gradle
├── docker-compose.yml
└── .env.example
```

## Requirements Coverage

| Requirement | Status |
| --- | --- |
| User authentication for hosts | Done |
| Room creation | Done |
| Public invite link | Done |
| Real-time signaling for WebRTC | Done |
| Mute / unmute audio | Done |
| Enable / disable video | Done |
| Host moderation controls | Done |
| Join / leave room handling | Done |
| Email invitation | Not implemented |

## Prerequisites

- Java 17 or newer
- Gradle 8+ or the included Gradle wrapper
- Docker and Docker Compose, or a local PostgreSQL instance
- `curl` for testing APIs

## Environment Setup

The application now loads a real `.env` file at startup using a Spring Boot `EnvironmentPostProcessor`.

Copy the example environment file and edit the values for your machine:

```bash
cp .env.example .env
```

The active database settings are read from [src/main/resources/application.properties](src/main/resources/application.properties), which now uses placeholders such as `${SPRING_DATASOURCE_URL:...}` and `${JWT_SECRET:...}`.

If a `.env` file is present in the backend directory, it is loaded automatically before Spring resolves those placeholders.

## Database Setup

### Option 1: Docker Compose

```bash
cd /home/jsb/ITDA/backend
docker-compose up -d
```

This starts both the PostgreSQL database and the backend application. The app container reads the same `.env` file and also receives database values through Docker Compose environment variables.

If you run the container manually, pass the same values with `--env-file .env` or explicit `-e` flags.

### Option 2: Manual PostgreSQL

Use the scripts in [sql](sql):

```bash
psql -U postgres -f sql/create_database.sql
psql -U postgres -d video_conference -f sql/schema.sql
```

Optional sample data:

```bash
psql -U postgres -d video_conference -f sql/seed.sql
```

## Run the Application

```bash
./gradlew bootRun
```

The backend runs at:

```text
http://localhost:8080/api
```

## API Summary

Full endpoint details, request/response examples, and WebSocket payloads are documented in [API_DOCUMENTATION.md](API_DOCUMENTATION.md).

### Authentication

- `POST /api/auth/register`
- `POST /api/auth/login`

### Rooms

- `POST /api/rooms`
- `GET /api/rooms/{roomId}`
- `GET /api/rooms/join-link/{roomId}`
- `GET /api/rooms/{roomId}/participants`
- `GET /api/rooms/my-rooms`
- `POST /api/rooms/{roomId}/close`
- `POST /api/rooms/{roomId}/mute/{userId}`
- `POST /api/rooms/{roomId}/unmute/{userId}`
- `POST /api/rooms/{roomId}/kick/{userId}`

### WebSocket Signaling

```text
ws://localhost:8080/api/ws/rooms/{roomId}
```

Required headers:

- `X-User-Id`
- `X-Username`

Supported message types:

- `offer`
- `answer`
- `ice-candidate`
- `get-participants`
- `audio-state-changed`
- `video-state-changed`
- `mute-participant`
- `kick-participant`

## End-to-End Manual Test Flow

### 1. Register or log in as host

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"host@test.com","password":"pass123","username":"host"}'
```

### 2. Create a room

```bash
curl -X POST http://localhost:8080/api/rooms \
  -H "Authorization: Bearer <HOST_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"roomName":"Demo Room"}'
```

### 3. Connect host and guest WebSocket clients

Use `wscat` or `websocat`:

```bash
wscat -c "ws://localhost:8080/api/ws/rooms/<ROOM_ID>" \
  -H "X-User-Id: <HOST_ID>" \
  -H "X-Username: host"
```

```bash
wscat -c "ws://localhost:8080/api/ws/rooms/<ROOM_ID>" \
  -H "X-User-Id: <GUEST_ID>" \
  -H "X-Username: guest"
```

### 4. Verify moderation and signaling

- send `{"type":"get-participants"}`
- send `{"type":"audio-state-changed","muted":true}`
- send `{"type":"video-state-changed","disabled":true}`
- call `POST /api/rooms/{roomId}/mute/{userId}`
- call `POST /api/rooms/{roomId}/kick/{userId}`
- call `POST /api/rooms/{roomId}/close`

## Screenshots

The following screenshots are included for submission evidence:

### Host flow

- [Getting host token](Pictures/gettingtokenforhost.png)
- [Creating room as host](Pictures/creatinghost.png)
- [Host connected](Pictures/hostconnected.png)
- [Getting join link](Pictures/gettingjoinlinkcreatedbyhost.png)

### Guest flow

- [Getting guest token](Pictures/creatingtokenforguest1.png)
- [Guest joins room](Pictures/guestjoinedtheroom.png)
- [Guest audio state change](Pictures/guestchangingaudiostate.png)

### Moderation and room lifecycle

- [Host mutes participant](Pictures/mutingparticipatusinghost.png)
- [Participant kicked](Pictures/participantkickedbyhost.png)
- [WebSocket kick event](Pictures/kickedwebsocket.png)
- [Room closed by host](Pictures/roomclosedusinghost.png)
- [Join attempt after close](Pictures/joiningtheroomwhenitsclosed.png)

### Signaling and events

- [SDP simulation](Pictures/sdpforsimulation.png)
- [Host receives state changed event](Pictures/hostgettingstatechangedevent.png)

## Design Notes

- Authentication is required only for host actions and room creation.
- Guests can join using the room link without logging in.
- WebRTC media stays peer-to-peer; the backend only handles signaling and room state.
- Room close now shuts down live WebSocket connections and blocks new joins.

## Important Assumptions

- Raw WebSocket was used instead of STOMP.
- WebSocket authentication is header-based for the current demo flow.

## Troubleshooting

### PostgreSQL will not start

```bash
docker ps
docker-compose logs -f postgres
```

### Backend build fails

```bash
./gradlew clean build
```

### Port 8080 is already in use

Change `server.port` in [src/main/resources/application.properties](src/main/resources/application.properties).

## Submission Checklist

- Backend code pushed to GitHub
- README contains setup and test instructions
- `.env.example` is present
- Database scripts are included
- Screenshots are linked from [Pictures](Pictures)
- Complex WebRTC and WebSocket logic contains comments

