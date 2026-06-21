-- ─────────────────────────────────────────────────────────────────────────────
-- Sliding Window (log) — evict + count + admit in a single atomic round trip.
--
-- Without this script the evict (ZREMRANGEBYSCORE), count (ZCARD) and admit
-- (ZADD) are three separate calls; two concurrent requests can both read the
-- same count and both be admitted, breaching the limit. Running them in one Lua
-- script makes the check-and-admit atomic.
--
-- KEYS[1] = window key (rate:sw:{apiKey})  — a sorted set, score = epoch ms
-- ARGV[1] = now (epoch ms)
-- ARGV[2] = windowMs
-- ARGV[3] = maxRequests
-- ARGV[4] = ttlSeconds
-- ARGV[5] = unique member id for this request
--
-- Returns: { allowed(0|1), remaining, retryAfterSeconds }  (as strings)
-- ─────────────────────────────────────────────────────────────────────────────

local now        = tonumber(ARGV[1])
local windowMs   = tonumber(ARGV[2])
local maxReq     = tonumber(ARGV[3])
local ttl        = tonumber(ARGV[4])
local member     = ARGV[5]
local windowStart = now - windowMs

-- Drop everything older than the window.
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, windowStart)

local count = redis.call('ZCARD', KEYS[1])
local allowed = 0
local remaining = 0
local retryAfter = 0

if count < maxReq then
    redis.call('ZADD', KEYS[1], now, member)
    redis.call('EXPIRE', KEYS[1], ttl)
    allowed = 1
    remaining = maxReq - (count + 1)
else
    -- Rejected: work out when the oldest in-window request expires.
    local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
    if oldest and oldest[2] then
        retryAfter = math.ceil((windowMs - (now - tonumber(oldest[2]))) / 1000)
        if retryAfter < 1 then retryAfter = 1 end
    else
        retryAfter = math.ceil(windowMs / 1000)
    end
end

return { tostring(allowed), tostring(remaining), tostring(retryAfter) }
