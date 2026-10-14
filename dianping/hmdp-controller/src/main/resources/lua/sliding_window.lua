-- 秒杀入口滑动窗口限流：ZSET 存请求毫秒时间戳，窗口外淘汰后计数
-- KEYS[1] = rate:sw:seckill:{userId}
-- ARGV[1] = nowMs        当前毫秒时间戳
-- ARGV[2] = windowMs     窗口长度（毫秒）
-- ARGV[3] = maxRequests  窗口内最大请求数
-- ARGV[4] = member       本次请求的唯一标识（时间戳-随机串）
-- 返回：1 放行；0 被限流
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local maxReq = tonumber(ARGV[3])
local member = ARGV[4]
local minScore = now - window

-- 1. 淘汰窗口外的旧请求
redis.call('ZREMRANGEBYSCORE', key, 0, minScore)
-- 2. 统计窗口内请求数
local count = redis.call('ZCARD', key)
if count >= maxReq then
    return 0
end
-- 3. 记录本次请求并续期
redis.call('ZADD', key, now, member)
redis.call('PEXPIRE', key, window)
return 1
