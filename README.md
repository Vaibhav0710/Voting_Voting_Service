# 🗳️ Voting Service

> Part of the **Blockchain-Inspired Online Voting System** — a production-grade, scalable microservices platform.

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Kafka](https://img.shields.io/badge/Kafka-Event%20Streaming-black.svg)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## 📌 Overview

The **Voting Service** is the heart of the system. It is responsible for processing vote casting securely, ensuring no user can double vote, and maintaining a tamper-proof audit trail using SHA-256 cryptographic hashing (blockchain-inspired). It leverages Redis for high-speed idempotency and rate-limiting, and PostgreSQL for absolute data consistency.

### Feature Status
- ✅ Secure vote casting endpoint
- ✅ Double-vote prevention (3 layers: Redis Set, DB Constraint, Idempotency keys)
- ✅ Cryptographic vote hashing (SHA-256)
- ✅ Vote receipt generation and validation
- ✅ Chain validation mechanism to detect tampering
- ✅ Cross-service integration with User & Candidate services (via OpenFeign)
- ✅ Event-driven architecture (Kafka producers and consumers)
- ✅ DTO-based request/response separation
- ✅ JWT integration and validation
- 🔜 Unit + Integration tests
- 🔜 Redis Caching implementations (mostly done)
- 🔜 Actuator & Prometheus metrics

---

## 🏗️ Architecture

```
                    ┌──────────────────┐
                    │   API Gateway    │
                    │ (Spring Cloud)   │
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
    ┌─────────▼──┐  ┌───────▼────┐  ┌──────▼──────┐
    │  User      │  │ Candidate  │  │ ★VOTING★   │
    │  Service   │  │ Service    │  │ SERVICE     │
    │  (8081)    │  │ (8082)     │  │ (8083)      │
    └────────────┘  └────────────┘  └──────┬──────┘
         ▲               ▲                 │
         │               │                 ▼
         └───────(OpenFeign Sync)──────────┘

         ────────(Kafka Async Events)──────► Result Service (8084)
```

### Cross-Service Communication

| Target / Consumer | Protocol | Endpoints Called / Topic | Purpose |
|-------------------|----------|--------------------------|---------|
| **User Service** | OpenFeign (sync) | `GET /api/v1/auth/validate` | Validates JWT token |
| **Candidate Service** | OpenFeign (sync) | `GET /api/v1/candidates/{id}/validate` | Confirms candidate is active and belongs to the election |
| **Voting Service** | Kafka (async) | Listens to `candidate.status-changed` | Reacts to candidate disqualifications |
| **Result Service** | Kafka (async) | Listens to `vote.cast` | Aggregates votes in real-time |

---

## 🛠️ Tech Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Java | 17 |
| Framework | Spring Boot | 3.3.6 |
| Cloud | Spring Cloud | 2023.0.3 |
| Database | PostgreSQL (dedicated) | 16 |
| Caching | Redis | 7.x |
| Event Bus | Apache Kafka | 3.x |
| ORM | Spring Data JPA / Hibernate | — |
| Security | Spring Security + JWT | — |
| Inter-Service | OpenFeign | — |
| Build Tool | Maven | 3.8+ |
| Boilerplate | Lombok | — |

---

## 🔌 API Reference

### Base URL
```
http://localhost:8083/api/v1/votes
```

### Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `POST` | `/api/v1/votes` | Cast a vote (requires `idempotencyKey` header) | `VOTER` |
| `GET` | `/api/v1/votes/{voteId}` | Fetch a vote receipt | `VOTER` / `ADMIN` |
| `GET` | `/api/v1/votes/user/{userId}/election/{electionId}` | Check if user voted | INTERNAL |
| `GET` | `/api/v1/votes/election/{electionId}` | Get all votes in an election | `ADMIN` |
| `GET` | `/api/v1/votes/election/{electionId}/count` | Get total counts (fallback method) | `ADMIN` |
| `POST` | `/api/v1/votes/verify/{voteId}` | Verify hash integrity of a specific vote | `ADMIN` |
| `POST` | `/api/v1/votes/chain/validate/{electionId}` | Validate the entire chain of votes | `ADMIN` |

---

## 🗄️ Database Schema

**Database:** `voting_service_db` (isolated — each microservice owns its data)

```sql
CREATE TABLE votes (
    id              BIGSERIAL PRIMARY KEY,
    external_id     UUID UNIQUE NOT NULL,
    user_id         UUID NOT NULL,
    candidate_id    UUID NOT NULL,
    election_id     UUID NOT NULL,
    vote_hash       VARCHAR(256) NOT NULL,
    prev_hash       VARCHAR(256) NOT NULL,
    timestamp       TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'CAST',
    
    -- Absolute guarantee against double voting at the DB level
    CONSTRAINT uq_user_election UNIQUE (user_id, election_id)
);
```

### Vote Hashing Logic
```
dataToHash = userId + candidateId + electionId + timestamp + prevHash
voteHash = SHA_256(dataToHash)
```

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL 14+
- Redis 6+
- Apache Kafka 3+ (and Zookeeper)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Vaibhav0710/Voting_Voting_Service.git
   cd voting-service
   ```

2. **Start Infrastructure (Kafka, Zookeeper, Redis)**
   *(A `docker-compose.yml` is provided at the root of the project to spin up infrastructure)*
   ```bash
   docker-compose up -d
   ```

3. **Set environment variables**
   ```bash
   export VOTING_DB_URL=jdbc:postgresql://localhost:5432/voting_service_db
   export VOTING_DB_USER=postgres
   export VOTING_DB_PASSWORD=yourpassword
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   export JWT_SECRET=your_jwt_secret
   ```

4. **Build and Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

---

## 📝 License

This project is licensed under the MIT License.

---

> **Maintainer:** Vaibhav Jain  
> **Last Updated:** May 2026
