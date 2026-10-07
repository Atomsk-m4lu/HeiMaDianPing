#!/bin/bash
# ============================================================================
# JVM Patrol 压测诊断验证 — 在秒杀负载下注入7种JVM问题，配合jvm-patrol诊断
# 用法: ./test_jvm_patrol.sh
# ============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESULT_DIR="${SCRIPT_DIR}/jvm-patrol-results"
APP_URL="http://localhost:8081"
PID=61053

mkdir -p "${RESULT_DIR}"

declare -a ROUND_NAMES ROUND_TRIGGERS ROUND_DIRECTIONS

add_round() {
  ROUND_NAMES+=("$1")
  ROUND_TRIGGERS+=("$2")
  ROUND_DIRECTIONS+=("$3")
}

add_round "01-cpu-regex" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/cpu-regex/on" \
  "cpu"

add_round "02-cpu-spin" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/cpu-spin/on" \
  "cpu"

add_round "03-memory-leak" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/memory-leak/on" \
  "memory"

add_round "04-threadlocal-leak" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/threadlocal-leak/on" \
  "memory"

add_round "05-deadlock" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/deadlock/on" \
  "thread"

add_round "06-thread-explosion" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/thread-explosion/on" \
  "thread"

add_round "07-gc-alloc" \
  "curl -s -X POST ${APP_URL}/jvm/seckill-bug/gc-alloc/on" \
  "gc"

echo "============================================"
echo "  JVM Patrol 压测诊断验证"
echo "  PID: ${PID}"
echo "  方向: ${ROUND_DIRECTIONS[*]}"
echo "  结果目录: ${RESULT_DIR}"
echo "============================================"

run_round() {
  local idx=$1
  local name="${ROUND_NAMES[$idx]}"
  local trigger="${ROUND_TRIGGERS[$idx]}"
  local direction="${ROUND_DIRECTIONS[$idx]}"

  echo ""
  echo "============================================"
  echo "  Round ${idx}: ${name}"
  echo "  方向: ${direction}"
  echo "============================================"

  # 1. 环境准备
  echo "[${name}] 准备环境..."
  bash "${SCRIPT_DIR}/prepare_seckill_test.sh" 3000 13

  # 2. 开启秒杀链路 JVM 问题开关
  echo "[${name}] 开启秒杀链路bug开关..."
  eval "${trigger}" > /dev/null 2>&1
  echo "[${name}] 开关已开启"

  # 3. 等待开关生效
  echo "[${name}] 等待 (2s)..."
  sleep 2

  # 4. 验证开关状态
  echo "[${name}] 验证bug开关状态..."
  curl -s "${APP_URL}/jvm/seckill-bug/status" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
for k,v in d.items():
    print(f'  {k}: {v}')
" 2>/dev/null || echo "  (无法获取状态)"

  # 5. 启动压测
  echo "[${name}] 启动压测 (3000并发/2s)..."
  bash "${SCRIPT_DIR}/run_seckill_test.sh" 3000 2 13

  # 6. 暂停等待 jvm-patrol 诊断
  echo ""
  echo "  ╔══════════════════════════════════════════╗"
  echo "  ║  请对 Claude 发送:                        ║"
  echo "  ║  /jvm-patrol ${PID} ${direction}              ║"
  echo "  ╚══════════════════════════════════════════╝"
  echo ""
  read -p "  诊断完成后按 Enter 收集报告..."

  # 7. 收集最新报告
  local latest_report=$(ls -t "${SCRIPT_DIR}/../docs/jvm-patrol-"*.html 2>/dev/null | head -1)
  if [ -n "${latest_report}" ] && [ -f "${latest_report}" ]; then
    cp "${latest_report}" "${RESULT_DIR}/${name}.html"
    echo "[${name}] 报告已保存: ${RESULT_DIR}/${name}.html ($(du -h "${latest_report}" | cut -f1))"
  else
    echo "[${name}] WARN: 未找到 jvm-patrol 报告"
  fi

  # 8. 关闭全部bug开关 + 重置 JVM 状态
  echo "[${name}] 关闭全部bug开关 + 重置 JVM..."
  curl -s -X POST "${APP_URL}/jvm/seckill-bug/all/off" > /dev/null
  curl -s -X POST "${APP_URL}/jvm/reset" > /dev/null
  sleep 2
  echo "[${name}] 重置完成，当前开关状态:"
  curl -s "${APP_URL}/jvm/seckill-bug/status" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
for k,v in d.items():
    print(f'  {k}: {v}')
" 2>/dev/null || echo "  (无法获取状态)"

  echo "[${name}] 本轮结束"
}

generate_summary() {
  local summary_file="${RESULT_DIR}/summary.txt"
  cat > "${summary_file}" <<EOF
========================================
  JVM Patrol 压测诊断验证 汇总
  时间: $(date '+%Y-%m-%d %H:%M:%S')
  PID: ${PID}
========================================

EOF

  for idx in "${!ROUND_NAMES[@]}"; do
    local name="${ROUND_NAMES[$idx]}"
    local direction="${ROUND_DIRECTIONS[$idx]}"
    local report="${RESULT_DIR}/${name}.html"
    local status="MISSING"
    local size="N/A"
    if [ -f "${report}" ]; then
      status="DONE"
      size=$(du -h "${report}" | cut -f1)
    fi
    printf "  %-20s | direction: %-6s | status: %-7s | size: %s\n" \
      "${name}" "${direction}" "${status}" "${size}" >> "${summary_file}"
  done

  echo "" >> "${summary_file}"
  echo "报告目录: ${RESULT_DIR}" >> "${summary_file}"
  echo "========================================" >> "${summary_file}"

  cat "${summary_file}"
}

# ==== 主流程 ====
for idx in "${!ROUND_NAMES[@]}"; do
  run_round "${idx}"
done

generate_summary

echo ""
echo "============================================"
echo "  全部 7 轮测试完成！"
echo "  报告: ${RESULT_DIR}"
echo "============================================"
