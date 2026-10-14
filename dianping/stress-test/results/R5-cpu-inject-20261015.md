# R5 故障演练记录 —— CPU 注入（fibonacciWarmup）火焰栈 + 快慢对照 + 恢复

- 日期：2026-10-15 00:44–00:55（VM 时间）
- 环境：VM rocky9-dev；新版 jar（含批次1-4）；测试券 16；注入开关 `/jvm/seckill-bug/cpu-spin/on|off`
- 注入点（源码）：`VoucherOrderServiceImpl.seckillVoucherPlus()` 第 234-236 行 → `fibonacciWarmup(42)`（第 440-442 行，朴素递归）

## 1. 快慢对照（核心结论）

| 组 | 请求 | 耗时 | 说明 |
|---|---|---|---|
| 注入关闭（对照，868） | 1 | **0.021s** | 基线 |
| 注入开启·冷 JIT（855/854/877/876） | 4 | **16.0 / 14.0 / 12.0 / 10.0s** | 首轮注入，fib(42) 解释执行+JIT 过渡 |
| 注入开启·热 JIT（865/756） | 2 | **0.98 / 0.99s** | JIT 编译完成后仍约为对照的 47 倍 |
| 注入开启·冷 JIT（794/618/607） | 3 | **16.27s ×3** | 第一次尝试组 |

## 2. jstack 火焰栈（直接证据，2 帧命中）

- 抓拍时机：重启后冷 JIT，`jstack` 第 3、4 次抓拍各命中 1 帧（26 层递归帧）
- 证据原文：

```
"http-nio-8081-exec-16" #128 daemon prio=5 os_prio=0 cpu=655.38ms elapsed=6.26s ... nid=0x7d2b runnable
   java.lang.Thread.State: RUNNABLE
	at com.hmdp.service.impl.VoucherOrderServiceImpl.fibonacciWarmup(VoucherOrderServiceImpl.java:442)
	at com.hmdp.service.impl.VoucherOrderServiceImpl.fibonacciWarmup(VoucherOrderServiceImpl.java:442)
	...（共 26 层 fibonacciWarmup 递归帧）
	at com.hmdp.service.impl.VoucherOrderServiceImpl.seckillVoucherPlus(VoucherOrderServiceImpl.java:236)
	at com.hmdp.service.impl.VoucherOrderServiceImpl$$FastClassBySpringCGLIB$$ba28bb10.invoke(<generated>)
```

- 落点闭环：`seckillVoucherPlus:236`（注入点）→ `fibonacciWarmup:442`（燃烧体）→ `RUNNABLE` 占核。

## 3. CPU 指标证据

- actuator `process_cpu_usage`：0.080（基线）→ **0.283**（燃烧中）；`system_cpu_usage`：0.112 → **0.683**
- `top`：C2 CompilerThread ~22–24%、http-nio-8081 业务线程 ~31% 同时高占比（JIT 编译 + 燃烧叠加）

## 4. 恢复验证

- `cpu-spin/off` 后：7 个注入开关全 false；对照请求 0.021s；health UP
- 落尾状态：应用运行正常（pid 31894），监控栈正常

## 5. 副作用与发现（如实记录）

### 5.1 MQ 发送超时（6 笔，均已自愈）
CPU 饱和期间 3+3 笔 `syncSend` 触发 10s 客户端超时（`sendDefaultImpl call timeout`）→ 消息落 `tb_mq_event_record`（PENDING_RETRY）→ 30–60s 内重试 **SENT** → 消费端建单被 `(user_id,voucher_id)` 唯一键拦下 → 幂等忽略。**无新订单产生**（DB 订单总数保持 4000）。

### 5.2 幽灵预占（4 笔，新发现，同 R2 盲区类）
865 / 756 / 868 / 876 四个请求：MQ 发送成功、返回 `success:true`，但消费端因 `uk_user_voucher` 唯一键（旧单已存在）幂等忽略 → **DB 无新订单、无 DEDUCT 日志**；而 Redis 侧预扣与 order-set 占用未回滚（`sismember=1`×4）。后续：
- `OrderCreateVerifyJob` 校验任务 6 次重试后 **FAILED**（4 条）——且未生成恢复任务（`OrderCreateVerificationService` 的 @Autowired 4 参构造器将 recoveryService 传 null，接力链断点，代码位置已定位）
- 无任何机制可自动发现该预占（对账扫描只扫 DB 日志）→ 与 R2 的"Redis-only 残留"同类：**兜底体系对"无 DB 留痕"的漂移盲区**
- 影响面：4 个测试用户在券 16 的预占（下次 prepare 重置券键时自动清除；如需立即清净可 SREM+INCRBY）

### 5.3 边界语义
"HTTP 200 + success:true"（876）= 消息投递成功，**不等于**建单成功；唯一键拦截发生在消费端，响应层无法感知。

### 5.4 附：R2 事后自愈仍在推进
超时关单任务持续关闭 R2 遗留 UNPAID 单：已关闭 780 笔（status=4），DB 库存回补到 880；R2 时代校验任务全部 SUCCESS。

## 存证位置
- VM：`/tmp/r5d_js_3.txt`、`/tmp/r5d_js_4.txt`（火焰栈两帧）、`app.log`（消费端/回滚痕迹）
- DB 证据：`tb_order_create_verify_task`（4 条 FAILED）、`tb_mq_event_record`（3598-3603 SENT）、`SHOW INDEX`（uk_user_voucher）
- 本文件 = 本地归档（stress-test/results/）
