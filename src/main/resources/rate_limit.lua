---
--- 滑动窗口限流 Lua 脚本
--- KEYS[1]: 限流key
--- ARGV[1]: 窗口内最大请求数
--- ARGV[2]: 窗口大小（毫秒）
--- ARGV[3]: 当前时间戳（毫秒）
--- ARGV[4]: 唯一成员标识
---
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local member = ARGV[4]

-- 移除窗口外的过期记录
redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMs)

-- 统计当前窗口内请求数
local count = redis.call('ZCARD', key)

if count >= limit then
    return 0  -- 超过限制，拒绝
end

-- 记录本次请求
redis.call('ZADD', key, now, member)
redis.call('EXPIRE', key, math.ceil(windowMs / 1000) + 1)
return 1  -- 通过
