# Codex / AI Coding Assistant Instructions

> These instructions are for GitHub Copilot, Cursor, or any AI coding assistant
> working on the `rate-limiter-service` project.
> Place this file at the root of your repo — Cursor reads it automatically.

---

## Project Context

This is a **Spring Boot 3.2 / Java 17** microservice implementing API rate limiting.

- **Language**: Java 17 (use records, switch expressions, text blocks where appropriate)
- **Framework**: Spring Boot 3.2 with Spring Data JPA and Spring Data Redis
- **Database**: Supabase (PostgreSQL) via JDBC — never use MySQL syntax
- **Cache**: Redis 7 via `RedisTemplate<String, String>` — never use Jedis directly
- **Build**: Maven 3.9
- **Test DB**: H2 in-memory (`@ActiveProfiles("test")`)

---

## Package Structure

```
com.krithika.ratelimiter
├── config/          RedisConfig, SwaggerConfig
├── controller/      ApiClientController, DemoController, AnalyticsController
├── dto/             Request/Response DTOs (no domain logic here)
├── exception/       ApiKeyNotFoundException, GlobalExceptionHandler
├── filter/          RateLimitFilter (servlet filter, runs before controllers)
├── model/           JPA entities: ApiClient, ApiUsageLog, RateLimitAlert
├── repository/      Spring Data JPA repositories
├── scheduler/       RetentionScheduler
└── service/         RateLimiterService, TokenBucketService, SlidingWindowService,
                     ApiKeyService, UsageAnalyticsService
```

---

## Coding Rules

### Java Style
- Use Lombok: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on all DTOs and entities
- Use `@Slf4j` for logging — never `System.out.println`
- Use `log.debug()` for algorithm steps, `log.info()` for state changes, `log.warn()` for rejections/alerts, `log.error()` for exceptions
- Prefer immutable DTOs — use `@Builder` pattern
- Never use `var` for complex types — keep types explicit for readability

### Spring
- Controllers are thin — delegate all logic to services
- Services are `@Transactional` where they write to DB
- Never inject repositories directly into controllers
- Use constructor injection (Lombok `@RequiredArgsConstructor`) — never `@Autowired` on fields
- Async operations use `@Async` on private methods within the service — never block the main request thread

### Redis
- All Redis keys follow the pattern: `rate:{algorithm-prefix}:{apiKey}:{field}`
  - Token Bucket: `rate:tb:{apiKey}:tokens`, `rate:tb:{apiKey}:last`
  - Sliding Window: `rate:sw:{apiKey}`
- Always set TTL on Redis keys — never leave keys without expiry
- Use `redisTemplate.opsForValue()` for strings, `redisTemplate.opsForZSet()` for sorted sets
- Handle `RedisConnectionFailureException` in the filter — fail-open by default

### Database / Supabase
- Use PostgreSQL-compatible JPQL — never MySQL-specific syntax
- Native queries must work on PostgreSQL (`TO_CHAR`, not `DATE_FORMAT`)
- Always add `@Index` annotations on frequently queried columns
- Use `@CreationTimestamp` and `@UpdateTimestamp` for audit fields — never set them manually
- UUID primary keys: `@GeneratedValue(strategy = GenerationType.UUID)` on `String` IDs

### DTOs
- Never return JPA entities directly from controllers — always use DTOs
- Request DTOs have `@Valid` + bean validation annotations
- Response DTOs are plain POJOs with Lombok

### Error Handling
- Business exceptions extend `RuntimeException` with meaningful messages
- `GlobalExceptionHandler` handles all exceptions — never catch-and-swallow in controllers
- Filter errors write JSON directly via `HttpServletResponse.getWriter()` (cannot use `@RestControllerAdvice` in filters)
- Never leak stack traces in API responses

---

## Algorithm Contracts — Do Not Change

### TokenBucketService.tryConsume(ApiClient)
- Must read `client.getBucketCapacity()`, fall back to `@Value` default if null
- Must set TTL on every Redis write
- Must return `RateLimitResult` with `allowed`, `remaining`, `limit`, `resetInSeconds`, `usagePercent`

### SlidingWindowService.tryConsume(ApiClient)
- Must use `ZREMRANGEBYSCORE` to evict old entries before counting
- Member in sorted set must be unique per request (use timestamp + thread ID + random)
- Must calculate `resetInSeconds` based on oldest entry in window, not full window duration
- Must return same `RateLimitResult` shape as TokenBucketService

---

## What NOT to Generate

- Do not add Spring Security (authentication is API-key only, handled in the filter)
- Do not add Liquibase or Flyway (using `ddl-auto: update` intentionally for simplicity)
- Do not add WebFlux / reactive code (this is a standard blocking servlet app)
- Do not use `@Scheduled` for rate counter cleanup (Redis TTL handles this automatically)
- Do not add a frontend (this is a pure backend microservice)
- Do not use `Optional.get()` without `isPresent()` check — use `orElseThrow()`

---

## Test Conventions

- Test class name: `{ClassUnderTest}Test`
- Use `@SpringBootTest` + `@ActiveProfiles("test")` for integration tests
- Use `@BeforeEach` to clear Redis keys before each test using the exact key pattern
- Use AssertJ (`assertThat`) not JUnit `assertEquals`
- Test naming: `@DisplayName("Should do X when Y")`
- Mock external calls with `@MockBean` where needed
- Never use `Thread.sleep()` in tests — design around it

---

## Commit Message Convention (Conventional Commits)

```
feat: add sliding window algorithm with Redis sorted sets
fix: correct TTL calculation in token bucket refill
docs: update README with Railway deployment steps
test: add unit tests for token bucket edge cases
refactor: extract tier limit config to constants
chore: update dependencies in pom.xml
```

---

## Environment Variables Expected

```bash
SUPABASE_DB_URL       # jdbc:postgresql://db.xxx.supabase.co:5432/postgres
SUPABASE_DB_USER      # postgres
SUPABASE_DB_PASSWORD  # your-supabase-password
REDIS_HOST            # localhost (dev) or Railway internal host (prod)
REDIS_PORT            # 6379
```

Never hardcode these. Never commit `.env` files.

---

## When Adding a New Feature, Follow This Checklist

- [ ] Entity (if new DB table) in `model/` with proper JPA annotations
- [ ] Repository in `repository/` with named queries or `@Query`
- [ ] DTO in `dto/` for request and response
- [ ] Service in `service/` with business logic
- [ ] Controller in `controller/` with Swagger `@Operation` annotations
- [ ] Exception handling in `GlobalExceptionHandler` if new exception type
- [ ] Test class in `src/test/`
- [ ] Update README if the API surface changes
