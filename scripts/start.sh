#!/bin/bash
# FlowPilot 一键启动脚本：自动加载本地密钥（backend/.env.local）并启动服务
set -e
cd "$(dirname "$0")/.."

# 本地密钥（存在则加载）
if [ -f backend/.env.local ]; then
  set -a
  # shellcheck disable=SC1091
  source backend/.env.local
  set +a
  echo "[FlowPilot] 已加载本地密钥配置 backend/.env.local"
else
  echo "[FlowPilot] 未找到 backend/.env.local，将以 Mock 演示模式启动"
fi

# JDK 自动探测（macOS 常见路径）
if [ -z "$JAVA_HOME" ] && [ -d /Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
fi

# Maven：优先用项目内置的 .tools/maven，其次系统 mvn
MAVEN="mvn"
if [ -x .tools/apache-maven-3.9.9/bin/mvn ]; then
  MAVEN=".tools/apache-maven-3.9.9/bin/mvn"
fi

cd backend
echo "[FlowPilot] 启动中：AI Provider=$FLOWPILOT_AI_PROVIDER，浏览器访问 http://localhost:8080"
exec "$MAVEN" spring-boot:run
