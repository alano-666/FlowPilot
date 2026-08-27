# FlowPilot 流程领航员

> 飞书 AI 流程跟踪助手：解析业务流程文档 → AI 建模流程模板 → 归集飞书/企微/微信多渠道聊天记录 → 自动识别项目节点/进度/干系人/风险 → 可视化看板 + 一键沟通 + 超时预警 + 周月报导出。

## 快速开始（3 分钟）

```bash
cd backend && mvn spring-boot:run
# 浏览器打开 http://localhost:8080
# 账号：admin / admin123
```

无需数据库、无需 AI Key（默认 Mock 演示模式）、前端已内置。体验路径：
看板 → 项目详情 → 「🎭 生成演示消息」→「🤖 立即 AI 分析」→ 观察进度/干系人/风险自动更新。

## 核心能力（V1.0）

| 模块 | 能力 |
|---|---|
| 流程知识库 | Word/PDF/Markdown/TXT/飞书文档 → AI 抽取节点/分支/完成标准/责任角色/词库 → 可视化编辑 → 版本化发布 |
| AI 状态识别引擎 | 增量聊天记录 → LLM 结构化识别当前节点/进度/证据链/干系人/风险/下一步建议（Provider 可插拔：Claude 官方 SDK / Anthropic 兼容网关 / OpenAI 兼容 / Mock；官方 API 走服务端 Schema 严格模式，第三方网关自动宽松 JSON 模式） |
| 多渠道接入 | 飞书群自动同步（事件回调+定时拉取）、企微回调+机器人推送（会话存档 SDK 接入位）、**微信导入文件夹监控**、**邮箱邮件（IMAP 定时拉取，主题/发件人模糊匹配项目）**、Mock 演示 |
| 可视化看板 | 统计卡片、项目卡片网格（进度条/风险标识/最近动态）、流程图（✅完成/🔄进行中/⏳待开始）、筛选搜索 |
| 人工校准 | 修正最高优先级 + 锁定保护 + AI 建议待确认 + 全量审计日志 |
| 通知预警 | SLA 超时 @责任人、节点完成联动通知、沟通风险预警、每日进度摘要 |
| 报告导出 | 周报/月报自动生成，Excel / PDF / HTML 三格式，卡点节点/平均耗时/响应时长/超时率统计 |
| 数据安全 | 数据库列级 AES-256-GCM 加密（聊天/模板/AI 结果/联系方式）、上传文档不落盘、90 天自动清理、登录防爆破、操作审计日志 |

## 技术栈

- **后端**：Java 21+（开发环境 JDK 25）/ Spring Boot 3.5 / Spring Data JPA / H2(开发)→PostgreSQL(生产) / JWT / Apache POI / PDFBox / OpenPDF
- **AI**：Anthropic 官方 Java SDK（结构化输出 + 自适应思考）/ OpenAI 兼容协议 / Mock
- **前端**：Vue 3 / Vite / Element Plus / Pinia（构建产物内嵌后端，单包部署）
- **定时任务**：Spring @Scheduled（同步/分析/巡检/摘要/报告/清理）

## 配置 AI（接入真实大模型）

```bash
# Claude（Anthropic 官方 SDK，默认模型 claude-opus-5）
export FLOWPILOT_AI_PROVIDER=anthropic
export FLOWPILOT_AI_ANTHROPIC_APIKEY=sk-ant-xxx

# 第三方 Anthropic 兼容网关（中转站）：加配 base-url 并关闭严格 Schema
export FLOWPILOT_AI_ANTHROPIC_BASEURL=https://网关地址/api/anthropic
export FLOWPILOT_AI_ANTHROPIC_MODEL=deepseek-v4-pro
export FLOWPILOT_AI_ANTHROPIC_STRICTSCHEMA=false

# 或任意 OpenAI 兼容服务（DeepSeek/通义/Ollama 等）
export FLOWPILOT_AI_PROVIDER=openai
export FLOWPILOT_AI_OPENAI_BASEURL=https://api.deepseek.com/v1
export FLOWPILOT_AI_OPENAI_APIKEY=sk-xxx
export FLOWPILOT_AI_OPENAI_MODEL=deepseek-chat
```

## 文档索引

| 文档 | 内容 |
|---|---|
| [docs/01-开发计划.md](docs/01-开发计划.md) | 里程碑、技术选型、版本规划 |
| [docs/02-开发明细.md](docs/02-开发明细.md) | 架构、模块设计、核心流程、配置速查 |
| [docs/03-API接口文档.md](docs/03-API接口文档.md) | 全部 REST 接口规范（请求/响应/错误码） |
| [docs/04-数据库设计.md](docs/04-数据库设计.md) | 14 张表结构与索引 |
| [docs/05-部署指南.md](docs/05-部署指南.md) | 本地运行/打包/Docker/生产配置 |
| [docs/06-微信聊天记录自动化方案.md](docs/06-微信聊天记录自动化方案.md) | 微信导入自动化与合规边界 |
| [docs/07-飞书企微接入指南.md](docs/07-飞书企微接入指南.md) | 开放平台官方链接、应用创建步骤、网页应用嵌入侧边栏 |
| [docs/08-测试与上线指南.md](docs/08-测试与上线指南.md) | 测试清单、服务器部署、灰度回滚、运维监控 |
| [docs/09-数据安全与隐私保护.md](docs/09-数据安全与隐私保护.md) | 加密存储、密钥管理、合规红线、事故预案 |

## 安全与合规

- 个人微信无官方 API，仅提供合规导入通道（文档 06），不含任何非官方抓取实现
- 聊天数据采集前需完成员工/客户知情同意（企业微信会话存档按官方要求）
- 原始消息默认 90 天自动清理；敏感信息（手机号等）API 脱敏输出
- 生产环境务必：替换 JWT 密钥、启用 HTTPS（反向代理）、数据库加密存储
