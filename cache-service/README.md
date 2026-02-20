# Cache Service

A production-ready caching microservice built on **Spring Boot 3**, **Redis**, **Lettuce**, and **Redisson**.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Cache Service                            │
│                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐   │
│  │  REST API    │   │  Service     │   │  Redis Clients   │   │
│  │  Controllers │──▶│  Layer       │──▶│                  │   │
│  │              │   │              │   │  ┌─────────────┐ │   │
│  │ /api/v1/cache│   │ CacheService │   │  │  Lettuce    │ │   │
│  │ /api/v1/locks│   │ LockService  │   │  │  (CRUD/Bulk)│ │   │
│  └──────────────┘   └──────────────┘   │  └─────────────┘ │   │
│                                        │  ┌─────────────┐ │   │
│  ┌──────────────┐                      │  │  Redisson   │ │   │
│  │  Actuator    │                      │  │  (Locks)    │ │   │
│  │  /health     │                      │  └─────────────┘ │   │
│  │  /metrics    │                      └──────────────────┘   │
│  │  /prometheus │                                              │
│  └──────────────┘                                              │
└─────────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
     ┌──────────────┐ ┌─────────────┐ ┌────────────────┐
     │  Standalone  │ │  Sentinel   │ │    Cluster     │
     │  Redis Node  │ │  (HA mode)  │ │  (Sharded mode)│
     └──────────────┘ └─────────────┘ └────────────────┘
```

### Redis Client Responsibilities

| Client | Purpose | Operations |
|--------|---------|------------|
| **Lettuce** (via Spring Data Redis) | Primary cache CRUD | GET, SET, DEL, SCAN, MGET, MSET, INCR, Hash commands |
| **Redisson** | Distributed primitives | Distributed locks (RLock), force-unlock |

### Supported Redis Topologies

| Mode | Config value | Use case |
|------|-------------|----------|
| Standalone | `standalone` | Dev/test; single Redis node |
| Sentinel | `sentinel` | Production HA; auto-failover |
| Cluster | `cluster` | Production scale-out; horizontal sharding |

Set the mode via `REDIS_MODE` environment variable.

---

## Quick Start

### Prerequisites

- Java 17+
- Docker & Docker Compose
- Maven 3.8+ (or use the Maven Wrapper)

### 1. Clone and configure

```bash
git clone <repo-url>
cd cache-service
cp .env.example .env
# Edit .env if needed (defaults work for local dev)
```

### 2. Start with Docker Compose (Standalone Redis)

```bash
docker compose up
```

This starts:
- Redis 7 on `localhost:6379`
- Cache Service on `localhost:8080`

### 3. Start with Redis Sentinel (HA)

```bash
docker compose --profile sentinel up
```

This starts 1 Redis master, 2 replicas, 3 Sentinel nodes, and the service on port `8081`.

### 4. Build the JAR manually

```bash
./mvnw clean package -DskipTests
java -jar target/cache-service-1.0.0.jar
```

---

## API Reference

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
OpenAPI spec: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Cache Operations (`/api/v1/cache`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/{key}` | Get a cached value |
| `PUT` | `/{key}` | Set a value (with optional TTL) |
| `PUT` | `/{key}/nx` | Set only if key does not exist (SET NX EX) |
| `DELETE` | `/{key}` | Delete a key |
| `GET` | `/{key}/exists` | Check if a key exists |
| `GET` | `/{key}/ttl` | Get remaining TTL in seconds |
| `PATCH` | `/{key}/ttl?seconds=N` | Update TTL of an existing key |
| `DELETE` | `/{key}/ttl` | Remove expiry (make key persistent) |
| `GET` | `/scan?pattern=user:*&limit=100` | Scan keys by glob pattern (SCAN, not KEYS) |
| `DELETE` | `/scan?pattern=user:*` | Delete all keys matching pattern |
| `POST` | `/bulk/get` | Bulk fetch multiple keys (MGET) |
| `POST` | `/bulk/set` | Bulk write multiple keys (MSET / pipeline) |
| `GET` | `/{key}/hash` | Get all hash fields (HGETALL) |
| `GET` | `/{key}/hash/{field}` | Get a single hash field (HGET) |
| `PUT` | `/{key}/hash/{field}` | Set a hash field (HSET) |
| `DELETE` | `/{key}/hash/{field}` | Delete a hash field (HDEL) |
| `POST` | `/{key}/increment` | Increment counter (INCR) |
| `POST` | `/{key}/increment/{delta}` | Increment counter by delta (INCRBY) |
| `POST` | `/{key}/decrement` | Decrement counter (DECR) |
| `GET` | `/info` | Get Redis server info |

### Distributed Locks (`/api/v1/locks`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/{lockName}` | Acquire a distributed lock |
| `DELETE` | `/{lockName}` | Release a distributed lock |
| `GET` | `/{lockName}` | Check lock status |
| `DELETE` | `/{lockName}/force` | Force-release (admin) |

### Example Requests

**Set a key with TTL:**
```bash
curl -X PUT http://localhost:8080/api/v1/cache/user:42 \
  -H 'Content-Type: application/json' \
  -d '{"value": "Alice", "ttl_seconds": 3600}'
```

**Get a key:**
```bash
curl http://localhost:8080/api/v1/cache/user:42
```

