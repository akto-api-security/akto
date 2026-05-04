-- CMS Range Query (read-only, no increment)
-- Queries CMS across multiple minute keys and returns the sum.
-- Used by ParamEnumerationDetector for threshold checks without incrementing.
--
-- KEYS[1..N] = "cms|{minute}" for each minute in the range
-- ARGV[1]    = key to query (the item string)

local key = ARGV[1]
local total = 0

for i = 1, #KEYS do
    local ok, res = pcall(function()
        return redis.call('CMS.QUERY', KEYS[i], key)
    end)
    if ok and res and res[1] then
        total = total + res[1]
    end
end

return total
