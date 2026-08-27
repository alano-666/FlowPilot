@echo off
REM FlowPilot Windows 一键启动（发行包版）
REM 前置：已安装 Java 17+（https://adoptium.net 下载），flowpilot.jar 与本文件同目录
chcp 65001 >nul
cd /d %~dp0

echo ==============================================
echo  FlowPilot 流程领航员 启动中...
echo  浏览器打开 http://localhost:8080 （admin/admin123）
echo  配置项在 .env.example 中说明，可用 set 命令或系统环境变量设置
echo ==============================================

REM 从 .env.local 读取配置（若存在）
if exist .env.local (
  for /f "usebackq tokens=1,* delims==" %%a in (".env.local") do set "%%a=%%b"
  echo [FlowPilot] 已加载本地配置 .env.local
) else (
  echo [FlowPilot] 未找到 .env.local，将以 Mock 演示模式启动
)

java -jar flowpilot.jar
pause
