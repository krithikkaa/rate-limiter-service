# 🚦 API Rate Limiter Service

> A production-grade rate limiting microservice built with **Spring Boot**, **Redis**, and **Supabase (PostgreSQL)**.  
> Features Token Bucket and Sliding Window algorithms, per-client API key management, usage analytics, and alert system.

---

## Architecture

```
  Client Request
       │
       ▼
  ┌─────────────────────────────────┐
  │     RateLimitFilter (Servlet)   │  ← Intercepts every request
  │   Extracts X-API-Key header     │
  └────────────┬────────────────────┘
               │
               ▼
  ┌─────────────────────────────────┐
  │      RateLimiterService         │
  │  1. Validate API key            │──── Supabase (PostgreSQL)
  │  2. Route to algorithm          │       api_clients table
  │  3. Async: log to Supabase      │──── Supabase (PostgreSQL)
  │  4. Async: fire alerts          │       api_usage_logs table
  └────────┬────────────┬───────────┘
           │            │
           ▼            ▼
  ┌──────────────┐  ┌──────────────────┐
  │ Token Bucket │  │  Sliding Window  │
  │   Service    │  │    Service       │
  │              │  │                  │
  │ Redis Keys:  │  │ Redis Key:       │
  │ rate:tb:*    │  │ rate:sw:*        │
  └──────────────┘  └──────────────────┘
           │
           ▼
    Response with headers:
    X-RateLimit-Limit
    X-RateLimit-Remaining
    X-RateLimit-Reset
    X-RateLimit-Algorithm
```

---

## Tech Stack

| Layer         | Technology                    | Why                                      |
|---------------|-------------------------------|------------------------------------------|
| Language      | Java 17                       | LTS, records, switch expressions         |
| Framework     | Spring Boot 3.2               | Industry standard for microservices      |
| Rate Limiting | Redis 7                       | Sub-millisecond counter ops              |
| Database      | Supabase (PostgreSQL)         | Free managed Postgres + REST dashboard   |
| API Docs      | Swagger / OpenAPI             | Self-documenting APIs                    |
| Containerisation | Docker + Docker Compose    | Reproducible local setup                 |
| CI/CD         | GitHub Actions                | Auto-build and test on push              |

---

## Rate Limiting Algorithms

### Token Bucket
- Bucket holds N tokens (capacity)
- Each request consumes 1 token
- Tokens refill at a fixed rate
- **Allows bursting** — good for APIs that need to absorb short spikes
- Used by: AWS API Gateway, Stripe

### Sliding Window
- Tracks timestamps of all requests in a Redis sorted set
- On each request, removes entries older than the window
- Counts remaining; rejects if at limit
- **Strict** — no burst; evenly distributed
- Used by: GitHub API, Cloudflare

---

## Setup

### Prerequisites
- Java 17
- Maven 3.9+
- Docker Desktop
- Supabase account (free at [supabase.com](https://supabase.com))
- Redis Cloud free tier (or use Docker)

---

### Step 1 — Clone the repo

```bash
git clone https://github.com/YOUR_USERNAME/rate-limiter-service.git
cd rate-limiter-service
```

---

### Step 2 — Set up Supabase

1. Go to [supabase.com](https://supabase.com) → New Project
2. Name it `rate-limiter`
3. Go to **Settings → Database → Connection string (JDBC)**
4. Copy the JDBC URL — it looks like:
   ```
   jdbc:postgresql://db.xxxx.supabase.co:5432/postgres
   ```
5. Note your database password (set when creating the project)

Tables are created automatically on first run (`ddl-auto: update`).

---

### Step 3 — Set environment variables

Create a `.env` file (never commit this):

```bash
SUPABASE_DB_URL=jdbc:postgresql://db.xxxx.supabase.co:5432/postgres
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=your_supabase_password
REDIS_HOST=localhost
REDIS_PORT=6379
```

---

### Step 4 — Start Redis locally

```bash
docker-compose up -d redis
```

Verify:
```bash
docker exec -it rate-limiter-redis redis-cli ping
# → PONG
```

---

### Step 5 — Run the application

```bash
# Export env vars (Mac/Linux)
export $(cat .env | xargs)

# Run
mvn spring-boot:run
```

App starts at: http://localhost:8080  
Swagger UI: http://localhost:8080/swagger-ui.html

---

## API Reference

### Register an API Client

```bash
curl -X POST http://localhost:8080/api/v1/clients \
  -H "Content-Type: application/json" \
  -d '{
    "clientName": "My App",
    "email": "you@example.com",
    "algorithm": "SLIDING_WINDOW",
    "tier": "FREE"
  }'
```

Response:
```json
{
  "id": "abc-123",
  "clientName": "My App",
  "apiKey": "rl_live_a1b2c3d4...",
  "tier": "FREE",
  "effectiveLimit": 30
}
```

---

### Hit a Rate-Limited Endpoint

```bash
curl http://localhost:8080/api/v1/demo/ping \
  -H "X-API-Key: rl_live_a1b2c3d4..."
```

Response headers:
```
X-RateLimit-Limit: 30
X-RateLimit-Remaining: 29
X-RateLimit-Reset: 60
X-RateLimit-Algorithm: SLIDING_WINDOW
X-RateLimit-Usage-Pct: 3%
```

When limit exceeded (HTTP 429):
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Max 30 requests per 60s. Retry after 23s."
}
```

---

### View Usage Analytics

```bash
curl http://localhost:8080/api/v1/analytics/usage/rl_live_a1b2c3d4...
```

---

## Tier Limits

| Tier       | Requests / window |
|------------|-------------------|
| FREE       | 30 / min          |
| BASIC      | 60 / min          |
| PRO        | 300 / min         |
| ENTERPRISE | 1000 / min        |
| CUSTOM     | Your override     |

---

## Supabase Tables

After first run, you'll see these in the Supabase dashboard:

- `api_clients` — registered clients and their keys
- `api_usage_logs` — every request (analytics source)
- `rate_limit_alerts` — 80% warning + exceeded events

---

## Running Tests

```bash
mvn test
```

Tests use **H2 in-memory database** and an **embedded Redis** on port 6370 — no external services needed.

---

## Deployment (Railway.app — Free)

1. Push to GitHub
2. Go to [railway.app](https://railway.app) → New Project → Deploy from GitHub
3. Add a Redis plugin from the Railway dashboard
4. Set environment variables:
   - `SUPABASE_DB_URL`
   - `SUPABASE_DB_USER`
   - `SUPABASE_DB_PASSWORD`
   - `REDIS_HOST` (Railway Redis internal host)
   - `REDIS_PORT`
5. Railway auto-builds from your `Dockerfile`

