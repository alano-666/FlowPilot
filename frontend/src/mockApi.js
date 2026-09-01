/**
 * 演示模式数据层（GitHub Pages 静态版专用）。
 * 与后端 API 返回结构完全一致，所有界面功能可用，数据为内置模拟，
 * 页面顶部有醒目「演示模式」横幅标识。
 *
 * 构建方式：VITE_DEMO_MODE=true vite build --outDir ../site --base=/flowpilot/
 */
const now = new Date()
const ts = (hoursAgo) => new Date(now.getTime() - hoursAgo * 3600_000).toISOString()

/* ---------- 模板（与后端演示数据一致） ---------- */
const templates = [
  {
    id: 1, name: '远程安装设备', description: '客户远程设备安装标准流程：开通策略 → 开启远程权限 → 网络校验 → 远程安装 → 测试验收',
    version: 2, status: 'ACTIVE', sourceDocName: '远程安装设备操作流程V2.0.docx（演示数据）',
    createdBy: 'system', createdAt: ts(720), updatedAt: ts(48),
    nodesJson: JSON.stringify([
      { key: 'open_policy', name: '开通策略', type: 'start', completion_criteria: '客户后台显示策略已生效', responsible_roles: ['客户IT', '我方技术支持'], sla_hours: 4 },
      { key: 'enable_remote', name: '开启远程权限', type: 'normal', completion_criteria: '远程桌面可连接', responsible_roles: ['客户IT'], sla_hours: 2 },
      { key: 'network_check', name: '网络环境校验', type: 'normal', completion_criteria: '网络连通性测试通过', responsible_roles: ['我方技术支持'], sla_hours: 2 },
      { key: 'remote_install', name: '远程安装', type: 'normal', completion_criteria: '设备安装完成并重启正常', responsible_roles: ['我方技术支持'], sla_hours: 8 },
      { key: 'acceptance', name: '测试验收', type: 'end', completion_criteria: '客户签字确认验收单', responsible_roles: ['客户IT', '销售'], sla_hours: 24 }
    ]),
    branchesJson: JSON.stringify([{ condition: '如果客户已购买远程授权', from: 'open_policy', to: 'enable_remote' }]),
    glossaryJson: JSON.stringify([{ term: '策略', synonyms: ['policy', '授权策略'], explanation: '设备远程管理的授权开通策略' }])
  },
  {
    id: 2, name: '软件项目管理流程', description: '软件交付项目全生命周期：立项 → 需求确认 → 排期 → 开发 → 验收 → 上线 → 复盘',
    version: 1, status: 'ACTIVE', sourceDocName: '软件项目管理流程操作文档.docx（演示数据）',
    createdBy: 'system', createdAt: ts(600), updatedAt: ts(72),
    nodesJson: JSON.stringify([
      { key: 'project_init', name: '项目立项', type: 'start', completion_criteria: '立项评审通过，项目章程已发布', responsible_roles: ['项目经理'], sla_hours: 24 },
      { key: 'requirement_confirm', name: '需求确认', type: 'normal', completion_criteria: '需求规格说明书评审通过', responsible_roles: ['产品经理', '项目经理'], sla_hours: 48 },
      { key: 'schedule_plan', name: '排期计划', type: 'normal', completion_criteria: '项目计划与里程碑已确认', responsible_roles: ['项目经理'], sla_hours: 24 },
      { key: 'development', name: '开发实施', type: 'normal', completion_criteria: '迭代功能开发完成并提测', responsible_roles: ['研发团队'], sla_hours: 168 },
      { key: 'test_acceptance', name: '测试验收', type: 'normal', completion_criteria: '测试通过，验收报告已签署', responsible_roles: ['测试工程师', '项目经理'], sla_hours: 72 },
      { key: 'release', name: '上线发布', type: 'normal', completion_criteria: '生产环境发布完成', responsible_roles: ['运维', '研发负责人'], sla_hours: 24 },
      { key: 'retrospective', name: '项目复盘', type: 'end', completion_criteria: '复盘会议纪要已归档', responsible_roles: ['项目经理'], sla_hours: 72 }
    ]),
    branchesJson: JSON.stringify([{ condition: '如果需求发生变更', from: 'requirement_confirm', to: 'requirement_confirm' }]),
    glossaryJson: JSON.stringify([{ term: '里程碑', synonyms: ['milestone'], explanation: '项目关键时间节点' }])
  },
  {
    id: 3, name: '软件需求更新流程', description: '软件产品需求从收集到客户验收：收集 → 评审 → 设计 → 开发 → 回归 → 验收',
    version: 1, status: 'ACTIVE', sourceDocName: '软件需求更新流程.md（演示数据）',
    createdBy: 'system', createdAt: ts(580), updatedAt: ts(96),
    nodesJson: JSON.stringify([
      { key: 'requirement_collection', name: '需求收集', type: 'start', completion_criteria: '需求单已录入系统', responsible_roles: ['产品经理'], sla_hours: 24 },
      { key: 'requirement_review', name: '需求评审', type: 'normal', completion_criteria: '评审会议纪要已确认', responsible_roles: ['产品经理', '研发负责人'], sla_hours: 48 },
      { key: 'solution_design', name: '方案设计', type: 'normal', completion_criteria: '技术方案评审通过', responsible_roles: ['研发负责人'], sla_hours: 48 },
      { key: 'development', name: '开发实现', type: 'normal', completion_criteria: '代码合入主干并自测通过', responsible_roles: ['研发工程师'], sla_hours: 96 },
      { key: 'regression_test', name: '回归测试', type: 'normal', completion_criteria: '测试用例全部通过', responsible_roles: ['测试工程师'], sla_hours: 48 },
      { key: 'customer_acceptance', name: '客户验收', type: 'end', completion_criteria: '客户确认验收单', responsible_roles: ['产品经理', '客户'], sla_hours: 72 }
    ]),
    branchesJson: JSON.stringify([]),
    glossaryJson: JSON.stringify([{ term: '需求单', synonyms: ['demand ticket'], explanation: '需求录入凭证' }])
  },
  {
    id: 4, name: '客户支持工单流程', description: '客户问题工单处理：受理 → 诊断 → 方案确认 → 处理 → 客户验证 → 归档',
    version: 1, status: 'ACTIVE', sourceDocName: '客户支持工单流程操作文档.docx（演示数据）',
    createdBy: 'system', createdAt: ts(500), updatedAt: ts(120),
    nodesJson: JSON.stringify([
      { key: 'ticket_accept', name: '工单受理', type: 'start', completion_criteria: '工单已创建并指派', responsible_roles: ['客服', '技术支持'], sla_hours: 2 },
      { key: 'diagnose', name: '问题诊断', type: 'normal', completion_criteria: '问题根因已定位', responsible_roles: ['技术支持'], sla_hours: 4 },
      { key: 'solution_confirm', name: '方案确认', type: 'normal', completion_criteria: '解决方案已获客户同意', responsible_roles: ['技术支持', '客户'], sla_hours: 8 },
      { key: 'fix_implement', name: '处理实施', type: 'normal', completion_criteria: '问题已修复并验证', responsible_roles: ['技术支持'], sla_hours: 24 },
      { key: 'customer_verify', name: '客户验证', type: 'normal', completion_criteria: '客户确认问题解决', responsible_roles: ['客户'], sla_hours: 24 },
      { key: 'ticket_close', name: '关闭归档', type: 'end', completion_criteria: '工单已关闭并归档', responsible_roles: ['客服'], sla_hours: 4 }
    ]),
    branchesJson: JSON.stringify([{ condition: '如果问题复现', from: 'customer_verify', to: 'diagnose' }]),
    glossaryJson: JSON.stringify([{ term: '工单', synonyms: ['ticket', 'case'], explanation: '客户问题处理凭证' }])
  },
  {
    id: 5, name: '软件需求更新流程-草稿', description: '真实大模型从文档解析生成的草稿（演示）',
    version: 1, status: 'DRAFT', sourceDocName: '软件需求更新流程.md',
    createdBy: 'admin', createdAt: ts(24), updatedAt: ts(24),
    nodesJson: JSON.stringify([
      { key: 'requirement_collection', name: '需求收集', type: 'start', completion_criteria: '需求单已录入系统', responsible_roles: ['产品经理'], sla_hours: 24 },
      { key: 'requirement_review', name: '需求评审', type: 'normal', completion_criteria: '评审会议纪要已确认', responsible_roles: ['产品经理', '研发负责人'], sla_hours: 48 },
      { key: 'solution_design', name: '方案设计', type: 'normal', completion_criteria: '方案评审通过', responsible_roles: ['研发负责人'], sla_hours: 48 },
      { key: 'architecture_review', name: '架构师评审', type: 'normal', completion_criteria: null, responsible_roles: ['架构师'], sla_hours: null },
      { key: 'development_implementation', name: '开发实现', type: 'normal', completion_criteria: '代码合入主干并自测通过', responsible_roles: ['研发工程师'], sla_hours: 96 },
      { key: 'regression_testing', name: '回归测试', type: 'normal', completion_criteria: '测试用例全部通过', responsible_roles: ['测试工程师'], sla_hours: 48 },
      { key: 'customer_acceptance', name: '客户验收', type: 'end', completion_criteria: '客户确认验收单', responsible_roles: ['产品经理', '客户'], sla_hours: 72 }
    ]),
    branchesJson: JSON.stringify([{ condition: '如果需求影响范围大', from: 'solution_design', to: 'architecture_review' }]),
    glossaryJson: JSON.stringify([{ term: '需求单', synonyms: ['demand ticket', '工单'], explanation: '需求录入凭证' }, { term: '验收单', synonyms: ['acceptance form'], explanation: '客户验收签署文档' }])
  }
]

