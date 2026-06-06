-- Async Threat Processor (batch)
-- Processes a batch of messages: CMS increment, sliding window query,
-- distribution update, mitigation check, threshold check, API-level hit count.
-- Returns table: { ipRateBreaches=["idx|count",...], apiCountBreaches=["idx|count",...] }
--
-- ARGV layout:
--   [1]  = number of messages in batch
--   [2]  = CMS epsilon
--   [3]  = CMS delta
--   [4]  = CMS TTL in seconds
--   [5]  = distribution hash TTL in seconds
--   [6]  = number of bucket ranges
--   [7..7+numBuckets-1] = bucket ranges as "label,min,max"
--   Then for each message (block of 8 fields):
--     [offset+0] = ipApiCmsKey
--     [offset+1] = apiKey (e.g., "123|/api/users|GET")
--     [offset+2] = epochMin
--     [offset+3] = rateLimitWindow (minutes)
--     [offset+4] = threshold (-1 means unlimited)
--     [offset+5] = mitigationPeriod (seconds)
--     [offset+6] = apiLevelWindow (minutes, 0 = disabled)
--     [offset+7] = apiLevelThreshold (count, 0 = disabled)

local numMessages = tonumber(ARGV[1])
local epsilon = tonumber(ARGV[2])
local delta = tonumber(ARGV[3])
local cmsTTL = tonumber(ARGV[4])
local distTTL = tonumber(ARGV[5])
local numBuckets = tonumber(ARGV[6])

-- Parse bucket ranges
local buckets = {}
for i = 1, numBuckets do
    local parts = {}
    for p in string.gmatch(ARGV[6 + i], "[^,]+") do
        table.insert(parts, p)
    end
    table.insert(buckets, {
        label = parts[1],
        min = tonumber(parts[2]),
        max = tonumber(parts[3])
    })
end

local function getBucketLabel(count)
    for _, b in ipairs(buckets) do
        if count >= b.min and count <= b.max then
            return b.label
        end
    end
    return "b1"
end

local function getWindowEnd(min, size)
    return math.floor((min - 1) / size + 1) * size
end

local mitigationPrefix = "ratelimit:"
local mitigationSuffix = ":mitigation"
local apiCountTTL = 28800  -- 8 hours

local windowSizes = {5, 15, 30}
local breaches = {}
local apiCountBreaches = {}
local msgOffset = 6 + numBuckets + 1
local fieldsPerMessage = 8

for m = 1, numMessages do
    local base = msgOffset + (m - 1) * fieldsPerMessage
    local ipApiCmsKey = ARGV[base]
    local apiKey = ARGV[base + 1]
    local currentMin = tonumber(ARGV[base + 2])
    local rateLimitWindow = tonumber(ARGV[base + 3])
    local threshold = tonumber(ARGV[base + 4])
    local mitigationPeriod = tonumber(ARGV[base + 5])
    local apiLevelWindow = tonumber(ARGV[base + 6])
    local apiLevelThreshold = tonumber(ARGV[base + 7])

    local cmsKey = "cms|" .. currentMin

    -- 1. Init CMS if not exists
    pcall(function()
        redis.call('CMS.INITBYPROB', cmsKey, epsilon, delta)
    end)

    -- 2. Increment CMS
    redis.call('CMS.INCRBY', cmsKey, ipApiCmsKey, 1)
    redis.call('EXPIRE', cmsKey, cmsTTL)

    -- 3. Sliding window query for rate limit
    local slidingStart = currentMin - rateLimitWindow + 1
    local slidingCount = 0
    for min = slidingStart, currentMin do
        local ok, res = pcall(function()
            return redis.call('CMS.QUERY', "cms|" .. min, ipApiCmsKey)
        end)
        if ok and res and res[1] then
            slidingCount = slidingCount + res[1]
        end
    end

    -- 4. Distribution update for all tumbling windows
    for _, windowSize in ipairs(windowSizes) do
        local windowEnd = getWindowEnd(currentMin, windowSize)
        local windowStart = windowEnd - windowSize + 1

        -- Query CMS count across tumbling window
        local count = 0
        for min = windowStart, windowEnd do
            local ok, res = pcall(function()
                return redis.call('CMS.QUERY', "cms|" .. min, ipApiCmsKey)
            end)
            if ok and res and res[1] then
                count = count + res[1]
            end
        end

        local distKey = "dist|" .. windowSize .. "|" .. windowStart .. "|" .. apiKey
        local apisKey = "distApis|" .. windowSize .. "|" .. windowStart
        local prevBucketHashKey = "prevBuckets|" .. windowSize .. "|" .. windowStart

        -- Initialize dist hash with all buckets at 0 if it doesn't exist
        if redis.call('EXISTS', distKey) == 0 then
            for _, b in ipairs(buckets) do
                redis.call('HSET', distKey, b.label, 0)
            end
        end
        redis.call('EXPIRE', distKey, distTTL)

        local newBucket = getBucketLabel(count)
        local prevBucket = redis.call('HGET', prevBucketHashKey, ipApiCmsKey)

        -- Update bucket counts
        if prevBucket == false then
            -- First time this IP+API is seen in this window
            redis.call('HINCRBY', distKey, newBucket, 1)
        elseif prevBucket ~= newBucket then
            redis.call('HINCRBY', distKey, prevBucket, -1)
            redis.call('HINCRBY', distKey, newBucket, 1)
        end

        redis.call('HSET', prevBucketHashKey, ipApiCmsKey, newBucket)
        redis.call('EXPIRE', prevBucketHashKey, distTTL)

        -- Track this API as active in this window
        redis.call('SADD', apisKey, apiKey)
        redis.call('EXPIRE', apisKey, distTTL)
    end

    -- 5. API-level hit count: INCR per bin + index + threshold check
    local apiCountKey = "apiCount|" .. apiKey .. "|" .. currentMin
    redis.call('INCR', apiCountKey)
    redis.call('EXPIRE', apiCountKey, apiCountTTL)
    redis.call('ZADD', "apiCountIndex", currentMin, apiKey)

    if apiLevelWindow > 0 and apiLevelThreshold > 0 then
        local apiTotal = 0
        for i = currentMin - apiLevelWindow + 1, currentMin do
            local v = redis.call('GET', "apiCount|" .. apiKey .. "|" .. i)
            if v then apiTotal = apiTotal + tonumber(v) end
        end
        if apiTotal >= apiLevelThreshold then
            table.insert(apiCountBreaches, (m - 1) .. "|" .. apiTotal)
        end
    end

    -- 6. IP-level threshold check + mitigation
    if threshold > 0 and slidingCount > threshold then
        local mitigationKey = mitigationPrefix .. ipApiCmsKey .. mitigationSuffix
        local inMitigation = redis.call('EXISTS', mitigationKey)
        if inMitigation == 0 then
            -- Set mitigation period
            redis.call('SETEX', mitigationKey, mitigationPeriod, mitigationPeriod)
            -- Add to breaches
            table.insert(breaches, (m - 1) .. "|" .. slidingCount)
        end
    end
end

return {breaches, apiCountBreaches}