**Bulk set:**
```bash
curl -X POST http://localhost:8080/api/v1/cache/bulk/set \
  -H 'Content-Type: application/json' \
  -d '{"entries": {"k1":"v1","k2":"v2"}, "ttl_seconds": 600}'
```

**Acquire a distributed lock:**
```bash
curl -X POST http://localhost:8080/api/v1/locks/order-processing \
  -H 'Content-Type: application/json' \
  -d '{"wait_time_ms": 0, "lease_time_ms": 30000}'
```

---

## Configuration

All settings are driven by environment variables. See `.env.example` for the full list.

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_MODE` | `standalone` | `standalone` / `sentinel` / `cluster` |
| `REDIS_HOST` | `localhost` | Redis host (standalone mode) |
| `REDIS_PORT` | `6379` | Redis port (standalone mode) |
| `REDIS_PASSWORD` | _(empty)_ | Redis AUTH password |
| `REDIS_SENTINEL_MASTER` | `mymaster` | Sentinel master name |
| `REDIS_SENTINEL_NODES` | `localhost:26379` | Comma-separated sentinel nodes |
| `REDIS_CLUSTER_NODES` | `localhost:7000,...` | Comma-separated cluster nodes |
| `REDIS_POOL_MAX_ACTIVE` | `16` | Lettuce pool max connections |
| `SERVER_PORT` | `8080` | HTTP server port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile |

---

## Health & Observability

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Health status including Redis connectivity |
| `GET /actuator/metrics` | All Micrometer metrics |
| `GET /actuator/prometheus` | Prometheus-format metrics scrape endpoint |
| `GET /actuator/loggers` | View/change log levels at runtime |

### Prometheus Scrape Config

```yaml
scrape_configs:
  - job_name: cache-service
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['cache-service:8080']
```

---

## Running Tests

```bash
# Unit tests only (fast, no Redis needed)
./mvnw test

# Integration tests (requires Docker for Testcontainers)
./mvnw verify

# All tests
./mvnw verify -Psurefire
```

---

## Project Structure

```
cache-service/
├── src/
│   ├── main/java/com/example/cacheservice/
│   │   ├── CacheServiceApplication.java       # Entry point
│   │   ├── config/
│   │   │   ├── RedisConfig.java               # Lettuce connection factory
│   │   │   ├── RedissonConfig.java            # Redisson client
│   │   │   ├── RedisProperties.java           # Typed config properties
│   │   │   └── OpenApiConfig.java             # Swagger config
│   │   ├── controller/
│   │   │   ├── CacheController.java           # Cache REST endpoints
│   │   │   └── LockController.java            # Lock REST endpoints
│   │   ├── service/
│   │   │   ├── CacheService.java              # Cache interface
│   │   │   ├── LockService.java               # Lock interface
│   │   │   └── impl/
│   │   │       ├── LettuceCacheServiceImpl.java   # Lettuce implementation
│   │   │       └── RedissonLockServiceImpl.java   # Redisson implementation
│   │   ├── dto/
│   │   │   ├── request/                       # Request DTOs
│   │   │   └── response/                      # Response DTOs (ApiResponse envelope)
│   │   ├── exception/
│   │   │   ├── CacheKeyNotFoundException.java
│   │   │   ├── LockAcquisitionException.java
│   │   │   └── GlobalExceptionHandler.java    # Centralised error handling
│   │   └── health/
│   │       └── RedisHealthIndicator.java      # Custom health check
│   └── main/resources/
│       ├── application.yml                    # Base config
│       ├── application-dev.yml
│       ├── application-staging.yml
│       ├── application-prod.yml
│       └── logback-spring.xml                 # Structured logging
├── src/test/
│   └── java/com/example/cacheservice/
│       ├── controller/CacheControllerTest.java    # Web-layer unit tests
│       ├── service/LettuceCacheServiceImplTest.java # Service unit tests
│       └── integration/CacheIntegrationTest.java  # Testcontainers integration tests
├── docker/
│   └── sentinel.conf                          # Sentinel config for Docker Compose
├── Dockerfile                                 # Multi-stage Docker build
├── docker-compose.yml                         # Local dev (standalone + sentinel profiles)
├── .env.example                               # Environment variable reference
└── pom.xml
```

---

## Design Decisions

1. **Lettuce + Redisson coexistence**: Lettuce (the Spring Data Redis default) handles all cache CRUD operations; Redisson is wired alongside it exclusively for distributed locking. Both clients are configured with matching topology settings to point at the same Redis deployment.

2. **`SCAN` over `KEYS`**: All pattern-based key enumeration uses the non-blocking `SCAN` cursor internally to avoid stalling the Redis event loop under large keyspaces.

3. **Pipeline for bulk TTL writes**: `msetWithTtl` pipelines `SET + EXPIRE` commands in a single connection round-trip instead of issuing one request per key.

4. **Redisson watch-dog**: Redisson's RLock automatically extends lease times for long-running operations, preventing premature lock expiry without requiring callers to manually renew.

5. **`ApiResponse<T>` envelope**: Every API response is wrapped in a consistent envelope with `success`, `message`, `data`, `error_code`, and `timestamp` fields, making client-side error handling uniform.
