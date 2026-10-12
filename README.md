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

The **Voting Service** is the heart of the system. It is responsible for processing vote casting securely, ensuring no user can double vote, and maintaining a tamper-proof audit trail using SHA-256 cryptographic hashing (blockchain-inspired). It uses Redis for fast double-vote and idempotency checks and PostgreSQL for absolute data consistency.

### Feature Status
- ✅ Secure vote casting endpoint
- ✅ Double-vote prevention (3 layers: idempotency key, Redis flag, DB constraint), tested with 50 concurrent requests
- ✅ Cryptographic vote hashing (SHA-256) and a per-election hash chain that is safe under concurrency
- ✅ Vote receipt generation and verification
- ✅ Chain validation mechanism to detect tampering
- ✅ Candidate validation via OpenFeign with retry, circuit breaker, bulkhead and cached-status fallback
- ✅ Event-driven architecture (Kafka producers and consumers; events sent after commit)
- ✅ DTO-based request/response separation
- ✅ JWT validation in the service (userId claim) and role-based access
- ✅ Unit + integration tests (66)
- ✅ Observability: `votes_cast_total{outcome=…}` counter, Prometheus metrics, Zipkin tracing (incl. Feign and Kafka), JSON logs, AUDIT log (never records the candidate chosen)

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
     issues JWTs         ▲                 │
                         │                 ▼
                         └─(OpenFeign Sync)┘

         ────────(Kafka Async Events)──────► Result Service (8084)
```

### Cross-Service Communication

| Target / Source | Protocol | Endpoints Called / Topic | Purpose |
|-------------------|----------|--------------------------|---------|
| **User Service** | none at runtime | — | Issues the JWT; this service verifies it locally with the shared secret |
| **Candidate Service** | OpenFeign (sync) | `GET /api/v1/candidates/{id}/validate` | Confirms candidate is active and belongs to the election |
| **Candidate Service** | Kafka (async, consumed) | `candidate.created` / `status-changed` / `deleted` | Keeps the candidate status cache current |
| **Result Service** | Kafka (async, published) | `vote.cast` | Aggregates votes in real time |

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
| Resilience | Resilience4j | 2.2.0 |
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
| `POST` | `/api/v1/votes` | Cast a vote. Body `{candidateId, electionId, idempotencyKey}`. 201 new vote, 200 idempotent replay, 409 already voted, 400 invalid candidate, 503 Candidate Service down and status unknown | `VOTER` |
| `GET` | `/api/v1/votes/{voteId}` | Fetch a vote receipt | owner or `ADMIN` |
| `GET` | `/api/v1/votes/user/{userId}/election/{electionId}` | Check if a user voted | that user or `ADMIN` |
| `GET` | `/api/v1/votes/election/{electionId}` | All votes in an election (paged: `content` + `page`) | `ADMIN` |
| `GET` | `/api/v1/votes/election/{electionId}/count` | Vote counts per candidate | public |
| `POST` | `/api/v1/votes/verify/{voteId}` | Verify hash integrity of a vote | owner or `ADMIN` |
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
    vote_hash       VARCHAR(64) NOT NULL,
    prev_hash       VARCHAR(64),             -- NULL when chaining is off
    timestamp       TIMESTAMP NOT NULL,
    idempotency_key VARCHAR(64),
    status          VARCHAR(20) NOT NULL,    -- CAST / VERIFIED / DISPUTED
    created_at      TIMESTAMP NOT NULL,

    -- Absolute guarantee against double voting at the DB level
    CONSTRAINT uq_user_election UNIQUE (user_id, election_id),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

-- One row per election: the newest hash of its chain, locked (SELECT … FOR UPDATE) while a vote is cast
CREATE TABLE election_chain_heads (
    election_id UUID PRIMARY KEY,
    last_hash   VARCHAR(64) NOT NULL
);
```
Tables are created by Hibernate (`ddl-auto: update`).

### Vote Hashing Logic
```
dataToHash = userId + candidateId + electionId + epochSecondsUtc(timestamp) + (prevHash or "")
voteHash   = SHA_256(dataToHash)   // 64 hex chars
```
Chaining (`VOTING_CHAIN_ENABLED`, default `true`): `prevHash` is the election's previous vote hash (`GENESIS` for the
first vote). Votes in one election are linked one at a time under a row lock; different elections never wait on each other.

### Double-vote defence
1. Idempotency key (per user): a retry returns the original receipt.
2. Redis flag `vot:voted:{userId}:{electionId}`: fast rejection.
3. DB unique `(user_id, election_id)`: the final guarantee under races.

Redis flags and the `vote.cast` event are written only after the transaction commits.

### Candidate Service outages
Candidate validation is wrapped in Resilience4j: 3 retries, a circuit breaker and a bulkhead of 50 calls. If the
Candidate Service stays down, the vote is decided from a Redis copy of candidate statuses, kept current from
`candidate.*` Kafka events. With no cached status the vote gets a 503.

---

## 📡 Kafka Events Published

### `vote.cast` Topic
Published after every successful vote. Consumed by the Result Service for real-time tallying.

```json
{
  "voteId": "uuid",
  "userId": "uuid",
  "candidateId": "uuid",
  "electionId": "uuid",
  "voteHash": "sha256-hash",
  "timestamp": "2026-05-29T00:00:00"
}
```

**Partition Key:** `electionId` — ensures all votes for the same election are ordered.

### `vote.verified` Topic
Published (key = `voteId`) when a vote's hash is verified as valid.

## 📥 Kafka Events Consumed
`candidate.created`, `candidate.status-changed`, `candidate.deleted` update the candidate status cache used during
Candidate Service outages.

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
   export VOTING_DB_URL=jdbc:postgresql://localhost:5432   # server base URL; /voting_service_db is appended
   export VOTING_DB_USER=postgres
   export VOTING_DB_PASSWORD=yourpassword
   export JWT_SECRET=<same Base64 secret as user-service>
   # optional: REDIS_HOST, REDIS_PORT, KAFKA_BOOTSTRAP_SERVERS, EUREKA_URL, VOTING_CHAIN_ENABLED
   ```

4. **Build and Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

---


## 📈 Observability

| What | How |
|------|-----|
| Metrics | `GET /actuator/prometheus` (tag `application=<service>`); health probes at `/actuator/health/liveness` and `/readiness` |
| Tracing | Micrometer + Brave; trace ids in every log line. Export to Zipkin with `TRACING_SAMPLING` (default 0.0 locally, 0.1 in `prod`) and `ZIPKIN_URL` |
| Logs | Plain console by default; `json-logs` profile = one JSON object per line; `logstash` profile also ships to `LOGSTASH_HOST:LOGSTASH_PORT`. `prod` turns on `json-logs` |
| Audit | `AUDIT` logger lines `action=… actor=… target=… outcome=…` (no passwords, tokens or ballot choices) |

---

## 📝 License

This project is licensed under the MIT License.

---

> **Maintainer:** Vaibhav Jain  
> **Last Updated:** May 2026
