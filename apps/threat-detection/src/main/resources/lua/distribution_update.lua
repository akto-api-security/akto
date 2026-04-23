-- Distribution Bucket Update
-- Called by the stream consumer for each message.
-- Queries CMS for 3 tumbling windows (5, 15, 30 min), updates distribution hashes.
--
-- KEYS: none (all keys are computed inside the script)
-- ARGV[1] = ipApiCmsKey
-- ARGV[2] = apiKey (e.g., "123|/api/users|GET")
-- ARGV[3] = currentEpochMin
-- ARGV[4] = distribution hash TTL in seconds (e.g., 28800)
-- ARGV[5] = CMS epsilon
-- ARGV[6] = CMS delta
-- ARGV[7..N] = bucket ranges as "label,min,max" (e.g., "b1,1,10", "b2,11,50", ...)

local ipApiCmsKey = ARGV[1]
local apiKey = ARGV[2]
local currentMin = tonumber(ARGV[3])
local distTTL = tonumber(ARGV[4])
local epsilon = tonumber(ARGV[5])
local delta = tonumber(ARGV[6])

-- Parse bucket ranges
local buckets = {}
for i = 7, #ARGV do
    local parts = {}
    for p in string.gmatch(ARGV[i], "[^,]+") do
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

local windowSizes = {5, 15, 30}

for _, windowSize in ipairs(windowSizes) do
    local windowEnd = getWindowEnd(currentMin, windowSize)
    local windowStart = windowEnd - windowSize + 1

    -- Query CMS across all minutes in this tumbling window
    local count = 0
    for min = windowStart, windowEnd do
        local cmsKey = "cms|" .. min
        local ok, res = pcall(function()
            return redis.call('CMS.QUERY', cmsKey, ipApiCmsKey)
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
        -- New IP-API pair in this window
        redis.call('HINCRBY', distKey, newBucket, 1)
    elseif oldBucket ~= newBucket then
        -- IP moved between buckets
        redis.call('HINCRBY', distKey, oldBucket, -1)
        redis.call('HINCRBY', distKey, newBucket, 1)
    end

    -- Track this API as active in this window
    redis.call('SADD', apisKey, apiKey)
    redis.call('EXPIRE', apisKey, distTTL)
end

return 1