/* ---------- 项目 ---------- */
const projects = [
  {
    id: 1, code: 'P20260827001', name: '上海某某科技远程安装', templateId: 1, templateName: '远程安装设备',
    customerName: '上海某某科技', status: 'ACTIVE', currentNodeKey: 'acceptance', progress: 1.0,
    riskStatus: 'WARNING', manualLock: false, startedAt: ts(72), lastAnalyzedAt: ts(2),
    latestActivity: '【风险】节点「测试验收」已持续 2 小时未推进，请注意跟进。',
    lastActivityAt: ts(2), updatedAt: ts(2), ownerId: 2, createdBy: 'system', createdAt: ts(72),
    templateSnapshotJson: JSON.stringify({ name: '远程安装设备', nodes: JSON.parse(templates[0].nodesJson), branches: [], glossary: [] })
  },
  {
    id: 2, code: 'P20260827002', name: '杭州云启-数据中台建设项目', templateId: 2, templateName: '软件项目管理流程',
    customerName: '杭州云启科技', status: 'ACTIVE', currentNodeKey: 'development', progress: 0.42,
    riskStatus: 'WARNING', manualLock: false, startedAt: ts(60), lastAnalyzedAt: ts(3),
    latestActivity: '「开发实施」正在进行中，需要客户侧配合。',
    lastActivityAt: ts(3), updatedAt: ts(3), ownerId: 2, createdBy: 'system', createdAt: ts(60),
    templateSnapshotJson: JSON.stringify({ name: '软件项目管理流程', nodes: JSON.parse(templates[1].nodesJson), branches: [], glossary: [] })
  },
  {
    id: 3, code: 'P20260827003', name: '深圳智联-ERP需求更新V2.3', templateId: 3, templateName: '软件需求更新流程',
    customerName: '深圳智联软件', status: 'ACTIVE', currentNodeKey: 'customer_acceptance', progress: 0.83,
    riskStatus: 'WARNING', manualLock: false, startedAt: ts(96), lastAnalyzedAt: ts(4),
    latestActivity: '「客户验收」正在进行中，需要客户侧配合。',
    lastActivityAt: ts(4), updatedAt: ts(4), ownerId: 2, createdBy: 'system', createdAt: ts(96),
    templateSnapshotJson: JSON.stringify({ name: '软件需求更新流程', nodes: JSON.parse(templates[2].nodesJson), branches: [], glossary: [] })
  },
  {
    id: 4, code: 'P20260827004', name: '广州迅达-网络故障工单', templateId: 4, templateName: '客户支持工单流程',
    customerName: '广州迅达物流', status: 'ACTIVE', currentNodeKey: 'fix_implement', progress: 0.5,
    riskStatus: 'BLOCKED', manualLock: false, startedAt: ts(36), lastAnalyzedAt: ts(5),
    latestActivity: '【风险】备用设备到货延迟，解决方案无法实施',
    lastActivityAt: ts(5), updatedAt: ts(5), ownerId: 2, createdBy: 'system', createdAt: ts(36),
    templateSnapshotJson: JSON.stringify({ name: '客户支持工单流程', nodes: JSON.parse(templates[3].nodesJson), branches: [], glossary: [] })
  }
]

