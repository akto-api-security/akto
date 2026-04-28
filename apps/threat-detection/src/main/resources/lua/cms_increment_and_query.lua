-- CMS Increment + Sliding Window Range Query
-- Increments CMS for current minute, then queries across a sliding window range.
-- Returns the total count across the range (used for rate limiting).
--
-- KEYS[1]    = "cms|{currentMinute}" (key to increment)
-- KEYS[2..N] = "cms|{min}" for each minute in sliding window
--
-- ARGV[1] = ipApiCmsKey (the item string to add/query)
-- ARGV[2] = CMS TTL in seconds (e.g., 28800 for 8 hours)
-- ARGV[3] = CMS epsilon (e.g., 0.01)
-- ARGV[4] = CMS delta / confidence (e.g., 0.01)

local ipApiCmsKey = ARGV[1]
local ttl = tonumber(ARGV[2])
local epsilon = tonumber(ARGV[3])
local delta = tonumber(ARGV[4])

-- Step 1: Create CMS if it doesn't exist (CMS.INITBYPROB errors if already exists)
pcall(function()
    redis.call('CMS.INITBYPROB', KEYS[1], epsilon, delta)
end)

-- Step 2: Increment
redis.call('CMS.INCRBY', KEYS[1], ipApiCmsKey, 1)
redis.call('EXPIRE', KEYS[1], ttl)

-- Step 3: Query across all minutes in the sliding window (KEYS[2..N])
local total = 0
for i = 2, #KEYS do
    local ok, res = pcall(function()
        return redis.call('CMS.QUERY', KEYS[i], ipApiCmsKey)
    end)
    if ok and res and res[1] then
        total = total + res[1]
    end
end

return total
