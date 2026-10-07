-- ============================================================================
-- 秒杀Plus Lua脚本：在Redis中原子完成资格校验 + 库存扣减 + 追踪日志
--
-- 参数说明：
--   ARGV[1] voucherId   - 优惠券ID
--   ARGV[2] userId      - 用户ID
--   ARGV[3] orderId     - 预生成的订单ID（雪花算法）
--   ARGV[4] traceId     - 对账追踪ID（trace-{orderId}）
--   ARGV[5] ttlSeconds  - 追踪日志过期时间（秒）
--   ARGV[6] nowMillis   - 当前时间戳（毫秒）
--
-- Redis Key 说明：
--   seckill:stock:{voucherId}       - 秒杀库存（需提前预热）
--   seckill:order:{voucherId}       - 已下单用户集合（Set，一人一单校验）
--   seckill:req:{voucherId}:{userId} - 请求去重标记（防止同用户短时间重复请求）
--   seckill:trace:log:{voucherId}   - 对账追踪日志（Hash，field=traceId）
--
-- 返回值：
--    0  - 秒杀成功，库存已扣减
--    1  - 库存不足
--    2  - 重复下单（该用户已购买此券）
--    3  - 请求过于频繁（1秒内重复请求）
--   -1  - 库存未初始化（stock key 不存在）
-- ============================================================================

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
local traceId = ARGV[4]
local ttlSeconds = tonumber(ARGV[5])
local nowMillis = ARGV[6]
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local requestKey = 'seckill:req:' .. voucherId .. ':' .. userId
local traceLogKey = 'seckill:trace:log:' .. voucherId

-- 第1步：请求去重 — 同一用户1秒内只允许一个请求进入
-- setnx 原子保证：key不存在则设置成功，存在则拒绝
if redis.call('setnx', requestKey, '1') == 0 then
    return 3
end
redis.call('expire', requestKey, 1)

-- 第2步：检查库存是否存在（是否已预热）
local stock = tonumber(redis.call('get', stockKey))
if stock == nil then
    return -1
end

-- 第3步：库存不足检查
if stock <= 0 then
    redis.call('del', requestKey)  -- 清理去重标记
    return 1
end

-- 第4步：一人一单校验 — 检查该用户是否已在此券的已购集合中
if redis.call('sismember', orderKey, userId) == 1 then
    redis.call('del', requestKey)  -- 清理去重标记
    return 2
end

-- 第5步：原子扣减库存并记录用户
local beforeQty = stock
local afterQty = beforeQty - 1
redis.call('incrby', stockKey, -1)    -- 库存 -1
redis.call('sadd', orderKey, userId)   -- 用户加入已购集合

-- 第6步：记录扣减前后的库存快照
local logEntry = cjson.encode({
    traceId = traceId,
    orderId = orderId,
    userId = userId,
    voucherId = voucherId,
    beforeQty = beforeQty,
    changeQty = 1,
    afterQty = afterQty,
    logType = 'DEDUCT',
    ts = nowMillis
})
redis.call('hset', traceLogKey, traceId, logEntry)
if ttlSeconds ~= nil and ttlSeconds > 0 then
    redis.call('expire', traceLogKey, ttlSeconds)
end
return 0