const stakeholders = [
  { id: 1, projectId: 1, nodeKey: 'acceptance', role: '客户IT', name: '张工', contactType: 'WECOM', contactId: 'zhanggong_it', wechatId: null, avatarUrl: null, updatedAt: ts(2) },
  { id: 2, projectId: 1, nodeKey: 'acceptance', role: '我方技术支持', name: '李四', contactType: 'FEISHU', contactId: 'ou_lisi', wechatId: null, avatarUrl: null, updatedAt: ts(2) },
  { id: 3, projectId: 1, nodeKey: 'acceptance', role: '销售', name: '王五', contactType: 'WECHAT', contactId: 'wangwu_sales', wechatId: 'wangwu_sales', avatarUrl: null, updatedAt: ts(2) },
  { id: 4, projectId: 2, nodeKey: 'development', role: '研发团队', name: '陈工', contactType: 'FEISHU', contactId: 'ou_chen', wechatId: null, avatarUrl: null, updatedAt: ts(3) },
  { id: 5, projectId: 3, nodeKey: 'customer_acceptance', role: '产品经理', name: '赵敏', contactType: 'FEISHU', contactId: 'ou_zhaomin', wechatId: null, avatarUrl: null, updatedAt: ts(4) },
  { id: 6, projectId: 4, nodeKey: 'fix_implement', role: '技术支持', name: '刘工', contactType: 'WECOM', contactId: 'liugong', wechatId: null, avatarUrl: null, updatedAt: ts(5) }
]

