-- 秒杀入口滑动窗口限流
-- --------------------------------------------------------------------
-- 核心思路：Redis ZSET 的 score 存请求毫秒时间戳，member 存唯一标识。
-- 每次请求先 ZREMRANGEBYSCORE 淘汰窗口外旧记录 → ZCARD 计数，
-- 超限返回 0 拒，否则 ZADD 写入本次请求 + PEXPIRE 续期 —— 三步用 Lua 原子执行，
-- 高并发下计数不失真（普通分步命令会被并发请求同时读到相同 count）。
-- --------------------------------------------------------------------
-- KEYS[1] = rate:sw:seckill:{userId}       按 userId 隔离的限流窗口（每个用户独立 ZSET）
-- ARGV[1] = nowMs        当前毫秒时间戳
-- ARGV[2] = windowMs     窗口长度（毫秒）
-- ARGV[3] = maxRequests  窗口内最大请求数
-- ARGV[4] = member       本次请求唯一标识（毫秒时间戳-8位随机串，同毫秒并发互不覆盖）
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