#!/bin/bash
# FlowPilot 一键启动脚本（自动识别两种场景）
#   场景一：发行包（脚本与 flowpilot.jar 在同一目录）→ 直接 java -jar 运行，零依赖
#   场景二：源码仓库（脚本在 scripts/ 下，上级目录有 backend/pom.xml）→ Maven 运行
set -e

# 自动识别脚本所在布局：发行包根目录 or 源码仓库 scripts/ 目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/flowpilot.jar" ] || [ -f "$SCRIPT_DIR/.env.example" ]; then
  BASE="$SCRIPT_DIR"                      # 发行包：脚本就在包根目录
else
  BASE="$(dirname "$SCRIPT_DIR")"         # 源码仓库：scripts/start.sh → 仓库根目录
fi
cd "$BASE"

# 本地密钥加载：优先当前目录 .env.local，其次 backend/.env.local
# 注意：过滤空值行——空的环境变量会覆盖程序默认值导致启动失败
load_env() {
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      '' | \#*) continue ;;
    esac
    line="${line#export }"
    key="${line%%=*}"
    val="${line#*=}"
    # 去掉首尾引号
    val="${val%\"}"; val="${val#\"}"; val="${val%\'}"; val="${val#\'}"
    if [ -n "$val" ]; then
      export "$key=$val"
    fi
  done < "$1"
}
if [ -f .env.local ]; then
  load_env .env.local
  echo "[FlowPilot] 已加载本地配置 .env.local（空值已自动跳过）"
elif [ -f backend/.env.local ]; then
  load_env backend/.env.local
  echo "[FlowPilot] 已加载本地配置 backend/.env.local（空值已自动跳过）"
else
  echo "[FlowPilot] 未找到 .env.local，将以 Mock 演示模式启动（配置方法见 .env.example）"
fi

# JDK 自动探测（macOS 常见路径）
if [ -z "$JAVA_HOME" ] && [ -d /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
fi

# 场景一：发行包（jar 就在当前目录）
if [ -f flowpilot.jar ]; then
  echo "[FlowPilot] 检测到发行包模式（flowpilot.jar），启动中：http://localhost:8080"
  exec java -jar flowpilot.jar
fi

# 场景二：源码仓库（Maven 运行）
if [ -f backend/pom.xml ]; then
  MAVEN="mvn"
  if [ -x .tools/apache-maven-3.9.9/bin/mvn ]; then
    MAVEN=".tools/apache-maven-3.9.9/bin/mvn"
  fi
  cd backend
  echo "[FlowPilot] 源码模式启动中：AI Provider=${FLOWPILOT_AI_PROVIDER:-mock}，浏览器访问 http://localhost:8080"
  exec "$MAVEN" spring-boot:run
fi

echo "[FlowPilot] 错误：当前目录既没有 flowpilot.jar 也没有 backend/pom.xml，请确认解压完整。"
exit 1