const analyses = [
  { id: 1, projectId: 1, provider: 'mock', model: null, triggerType: 'SCHEDULE', status: 'SUCCESS', messageCount: 10, createdAt: ts(2), finishedAt: ts(2),
    resultJson: JSON.stringify({ current_node_key: 'acceptance', completed_nodes: ['open_policy', 'enable_remote', 'network_check', 'remote_install'], progress: 1.0, risk_status: 'warning', evidence: [], stakeholders_update: [], risks: ['节点「测试验收」已持续 2 小时未推进'], suggested_next_action: '联系客户签字确认验收单', temp_nodes: [], latest_activity: '风险提示' }) },
  { id: 2, projectId: 2, provider: 'mock', model: null, triggerType: 'SCHEDULE', status: 'SUCCESS', messageCount: 9, createdAt: ts(3), finishedAt: ts(3),
    resultJson: JSON.stringify({ current_node_key: 'development', completed_nodes: ['project_init', 'requirement_confirm', 'schedule_plan'], progress: 0.42, risk_status: 'warning', evidence: [], stakeholders_update: [], risks: [], suggested_next_action: '推进节点「开发实施」', temp_nodes: [], latest_activity: '开发实施进行中' }) },
  { id: 3, projectId: 3, provider: 'mock', model: null, triggerType: 'SCHEDULE', status: 'SUCCESS', messageCount: 8, createdAt: ts(4), finishedAt: ts(4),
    resultJson: JSON.stringify({ current_node_key: 'customer_acceptance', completed_nodes: ['requirement_collection', 'requirement_review', 'solution_design', 'development', 'regression_test'], progress: 0.83, risk_status: 'warning', evidence: [], stakeholders_update: [], risks: [], suggested_next_action: '推进节点「客户验收」', temp_nodes: [], latest_activity: '客户验收进行中' }) },
  { id: 4, projectId: 4, provider: 'mock', model: null, triggerType: 'SCHEDULE', status: 'SUCCESS', messageCount: 12, createdAt: ts(5), finishedAt: ts(5),
    resultJson: JSON.stringify({ current_node_key: 'fix_implement', completed_nodes: ['ticket_accept', 'diagnose', 'solution_confirm'], progress: 0.5, risk_status: 'blocked', evidence: [], stakeholders_update: [], risks: ['客户反馈故障又复现了', '客户侧联系人两天未回复消息', '备用设备到货延迟'], suggested_next_action: '催办备用设备到货', temp_nodes: [], latest_activity: '备用设备到货延迟，解决方案无法实施' }) }
]

const insights = [
  { id: 1, projectId: 1, runId: 1, messageId: 1, detectedNodeKey: 'open_policy', summary: '客户IT回复策略已生效', confidence: 0.93, createdAt: ts(2) },
  { id: 2, projectId: 1, runId: 1, messageId: 2, detectedNodeKey: 'enable_remote', summary: '技术支持发送远程邀请', confidence: 0.88, createdAt: ts(2) },
  { id: 3, projectId: 4, runId: 4, messageId: 3, detectedNodeKey: 'diagnose', summary: '故障复现，根因待进一步确认', confidence: 0.75, createdAt: ts(5) }
]

const calibrations = [
  { id: 1, projectId: 1, userId: 1, username: 'admin', field: 'progress', oldValue: '0.8', newValue: '1.0', note: '客户电话确认已完成安装', createdAt: ts(6) }
]

const suggestions = []

