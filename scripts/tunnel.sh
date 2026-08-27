#!/bin/bash
# FlowPilot 内网穿透脚本：没有域名/服务器时，把本机服务暴露为公网 HTTPS，
# 输出可直接填进飞书开发者后台的回调地址。
#
# 支持三种穿透工具（任选其一，均免费额度够演示用）：
#   ngrok        https://ngrok.com/download（需注册取 token，国内可直连）
#   cloudflared  https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
#   cpolar       国产首选 https://www.cpolar.com/（注册即用，稳定性好）
#
# 用法：./scripts/tunnel.sh [ngrok|cloudflared|cpolar] [本地端口=8080]
set -e
cd "$(dirname "$0")/.."

TOOL="${1:-cpolar}"
PORT="${2:-8080}"

echo "=============================================="
echo " FlowPilot 内网穿透（${TOOL}）"
echo "=============================================="
echo "请先启动 FlowPilot 服务（./scripts/start.sh），确认 http://localhost:${PORT} 可访问后继续。"
read -r -p "服务已在运行？按回车继续（Ctrl+C 取消）..."

PUBLIC_URL=""
case "$TOOL" in
  ngrok)
    if ! command -v ngrok >/dev/null 2>&1; then
      echo "未检测到 ngrok。安装：brew install ngrok（或到 https://ngrok.com/download 下载）"
      echo "首次使用：ngrok config add-authtoken <你的token>（ngrok 官网注册可得）"
      exit 1
    fi
    ngrok http "$PORT" > /tmp/flowpilot-tunnel.log 2>&1 &
    TUNNEL_PID=$!
    echo "ngrok 启动中（PID $TUNNEL_PID），等待分配公网地址..."
    for i in $(seq 1 15); do
      sleep 1
      PUBLIC_URL=$(curl -s --max-time 3 http://127.0.0.1:4040/api/tunnels 2>/dev/null \
        | grep -o '"public_url":"https://[^"]*"' | head -1 | cut -d'"' -f4 || true)
      [ -n "$PUBLIC_URL" ] && break
    done
    ;;
  cloudflared)
    if ! command -v cloudflared >/dev/null 2>&1; then
      echo "未检测到 cloudflared。安装：brew install cloudflared"
      exit 1
    fi
    cloudflared tunnel --url "http://localhost:${PORT}" > /tmp/flowpilot-tunnel.log 2>&1 &
    TUNNEL_PID=$!
    echo "cloudflared 启动中（PID $TUNNEL_PID），等待分配公网地址..."
    for i in $(seq 1 20); do
      sleep 1
      PUBLIC_URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /tmp/flowpilot-tunnel.log 2>/dev/null | head -1 || true)
      [ -n "$PUBLIC_URL" ] && break
    done
    ;;
  cpolar)
    # cpolar 常见安装位置（brew 无此包，官方下载解压到 ~/.local/bin）
    CPOLAR_BIN=""
    for candidate in "$HOME/.local/bin/cpolar" /opt/homebrew/bin/cpolar /usr/local/bin/cpolar cpolar; do
      if command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ]; then
        CPOLAR_BIN="$candidate"
        break
      fi
    done
    if [ -z "$CPOLAR_BIN" ]; then
      echo "未检测到 cpolar。安装（Apple 芯片 Mac）："
      echo "  mkdir -p ~/.local/bin && curl -sL https://www.cpolar.com/static/downloads/releases/latest/cpolar-stable-darwin-arm64.zip -o /tmp/cpolar.zip && unzip -o /tmp/cpolar.zip -d ~/.local/bin/ && chmod +x ~/.local/bin/cpolar"
      echo "首次使用需注册：https://www.cpolar.com/ 注册后复制 authtoken，执行 ~/.local/bin/cpolar authtoken <你的token>"
      exit 1
    fi
    "$CPOLAR_BIN" http "$PORT" > /tmp/flowpilot-tunnel.log 2>&1 &
    TUNNEL_PID=$!
    echo "cpolar 启动中（PID $TUNNEL_PID），等待分配公网地址..."
    for i in $(seq 1 20); do
      sleep 1
      PUBLIC_URL=$(grep -o 'https://[a-z0-9]*\.cpolar[^ ]*' /tmp/flowpilot-tunnel.log 2>/dev/null | head -1 || true)
      [ -n "$PUBLIC_URL" ] && break
    done
    ;;
  *)
    echo "未知工具: $TOOL（可选 ngrok / cloudflared / cpolar）"; exit 1
    ;;
esac

if [ -z "$PUBLIC_URL" ]; then
  echo "❌ 未能自动获取公网地址（可能工具未注册或网络受限）。"
  echo "   可手动查看 /tmp/flowpilot-tunnel.log 获取，或改用其它工具重试。"
  exit 1
fi

echo ""
echo "✅ 公网地址已就绪！请把以下两个地址填进飞书开发者后台："
echo ""
echo "   【事件订阅-请求地址】"
echo "   ${PUBLIC_URL}/api/v1/webhooks/feishu/events"
echo ""
echo "   【网页应用-入口地址】"
echo "   ${PUBLIC_URL}/"
echo ""
echo "提示："
echo "  - 免费版穿透地址重启后会变化，演示当天重新运行本脚本并更新飞书后台即可；"
echo "  - 穿透进程保持运行：tail -f /tmp/flowpilot-tunnel.log 查看日志，Ctrl+C 停止。"
echo "=============================================="
