-- ============================================================================
-- 秒杀Plus回滚Lua脚本：回退Redis中的库存扣减和用户标记
--
-- 触发场景：
--   1. MQ消息发送失败 → 需要回退Lua脚本已扣减的Redis库存
--   2. DB库存不足    → 建单失败，需要恢复Redis库存供其他用户抢购
--   3. 超时未支付    → 取消订单，释放库存
--
-- 参数说明：
--   ARGV[1] voucherId   - 优惠券ID
--   ARGV[2] userId      - 用户ID
--   ARGV[3] orderId     - 订单ID
--   ARGV[4] traceId     - 对账追踪ID
--   ARGV[5] ttlSeconds  - 追踪日志过期时间（秒）
--   ARGV[6] nowMillis   - 当前时间戳（毫秒）
--
-- Redis Key 说明：
--   seckill:stock:{voucherId}        - 秒杀库存
--   seckill:order:{voucherId}        - 已下单用户集合
--   seckill:req:{voucherId}:{userId}  - 请求去重标记
--   seckill:trace:log:{voucherId}    - 对账追踪日志（Hash）
--
-- 幂等性保证：
--   通过检查 traceLog 中是否已存在 RESTORE 类型的日志来防止重复回滚
--   如果之前已经回滚过，则跳过库存恢复，仅清理请求标记
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

-- 幂等检查：如果该 traceId 已经执行过 RESTORE，说明已回滚过，不再重复操作
local previousTrace = redis.call('hget', traceLogKey, traceId)
if previousTrace then
    local previous = cjson.decode(previousTrace)
    if previous['logType'] == 'RESTORE' then
        redis.call('del', requestKey)
        return 0
    end
end

-- 回滚操作：将用户从已购集合移除，恢复库存
-- srem 返回1表示用户确实在集合中被移除了（正常回滚路径）
-- srem 返回0表示用户不在集合中（可能订单从未创建成功，或不一致场景）
local removed = redis.call('srem', orderKey, userId)
local currentStock = tonumber(redis.call('get', stockKey))
if removed == 1 then
    if currentStock == nil then
        currentStock = 0
    end
    local beforeQty = currentStock
    local afterQty = beforeQty + 1
    redis.call('incrby', stockKey, 1)   -- 库存 +1

    -- 记录 RESTORE 类型对账追踪日志，标记本次回滚
    local logEntry = cjson.encode({
        traceId = traceId,
        orderId = orderId,
        userId = userId,
        voucherId = voucherId,
        beforeQty = beforeQty,
        changeQty = 1,
        afterQty = afterQty,
        logType = 'RESTORE',
        ts = nowMillis
    })
    redis.call('hset', traceLogKey, traceId, logEntry)
    if ttlSeconds ~= nil and ttlSeconds > 0 then
        redis.call('expire', traceLogKey, ttlSeconds)
    end
end
-- 清理请求去重标记，允许用户后续重试
redis.call('del', requestKey)
return 0