const messages = {
  1: [
    { id: 101, projectId: 1, channelType: 'MOCK', channelId: 'mock_1', msgId: 'm1', senderId: 'ou_lisi', senderName: '李四', content: '【完成】远程安装 remote_install 已完成，设备重启正常。', msgType: 'TEXT', sentAt: ts(8), source: 'MOCK', createdAt: ts(8) },
    { id: 102, projectId: 1, channelType: 'MOCK', channelId: 'mock_1', msgId: 'm2', senderId: 'zhanggong_it', senderName: '张工', content: '好的，我们开始测试验收。', msgType: 'TEXT', sentAt: ts(7), source: 'MOCK', createdAt: ts(7) },
    { id: 103, projectId: 1, channelType: 'MOCK', channelId: 'mock_1', msgId: 'm3', senderId: 'system', senderName: '系统演示', content: '【风险】节点「测试验收」已持续 2 小时未推进，请注意跟进。', msgType: 'TEXT', sentAt: ts(3), source: 'MOCK', createdAt: ts(3) },
    { id: 104, projectId: 1, channelType: 'WECHAT_IMPORT', channelId: '上海某某科技-客户微信群记录.txt', msgId: 'wx1', senderId: null, senderName: '张工', content: '早上好，昨天说的策略开通情况我确认一下', msgType: 'TEXT', sentAt: ts(26), source: 'IMPORT', createdAt: ts(26) }
  ],
  2: [
    { id: 201, projectId: 2, channelType: 'MOCK', channelId: 'mock_2', msgId: 'm1', senderId: 'ou_chen', senderName: '陈工', content: '「开发实施」正在进行中，需要客户侧配合。', msgType: 'TEXT', sentAt: ts(4), source: 'MOCK', createdAt: ts(4) },
    { id: 202, projectId: 2, channelType: 'EMAIL', channelId: 'pm@demo.com', msgId: 'email1', senderId: '项目经理-周蕾', senderName: '项目经理-周蕾', content: '📧【邮件】主题：数据中台项目周报\n本周完成排期确认，进入开发阶段。', msgType: 'TEXT', sentAt: ts(10), source: 'SYNC', createdAt: ts(10) }
  ],
  3: [
    { id: 301, projectId: 3, channelType: 'MOCK', channelId: 'mock_3', msgId: 'm1', senderId: 'ou_zhaomin', senderName: '赵敏', content: '「客户验收」正在进行中，需要客户侧配合。', msgType: 'TEXT', sentAt: ts(5), source: 'MOCK', createdAt: ts(5) }
  ],
  4: [
    { id: 401, projectId: 4, channelType: 'MOCK', channelId: 'mock_4', msgId: 'm1', senderId: 'liugong', senderName: '刘工', content: '【完成】方案确认 solution_confirm 客户已同意更换设备方案。', msgType: 'TEXT', sentAt: ts(9), source: 'MOCK', createdAt: ts(9) },
    { id: 402, projectId: 4, channelType: 'MOCK', channelId: 'mock_4', msgId: 'm2', senderId: 'system', senderName: '系统演示', content: '【风险】备用设备到货延迟，解决方案无法实施', msgType: 'TEXT', sentAt: ts(6), source: 'MOCK', createdAt: ts(6) },
    { id: 403, projectId: 4, channelType: 'MOCK', channelId: 'mock_4', msgId: 'm3', senderId: 'system', senderName: '系统演示', content: '【风险】客户侧联系人两天未回复消息，沟通停滞', msgType: 'TEXT', sentAt: ts(6), source: 'MOCK', createdAt: ts(6) },
    { id: 404, projectId: 4, channelType: 'MOCK', channelId: 'mock_4', msgId: 'm4', senderId: 'system', senderName: '系统演示', content: '【风险】客户反馈故障又复现了，远程排查无果', msgType: 'TEXT', sentAt: ts(6), source: 'MOCK', createdAt: ts(6) }
  ]
}

const notifications = [
  { id: 1, projectId: 4, type: 'RISK_ALERT', title: '项目风险预警', content: '识别到以下风险：\n- 备用设备到货延迟，解决方案无法实施', targetsJson: '[]', status: 'SENT', errorMsg: null, createdAt: ts(5), executedAt: ts(5) },
  { id: 2, projectId: 1, type: 'NODE_COMPLETED', title: '节点完成，推进「测试验收」', content: '已完成节点：remote_install，请下一节点责任人启动工作。', targetsJson: '[]', status: 'SENT', errorMsg: null, createdAt: ts(7), executedAt: ts(7) },
  { id: 3, projectId: 3, type: 'DAILY_DIGEST', title: '每日进度摘要', content: '今日共 4 个进行中项目', targetsJson: '[]', status: 'SENT', errorMsg: null, createdAt: ts(15), executedAt: ts(15) }
]

const channelStatus = {
  feishu: { configured: false, appId: '', supported: true, note: '群消息自动同步 + 事件回调 + 一键深链沟通' },
  wecom: { configured: false, corpId: '', supported: true, note: '回调 + 群机器人推送；全量群消息需官方会话存档 SDK' },
  wechat: { configured: true, watchDir: './data/watch', watchEnabled: true, ocr: 'disabled', note: '个人微信无官方 API：文件夹监控自动导入 + 截图 OCR' },
  email: { configured: false, username: '', supported: true, note: 'IMAP 定时拉取项目相关邮件，作为 AI 分析数据源' },
  mock: { configured: true, note: '演示渠道：无凭证生成仿真群聊' }
}

