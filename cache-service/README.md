# Cache Service

A production-ready RESTful cache service built on Redis, using **Lettuce** for high-throughput CRUD operations and **Redisson** for distributed primitives (locks, rate limiters).

## Table of Contents

- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
  - [Redis Modes](#redis-modes)
- [API Reference](#api-reference)
- [Docker](#docker)
- [Testing](#testing)
- [Observability](#observability)

---

## Architecture

The service follows **Clean Architecture** with three layers:

```
┌────────────────────────────────────────────────┐
│  API Layer          (api/)                      │
│  Controllers · DTOs · Exception Handling        │
├────────────────────────────────────────────────┤
│  Business Layer     (business/)                 │
│  Services · Domain Models · Business Rules      │
├────────────────────────────────────────────────┤
│  Infrastructure     (infrastructure/)           │
│  Redis Config · Repository Impl · Health        │
└────────────────────────────────────────────────┘
         │ Lettuce              │ Redisson
    ┌────┴────┐           ┌─────┴──────┐
    │  Redis  │           │   Redis    │
    │  CRUD   │           │   Locks   │
    └─────────┘           └────────────┘
```

**Why two Redis clients?**

| Client | Purpose | Strengths |
|--------|---------|-----------|
| **Lettuce** | Cache CRUD (get/set/delete/scan) | Async, non-blocking, connection pooling, cluster-native |
| **Redisson** | Distributed locks, pub/sub | High-level abstractions, reentrant locks, Lua-atomic ops |

---

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Primary Redis Client | Lettuce 6 (via Spring Data Redis) |
| Distributed Ops Client | Redisson 3.27 |
| API Documentation | SpringDoc OpenAPI 3 / Swagger UI |
| Metrics | Micrometer + Prometheus |
| Build | Maven 3.9 |
| Containerization | Docker (multi-stage, layered JAR) |
| Integration Testing | Testcontainers |

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Docker (for Redis and integration tests)

### Option 1: Run with Docker Compose (recommended)

```bash
# Clone and start everything (app + Redis)
git clone <repo-url>
cd cache-service

# Start Redis standalone
docker compose up -d redis

# Run the application locally
cp .env.example .env
SPRING_PROFILES_ACTIVE=standalone mvn spring-boot:run
```

### Option 2: Full Docker Compose stack

```bash
docker compose up -d
# App: http://localhost:8080
# Actuator: http://localhost:8081
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### Option 3: Run with Redis Sentinel (HA)

```bash
docker compose -f docker-compose-sentinel.yml up -d
```

---

## Configuration

All configuration is driven by environment variables. Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

### Key Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `standalone` | Redis mode: `standalone`, `sentinel`, `cluster` |
| `REDIS_HOST` | `localhost` | Redis host (standalone mode) |
| `REDIS_PORT` | `6379` | Redis port (standalone mode) |
| `REDIS_PASSWORD` | _(empty)_ | Redis AUTH password |
| `REDIS_SENTINEL_MASTER` | `mymaster` | Sentinel master name |
| `REDIS_SENTINEL_NODES` | `localhost:26379,...` | Comma-separated sentinel nodes |
| `REDIS_CLUSTER_NODES` | `localhost:7000,...` | Comma-separated cluster nodes |
| `CACHE_NAMESPACE` | `cache-service` | Key prefix (prevents collisions) |
| `CACHE_DEFAULT_TTL` | `3600` | Default TTL in seconds |
| `CACHE_MAX_KEY_LENGTH` | `512` | Max key length in characters |
| `CACHE_MAX_VALUE_SIZE` | `1048576` | Max value size in bytes (1MB) |

### Redis Modes

#### Standalone (development)
```bash
SPRING_PROFILES_ACTIVE=standalone
REDIS_HOST=localhost
REDIS_PORT=6379
```

#### Sentinel (high-availability)
```bash
SPRING_PROFILES_ACTIVE=sentinel
REDIS_SENTINEL_MASTER=mymaster
REDIS_SENTINEL_NODES=sentinel1:26379,sentinel2:26379,sentinel3:26379
```

Requires: 1 Redis master + ≥1 replica + 3 sentinel processes.

#### Cluster (horizontal scaling)
```bash
SPRING_PROFILES_ACTIVE=cluster
REDIS_CLUSTER_NODES=node1:7000,node2:7001,node3:7002,node4:7003,node5:7004,node6:7005
```

Requires: ≥3 master nodes (6 nodes recommended: 3 masters + 3 replicas).

> **Cluster compatibility:** `KEYS` is disabled. The service uses `SCAN` everywhere for non-blocking, cluster-safe key enumeration.

---

## API Reference

Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI spec: `http://localhost:8080/v3/api-docs`

### Cache Endpoints (`/api/v1/cache`)

#### Get a value
```http
GET /api/v1/cache/{key}
```
**Response 200:**
```json
{
  "key": "user:42",
  "value": "{\"name\": \"Alice\"}",
  "ttlSeconds": 3598,
  "hasExpiry": true,
  "retrievedAt": "2024-01-15T10:30:00Z"
}
```
**Response 404:** Key not found

---

#### Set a value
```http
PUT /api/v1/cache/{key}
Content-Type: application/json

{
  "value": "{\"name\": \"Alice\"}",
  "ttlSeconds": 3600
}
```
Use `ttlSeconds: 0` for no expiry (persistent key).

---

#### Delete a key
```http
DELETE /api/v1/cache/{key}
```
Returns **204** if deleted, **404** if not found.

---

#### Check existence
```http
GET /api/v1/cache/{key}/exists
```
Returns **200** if exists, **404** if not.

---

#### Get TTL
```http
GET /api/v1/cache/{key}/ttl
```
```json
{ "ttlSeconds": 900 }
```
TTL values: `-1` = no expiry, `-2` = key not found.

---

#### Update TTL
```http
PATCH /api/v1/cache/{key}/ttl
Content-Type: application/json

{ "ttlSeconds": 7200 }
```
Use `ttlSeconds: 0` to make the key persistent.

---

#### Atomic Counter
```http
POST /api/v1/cache/{key}/increment?delta=1
```
```json
{ "value": 42 }
```

---

#### Scan keys by pattern
```http
GET /api/v1/cache?pattern=user:*
```
```json
{
  "pattern": "user:*",
  "count": 3,
  "keys": ["user:1", "user:2", "user:3"]
}
```

---

#### Delete keys by pattern
```http
DELETE /api/v1/cache?pattern=session:expired:*
```
```json
{ "deleted": 42 }
```

---

#### Bulk Get
```http
POST /api/v1/cache/bulk/get
Content-Type: application/json

{ "keys": ["user:1", "user:2", "user:3"] }
```

---

#### Bulk Set
```http
POST /api/v1/cache/bulk/set
Content-Type: application/json

{
  "entries": {
    "user:1": "Alice",
    "user:2": "Bob"
  },
  "ttlSeconds": 3600
}
```

---

#### Bulk Delete
```http
DELETE /api/v1/cache/bulk
Content-Type: application/json

{ "keys": ["session:a", "session:b"] }
```

---

### Lock Endpoints (`/api/v1/locks`)

#### Acquire a lock
```http
POST /api/v1/locks/{lockKey}/acquire
Content-Type: application/json

{
  "waitTimeMs": 5000,
  "leaseTimeMs": 30000
}
```
**Response 200:** Lock acquired
**Response 409:** Lock not available (timeout)

---

#### Release a lock
```http
DELETE /api/v1/locks/{lockKey}/release
```

---

#### Check lock status
```http
GET /api/v1/locks/{lockKey}/status
```
```json
{
  "lockKey": "order:123",
  "locked": true,
  "heldByCurrentThread": false,
  "holdCount": 0
}
```

---

## Docker

### Build the image
```bash
docker build -t cache-service:1.0.0 .
```

### Run with Docker
```bash
docker run -d \
  --name cache-service \
  -p 8080:8080 \
  -p 8081:8081 \
  -e SPRING_PROFILES_ACTIVE=standalone \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PORT=6379 \
  cache-service:1.0.0
```

### Docker Compose Commands
```bash
# Start all services
docker compose up -d

# Start with Redis Commander UI
docker compose --profile tools up -d

# View logs
docker compose logs -f cache-service

# Stop all
docker compose down

# Stop and remove volumes
docker compose down -v

# Start Sentinel stack
docker compose -f docker-compose-sentinel.yml up -d
```

---

## Testing

### Unit Tests (no Docker required)
```bash
mvn test
```

### Integration Tests (requires Docker)
Integration tests use Testcontainers to spin up a real Redis instance automatically.

```bash
mvn verify -P integration-test
```

### Test Coverage
```bash
mvn test jacoco:report
open target/site/jacoco/index.html
```

---

## Observability

### Health Check
```bash
curl http://localhost:8081/actuator/health
```
```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP",
      "details": {
        "ping": "PONG",
        "version": "7.2.4",
        "uptime_seconds": "3600",
        "used_memory_human": "2.00M",
        "connected_clients": "3"
      }
    }
  }
}
```

### Prometheus Metrics
```bash
curl http://localhost:8081/actuator/prometheus
```

Key metrics:
- `cache.redis.operation_seconds` – Latency per operation type (get/set/delete/scan)
- `cache.redis.operation.errors_total` – Error count by operation and error type
- `cache.hits_total` – Cache hit count
- `cache.misses_total` – Cache miss count
- `jvm.*` – JVM metrics (heap, GC, threads)
- `process.*` – Process metrics (CPU, memory)

### Available Actuator Endpoints
| Endpoint | URL |
|----------|-----|
| Health | `http://localhost:8081/actuator/health` |
| Metrics | `http://localhost:8081/actuator/metrics` |
| Prometheus | `http://localhost:8081/actuator/prometheus` |
| Environment | `http://localhost:8081/actuator/env` |
| Info | `http://localhost:8081/actuator/info` |

---

## Project Structure

```
cache-service/
├── src/
│   ├── main/
│   │   ├── java/com/example/cacheservice/
│   │   │   ├── CacheServiceApplication.java
│   │   │   ├── api/                          # REST Layer
│   │   │   │   ├── controller/
│   │   │   │   │   ├── CacheController.java
│   │   │   │   │   └── LockController.java
│   │   │   │   ├── dto/                      # Request/Response DTOs
│   │   │   │   └── exception/
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   ├── business/                     # Business Layer
│   │   │   │   ├── model/
│   │   │   │   │   └── CacheEntry.java
│   │   │   │   └── service/
│   │   │   │       ├── CacheService.java     # Interface
│   │   │   │       ├── LockService.java      # Interface
│   │   │   │       └── impl/
│   │   │   │           ├── CacheServiceImpl.java
│   │   │   │           └── LockServiceImpl.java
│   │   │   └── infrastructure/               # Data Access Layer
│   │   │       ├── config/
│   │   │       │   ├── RedisProperties.java  # @ConfigurationProperties
│   │   │       │   ├── LettuceConfig.java    # Lettuce connection factory
│   │   │       │   ├── RedissonConfig.java   # Redisson client
│   │   │       │   └── SwaggerConfig.java
│   │   │       ├── health/
│   │   │       │   └── RedisHealthIndicator.java
│   │   │       └── repository/
│   │   │           ├── CacheRepository.java  # Interface (port)
│   │   │           └── impl/
│   │   │               └── LettuceCacheRepository.java
│   │   └── resources/
│   │       ├── application.yml               # Base config
│   │       ├── application-standalone.yml    # Standalone Redis
│   │       ├── application-sentinel.yml      # Redis Sentinel
│   │       ├── application-cluster.yml       # Redis Cluster
│   │       └── logback-spring.xml            # Structured logging
│   └── test/
│       ├── java/com/example/cacheservice/
│       │   ├── api/controller/CacheControllerTest.java  (unit)
│       │   ├── business/service/CacheServiceImplTest.java (unit)
│       │   └── infrastructure/repository/
│       │       └── LettuceCacheRepositoryIntegrationTest.java (integration)
│       └── resources/application-test.yml
├── docker/
│   └── redis/
│       ├── standalone.conf
│       └── sentinel.conf
├── Dockerfile                                # Multi-stage build
├── docker-compose.yml                        # Standalone stack
├── docker-compose-sentinel.yml               # Sentinel HA stack
├── pom.xml
├── .env.example
└── README.md
```
