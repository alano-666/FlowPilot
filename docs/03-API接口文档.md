# FlowPilot API 接口文档（V1.0）

> 版本：V1.0　|　更新：2026-08-27　|　Base URL：`http://<host>:8080/api/v1`

## 1. 通用约定

### 1.1 响应结构

所有接口返回统一信封：

```json
{ "code": 0, "message": "success", "data": { } }
```

- `code = 0` 表示成功；非 0 为错误码（见 1.3）
- `data` 为业务数据，错误时为 `null`

### 1.2 认证方式

除登录与渠道回调外，所有接口需携带请求头：

```
Authorization: Bearer <JWT_TOKEN>
```

Token 由 `POST /auth/login` 签发，默认 24 小时过期（可配置）。
角色层级：`ADMIN`(企业管理者) > `MANAGER`(流程负责人) > `MEMBER`(执行人员)，
接口标注「角色：MANAGER」时 MANAGER 与 ADMIN 均可访问。

### 1.3 错误码表

| 码 | 含义 | 码 | 含义 |
|---|---|---|---|
| 0 | 成功 | 40903 | 模板已停用不可编辑 |
| 40000 | 参数校验失败 | 40904 | 模板无节点无法发布 |
| 40001 | 上传文件过大 | 40905 | 节点 JSON 非法/key 重复 |
| 40002 | 内容为空 | 40906 | 模板未发布不可建项目 |
| 40003 | 不支持的文档格式 | 40907 | 渠道已绑定 |
| 40004 | 文档解析失败 | 40908 | 建议已处理 |
| 40005 | 飞书文档链接无效 | 40909 | 用户名已存在 |
| 40006 | 渠道参数错误 | 40910 | 模板无节点无法演示 |
| 40007 | 校准参数错误 | 40901 | 无新增消息无需分析 |
| 40008 | 密码过短 | 50000 | 服务内部错误 |
| 40100 | 未登录 | 50010 | AI 调用失败 |
| 40101 | 凭证无效/过期 | 50011 | 建议内容解析失败 |
| 40102 | 用户名或密码错误 | 50012 | 报告生成失败 |
| 40103 | 账号已停用 | 50020 | AI Provider 配置错误 |
| 40300 | 权限不足 | 50030 | 飞书接口错误 |
| 40402 | 模板不存在 | 40403 | 渠道绑定不存在 |
| 40404 | 干系人不存在 | 40405 | 建议不存在 |
| 40406 | 用户不存在 | 40400 | 接口不存在 |

### 1.4 分页响应结构

```json
{ "items": [ ], "total": 12, "page": 1, "size": 20 }
```

---

## 2. 认证与用户

### 2.1 登录

```
POST /auth/login        [公开]
```

请求体：

```json
{ "username": "admin", "password": "admin123" }
```

响应 `data`：

```json
{
  "token": "eyJhbGciOi...",
  "user": { "id": 1, "username": "admin", "displayName": "企业管理员",
}
```

### 2.2 当前用户

```
GET /auth/me
```

### 2.3 用户列表

```
GET /users
```

### 2.4 创建用户　[角色：ADMIN]

```
POST /users
{ "username": "zhangsan", "password": "123456", "displayName": "张三", "role": "MANAGER" }
```

### 2.5 修改用户　[角色：ADMIN]

```
PUT /users/{id}
{ "displayName": "张三丰", "role": "MANAGER", "feishuOpenId": "ou_xxx",
```

### 2.6 重置密码　[角色：ADMIN]

```
PUT /users/{id}/password
{ "newPassword": "newpass123" }
```

---

## 3. 流程模板（PRD 3.1）

### 3.1 模板列表

```
GET /templates?keyword=&page=1&size=20
```

响应 `data.items` 元素：

```json
{ "id": 1, "name": "远程安装设备", "description": "...", "version": 1,
  "status": "ACTIVE", "nodesJson": "[...]", "branchesJson": "[...]",
  "glossaryJson": "[...]", "sourceDocName": "远程安装设备操作流程V2.0.docx",
  "createdBy": "admin", "createdAt": "...", "updatedAt": "..." }
```

### 3.2 模板详情

```
GET /templates/{id}
```

### 3.3 上传文档 AI 建模　[角色：MANAGER]

