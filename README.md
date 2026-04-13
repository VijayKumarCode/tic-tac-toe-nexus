# Nexus Multiplayer Arena

[![Live](https://img.shields.io/badge/Live-nexusgame.space-3fb950?style=flat-square&logo=spring&logoColor=white)](https://nexusgame.space)
[![Java](https://img.shields.io/badge/Java_17-ED8B00?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP%2FSockJS-FF6B6B?style=flat-square)](https://stomp.github.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=flat-square&logo=postgresql)](https://www.postgresql.org/)

Real-time multiplayer Tic-Tac-Toe platform with concurrent user sessions, WebSocket-based game state, and full account management.

**Live:** [nexusgame.space](https://nexusgame.space)

---

## System Architecture

```
                         ┌─────────────────────────────┐
                         │   nexusgame.space (Vercel)   │
                         │   Vanilla JS · SockJS         │
                         │   STOMP WebSocket client      │
                         └───────┬─────────────┬─────────┘
                                 │ HTTP         │ WebSocket
                          REST   │              │ STOMP/SockJS
                                 ▼              ▼
┌────────────────────────────────────────────────────────────────┐
│                  Spring Boot 3 Backend                          │
│                                                                  │
│  ┌──────────────────┐    ┌────────────────────────────────────┐ │
│  │  UserController   │    │         GameController             │ │
│  │  /api/users/**    │    │    @MessageMapping handlers        │ │
│  │  - register       │    │    /app/challenge                  │ │
│  │  - login          │    │    /app/challenge/reply            │ │
│  │  - logout         │    │    /app/toss/{roomId}              │ │
│  │  - sync/heartbeat │    │    /app/toss/decision/{roomId}     │ │
│  │  - lobby          │    │    /app/move/{roomId}              │ │
│  │  - leaderboard    │    │    /app/game.abort                 │ │
│  └──────────────────┘    │    /app/reset/{roomId}             │ │
│                           └────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │                    Service Layer                          │    │
│  │  UserService · GameService · ChallengeService            │    │
│  │  OtpService · AccountRecoveryService                     │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ┌─────────────────────────┐   ┌──────────────────────────────┐ │
│  │   In-Memory Game State  │   │    Scheduled Tasks           │ │
│  │   ConcurrentHashMap     │   │    @Scheduled(10s) heartbeat │ │
│  │   - playerXMap          │   │    @Scheduled(5s)  challenge │ │
│  │   - playerOMap          │   │    expiry cleanup            │ │
│  │   - currentTurnMap      │   └──────────────────────────────┘ │
│  │   - tossWinnerMap       │                                    │
│  │   - roomPlayersMap      │   WebSocketEventListener           │
│  └─────────────────────────┘   SessionConnected → store user   │
│                                SessionDisconnect → logout +    │
│                                cancel stale challenges          │
└───────────────────┬─────────────────────────────────────────────┘
                    │
          ┌─────────┴──────────┐
          │                    │
          ▼                    ▼
┌─────────────────┐  ┌─────────────────────┐
│  PostgreSQL      │  │  Resend HTTP API     │
│  Neon (cloud)    │  │  Activation emails   │
│                  │  │  OTP delivery        │
│  Tables:         │  │  Port 443 (HTTPS)    │
│  - users         │  │  Not SMTP (blocked   │
│  - game_moves    │  │  on cloud providers) │
│  - challenges    │  └─────────────────────┘
└─────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5, Spring Data JPA, Spring Security |
| WebSocket | STOMP protocol over SockJS, SimpMessagingTemplate |
| Database | PostgreSQL 16 on Neon (serverless cloud) |
| ORM | Hibernate 6, JPQL |
| Email | Resend HTTP REST API (replaces SMTP) |
| CI/CD | GitHub Actions → SCP → Oracle Cloud VM → systemd |
| Frontend | Vanilla JS, SockJS 1.6, STOMP.js 2.3, Vercel |

---

## Core Features

**Account System**
- Registration with bcrypt password hashing
- Email activation via token (24-hour expiry)
- OTP-based password reset and username recovery
- Secure login with constant-time comparison (prevents user enumeration)

**Lobby**
- Real-time presence tracking via heartbeat (`/api/users/sync`)
- Automatic idle-to-offline after 2 minutes inactivity
- `IN_GAME` status preserved during heartbeat (never overwritten to ONLINE)
- Live challenge notifications via personal WebSocket channel `/topic/challenges/{username}`

**Game Flow**
1. Player A sends challenge → `/app/challenge` → broadcasts to `/topic/challenges/{playerB}`
2. Player B accepts → both marked `IN_GAME`, room channel subscribed
3. Alphabetically first username is "host", flips coin via `/app/toss/{roomId}`
4. Toss winner chooses X or O → `/app/toss/decision/{roomId}`
5. Server assigns symbols, sets first player in `currentTurnMap`
6. Moves broadcast to `/topic/game/{roomId}` — server validates turn, position, symbol
7. Win/draw detection after each move; stats updated in DB

**Disconnect Handling**
- `SessionDisconnectEvent` → marks user OFFLINE + cancels pending challenges
- Username stored in STOMP session attributes on connect

---

## Repository Structure

```
nexus/
├── backend/
│   └── src/main/java/com/vk/gaming/nexus/
│       ├── config/
│       │   ├── AppConfig.java              # RestTemplate @Bean
│       │   ├── SecurityConfig.java         # CORS, CSRF disabled
│       │   ├── WebSocketConfig.java        # STOMP endpoint setup
│       │   └── WebSocketEventListener.java # Connect/disconnect handling
│       ├── controller/
│       │   ├── GameController.java         # @MessageMapping handlers
│       │   └── UserController.java         # REST API for user management
│       ├── dto/
│       │   ├── AuthRequest.java
│       │   ├── ChallengeMessage.java
│       │   ├── ChallengeStatus.java (enum)
│       │   ├── GameMove.java
│       │   ├── GameSystemMessage.java
│       │   ├── MessageType.java (enum)
│       │   ├── OtpData.java
│       │   ├── PlayerStatus.java
│       │   ├── RecoveryRequest.java
│       │   └── TossRequest.java
│       ├── entity/
│       │   └── User.java                   # @JsonIgnore on password/email
│       ├── exception/
│       │   ├── NexusBaseException.java
│       │   ├── EmailAlreadyRegisteredException.java
│       │   ├── UsernameTakenException.java
│       │   └── GlobalExceptionHandler.java
│       ├── model/
│       │   ├── ChallengeEntity.java        # @PrePersist createdAt fix
│       │   └── GameMoveEntity.java
│       ├── repository/
│       │   ├── ChallengeRepository.java    # @Repository, bulk JPQL
│       │   ├── GameMoveRepository.java
│       │   └── UserRepository.java         # Enum-param JPQL queries
│       └── service/
│           ├── AccountRecoveryService.java
│           ├── ChallengeService.java       # Auto-expiry scheduler
│           ├── GameService.java            # Game engine + recovery
│           ├── OtpService.java             # Resend HTTP API
│           └── UserService.java            # Presence, stats, auth
└── frontend/
    ├── index.html
    ├── nexus.css
    └── nexus.js                            # WebSocket client, game UI
```

---

## Production Bugs Fixed

These were found and fixed in production — not in a tutorial:

| Bug | Symptom | Root Cause | Fix |
|-----|---------|------------|-----|
| Moves rejected silently | Game board unresponsive after toss | `@Payload` missing on `handleGameMove` with `@DestinationVariable` — Jackson couldn't bind body | Added `@Payload` annotation |
| Game crashes every 10s | Random move rejections | `User$UserStatus` in JPQL — JVM bytecode separator, not source separator | Pass enum as `@Param` |
| SMTP silent failure | Users never activated | Railway blocks port 587 — connection hung 2 min, exception swallowed | Switched to Resend HTTP API port 443 |
| Lobby shows IN_GAME as ONLINE | Challengeable during game | `syncUserPresence` always set `ONLINE`, overwriting `IN_GAME` | Guard: only update if `!= IN_GAME` |
| `createdAt` always null | Wrong challenge resolved | `@CreationTimestamp` imported but not applied to field | Added `@PrePersist` |
| Password hash in API | Security leak | `GET /lobby` returned raw `User` entity | `@JsonIgnore` on sensitive fields |
| XSS in lobby | Crafted username ran JS | `onclick="sendChallenge('${username}')"` inline interpolation | `data-target` + `addEventListener` |
| Ghost users in lobby | Browser close ≠ logout | `SessionDisconnectEvent` handler read null username | Store username on `SessionConnectedEvent` |

---

## Running Locally

```bash
git clone https://github.com/VijayKumarCode/nexus
cd nexus/backend

# Set environment variables
export DB_URL=jdbc:postgresql://localhost:5432/nexus_db
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export RESEND_API_KEY=re_dummy_key
export APP_BASE=http://localhost:8080
export MAIL_FROM=onboarding@resend.dev

mvn spring-boot:run

# Frontend — open nexus/frontend/index.html
# Use Live Server or: python3 -m http.server 5500
```

---

## Author

**Vijay Kumar** — Java Backend Engineer  
[vijaykumarcode.space](https://vijaykumarcode.space) · [linkedin.com/in/vijaykumarcode](https://linkedin.com/in/vijaykumarcode)
