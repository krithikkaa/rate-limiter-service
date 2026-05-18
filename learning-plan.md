# Learning Plan — API Rate Limiter Project
### For: Krithika Shetty | Target: 8–12 LPA Backend Java Roles

---

## Overview

This plan gets you from "I understand the project" to "I can explain every line in an interview."  
Total time: **3–4 weekends** (roughly 20 hours).

---

## Week 1 — Redis (Days 1–3, ~6 hrs)

Redis is the core engine of this project. Before touching the code, understand it properly.

### What to learn

**Day 1 — Redis fundamentals (2 hrs)**
- What Redis is and why it's not a database replacement
- Data structures: String, List, Set, Sorted Set, Hash
- `SET`, `GET`, `INCR`, `EXPIRE`, `TTL`
- Play in Redis CLI: `docker run -it redis:alpine redis-cli`

```bash
SET counter 0
INCR counter
EXPIRE counter 60
TTL counter
```

**Day 2 — Sorted Sets deeply (2 hrs)**  
The sliding window algorithm uses sorted sets exclusively.
- `ZADD`, `ZRANGE`, `ZRANGEBYSCORE`, `ZREMRANGEBYSCORE`, `ZCARD`
- Understand: score = epoch timestamp, member = unique request ID

```bash
ZADD requests 1716000000000 "req-1"
ZADD requests 1716000060000 "req-2"
ZRANGEBYSCORE requests 1716000000000 1716000060000
ZREMRANGEBYSCORE requests 0 1715999999999
ZCARD requests
```

**Day 3 — Spring Data Redis (2 hrs)**
- `RedisTemplate<String, String>` vs `StringRedisTemplate`
- `opsForValue()` (strings), `opsForZSet()` (sorted sets)
- Key TTL management: why it matters for memory
- Read: `RedisConfig.java` in this project — understand every line

### Resources
- Redis docs: https://redis.io/docs/data-types/
- Free Redis playground: https://try.redis.io/
- Spring Data Redis guide: https://docs.spring.io/spring-data/redis/docs/current/reference/html/

---

## Week 1 — Algorithms (Days 4–5, ~4 hrs)

**Day 4 — Token Bucket (2 hrs)**

Read `TokenBucketService.java` line by line. Draw the flow on paper:

```
Request arrives
     │
     ▼
Calculate elapsed time since last refill
     │
     ▼
Add (elapsed / interval) × refillTokens to bucket
(cap at capacity)
     │
     ▼
Bucket > 0?  ──YES──► Decrement, Allow (200)
     │
     NO
     │
     ▼
Reject (429) with Retry-After header
```

Understand: why is this "burst-friendly"? What happens if a client sends 50 requests at once?

**Day 5 — Sliding Window (2 hrs)**

Read `SlidingWindowService.java` line by line. Draw the flow:

```
Request arrives at time T
     │
     ▼
Remove all entries with score < (T - windowSize) from sorted set
     │
     ▼
Count remaining entries
     │
     ▼
Count < maxRequests? ──YES──► Add T to sorted set, Allow (200)
     │
     NO
     │
     ▼
Find oldest entry timestamp → calculate retry-after
Reject (429)
```

Key insight to articulate: "The sliding window always looks at exactly the last N seconds, not aligned to a clock boundary."

---

## Week 2 — Supabase Setup + Project Running (Days 1–2, ~4 hrs)

**Day 1 — Supabase setup (2 hrs)**

1. Create account at supabase.com
2. Create project named `rate-limiter`
3. Go to Settings → Database → Connection Pooling → copy the JDBC URL
4. Note your password
5. Open Supabase Table Editor after running the app to verify tables were created
6. Browse `api_clients`, `api_usage_logs`, `rate_limit_alerts` tables

**Day 2 — Run the project end to end (2 hrs)**

```bash
# Start Redis
docker-compose up -d redis

# Set env vars
export SUPABASE_DB_URL=jdbc:postgresql://...
export SUPABASE_DB_USER=postgres
export SUPABASE_DB_PASSWORD=yourpassword

# Run
mvn spring-boot:run
```

Verify everything works:
1. Open Swagger: http://localhost:8080/swagger-ui.html
2. Register a client via `POST /api/v1/clients`
3. Hit `GET /api/v1/demo/ping` with your API key
4. Watch `X-RateLimit-Remaining` count down
5. Hit the limit — see 429
6. Open Supabase dashboard → see rows in `api_usage_logs`

---

## Week 2 — GitHub Setup (Day 3, ~2 hrs)

**Push the project to GitHub**

```bash
cd rate-limiter-service
git init
git add .
git commit -m "feat: initial rate limiter implementation

- Token Bucket and Sliding Window algorithms via Redis
- API key management stored in Supabase (PostgreSQL)
- Usage analytics and alert system
- Swagger UI, Docker, GitHub Actions CI"

git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/rate-limiter-service.git
git push -u origin main
```

