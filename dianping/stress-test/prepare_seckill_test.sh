#!/bin/bash
# ============================================================================
# 秒杀Plus压测准备脚本
# 用法: ./prepare_seckill_test.sh [用户数] [券ID]
# 默认: 1000用户, 券ID=13
# ============================================================================
set -e

USER_COUNT=${1:-1000}
VOUCHER_ID=${2:-13}
MYSQL_HOST="127.0.0.1"
MYSQL_USER="root"
MYSQL_PASS="123456"
MYSQL_DB="dingping"
REDIS_CLI="redis-cli"
CSV_FILE="/tmp/seckill_tokens.csv"

echo "=========================================="
echo "  秒杀Plus压测准备"
echo "  用户数: ${USER_COUNT}"
echo "  券ID:   ${VOUCHER_ID}"
echo "=========================================="

# ---- 1. 清理当前券ID的所有DB数据 ----
echo ""
echo "[1/8] 清理DB旧数据（券ID=${VOUCHER_ID}）..."

OLD_ORDERS=$(mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D "${MYSQL_DB}" -N \
  -e "SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = ${VOUCHER_ID}" 2>/dev/null)
OLD_LOGS=$(mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D "${MYSQL_DB}" -N \
  -e "SELECT COUNT(*) FROM tb_voucher_reconcile_log WHERE voucher_id = ${VOUCHER_ID}" 2>/dev/null)
OLD_VERIFY=$(mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D "${MYSQL_DB}" -N \
  -e "SELECT COUNT(*) FROM tb_order_create_verify_task" 2>/dev/null)
OLD_RECOVERY=$(mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D "${MYSQL_DB}" -N \
  -e "SELECT COUNT(*) FROM tb_order_create_recovery_task WHERE voucher_id = ${VOUCHER_ID}" 2>/dev/null)

echo "  旧订单: ${OLD_ORDERS:-0}  对账日志: ${OLD_LOGS:-0}  核验任务: ${OLD_VERIFY:-0}  恢复任务: ${OLD_RECOVERY:-0}"

mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D "${MYSQL_DB}" -e "
  DELETE FROM tb_voucher_order WHERE voucher_id = ${VOUCHER_ID};
  DELETE FROM tb_voucher_reconcile_log WHERE voucher_id = ${VOUCHER_ID};
  DELETE FROM tb_order_create_verify_task;
  DELETE FROM tb_order_create_recovery_task WHERE voucher_id = ${VOUCHER_ID};
" 2>/dev/null

echo "  DB旧数据已清理"

# ---- 2. 同步DB库存 ----
echo ""
echo "[2/8] 同步数据库库存..."
mysql -u"${MYSQL_USER}" -p"${MYSQL_PASS}" -D "${MYSQL_DB}" -N -e "
  UPDATE tb_seckill_voucher SET stock = ${USER_COUNT} WHERE voucher_id = ${VOUCHER_ID};
  SELECT CONCAT('  DB stock set to ', stock) FROM tb_seckill_voucher WHERE voucher_id = ${VOUCHER_ID};
" 2>/dev/null

# ---- 3. 清理Redis旧数据 ----
echo "[3/8] 清理Redis旧数据（券ID=${VOUCHER_ID}）..."
${REDIS_CLI} del "seckill:stock:${VOUCHER_ID}" 2>/dev/null
${REDIS_CLI} del "seckill:order:${VOUCHER_ID}" 2>/dev/null
${REDIS_CLI} del "seckill:trace:log:${VOUCHER_ID}" 2>/dev/null
# 清理所有用户的请求去重标记
for key in $(${REDIS_CLI} keys "seckill:req:${VOUCHER_ID}:*" 2>/dev/null); do
  ${REDIS_CLI} del "$key" 2>/dev/null
done
echo "  旧数据已清理"

# ---- 4. 清理旧用户token（可选，避免Redis内存膨胀） ----
echo "[4/8] 清理旧测试token..."
TOKEN_COUNT=$(${REDIS_CLI} keys "login:token:tokentest*" 2>/dev/null | wc -l | tr -d ' ')
if [ "${TOKEN_COUNT}" -gt 0 ]; then
  for key in $(${REDIS_CLI} keys "login:token:tokentest*" 2>/dev/null); do
    ${REDIS_CLI} del "$key" 2>/dev/null
  done
  echo "  已清理 ${TOKEN_COUNT} 个旧token"
else
  echo "  无旧token"
fi

# ---- 5. 设置Redis库存 ----
echo "[5/8] 设置Redis库存 = ${USER_COUNT}..."
${REDIS_CLI} set "seckill:stock:${VOUCHER_ID}" ${USER_COUNT}

# ---- 6. 生成用户token ----
echo "[6/8] 生成 ${USER_COUNT} 个用户token到Redis..."
${REDIS_CLI} --eval /dev/stdin 2>&1 <<LUA
  local count = 0
  for i = 1, ${USER_COUNT} do
    local token = "tokentest" .. i
    local key = "login:token:" .. token
    redis.call('hset', key,
      'id', i,
      'nickName', 'user' .. i,
      'icon', '')
    redis.call('expire', key, 36000)
    count = count + 1
  end
  return count
LUA
echo "  已生成"

# ---- 7. 生成CSV ----
echo "[7/8] 生成CSV文件: ${CSV_FILE}..."
> ${CSV_FILE}
for i in $(seq 1 ${USER_COUNT}); do
  echo "tokentest$i" >> ${CSV_FILE}
done
echo "  $(wc -l < ${CSV_FILE}) 行"

# ---- 8. 验证 ----
echo "[8/8] 验证环境..."
echo "  Redis stock:  $(${REDIS_CLI} get "seckill:stock:${VOUCHER_ID}")"
echo "  Redis orders: $(${REDIS_CLI} scard "seckill:order:${VOUCHER_ID}")"
echo "  Token count:  $(${REDIS_CLI} keys 'login:token:tokentest*' 2>/dev/null | wc -l)"
echo "  DB stock:     $(mysql -u${MYSQL_USER} -p${MYSQL_PASS} -D ${MYSQL_DB} -N -e "SELECT stock FROM tb_seckill_voucher WHERE voucher_id = ${VOUCHER_ID}" 2>/dev/null)"
echo "  DB orders:    $(mysql -u${MYSQL_USER} -p${MYSQL_PASS} -D ${MYSQL_DB} -N -e "SELECT COUNT(*) FROM tb_voucher_order WHERE voucher_id = ${VOUCHER_ID}" 2>/dev/null)"
echo "  DB logs:      $(mysql -u${MYSQL_USER} -p${MYSQL_PASS} -D ${MYSQL_DB} -N -e "SELECT COUNT(*) FROM tb_voucher_reconcile_log WHERE voucher_id = ${VOUCHER_ID}" 2>/dev/null)"

echo ""
echo "=========================================="
echo "  准备完成！可以开始压测："
echo ""
echo "  ~/java项目/apache-jmeter-5.6.3/bin/jmeter -n \\"
echo "    -t /tmp/seckill_plus_test.jmx \\"
echo "    -l /tmp/seckill_plus_result.jtl \\"
echo "    -e -o /tmp/seckill_plus_report"
echo "=========================================="