```
POST /templates/parse        multipart/form-data
  file: 流程文档（.txt/.md/.docx/.pdf，≤30MB）
```

处理流程：文档解析 → LLM 建模 → 生成 **DRAFT** 草稿模板（不直接生效）。
响应 `data` 为草稿模板实体。前端随后跳转模板编辑页人工确认。

### 3.4 粘贴文本 AI 建模　[角色：MANAGER]

```
POST /templates/parse-text
{ "docName": "远程安装流程.md", "text": "1. 开通策略：..." }
```

### 3.5 飞书文档链接建模　[角色：MANAGER]

```
POST /templates/parse-feishu
{ "docUrl": "https://xxx.feishu.cn/docx/AbCdEfGh" }
```

前置条件：飞书应用已开通文档读取权限（docs:docx:readonly）且被加入文档协作者。

### 3.6 编辑模板（自动生成版本快照）　[角色：MANAGER]

```
PUT /templates/{id}
{ "name": "远程安装设备", "description": "...",
  "nodesJson": "[{\"key\":\"open_policy\",\"name\":\"开通策略\",\"type\":\"start\",
    \"completion_criteria\":\"客户后台显示策略已生效\",
    \"responsible_roles\":[\"客户IT\"],\"sla_hours\":4}]",
  "branchesJson": "[{\"condition\":\"如果客户已购买远程授权\",\"from\":\"open_policy\",\"to\":\"enable_remote\"}]",
  "glossaryJson": "[{\"term\":\"策略\",\"synonyms\":[\"policy\"],\"explanation\":\"...\"}]",
  "note": "编辑器修改" }
```

校验规则：节点 JSON 必须合法、`key` 必填且唯一。

### 3.7 发布模板　[角色：MANAGER]

```
POST /templates/{id}/publish
```

发布后模板可用于创建项目；项目创建时固化模板快照。

### 3.8 停用 / 复制模板

```
POST /templates/{id}/archive       [角色：MANAGER]
POST /templates/{id}/duplicate
```

### 3.9 版本历史

```
GET /templates/{id}/versions
```

---

## 4. 项目（AI 事件，PRD 3.7）

### 4.1 项目分页列表（看板）

```
GET /projects?status=ACTIVE&riskStatus=WARNING&keyword=上海&page=1&size=20
```

参数均可选。响应 `data.items` 元素核心字段：

```json
{ "id": 1, "code": "P20260827001", "name": "上海某某科技远程安装",
  "templateId": 1, "templateName": "远程安装设备",
  "customerName": "上海某某科技", "status": "ACTIVE",
  "currentNodeKey": "remote_install", "progress": 0.6,
  "riskStatus": "WARNING", "manualLock": false,
  "latestActivity": "李四已远程连接客户服务器",
  "lastAnalyzedAt": "...", "updatedAt": "..." }
```

### 4.2 项目详情

```
GET /projects/{id}
```

响应 `data`：

```json
{ "project": { ... }, "channels": [ ... ], "stakeholders": [ ... ],
  "timeline": [ { "time": "...", "type": "message|analysis|calibration|notification", "text": "..." } ],
  "runs": [ ... ] }
```

### 4.3 创建项目　[角色：MANAGER]

```
POST /projects
{
  "name": "上海某某科技远程安装",
  "templateId": 1,
  "customerName": "上海某某科技",
  "channels": [ { "channelType": "FEISHU", "channelId": "oc_xxx", "channelName": "项目群" } ]
}
```

规则：模板必须已发布；项目编号自动生成（P+日期+序号）；模板节点/分支/词库以快照固化。

### 4.4 CSV 批量导入　[角色：MANAGER]

```
POST /projects/batch-import
{ "csvText": "项目名称,模板ID,客户名称\n北京某集团远程安装,1,北京某集团\n" }
```

响应 `data`：`{ "success": 1, "failed": 0, "errors": [] }`

### 4.5 状态流转（暂停/归档/恢复）　[角色：MANAGER]

```
PUT /projects/{id}/status
{ "status": "PAUSED" }   // ACTIVE / PAUSED / ARCHIVED
```

### 4.6 修改基本信息　[角色：MANAGER]