const importRecords = [
  { id: 1, projectId: 1, fileName: '上海某某科技-客户微信群记录.txt', format: 'TXT', messageCount: 4, status: 'SUCCESS', source: 'WATCH', note: '格式: TXT(复制格式)', createdBy: null, createdAt: ts(26) }
]

const settings = {
  ai: { provider: 'anthropic', activeProvider: 'anthropic', parallelism: 8, anthropicModel: 'deepseek-v4-pro', openaiModel: 'gpt-4o-mini' },
  notify: { digestCron: '0 0 9 * * ?', syncCron: '0 */30 * * * ?', slaCheckCron: '0 5 * * * ?', feishuWebhookConfigured: false, wecomWebhookConfigured: false },
  wechat: { watchDir: './data/watch', watchEnabled: true, ocrProvider: 'disabled' },
  data: { retentionDays: 90 },
  version: '1.0.0'
}

const users = [
  { id: 1, username: 'admin', displayName: '企业管理员', role: 'ADMIN', feishuOpenId: '', wecomUserId: '', phone: '138****5678' },
  { id: 2, username: 'manager', displayName: '流程负责人', role: 'MANAGER', feishuOpenId: 'ou_demo', wecomUserId: '', phone: '' }
]

/* ---------- 内存状态与工具 ---------- */
let nextId = 1000
const delay = (ms = 150) => new Promise(r => setTimeout(r, ms))
const page = (items, params) => {
  const p = (params && params.page) || 1
  const s = (params && params.size) || 20
  return { items: items.slice((p - 1) * s, p * s), total: items.length, page: p, size: s }
}
const timelineFor = (projectId) => {
  const events = []
  for (const m of messages[projectId] || []) {
    events.push({ time: m.sentAt, type: 'message', text: `💬 ${m.senderName}: ${m.content.slice(0, 60)}` })
  }
  for (const a of analyses.filter(x => x.projectId === projectId)) {
    events.push({ time: a.createdAt, type: 'analysis', text: `🤖 AI 分析完成（${a.provider}，消费 ${a.messageCount} 条消息）` })
  }
  for (const c of calibrations.filter(x => x.projectId === projectId)) {
    events.push({ time: c.createdAt, type: 'calibration', text: `✏️ ${c.username} 修正 ${c.field}: ${c.oldValue} → ${c.newValue}` })
  }
  for (const n of notifications.filter(x => x.projectId === projectId)) {
    events.push({ time: n.createdAt, type: 'notification', text: `🔔 ${n.title}（${n.status}）` })
  }
  events.sort((a, b) => new Date(b.time) - new Date(a.time))
  return events
}

