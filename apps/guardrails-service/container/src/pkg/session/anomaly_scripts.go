package session

import redis "github.com/redis/go-redis/v9"

// ──────────────────────────────────────────────────────────────────────────────
// luaCounter — atomic session-level counter with threshold + once-per-session flag
// ──────────────────────────────────────────────────────────────────────────────
//
// Used for both tool-call counting and error counting. Each call increments a
// simple integer counter per session. When the count reaches the threshold and
// the fired key doesn't exist, signals an anomaly and sets a permanent fired
// flag (same TTL as the counter) so it only fires once per session.
//
// Redis keys used:
//   KEY "anomaly:tc:{sessionID}"           — STRING integer counter of total tool calls
//   KEY "anomaly:err:{sessionID}"          — STRING integer counter of total errors
//   KEY "anomaly:fired:{sessionID}:{type}" — STRING "1" (once-per-session flag)
//
// Step-by-step example (limit=100):
//
//   1. First tool call: INCR → count=1, below limit → return 0
//   2. After 100 calls: INCR → count=100, fired key absent → SET fired, return 1
//   3. Call 101+: count>100, fired key EXISTS → return 0 (already reported)
//
// KEYS[1] = counter key
// KEYS[2] = fired key
// ARGV[1] = limit (threshold)
// ARGV[2] = counter TTL (seconds, for auto-cleanup of inactive sessions)
//
// Returns: 1 if anomaly should be reported, 0 otherwise.
var luaCounter = redis.NewScript(`
local key = KEYS[1]
local firedKey = KEYS[2]
local limit = tonumber(ARGV[1])
local counterTTL = tonumber(ARGV[2])

-- Increment and refresh TTL
local count = redis.call('INCR', key)
redis.call('EXPIRE', key, counterTTL)

-- Check threshold + once-per-session flag
if count >= limit then
  local exists = redis.call('EXISTS', firedKey)
  if exists == 0 then
    redis.call('SET', firedKey, '1', 'EX', counterTTL)
    return 1
  end
  return 2
end
return 0
`)
