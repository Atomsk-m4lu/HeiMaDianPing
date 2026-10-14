# R4 多级缓存验证记录 —— L1（Caffeine）开/关对照

- 日期：2026-10-15 00:42–00:47（VM 时间）
- 环境：VM rocky9-dev；新版 jar（含批次1-4）；接口 `GET /shop/1`（店铺详情，只读）
- 指标：`hmdp_cache_local_hits_total` / `hmdp_cache_local_misses_total`（LocalCacheConfig FunctionCounter，实时求值）
- Redis 证据：`INFO commandstats` 的 `cmdstat_get` 前后差值
- L2 数据：`cache:shop:1` 存在且未逻辑过期（ShopDetailSyncJob 每 30 分钟预热）

## 1. L1 开启态（默认配置 cache.local.enabled=true，不重启）
- 50 次请求（5 并发）：全部 200
- **hits = 45 / misses = 5**（5 个并发"首请求"各 miss 一次，之后 45 次全部 L1 命中）
- **Redis `cmdstat_get` 增量 = +5**（11580 → 11585）——50 次请求只有 5 次打到 Redis
- 日志 `[多级缓存] L1 命中: cache:shop:1` = 45 条（与 hits 完全一致）
- `cmdstat_hgetall` 无变化（排除 token 刷新干扰，1065 行日志不是本链路产生）

## 2. L1 关闭对照（重启一次，启动参数 `--cache.local.enabled=false`）
- 50 次请求（5 并发）：全部 200
- **hits = 0 / misses = 0**（完全不走 L1）
- **Redis `cmdstat_get` 增量 = +50**（11585 → 11635）——每次请求都读 Redis
- 日志 `[多级缓存] L1 命中` = 0 条

## 3. 恢复（`./start-app.sh` 默认配置重启）
- health UP；7 个注入开关全 false；新 pid = 30115

## 4. 结论
L1 开启时 50 次请求仅 5 次打到 Redis（省掉 90% 的读流量，命中全部来自 Caffeine）；指标（hits=45）与日志（45 条）与 Redis GET 差值（50-45=5）三方口径互相咬合 ✓；关闭后完全回退单级链路（每次读 Redis）✓；恢复默认后一切正常 ✓。

## 存证位置
- VM：`~/hmdp-mq/app.log.r4-l1-on`（开启态日志）、`~/hmdp-mq/app.log.r4-l1-off`（关闭态日志）
- 本文件 = 本地归档（stress-test/results/）
