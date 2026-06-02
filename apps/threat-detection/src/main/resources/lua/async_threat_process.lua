-- Async Threat Processor (batch)
-- Processes a batch of messages: CMS increment, sliding window query,
-- distribution update, mitigation check, threshold check.
-- Returns list of breach items as "ipApiCmsKey|count" strings.
--
-- ARGV layout:
--   [1]  = number of messages in batch
--   [2]  = CMS epsilon
--   [3]  = CMS delta
--   [4]  = CMS TTL in seconds
--   [5]  = distribution hash TTL in seconds
--   [6]  = number of bucket ranges
--   [7..7+numBuckets-1] = bucket ranges as "label,min,max"
--   Then for each message (block of 6 fields):
--     [offset+0] = ipApiCmsKey
--     [offset+1] = apiKey (e.g., "123|/api/users|GET")
--     [offset+2] = epochMin
--     [offset+3] = rateLimitWindow (minutes)
--     [offset+4] = threshold (-1 means unlimited)
--     [offset+5] = mitigationPeriod (seconds)

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

local windowSizes = {5, 15, 30}
local breaches = {}
local msgOffset = 6 + numBuckets + 1

for m = 1, numMessages do
    local base = msgOffset + (m - 1) * 6
    local ipApiCmsKey = ARGV[base]
    local apiKey = ARGV[base + 1]
    local currentMin = tonumber(ARGV[base + 2])
    local rateLimitWindow = tonumber(ARGV[base + 3])
    local threshold = tonumber(ARGV[base + 4])
    local mitigationPeriod = tonumber(ARGV[base + 5])

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

        -- Initialize distribution hash if it doesn't exist
        if redis.call('EXISTS', distKey) == 0 then
            for _, b in ipairs(buckets) do
                redis.call('HSET', distKey, b.label, 0)
            end
        end
        redis.call('EXPIRE', distKey, distTTL)

        local newBucket = getBucketLabel(count)
        local oldBucket = getBucketLabel(count - 1)

        -- Update bucket counts
        if count == 1 then
            redis.call('HINCRBY', distKey, newBucket, 1)
        elseif oldBucket ~= newBucket then
            redis.call('HINCRBY', distKey, oldBucket, -1)
            redis.call('HINCRBY', distKey, newBucket, 1)
        end

        -- Track this API as active in this window
        redis.call('SADD', apisKey, apiKey)
        redis.call('EXPIRE', apisKey, distTTL)
    end

    -- 5. Threshold check + mitigation
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

return breaches
