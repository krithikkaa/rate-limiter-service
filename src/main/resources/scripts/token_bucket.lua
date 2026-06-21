-- ─────────────────────────────────────────────────────────────────────────────
-- Token Bucket — atomic refill + consume in a single round trip.
--
-- Redis executes this whole script atomically (single-threaded command loop),
-- so the read-modify-write below cannot interleave with another request for the
-- same key. This removes the race condition present in a multi-call Java version.
--
-- KEYS[1] = tokens key      (rate:tb:{apiKey}:tokens)
-- KEYS[2] = last-refill key (rate:tb:{apiKey}:last)
-- ARGV[1] = capacity
-- ARGV[2] = refillTokens
-- ARGV[3] = refillIntervalSeconds
-- ARGV[4] = now (epoch seconds)
-- ARGV[5] = ttlSeconds
--
-- Returns: { allowed(0|1), remainingTokens, nextRefillInSeconds }  (as strings)
-- ─────────────────────────────────────────────────────────────────────────────

local capacity       = tonumber(ARGV[1])
local refillTokens   = tonumber(ARGV[2])
local refillInterval = tonumber(ARGV[3])
local now            = tonumber(ARGV[4])
local ttl            = tonumber(ARGV[5])

local tokens = tonumber(redis.call('GET', KEYS[1]))
local last   = tonumber(redis.call('GET', KEYS[2]))

-- First request for this key: start with a full bucket.
if tokens == nil or last == nil then
    tokens = capacity
    last   = now
end

-- Refill based on whole intervals elapsed since the last refill.
local elapsed = now - last
if elapsed < 0 then elapsed = 0 end
local intervals = math.floor(elapsed / refillInterval)
if intervals > 0 then
    tokens = math.min(capacity, tokens + (intervals * refillTokens))
    last   = last + (intervals * refillInterval)
end

-- Try to consume one token.
local allowed = 0
if tokens > 0 then
    tokens  = tokens - 1
    allowed = 1
end

-- Persist new state with a sliding TTL so idle keys self-expire.
redis.call('SET', KEYS[1], tokens, 'EX', ttl)
redis.call('SET', KEYS[2], last,   'EX', ttl)

local nextRefillIn = refillInterval - (now - last)
if nextRefillIn < 0 then nextRefillIn = 0 end

return { tostring(allowed), tostring(tokens), tostring(nextRefillIn) }