**Set up GitHub Actions secrets** (for CI to work):
- Go to repo → Settings → Secrets → Actions
- Add: nothing needed for tests (uses H2 + embedded Redis)

**Check Actions tab** — CI should run automatically on push.

**Add these to your README**:
- Add a CI badge: `![CI](https://github.com/YOUR_USERNAME/rate-limiter-service/actions/workflows/ci.yml/badge.svg)`

---

## Week 3 — Deploy to Railway (Day 1–2, ~3 hrs)

**Why Railway**: Free tier, auto-deploys from GitHub, Redis plugin built-in.

**Steps:**
1. Go to railway.app → Login with GitHub
2. New Project → Deploy from GitHub repo → select `rate-limiter-service`
3. Add Plugin → Redis (Railway provides the host/port automatically)
4. Add environment variables:
   - `SUPABASE_DB_URL`
   - `SUPABASE_DB_USER`
   - `SUPABASE_DB_PASSWORD`
   - `REDIS_HOST` → use Railway's internal Redis host
   - `REDIS_PORT` → 6379
5. Railway will auto-build from your Dockerfile
6. Your API will have a public URL like `https://rate-limiter-service-production.up.railway.app`

**Update README** with your live URL and a demo section.

---

## Week 3 — Interview Prep (Days 3–5, ~4 hrs)

Practice answering these out loud. These WILL come up.

### Algorithm Questions

**"Explain your Token Bucket implementation"**
> Walk through `TokenBucketService.java`. Explain: capacity, refill rate, how you calculate elapsed time, why you cap at capacity, what happens when bucket is empty.

**"Why did you use Redis sorted sets for sliding window?"**
> Score = epoch timestamp enables ZRANGEBYSCORE to efficiently query a time range. ZREMRANGEBYSCORE clears old entries in O(log N + M). This avoids needing a cron job or manual cleanup.

**"What's the time complexity of your sliding window?"**
> ZADD: O(log N). ZREMRANGEBYSCORE: O(log N + M) where M = expired entries. ZCARD: O(1). So each request is O(log N) amortised.

**"How would you handle Redis being down?"**
> Wrap Redis calls in try-catch. Decide: fail-open (allow all requests, degrade gracefully) or fail-closed (reject all, protect backend). In production, I'd use Resilience4j circuit breaker with a fallback to fail-open and alert the ops team.

### Design Questions

**"How would you scale this to 1 million requests/second?"**
> Redis Cluster (sharding by API key). Multiple Spring Boot instances behind a load balancer. Stateless service — Redis holds all state. Supabase connection pool tuning. Consider write-ahead log for usage analytics instead of synchronous inserts.

**"Why async logging to Supabase?"**
> The rate-limit decision should not be blocked by a database write. At 1000 req/s, synchronous DB writes would add 5–20ms latency per request. `@Async` runs the persistence on a separate thread pool, keeping the response path under 2ms.

**"How is this different from what you built at TCS?"**
> At TCS, I implemented AML rules inside a payment system — rules were business logic tied to a specific domain. Here, the rate limiter is infrastructure-level — it protects APIs regardless of what they do. The key skills I applied differently: Redis data structures (never used in payment work), algorithm design (token bucket refill calculation), and designing a system where the critical path (rate check) is completely decoupled from analytics persistence.

---

## Resume Update Checklist

After completing the project, add this to your resume:

```
Personal Project: API Rate Limiter Service                        May 2026
• Designed and implemented a production-grade API rate limiting microservice
  supporting Token Bucket and Sliding Window algorithms
• Built Redis-backed counters achieving sub-millisecond rate-limit decisions
  under concurrent load
• Integrated Supabase (PostgreSQL) for API key management and usage analytics
  tracking 100% of requests with async persistence to avoid latency impact
• Containerised with Docker; deployed on Railway with GitHub Actions CI/CD
  pipeline for automated build and test on every push
Tech Stack: Java 17, Spring Boot 3, Redis, PostgreSQL (Supabase), Docker, GitHub Actions
GitHub: github.com/YOUR_USERNAME/rate-limiter-service
```

---

## Quick Reference: Commands You'll Use Daily

```bash
# Start local stack
docker-compose up -d redis

# Run app
mvn spring-boot:run

# Run tests
mvn test

# Watch Redis in real time
docker exec -it rate-limiter-redis redis-cli monitor

# See your rate-limit keys in Redis
docker exec -it rate-limiter-redis redis-cli keys "rate:*"

# Build Docker image
docker build -t rate-limiter-service .

# Git workflow
git add .
git commit -m "feat: your message here"
git push
```
