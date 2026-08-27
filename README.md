# FlowPilot 流程领航员

> **一句话介绍**：把你的业务流程文档交给 AI 建模，自动跟踪飞书群/企业微信/微信/邮件里的沟通记录，
> AI 判断每个项目走到哪一步、进度多少、谁在负责、有没有风险，全部显示在一个看板上，还能自动提醒责任人。

---

## 🚀 新手使用教程（小白跟着做就行，每一步都写了"为什么"）

### 第 0 步：确认电脑上有 Java（一次性，2 分钟）

FlowPilot 是用 Java 写的，电脑上需要先有 Java 运行环境。

**怎么检查：**
- **Mac**：打开「终端」（按 `Command + 空格`，输入"终端"回车），粘贴下面这行回车：
  ```
  java -version
  ```
- **Windows**：按 `Win + R`，输入 `cmd` 回车，在黑窗口里输入 `java -version` 回车。

**怎么看结果：**
- ✅ 显示类似 `java version "17..."` 或 `"21..."` 或 `"25..."` → 已装好，跳到第 1 步
- ❌ 显示"找不到命令"或"不是内部或外部命令" → 没装，去 [https://adoptium.net](https://adoptium.net) 下载 Temurin 21（点最大的下载按钮），安装时一路"下一步"即可，装完重新打开终端再检查一次

> 💡 **为什么要装 Java？** FlowPilot 的后端服务是用 Java 写的，就像打开 Word 文档需要装 Office 一样，运行它需要 Java 环境。

### 第 1 步：拿到软件（两种方式，任选其一）

**方式 A：从 GitHub 下载（需要网络）**
1. 打开 https://github.com/alano-666/FlowPilot/releases
2. 找到最新的 **v1.0.0**，点 `flowpilot-v1.0.0.zip`（约 100MB）下载
3. 下载完成后看第 2 步

**方式 B：别人发的压缩包 / U 盘 / 微信文件（不用网络）**
1. 收到 `flowpilot-v1.0.0.zip` 文件后直接看第 2 步

> 💡 **下载慢怎么办？** 100MB 在国内网络下大约 1-5 分钟；如果太慢，找已经下好的人用微信/U 盘传一份（方式和 B 完全一样）。

### 第 2 步：解压，认识里面的文件（2 分钟）

把 zip 文件解压到任意位置（比如桌面），得到文件夹 `flowpilot-v1.0.0`，里面主要有：

| 文件 | 干什么的 | 你需要动它吗 |
|---|---|---|
| `flowpilot.jar` | 程序本体 | ❌ 不用动，别删 |
| `start.sh` | Mac/Linux 启动脚本 | ❌ 不用动 |
| `start.bat` | Windows 启动脚本 | ❌ 不用动 |
| `.env.example` | **配置模板** | ✅ **要改，见第 3 步** |
| `开箱即用.txt` | 精简版说明 | 选读 |
| `docs/` 文件夹 | 10 份详细文档 | 卡壳时查 |

### 第 3 步：填写配置（最重要的一步，5 分钟）

**3.1 先复制一份配置模板：**
- Mac：在终端里输入 `cp .env.example .env.local`（先 `cd` 进解压出来的文件夹）
- Windows：在文件夹里选中 `.env.example` → `Ctrl+C` 复制 → `Ctrl+V` 粘贴 → 把副本改名为 `.env.local`

**3.2 打开 `.env.local` 填内容**（用记事本/文本编辑打开，里面每行都有中文注释）：

**必填一：AI 密钥**（让 AI 干活的大脑，二选一）
```
# 方式1：用中转站（推荐，国内快）——把你买到的密钥填在等号后面
export FLOWPILOT_AI_PROVIDER=anthropic
export FLOWPILOT_AI_ANTHROPIC_APIKEY=在这里粘贴你的AI密钥
export FLOWPILOT_AI_ANTHROPIC_BASEURL=https://你的中转站地址/api/anthropic
export FLOWPILOT_AI_ANTHROPIC_MODEL=deepseek-v4-pro
export FLOWPILOT_AI_ANTHROPIC_STRICTSCHEMA=false

# 方式2：先不填任何 AI 配置 → 系统自动用"演示模式"（界面照常用，AI 用内置规则模拟）
```

**必填二：飞书凭证**（接真实飞书群时填；暂时没有就先留空，跳过不填也能用）
```
export FLOWPILOT_FEISHU_APPID=cli_xxxxxxx
export FLOWPILOT_FEISHU_APPSECRET=xxxxxxxx
```

> 💡 **AppID/Secret 从哪来？** 见 docs/07 文档第 0 章：注册飞书开放平台 → 创建企业自建应用 → 应用详情页里复制。评审演示必须填这一项。

**3.3 保存文件。**

### 第 4 步：启动（10 秒钟）

- **Mac/Linux**：终端里输入 `./start.sh` 回车
- **Windows**：双击 `start.bat`

看到类似 `Tomcat started on port 8080` 的字样就是启动成功了。**这个窗口别关**，关了就停了。

### 第 5 步：打开网页，登录（1 分钟）

1. 打开浏览器（Chrome/Edge/Safari 都行），地址栏输入：**http://localhost:8080**
2. 账号 `admin`，密码 `admin123`，点登录

**界面导览（7 个菜单，从左边栏进入）：**

| 菜单 | 里面有什么 |
|---|---|
| 📊 项目看板 | 所有项目的卡片墙：进度条、风险颜色、最近动态 |
| 项目详情（点卡片） | 流程图（✅完成/🔄进行中/⏳待开始）、干系人、证据链、消息记录 |
| 📚 流程模板 | 上传 Word/PDF 文档让 AI 自动建模，或编辑已有模板 |
| 🔌 数据源管理 | **飞书接入向导**（三步引导+一键检测）、微信导入、渠道绑定 |
| 📄 报告中心 | 生成周报/月报，下载 Excel/PDF |
| 🔔 通知预警 | 所有预警推送记录 |
| ⚙️ 系统设置 | 用户管理、运行配置 |

### 第 6 步：第一次体验（没接飞书也能玩，3 分钟）

1. 看板点进任意一个项目（自带 4 个演示项目）
2. 点右上角「🎭 生成演示消息」→ 再点「🤖 立即 AI 分析」
3. 观察：流程图节点推进、进度条变化、干系人卡片、风险预警自动出现

### 第 7 步：接入真实飞书（评审演示必做，约 15 分钟）

让系统真正读你飞书群里的消息、往群里发提醒：

1. 打开 https://open.feishu.cn/app → 创建企业自建应用（需要企业管理员同意）
2. 应用里开通 3 个权限：`im:message`、`im:chat`、`contact:user.base:readonly`
3. 事件订阅勾选 `im.message.receive_v1`，回调地址填 `https://你的公网地址/api/v1/webhooks/feishu/events`
   - **没有域名？** 终端运行 `./scripts/tunnel.sh`，脚本会自动把本机变成公网并**直接打印要填的完整地址**，复制粘贴即可
4. 把 AppID/Secret 填进第 3 步的 `.env.local`，重启
5. 数据源管理页点「🔍 检测飞书凭证」→ 显示 ✅ 后，把机器人拉进项目群，绑定群 ID
6. 在群里发一条消息，3 秒内它就会出现在项目详情页

> 完整的图文步骤和评审 5 分钟演示话术，见 `docs/07-飞书企微接入指南.md` 和 `docs/10-评审演示脚本.md`。

---

## ❓ 卡壳了？常见问题排查（对着查）

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 浏览器打不开 localhost:8080 | 服务没启动成功 | 回到启动窗口看有没有红色报错；确认窗口没被关掉 |
| 报错"端口被占用 Port 8080 was already in use" | 已经开了一个 FlowPilot 或别的程序占着 8080 | 关掉旧窗口重开；或用 `--server.port=8081` 换端口启动 |
| 报错"找不到 Java" | 第 0 步没装好 | 重装 Java 后**重新打开终端窗口**再启动（旧窗口认不到新装的 Java） |
| 登录页打不开但显示 404/白屏 | 前端资源没加载 | 确认是从 `flowpilot.jar` 同目录启动；换 Chrome 浏览器试试 |
| AI 分析报错"50010 AI 调用失败" | 密钥/网关配置不对 | 检查 `.env.local` 里 APIKEY 是否有多余空格、BASEURL 是否完整；把 PROVIDER 改成 mock 先确认系统本身正常 |
| 飞书检测显示 ❌ | AppID/Secret 错、应用未发布、网络不通 | 对照 docs/07 检查；先确认你电脑能打开 open.feishu.cn |
| 群里发消息看板没反应 | 回调地址不通或群没绑定 | 检查 tunnel 是否还在运行、飞书后台回调地址是否更新、机器人是否在群里、项目是否绑定了该群 chat_id |
| 修改配置后没生效 | 没重启 | 每次改 `.env.local` 后必须重启（关掉启动窗口重新运行 start 脚本） |
| Windows 双击 start.bat 闪退 | Java 没装或路径问题 | 先按第 0 步检查 java -version；在文件夹地址栏输入 cmd 回车后手动运行 `java -jar flowpilot.jar` 看报错 |

---

## 📚 进阶文档（想深入了解再看）

| 文档 | 内容 |
|---|---|
| [docs/01-开发计划.md](docs/01-开发计划.md) | 里程碑、技术选型、版本规划 |
| [docs/02-开发明细.md](docs/02-开发明细.md) | 架构、模块设计、核心流程、配置速查 |
| [docs/03-API接口文档.md](docs/03-API接口文档.md) | 全部 REST 接口规范（请求/响应/错误码） |
| [docs/04-数据库设计.md](docs/04-数据库设计.md) | 14 张表结构与索引 |
| [docs/05-部署指南.md](docs/05-部署指南.md) | 服务器部署、Docker、生产配置 |
| [docs/06-微信聊天记录自动化方案.md](docs/06-微信聊天记录自动化方案.md) | 微信导入自动化与合规边界 |
| [docs/07-飞书企微接入指南.md](docs/07-飞书企微接入指南.md) | 官方链接、应用创建图文步骤、嵌入飞书侧边栏 |
| [docs/08-测试与上线指南.md](docs/08-测试与上线指南.md) | 测试清单、上线步骤、运维监控 |
| [docs/09-数据安全与隐私保护.md](docs/09-数据安全与隐私保护.md) | 加密存储、密钥管理、合规红线 |
| [docs/10-评审演示脚本.md](docs/10-评审演示脚本.md) | 5 分钟演示「飞书×AI」真实闭环话术 |

## 🎯 演示站（界面预览，数据为模拟）

https://alano-666.github.io/FlowPilot/ —— 不用安装，浏览器打开就能看界面长什么样（功能是模拟的）。

## 🔧 技术栈（给开发者看）

- 后端：Java 21+ / Spring Boot 3.5 / Spring Data JPA / H2(开发)→PostgreSQL(生产) / JWT / POI / OpenPDF
- AI：Anthropic 官方 SDK（结构化输出）/ 第三方 Anthropic 兼容网关 / OpenAI 兼容协议 / Mock，可插拔
- 前端：Vue 3 / Vite / Element Plus（构建产物内嵌后端，单包部署）
- 定时任务：Spring @Scheduled（同步/分析/巡检/摘要/报告/清理）

## 🔒 安全与合规

- 个人微信无官方 API，仅提供合规导入通道（docs/06），不含任何非官方抓取实现
- 聊天数据采集前需完成员工/客户知情同意（企业微信会话存档按官方要求）
- 原始消息默认 90 天自动清理；数据库 AES-256-GCM 列加密；敏感信息 API 脱敏输出
- 生产环境务必：替换 JWT 密钥、启用 HTTPS、配置数据库加密密钥（docs/09）
