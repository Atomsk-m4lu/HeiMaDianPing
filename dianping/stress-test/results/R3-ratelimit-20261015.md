# R3 限流验证记录 —— 滑动窗口限流 开/关对照

- 日期：2026-10-15 00:30–00:38（VM 时间）
- 环境：VM rocky9-dev；新版 jar（含批次1-4）；测试券 16；限流器 = SlidingWindowInterceptor（window 1000ms / max 5，Redis ZSET+Lua）
- 说明：测试用户 1–30 因超时关单任务已移出订单集合，改用集合内用户 3286 / 3488 / 3724 / 2321（按 prepare 同款方式重建 login token）
- 所有请求路径：`POST /voucher-order/seckill-plus/16`，header `authorization: tokentest<uid>`；用户已在订单集合内，业务层为"重复下单"拒绝（HTTP 200 属业务响应），**限流拒绝为 HTTP 429，位于拦截器层，两者可清晰区分**

## 1. 开启态（默认配置，不重启）
- 单用户 tokentest3286 突发 12 并发：`200 200 200 200 429 429 200 429 429 429 429 429` = **5×200 + 7×429**（窗口内前 5 放行、第 6+ 拒绝）
- 429 响应体：`{"errorMsg":"请求过于频繁，请稍后再试","success":false}`
- 指标 `hmdp_ratelimit_rejected_total`：0 → **8.0**（7 并发被拒 + 1 次补测取响应体）
- 日志 `sliding window rate limit hit`：0 → **8**（与指标一致）
- Redis 窗口键存在：`rate:sw:seckill:3286`

## 2. 隔离验证（同刻另一用户）
- tokentest3488 突发 4 并发：**全部 200**（未被他人窗口影响 → 按 userId 隔离）

## 3. 关闭态对照（重启一次，启动参数 `--seckill.rate-limit.sliding-window.enabled=false`）
- 同参数 12 并发（tokentest3724）：**全部 200**（无 429）
- 日志 `sliding window rate limit hit`：0；指标：0.0（完全无拒绝）

## 4. 恢复（`./start-app.sh` 默认配置重启）
- health UP；7 个注入开关全 false；新 pid = 28990
- 复测 tokentest2321 突发 6 并发：`200 200 200 200 200 429` = 5×200 + 1×429（重启后限流重新生效）
- 日志 1 条 / 指标 1.0（重启后计数清零，与本次一致）

## 5. 结论
限流开启时"窗口内前 5 放行、第 6+ 返回 429"✓，按用户隔离 ✓，指标与日志同步计数 ✓；关闭后完全放行 ✓；恢复默认后限流重新生效 ✓。

## 存证位置
- VM：`~/hmdp-mq/app.log.r3-limit-on`（开启态日志）、`~/hmdp-mq/app.log.r3-limit-off`（关闭态日志）
- 本文件 = 本地归档（stress-test/results/）
