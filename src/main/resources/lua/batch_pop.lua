-- KEYS[1]: redis list key
-- ARGV[1]: threshold (300)

local key = KEYS[1]
local threshold = tonumber(ARGV[1])

local size = redis.call("LLEN", key)

if size > threshold then
    -- 取出所有数据
    local data = redis.call("LRANGE", key, 0, -1)
    -- 清空列表
    redis.call("DEL", key)
    return data
else
    return {}
end