<template>
  <div>
    <div class="fp-header">
      <h1>数据源管理</h1>
      <div class="gap8">
        <el-button type="primary" :loading="syncing" @click="syncAll">🔄 立即同步全部渠道</el-button>
      </div>
    </div>

    <!-- 飞书接入向导（评审开箱即用路径） -->
    <div class="fp-card">
      <div class="section-title">
        <h2>🚀 飞书接入向导（三步打通真实闭环）</h2>
        <el-button type="primary" size="small" :loading="testing" @click="testFeishu">
          {{ feishuOk === null ? '🔍 检测飞书凭证' : (feishuOk ? '✅ 凭证有效，重新检测' : '❌ 检测失败，重新检测') }}
        </el-button>
      </div>
      <el-alert v-if="feishuMsg" :type="feishuOk ? 'success' : 'warning'" :closable="true" style="margin-bottom:12px"
                @close="feishuMsg = ''">
        <template #title>{{ feishuMsg }}</template>
      </el-alert>
      <el-steps :active="feishuOk ? 3 : 0" simple style="margin-bottom:14px">
        <el-step title="创建飞书应用" />
        <el-step title="填凭证并重启" />
        <el-step title="自检通过" />
      </el-steps>
      <div class="small" style="line-height:2">
        <b>第 1 步</b>：打开 <a href="https://open.feishu.cn/app" target="_blank" style="color:var(--fp-blue-450)">飞书开发者后台</a>
        → 创建企业自建应用 → 开通权限（im:message、im:chat、contact:user.base:readonly）→ 事件订阅
        <code>im.message.receive_v1</code> → 发布版本（管理员审核）。详细图文步骤见
        <a href="https://github.com/alano-666/FlowPilot/blob/main/docs/07-%E9%A3%9E%E4%B9%A6%E4%BC%81%E5%BE%AE%E6%8E%A5%E5%85%A5%E6%8C%87%E5%8D%97.md" target="_blank" style="color:var(--fp-blue-450)">docs/07</a><br>
        <b>第 2 步</b>：在 <code>backend/.env.local</code> 填入
        <code>FLOWPILOT_FEISHU_APPID / FLOWPILOT_FEISHU_APPSECRET</code>（已配置 AI 网关密钥的同一文件），重启服务<br>
        <b>第 3 步</b>：点右上角「检测飞书凭证」→ 通过后把机器人加入项目群，在下方绑定群 chat_id 即可实时同步
      </div>
      <div v-if="feishuOk && feishuChats && feishuChats.length" class="small" style="margin-top:10px">
        🤖 机器人所在群：<el-tag v-for="c in feishuChats" :key="c.chat_id" size="small" style="margin-right:6px">{{ c.name }}（{{ c.chat_id }}）</el-tag>
      </div>
    </div>

    <!-- 渠道接入状态 -->
    <div class="stat-grid">
      <div class="stat-tile" v-for="(v, k) in channelStatus" :key="k">
        <div class="label">{{ channelLabel(k) }}</div>
        <div class="value" style="font-size:22px">{{ v.configured ? '✅ 已配置' : '⚪ 未配置' }}</div>
        <div class="small muted" style="margin-top:6px">{{ v.note }}</div>
      </div>
    </div>

    <!-- 微信导入（重点：自动导入文件夹） -->
    <div class="fp-card">
      <div class="section-title"><h2>📥 微信聊天记录导入（半自动 → 全自动流水线）</h2></div>
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        <template #title>
          个人微信没有官方数据接口。推荐方式：在微信电脑版复制聊天记录保存为 TXT/CSV
          （或截图），然后<b>直接扔进监控文件夹 <code>{{ watchDir }}</code></b>，
          系统会自动解析 → 归属项目 → 触发 AI 分析，无需任何手工操作。
          文件名包含项目名/客户名可自动匹配项目。详见 docs/06。
        </template>
      </el-alert>
      <div class="gap8" style="flex-wrap:wrap;margin-bottom:14px">
        <el-select v-model="importProjectId" placeholder="选择目标项目" style="width:280px">
          <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.customerName || '未填客户'}）`" :value="p.id" />
        </el-select>
        <el-upload :show-file-list="false" :auto-upload="false" accept=".txt,.csv,.png,.jpg,.jpeg"
                   :on-change="onFileChosen">
          <el-button type="primary">📄 上传记录文件/截图</el-button>
        </el-upload>
      </div>
      <el-table :data="importRecords" stripe>
        <el-table-column prop="fileName" label="文件名" min-width="220" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ { TXT: '文本', CSV: 'CSV', IMAGE: '截图' }[row.format] || row.format }}</template>
        </el-table-column>
        <el-table-column prop="messageCount" label="消息数" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ SUCCESS: 'success', PARTIAL: 'warning', FAILED: 'danger' }[row.status]">
              {{ { SUCCESS: '成功', PARTIAL: '部分', FAILED: '失败' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="240" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="导入时间" width="150">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 项目渠道矩阵（一个项目可绑多群、多类型渠道） -->
    <div class="fp-card">
      <div class="section-title">
        <h2>🔗 项目渠道矩阵（一项目多渠道：多飞书群 + 企微群 + 微信导入 + 邮件）</h2>
        <div class="gap8">
          <el-select v-model="bindProjectId" placeholder="选择项目查看/添加渠道" style="width:300px" @change="onProjectChange">
            <el-option v-for="p in projects" :key="p.id" :label="`${p.name}（${p.customerName || '未填客户'}）`" :value="p.id" />
          </el-select>
        </div>
      </div>

      <template v-if="bindProjectId">
        <!-- 已绑渠道（按类型分组） -->
        <div class="small muted" style="margin-bottom:8px">已绑定渠道（点击 ✕ 解绑；飞书群可绑定多个，全部会同步并进入 AI 分析）：</div>
        <div class="gap8" style="flex-wrap:wrap;margin-bottom:14px">
          <template v-for="c in boundChannels" :key="c.id">
            <el-tag size="large" closable
                    :type="c.channelType === 'FEISHU' ? 'primary' : 'info'"
                    @close="unbind(c)">
              {{ channelTypeLabel(c.channelType) }}：{{ c.channelName || c.channelId }}
              <span v-if="c.channelType === 'FEISHU' && c.tenantCode && c.tenantCode !== 'default'" class="muted small">（组织:{{ c.tenantCode }}）</span>
              <span v-if="!c.syncEnabled" class="muted small">（已停用）</span>
              <span v-if="c.channelType === 'FEISHU' && c.lastSyncAt" class="muted small">· {{ fmt(c.lastSyncAt) }} 同步过</span>
            </el-tag>
          </template>
          <span v-if="!boundChannels.length" class="muted small">尚未绑定渠道</span>
        </div>

        <!-- 添加渠道 -->
        <div class="gap8" style="flex-wrap:wrap">
          <el-select v-model="bindType" style="width:140px">
            <el-option label="🕊️ 飞书群" value="FEISHU" />
          </el-select>
          <template v-if="bindType === 'FEISHU'">
            <!-- 飞书组织（多租户）：先选组织，再选该组织机器人所在的群 -->
            <el-select v-model="bindTenantCode" placeholder="选择飞书组织" style="width:180px" @change="loadFeishuChats">
              <el-option v-for="t in tenantOptions" :key="t.code" :label="`${t.name}（${t.code}）`" :value="t.code" />
            </el-select>
            <el-select v-model="bindChannelId" placeholder="选择机器人所在的飞书群" style="width:300px" filterable
                       @visible-change="visible => { if (visible) loadFeishuChats() }">
              <el-option v-for="chat in feishuChatOptions" :key="chat.chat_id"
                         :label="`${chat.name}（${chat.chat_id}）`" :value="chat.chat_id" />
            </el-select>
            <el-input v-model="bindChannelId" placeholder="或手动粘贴 chat_id" style="width:200px" clearable />
          </template>
          <el-button type="primary" :loading="binding" @click="bindChannel">＋ 绑定渠道</el-button>
          <el-button size="small" text type="info" @click="loadFeishuChats" v-if="bindType === 'FEISHU'">刷新群列表</el-button>
        </div>
        <div class="small muted" style="margin-top:10px;line-height:1.8">
          💡 多渠道说明：飞书群可绑 <b>多个</b>（每个群的消息都会同步并参与 AI 分析）；
          微信导入文件与邮箱邮件<b>按项目名/客户名自动匹配归属</b>，无需手动绑定。
        </div>
      </template>
      <el-empty v-else description="先选择一个项目查看其渠道" :image-size="60" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const channelStatus = ref({})
const projects = ref([])
const importRecords = ref([])
const importProjectId = ref(null)
const syncing = ref(false)
const bindProjectId = ref(null)
const bindType = ref('FEISHU')
const bindChannelId = ref('')
const bindTenantCode = ref('default')
const boundChannels = ref([])
const feishuChatOptions = ref([])
const tenantOptions = ref([{ code: 'default', name: '默认组织' }])
const binding = ref(false)
const watchDir = ref('')

async function loadTenants() {
  try {
    const list = await api.get('/channels/feishu/tenants')
    if (list && list.length) tenantOptions.value = list
  } catch (e) { /* 未配置时保持默认 */ }
}

async function onProjectChange() {
  bindChannelId.value = ''
  if (!bindProjectId.value) { boundChannels.value = []; return }
  const d = await api.get(`/projects/${bindProjectId.value}`)
  boundChannels.value = d.channels.filter(c => c.channelType !== 'WECHAT_IMPORT')
}

/** 拉取指定组织的机器人所在飞书群（绑定渠道下拉直选） */
async function loadFeishuChats() {
  try {
    feishuChatOptions.value = await api.get('/channels/feishu/chats',
      { params: { tenantCode: bindTenantCode.value } })
  } catch (e) {
    // 未配置飞书时提示由拦截器处理
  }
}
const testing = ref(false)
const feishuOk = ref(null)
const feishuMsg = ref('')
const feishuChats = ref([])

async function testFeishu() {
  testing.value = true
  try {
    const r = await api.get('/channels/feishu/test')
    feishuOk.value = r.ok
    feishuMsg.value = r.message
    feishuChats.value = r.chats || []
  } catch (e) {
    feishuOk.value = false
    feishuMsg.value = '检测请求失败：' + (e.message || '网络错误')
  } finally {
    testing.value = false
  }
}

async function load() {
  const [status, ps, recs, watch] = await Promise.all([
    api.get('/channels/status'),
    api.get('/projects', { params: { size: 500 } }),
    api.get('/imports', { params: { size: 50 } }),
    api.get('/imports/watch-status')
  ])
  channelStatus.value = status
  projects.value = ps.items
  importRecords.value = recs.items
  watchDir.value = watch.watchDir
}

// 项目切换时刷新渠道列表（见 onProjectChange，由模板 @change 触发）

async function syncAll() {
  syncing.value = true
  try {
    const r = await api.post('/channels/sync')
    ElMessage.success(`同步完成，新增 ${r.syncedMessages} 条消息`)
  } finally { syncing.value = false }
}

async function onFileChosen(f) {
  if (!importProjectId.value) { ElMessage.warning('请先选择目标项目'); return }
  const form = new FormData()
  form.append('file', f.raw)
  const r = await api.post(`/imports/wechat?projectId=${importProjectId.value}`, form,
    { headers: { 'Content-Type': 'multipart/form-data' } })
  ElMessage.success(`导入完成：${r.messageCount} 条消息（${r.note}）`)
  load()
}

async function bindChannel() {
  if (!bindProjectId.value || !bindChannelId.value) { ElMessage.warning('请选择项目和渠道'); return }
  binding.value = true
  try {
    // 从群列表取群名作为渠道名（飞书）
    let name = bindChannelId.value
    if (bindType.value === 'FEISHU') {
      const hit = feishuChatOptions.value.find(c => c.chat_id === bindChannelId.value)
      if (hit) name = hit.name
    }
    await api.post(`/projects/${bindProjectId.value}/channels`, {
      channelType: bindType.value, channelId: bindChannelId.value, channelName: name,
      tenantCode: bindType.value === 'FEISHU' ? bindTenantCode.value : null
    })
    ElMessage.success('绑定成功，该渠道消息将进入 AI 分析')
    bindChannelId.value = ''
    onProjectChange()
  } catch (e) { /* 拦截器已提示 */ } finally { binding.value = false }
}

async function unbind(c) {
  await api.delete(`/projects/${c.projectId}/channels/${c.id}`)
  ElMessage.success('已解绑')
  onProjectChange()
}

function channelTypeLabel(t) {
  return { FEISHU: '🕊️ 飞书群', WECHAT_IMPORT: '💬 微信导入', MOCK: '🎭 演示' }[t] || t
}
function channelLabel(k) {
  return { feishu: '🕊️ 飞书', wechat: '💬 微信个人版', email: '📧 邮件', mock: '🎭 演示渠道' }[k] || k
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }

onMounted(() => { load(); loadTenants() })
</script>
