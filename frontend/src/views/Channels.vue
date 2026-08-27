<template>
  <div>
    <div class="fp-header">
      <h1>数据源管理</h1>
      <div class="gap8">
        <el-button type="primary" :loading="syncing" @click="syncAll">🔄 立即同步全部渠道</el-button>
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