```
PUT /projects/{id}
{ "name": "...", "customerName": "...", "ownerId": 2 }
```

### 4.7 渠道绑定/解绑/停用　[角色：MANAGER]

```
POST   /projects/{id}/channels            { "channelType": "FEISHU", "channelId": "oc_xxx", "channelName": "项目群" }
DELETE /projects/{id}/channels/{channelId}
PUT    /projects/{id}/channels/{channelId}/sync    { "enabled": false }
```

### 4.8 手动触发 AI 分析　[角色：MANAGER]

```
POST /projects/{id}/analyze
```

同步执行，响应 `data` 为本次运行记录：

```json
{ "id": 3, "projectId": 1, "provider": "mock", "model": null,
  "triggerType": "MANUAL", "status": "SUCCESS",
  "messageCount": 10, "createdAt": "...", "finishedAt": "..." }
```

前置条件：上次分析之后存在新消息，否则返回 `40901`。

### 4.9 分析历史 / 证据链

```
GET /projects/{id}/analyses
GET /projects/{id}/insights
```

`insights` 元素：`{ "id", "projectId", "runId", "messageId", "detectedNodeKey", "summary", "confidence" }`

### 4.10 人工修正　[角色：MANAGER]（PRD 3.6）

```
POST /projects/{id}/correction
{ "field": "current_node", "newValue": "enable_remote", "note": "客户电话确认", "lock": true }
```

- `field`：`current_node`（节点 key）/ `progress`（0~1 小数）/ `risk_status`（NORMAL/WARNING/BLOCKED）
- `lock` 缺省为 `true`：修正后项目锁定，AI 不得自动覆盖
- 每次修正写入校准日志（修改人/时间/前后值），见 4.13

### 4.11 解除锁定　[角色：MANAGER]

```
POST /projects/{id}/unlock
```

### 4.12 AI 待确认建议

```
GET  /projects/{id}/suggestions
POST /projects/suggestions/{suggestionId}/confirm    [角色：MANAGER] 采纳并覆盖项目状态
POST /projects/suggestions/{suggestionId}/reject     [角色：MANAGER] 驳回
```

锁定期间 AI 分析结果不落库，转为 `PENDING` 建议；用户采纳时视为授权覆盖。

### 4.13 校准日志（审计）

```
GET /projects/{id}/calibrations
```

元素：`{ "id", "projectId", "userId", "username", "field", "oldValue", "newValue", "note", "createdAt" }`

### 4.14 干系人管理　[角色：MANAGER]

```
POST   /projects/{id}/stakeholders            { "nodeKey": "enable_remote", "role": "客户IT",
                                               "contactId": "zhangsan", "wechatId": "" }
PUT    /projects/{id}/stakeholders/{sid}
DELETE /projects/{id}/stakeholders/{sid}
```

### 4.15 消息与时间线

```
GET /projects/{id}/messages?page=1&size=50    // 倒序
GET /projects/{id}/timeline                   // 消息+分析+校准+通知 合并
```

---

## 5. 渠道与同步（PRD 3.2）

### 5.1 渠道接入状态

```
GET /channels/status
```

响应示例：

```json
{ "feishu": { "configured": false, "appId": "", "supported": true,
              "note": "群消息自动同步 + 事件回调 + 一键深链沟通" },
  "wechat": { "configured": true, "watchDir": "./data/watch",
              "watchEnabled": true, "ocr": "disabled", "note": "..." },
  "mock":   { "configured": true, "note": "演示渠道" } }
```

### 5.2 飞书群列表（绑定用）

```
GET /channels/feishu/chats
// [ { "chat_id": "oc_xxx", "name": "项目交付群" } ]
```

### 5.3 手动触发全渠道同步　[角色：MANAGER]

```
POST /channels/sync
// { "syncedMessages": 12 }
```

同步后新增消息自动触发对应项目的异步 AI 分析。

### 5.4 生成演示消息（mock 渠道）　[角色：MANAGER]

```
POST /channels/mock/generate?projectId=1
{ "advanceTo": "acceptance" }   // 可选：推进到的节点 key；缺省推进到中间节点
// { "generated": 9 }
```

```
POST /webhooks/feishu/events     // 飞书：url_verification + im.message.receive_v1，支持 AES 解密
```