/* ---------- 路由表（与后端 API 同构） ---------- */
async function handle(method, url, data, params) {
  await delay()

  // 认证（演示模式接受任意非空用户名；页面上已提示演示账号）
  if (url === '/auth/login') {
    const name = (data && data.username) || 'admin'
    return {
      token: 'demo-token',
      user: { id: 1, username: name, displayName: name === 'admin' ? '企业管理员' : '流程负责人', role: name === 'admin' ? 'ADMIN' : 'MANAGER', feishuOpenId: '', wecomUserId: '', phone: '' }
    }
  }
  if (url === '/auth/me') return users[0]

  if (url === '/dashboard/overview') {
    const active = projects.filter(p => p.status === 'ACTIVE')
    return {
      activeCount: active.length,
      warningCount: active.filter(p => p.riskStatus === 'WARNING').length,
      blockedCount: active.filter(p => p.riskStatus === 'BLOCKED').length,
      archivedCount: projects.filter(p => p.status === 'ARCHIVED').length,
      avgProgress: Math.round(active.reduce((s, p) => s + p.progress, 0) / active.length * 1000) / 1000,
      todayAnalyzed: 4
    }
  }
  if (url === '/projects') {
    let list = [...projects]
    if (params) {
      if (params.keyword) {
        const k = params.keyword
        list = list.filter(p => [p.name, p.customerName, p.templateName].some(x => x && x.includes(k)))
      }
      if (params.status) list = list.filter(p => p.status === params.status)
      if (params.riskStatus) list = list.filter(p => p.riskStatus === params.riskStatus)
    }
    return page(list.sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt)), params)
  }

  const projectMatch = url.match(/^\/projects\/(\d+)$/)
  if (projectMatch) {
    const id = Number(projectMatch[1])
    const project = projects.find(p => p.id === id)
    return {
      project,
      channels: [
        { id: 900 + id, projectId: id, channelType: 'MOCK', channelId: 'mock_' + id, channelName: '演示群聊(自动生成)', syncEnabled: true, lastSyncAt: ts(2), lastSyncCursor: null, createdAt: ts(24) }
      ],
      stakeholders: stakeholders.filter(s => s.projectId === id),
      timeline: timelineFor(id),
      runs: analyses.filter(a => a.projectId === id)
    }
  }
  if (url.match(/^\/projects\/\d+\/analyze$/)) {
    return { id: ++nextId, projectId: 1, provider: 'mock', model: null, triggerType: 'MANUAL', status: 'SUCCESS', messageCount: 3, createdAt: new Date().toISOString(), finishedAt: new Date().toISOString() }
  }
  if (url.match(/^\/channels\/mock\/generate/)) {
    return { generated: 3 }
  }
  if (url.match(/^\/projects\/\d+\/messages$/)) {
    const id = Number(url.match(/^\/projects\/(\d+)\//)[1])
    return page([...(messages[id] || [])].sort((a, b) => new Date(b.sentAt) - new Date(a.sentAt)), params)
  }
  if (url.match(/^\/projects\/\d+\/insights$/)) return insights.filter(i => i.projectId === Number(url.match(/^\/projects\/(\d+)\//)[1]))
  if (url.match(/^\/projects\/\d+\/calibrations$/)) return calibrations.filter(c => c.projectId === Number(url.match(/^\/projects\/(\d+)\//)[1]))
  if (url.match(/^\/projects\/\d+\/suggestions$/)) return suggestions
  if (url.match(/^\/projects\/\d+\/correction$/)) {
    const id = Number(url.match(/^\/projects\/(\d+)\//)[1])
    const p = projects.find(x => x.id === id)
    if (data.field === 'progress') p.progress = Number(data.newValue)
    if (data.field === 'current_node') p.currentNodeKey = data.newValue
    if (data.field === 'risk_status') p.riskStatus = data.newValue
    p.manualLock = data.lock !== false
    calibrations.unshift({ id: ++nextId, projectId: id, userId: 1, username: 'admin', field: data.field, oldValue: '—', newValue: String(data.newValue), note: data.note || '', createdAt: new Date().toISOString() })
    return p
  }
  if (url.match(/^\/projects\/\d+\/unlock$/)) {
    const id = Number(url.match(/^\/projects\/(\d+)\//)[1])
    const p = projects.find(x => x.id === id)
    p.manualLock = false
    return p
  }
  if (url.match(/^\/projects\/\d+\/stakeholders$/)) {
    const id = Number(url.match(/^\/projects\/(\d+)\//)[1])
    stakeholders.push({ id: ++nextId, projectId: id, ...data, avatarUrl: null, updatedAt: new Date().toISOString() })
    return stakeholders[stakeholders.length - 1]
  }
  if (url === '/projects') {
    if (method === 'post') {
      const t = templates.find(x => x.id === data.templateId)
      const p = { id: ++nextId, code: 'P20260827005', name: data.name, templateId: data.templateId, templateName: t.name, customerName: data.customerName, status: 'ACTIVE', currentNodeKey: null, progress: 0, riskStatus: 'NORMAL', manualLock: false, startedAt: new Date().toISOString(), lastAnalyzedAt: null, latestActivity: null, lastActivityAt: new Date().toISOString(), updatedAt: new Date().toISOString(), ownerId: 1, createdBy: 'admin', createdAt: new Date().toISOString(), templateSnapshotJson: JSON.stringify({ name: t.name, nodes: JSON.parse(t.nodesJson), branches: [], glossary: [] }) }
      projects.push(p)
      return p
    }
  }
  const projectDeleteMatch = url.match(/^\/projects\/(\d+)$/)
  if (projectDeleteMatch && method === 'delete') {
    const id = Number(projectDeleteMatch[1])
    const idx = projects.findIndex(p => p.id === id)
    if (idx >= 0) projects.splice(idx, 1)
    return null
  }

  if (url === '/templates') {
    let list = [...templates]
    if (params && params.keyword) list = list.filter(t => t.name.includes(params.keyword))
    return page(list, params)
  }
  const templateMatch = url.match(/^\/templates\/(\d+)$/)
  if (templateMatch) return templates.find(t => t.id === Number(templateMatch[1]))
  if (url.match(/^\/templates\/\d+\/versions$/)) {
    return [{ id: ++nextId, templateId: 1, version: 1, snapshotJson: '{}', note: '演示版本', createdBy: 'system', createdAt: ts(72) }]
  }
  if (url === '/templates/parse-text') {
    const t = { id: ++nextId, name: (data.docName || '粘贴文本').replace(/\.[^.]+$/, ''), description: '由 AI 建模生成（演示）', version: 1, status: 'DRAFT', sourceDocName: data.docName || '粘贴文本.md', createdBy: 'admin', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), nodesJson: templates[4].nodesJson, branchesJson: '[]', glossaryJson: '[]' }
    templates.push(t)
    return t
  }
  if (url.match(/^\/templates\/\d+\/publish$/)) {
    const id = Number(url.match(/^\/templates\/(\d+)\//)[1])
    const t = templates.find(x => x.id === id)
    t.status = 'ACTIVE'
    t.version += 1
    return t
  }
  if (url.match(/^\/templates\/\d+\/duplicate$/)) {
    const id = Number(url.match(/^\/templates\/(\d+)\//)[1])
    const src = templates.find(x => x.id === id)
    const copy = { ...src, id: ++nextId, name: src.name + '-副本', status: 'DRAFT', version: 1 }
    templates.push(copy)
    return copy
  }
  if (url.match(/^\/templates\/\d+\/archive$/)) {
    const id = Number(url.match(/^\/templates\/(\d+)\//)[1])
    templates.find(x => x.id === id).status = 'ARCHIVED'
    return templates.find(x => x.id === id)
  }
  if (url.match(/^\/templates\/\d+$/)) {
    if (method === 'put') {
      const id = Number(url.match(/^\/templates\/(\d+)$/)[1])
      const t = templates.find(x => x.id === id)
      if (data.name) t.name = data.name
      if (data.description !== undefined) t.description = data.description
      if (data.nodesJson) t.nodesJson = data.nodesJson
      if (data.branchesJson) t.branchesJson = data.branchesJson
      if (data.glossaryJson) t.glossaryJson = data.glossaryJson
      t.version += 1
      return t
    }
  }

  if (url === '/channels/status') return channelStatus
  if (url === '/channels/feishu/tenants') {
    return [{ code: 'default', name: '默认组织', configured: true }]
  }
  if (url === '/channels/feishu/chats') {
    return [{ chat_id: 'oc_demo_1', name: '星辰订单中心升级项目' }, { chat_id: 'oc_demo_2', name: '公司内部交付群' }]
  }
  if (url === '/projects/casting/script') {
    return [
      { number: 1, title: '需求收集', description: '客户提需求，产品接单', lines: [{}] },
      { number: 2, title: '需求评审', description: '三方评审，结论基本通过但留了尾巴', lines: [{}] },
      { number: 3, title: '方案设计', description: '技术方案评审通过', lines: [{}] },
      { number: 4, title: '开发实现', description: '开发进展模糊，自我感觉良好', lines: [{}] },
      { number: 5, title: '回归测试', description: '测试结果含糊，疑似环境问题', lines: [{}] },
      { number: 6, title: '客户验收', description: '客户基本满意但提了小意见', lines: [{}] },
      { number: 7, title: '生产部署', description: '部署完成但出现波动，虚惊一场', lines: [{}] },
      { number: 8, title: '上线验证', description: '验证通过，流程关闭', lines: [{}] }
    ]
  }
  if (url.match(/^\/projects\/\d+\/cast$/)) {
    const id = Number(url.match(/^\/projects\/(\d+)\//)[1])
    const p = projects.find(x => x.id === id)
    if (p) {
      p.latestActivity = '【群演】第' + (data.scene || 1) + '幕正在上演…'
      p.updatedAt = new Date().toISOString()
    }
    return { casted: 3 }
  }
  if (url === '/channels/sync') return { syncedMessages: 2, syncedEmails: 0 }
  if (url === '/imports') return page(importRecords, params)
  if (url === '/imports/watch-status') {
    return { watchDir: './data/watch', hint: '把微信聊天记录 TXT/CSV 或截图扔进该目录即自动导入并触发 AI 分析；文件名包含项目名或客户名可自动归属项目。' }
  }
  if (url === '/imports/wechat') {
    importRecords.unshift({ id: ++nextId, projectId: 1, fileName: '演示-微信记录.txt', format: 'TXT', messageCount: 2, status: 'SUCCESS', source: 'API', note: '格式: TXT(复制格式)', createdBy: 'admin', createdAt: new Date().toISOString() })
    return importRecords[0]
  }
  if (url.match(/^\/projects\/\d+\/channels$/)) {
    return { id: ++nextId, projectId: 1, channelType: data.channelType, channelId: data.channelId, channelName: data.channelId, syncEnabled: true, createdAt: new Date().toISOString() }
  }
  if (url.match(/^\/projects\/\d+\/channels\/\d+$/)) return null


  console.warn('[演示模式] 未实现的接口:', method, url)
  return null
}

export default {
  get: (url, config) => handle('get', url, null, config && config.params),
  post: (url, data) => handle('post', url, data),
  put: (url, data) => handle('put', url, data),
  delete: (url) => handle('delete', url)
}
