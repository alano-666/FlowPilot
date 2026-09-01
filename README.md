# FlowPilot 流程领航员

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

**界面导览（5 个菜单，从左边栏进入）：**

| 菜单 | 里面有什么 |
|---|---|
| 📊 项目看板 | 所有项目的卡片墙：进度条、风险颜色、最近动态 |
| 项目详情（点卡片） | 流程图（✅完成/🔄进行中/⏳待开始）、干系人、证据链、消息记录 |
| 📚 流程模板 | 上传 Word/PDF 文档让 AI 自动建模，或编辑已有模板 |
| 🔌 数据源管理 | **飞书接入向导**（三步引导+一键检测）、微信导入、渠道绑定 |
| ⚙️ 系统设置 | 用户管理、运行配置 |

### 第 6 步：第一次体验（没接飞书也能看，3 分钟）

1. 看板点进任意一个演示项目（自带演示数据：模板、群聊消息、AI 分析结果都已就绪）
2. 点「🤖 立即 AI 分析」→ 观察流程图节点、进度条、干系人、风险标识的变化
3. 接好真实飞书群后（第 7 步），群里发消息 → 消息秒级进看板 → AI 分析 → 干系人自动识别

### 第 7 步：接入真实飞书（评审演示必做，约 15 分钟）

让系统真正读你飞书群里的消息、往群里发提醒。**按下面顺序操作，缺一步都不生效**：

**7.1 先补上「机器人」能力（90% 的人漏掉，导致后面找不到权限）**
1. 打开 https://open.feishu.cn/app → 创建企业自建应用
2. 左侧菜单 → 「**添加应用能力**」→ 找到【**机器人**】点添加
3. ⚠️ 不加机器人能力，所有消息相关权限根本不会显示出来

**7.2 开通权限（新版权限名，别搜旧名字）**
- 左侧菜单 → 「**开发配置**」→「**权限管理**」→【API 权限】标签页
- 页面上方切换到「**应用身份权限（tenant_access_token）**」
- 逐个搜索并开通（勾选后点开通，自建应用自动审批通过）：

| 作用 | 权限名（新版） | 旧教程里找不到很正常 |
|---|---|---|
| 机器人发提醒消息 | `im:message:send_as_bot` | 旧名 `im:message` |
| 读取群里消息（**推荐**，看板能跟踪全群对话） | `im:message.group_msg:readonly` | 旧名 `im:message` |
| 读取群里 @机器人 的消息（最低要求） | `im:message.group_at_msg:readonly` | 旧名 `im:message` |
| 读取单聊消息 | `im:message.p2p_msg:readonly` | 旧名 `im:message` |
| 读取群信息 | `im:chat:read` | 旧名 `im:chat` |
| 读取用户昵称头像 | `contact:user.base:readonly` | 名字没变 |

> 💡 **懒人方式**：权限管理页右上角「批量导入」→ 粘贴下面 JSON 一键开通：
> ```json
> {
>   "scopes": {
>     "tenant": [
>       "contact:user.base:readonly",
>       "im:chat:read",
>       "im:message.group_msg:readonly",
>       "im:message.group_at_msg:readonly",
>       "im:message.p2p_msg:readonly",
>       "im:message:send_as_bot"
>     ],
>     "user": []
>   }
> }
> ```
> 💡 **注意**：只开 `group_at_msg` 时，系统只能收到「@机器人」的消息；开了 `group_msg:readonly` 后群里任何消息都能被 AI 跟踪，体验最好。

**7.3 配置事件订阅（让飞书把群消息推给系统）**
1. 左侧「**事件与回调**」→ 订阅方式选【**将事件发送至开发者服务器**】
2. 回调地址填：`https://你的公网地址/api/v1/webhooks/feishu/events`
3. 「添加事件」→ 消息与群组 → 勾选「**接收消息 im.message.receive_v1**」→ 保存
4. ⚠️ 保存时飞书会立刻向回调地址发一条验证请求，系统必须正确应答才能保存成功；
   所以**先把公网地址准备好再填**（见 7.4），且填完让服务保持运行

**7.4 没有域名？用内网穿透变出公网地址**
```bash
./scripts/tunnel.sh        # 自动选择已安装的工具
./scripts/tunnel.sh cpolar # 指定 cpolar（国内推荐，需注册取 authtoken）
./scripts/tunnel.sh cloudflared # 免注册（cloudflared，试试就知网络是否通）
```
- 本机已装好：cpolar（`~/.local/bin/cpolar`，首次需 `cpolar authtoken <官网注册的token>`）+ cloudflared（免注册）
- 脚本会自动把本机变成公网，并**直接打印要填的完整回调地址**，复制粘贴即可
- ⚠️ 穿透的终端窗口**不能关**，关了公网地址就失效，群消息就收不到了
- 免费版地址重启后会变，演示当天重新运行脚本、把新地址更新到飞书后台即可

