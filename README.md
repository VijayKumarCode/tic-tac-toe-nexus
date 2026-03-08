# Tic-Tac-Toe Nexus

[![Java Version](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Database](https://img.shields.io/badge/Database-PostgreSQL-blue.svg)](https://www.postgresql.org/)

**Tic-Tac-Toe Nexus** is an industry-standard, real-time multiplayer gaming backend built to demonstrate cloud-native distributed systems. This project transitions a legacy peer-to-peer Java Desktop application into a centralized, web-enabled service capable of supporting cross-platform play across multiple devices.

---

## 🚀 Key Features

* **Real-Time Multiplayer**: Uses WebSockets (STOMP) for low-latency game events, replacing traditional polling or raw TCP sockets.
* **Live Presence Tracking**: Implements a PostgreSQL-backed session manager that tracks real-time `is_online` status, ensuring only active players appear in the lobby.
* **Centralized Game Logic**: The server acts as the "Source of Truth" to prevent cheating and synchronize board states across gadgets.
* **Mobile-Ready API**: Architected as a RESTful service to support responsive web frontends for phones, tablets, and desktops.

---

## 🛠️ Tech Stack

* **Backend**: Java 17, Spring Boot 3.4.x.
* **Database**: PostgreSQL (Hosted via Neon.com).
* **Real-time Communication**: Spring WebSocket with STOMP.
* **Configuration**: YAML-based hierarchical properties.
* **Containerization**: Docker (Configured for Render deployment).

---

## ⚙️ Configuration & Deployment

### Environment Variables
To run this project locally or in production (e.g., Render), the following environment variables are required:

| Variable | Description |
| :--- | :--- |
| `DB_URL` | The JDBC URL for your PostgreSQL instance (include `?sslmode=require` for Neon) |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `PORT` | The port the application listens on (default: 8080) |

---

## 🏗️ Getting Started

1. **Clone the repository**:
   ```bash
   git clone [https://github.com/VijayKumarCode/tic-tac-toe-nexus.git](https://github.com/VijayKumarCode/tic-tac-toe-nexus.git)
