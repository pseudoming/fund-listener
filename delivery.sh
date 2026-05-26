#!/bin/bash
# 显式加载 nvm 环境，避免非交互式 shell 丢失 PATH
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"

# 交付准入物理卡口
echo "==================================="
echo "🚀 执行交付前置自动化卡口检查..."
echo "🕒 启动时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "==================================="

# 1. 检查前端 E2E 路由与拦截器测试
cd frontend || exit 1
echo "[1/1] 正在运行 Playwright E2E 回归测试..."
npm run test:e2e

if [ $? -eq 0 ]; then
  echo ""
  echo "==================================="
  echo "✅ 交付卡口全绿放行！允许向用户发起回复。"
  echo "🕒 结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "==================================="
  exit 0
else
  echo ""
  echo "==================================="
  echo "❌ 交付卡口阻断：测试未通过！"
  echo "⚠️ 严禁向用户汇报，请立刻排查日志并静默修复！"
  echo "🕒 结束时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "==================================="
  exit 1
fi