**7.5 填凭证并重启**
- 把 App ID / App Secret 填进第 3 步的 `.env.local` 两行，保存后重启
- 打开数据源管理页点「**🔍 检测飞书凭证**」→ 显示 ✅ 即打通

**7.6 发布版本（最容易漏的一步！不发布 = 全部白配）**
- 左侧「**版本管理与发布**」→ 创建版本（版本号随意，如 1.0.0）→ 保存 → 发布
- 你是管理员就自己点同意；**权限、事件订阅不发布版本完全不会生效**
- 常见报错对照：检测时提示 `232034 The app is unavailable or inactivate` 就是还没发布版本

**7.7 绑定群聊，跑通闭环**
1. 把机器人拉进你的项目群（群设置 → 添加机器人 → FlowPilot）
2. 数据源管理页 → 项目渠道绑定 → 填该群的 `chat_id`（自检通过后页面上会列出机器人所在群，直接复制）
3. 群里随便发一条消息 → 3 秒内出现在项目详情页 → 点「立即 AI 分析」→ 看板更新 → 超时后系统自动在群里 @责任人

---

## ❓ 卡壳了？常见问题排查（对着查）

| 现象 | 原因 | 怎么办 |
|---|---|---|
| 浏览器打不开 localhost:8080 | 服务没启动成功 | 回到启动窗口看有没有红色报错；确认窗口没被关掉 |
| 报错"端口被占用 Port 8080 was already in use" | 已经开了一个 FlowPilot 或别的程序占着 8080 | 关掉旧窗口重开；或用 `--server.port=8081` 换端口启动 |
| 报错"找不到 Java" | 第 0 步没装好 | 重装 Java 后**重新打开终端窗口**再启动（旧窗口认不到新装的 Java） |
| 登录页打不开但显示 404/白屏 | 前端资源没加载 | 确认是从 `flowpilot.jar` 同目录启动；换 Chrome 浏览器试试 |
| AI 分析报错"50010 AI 调用失败" | 密钥/网关配置不对 | 检查 `.env.local` 里 APIKEY 是否有多余空格、BASEURL 是否完整；把 PROVIDER 改成 mock 先确认系统本身正常 |
| 飞书检测显示 ❌ | AppID/Secret 错、应用未发布、权限没开通 | 对照 docs/07 检查；报错 `232034 app is unavailable or inactivate` = **应用还没发布版本**，去「版本管理与发布」发布一个版本再试 |
| 飞书后台保存回调地址报"地址非法" | 地址不是公网 HTTPS，或验证请求没被正确应答 | 用 `tunnel.sh` 生成公网地址再填；填之前确认 FlowPilot 服务在运行；localhost/127.0.0.1 飞书永远连不上 |
| 群里发消息看板没反应 | 回调地址不通、应用未发布、权限不足或群没绑定 | ① 应用发布版本了吗 ② tunnel 窗口还开着吗 ③ 机器人还在群里吗 ④ 只开了 @机器人 权限时需 @机器人 发消息 ⑤ 项目绑定了该群 chat_id 吗 |
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
| [docs/08-测试与上线指南.md](docs/08-测试与上线指南.md) | 测试清单、上线步骤、运维监控 |
| [docs/09-数据安全与隐私保护.md](docs/09-数据安全与隐私保护.md) | 加密存储、密钥管理、合规红线 |
| [docs/10-评审演示脚本.md](docs/10-评审演示脚本.md) | 5 分钟演示「飞书×AI」真实闭环话术 |

## 🎯 演示站（界面预览，数据为模拟）

https://alano-666.github.io/FlowPilot/ —— 不用安装，浏览器打开就能看界面长什么样（功能是模拟的）。

## 🔧 技术栈（给开发者看）

- 后端：Java 21+ / Spring Boot 3.5 / Spring Data JPA / H2(开发)→PostgreSQL(生产) / JWT / POI
- AI：Anthropic 官方 SDK（结构化输出）/ 第三方 Anthropic 兼容网关 / OpenAI 兼容协议 / Mock，可插拔
- 前端：Vue 3 / Vite / Element Plus（构建产物内嵌后端，单包部署）
- 定时任务：Spring @Scheduled（同步/分析/巡检/摘要/报告/清理）

## 🔒 安全与合规

- 个人微信无官方 API，仅提供合规导入通道（docs/06），不含任何非官方抓取实现
- 原始消息默认 90 天自动清理；数据库 AES-256-GCM 列加密；敏感信息 API 脱敏输出
- 生产环境务必：替换 JWT 密钥、启用 HTTPS、配置数据库加密密钥（docs/09）