---

## 6. 微信导入（PRD 3.2 + 自动化增强）

### 6.1 上传聊天记录/截图

```
POST /imports/wechat?projectId=1     multipart/form-data
  file: 记录.txt / 记录.csv / 截图.png/jpg
```

- TXT/CSV：多格式自动识别（微信复制格式、CSV 导出等），见 docs/06
- 截图：启用 OCR（`flowpilot.wechat.ocr.provider=baidu`）时自动识别文字；未启用则归档待补录
- 响应：`{ "id", "fileName", "format": "TXT|CSV|IMAGE", "messageCount": 2,
           "status": "SUCCESS|PARTIAL|FAILED", "note": "..." }`

### 6.2 导入记录

```
GET /imports?projectId=&page=1&size=20
```

### 6.3 监控目录状态（自动导入）

```
GET /imports/watch-status
// { "watchDir": "/abs/path/data/watch",
//   "hint": "把微信聊天记录 TXT/CSV 或截图扔进该目录即自动导入并触发 AI 分析；文件名包含项目名或客户名可自动归属项目。" }
```

自动归属规则（按优先级）：
1. 文件名包含**唯一**匹配的项目名/客户名 → 归属该项目；
2. 无匹配但系统中仅有一个进行中项目 → 归属该项目；
3. 其余情况 → 记录失败原因并归档文件，不产生消息。

---

## 7. 看板（PRD 3.4）

```
GET /dashboard/overview
// { "activeCount": 1, "warningCount": 1, "blockedCount": 0,
//   "archivedCount": 0, "avgProgress": 0.6, "todayAnalyzed": 1 }

GET /dashboard/risks                       // 预警+卡顿项目列表
GET /dashboard/projects/{id}/stakeholders  // 项目干系人
```

---

## 8. 报告（PRD 3.9）

### 8.1 生成报告　[角色：MANAGER]

```
POST /reports/generate
{ "period": "周报", "from": "2026-08-18T00:00:00", "to": "2026-08-25T00:00:00" }
```

`period` 缺省「周报」；`from/to` 缺省按最近 7/30 天。响应 `data`：

```json
{ "summary": {
    "totalProjects": 3, "activeProjects": 2, "riskProjectCount": 1, "riskRate": 33.3,
    "flowStats": [ { "flowName": "远程安装设备", "projectCount": 2, "avgHours": 12.5 } ],
    "bottleneckNodes": [ { "node": "远程安装设备/开启远程权限", "stuckCount": 2 } ],
    "responseStats": [ { "project": "...", "avgResponseHours": 0.8 } ] },
  "files": { "excel": ".../周报-xxx.xlsx", "pdf": ".../周报-xxx.pdf", "html": ".../周报-xxx.html" } }
```

### 8.2 报告列表 / 下载

```
GET /reports
GET /reports/download/{fileName}
```

---

## 9. 通知（PRD 3.8）

### 9.1 通知任务列表

```
GET /notifications?projectId=&page=1&size=20
```

元素：`{ "id", "projectId", "type": "SLA_OVERDUE|NODE_COMPLETED|DAILY_DIGEST|RISK_ALERT",
        "title", "content", "targetsJson", "status": "SENT|PENDING|FAILED|SKIPPED",
        "createdAt", "executedAt" }`

### 9.2 手动推送每日摘要　[角色：MANAGER]

```
POST /notifications/digest
// { "pushedProjects": 2 }
```

### 9.3 定时任务（服务端自动执行）

| 任务 | 默认频率 | 说明 |
|---|---|---|
| 渠道增量同步 | 每 30 分钟 | 飞书群消息水位线同步 |
| 增量 AI 分析 | 每 30 分钟 | 有新增消息的项目自动分析 |
| SLA 巡检 | 每小时 5 分 | 超时节点 @责任人 + 风险升级 |
| 每日摘要 | 每天 9:00 | 推送进行中项目摘要 |
| 周报/月报 | 周一/每月 1 日 8:00 | 自动生成三种格式 |
| 数据留存清理 | 每天 3:30 | 超期原始消息清理（默认 90 天） |

---

## 10. 系统设置

```
GET /settings     // 运行配置（脱敏只读）+ 版本号
```