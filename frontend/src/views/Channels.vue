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

    <!-- 项目渠道绑定 -->
    <div class="fp-card">
      <div class="section-title">
        <h2>项目渠道绑定</h2>
        <div class="gap8">
          <el-select v-model="bindProjectId" placeholder="选择项目" style="width:260px">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
          <el-select v-model="bindType" style="width:150px">
            <el-option label="飞书群" value="FEISHU" />
            <el-option label="企微群" value="WECOM" />
          </el-select>
          <el-input v-model="bindChannelId" placeholder="chat_id / 群ID" style="width:200px" />
          <el-button type="primary" @click="bindChannel">绑定</el-button>
        </div>
      </div>
      <div v-if="boundChannels.length" class="gap8" style="flex-wrap:wrap">
        <el-tag v-for="c in boundChannels" :key="c.id" closable size="large"
                :type="c.syncEnabled ? 'primary' : 'info'"
                @close="unbind(c)">
          {{ channelLabel(c.channelType) }}：{{ c.channelName }}
          <span class="muted small">{{ c.syncEnabled ? '' : '（已停用）' }}</span>
        </el-tag>
      </div>
      <div v-else class="small muted">尚未绑定任何渠道。飞书群 ID 可联系管理员在飞书开放平台查询，或在机器人被加入群后调用「获取群列表」。</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
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
const boundChannels = ref([])
const watchDir = ref('')
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

watch(bindProjectId, async id => {
  if (!id) { boundChannels.value = []; return }
  const d = await api.get(`/projects/${id}`)
  boundChannels.value = d.channels.filter(c => c.channelType !== 'WECHAT_IMPORT')
})

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
  if (!bindProjectId.value || !bindChannelId.value) { ElMessage.warning('请填写项目和渠道 ID'); return }
  await api.post(`/projects/${bindProjectId.value}/channels`, {
    channelType: bindType.value, channelId: bindChannelId.value
  })
  ElMessage.success('绑定成功')
  bindChannelId.value = ''
  load()
}

async function unbind(c) {
  await api.delete(`/projects/${c.projectId}/channels/${c.id}`)
  ElMessage.success('已解绑')
  load()
}

function channelLabel(k) {
  return { feishu: '🕊️ 飞书', wecom: '💼 企业微信', wechat: '💬 微信个人版', email: '📧 邮件', mock: '🎭 演示渠道' }[k] || k
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }

onMounted(load)
</script>
