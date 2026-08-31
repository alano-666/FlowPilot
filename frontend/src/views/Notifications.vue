<template>
  <div>
    <div class="fp-header">
      <h1>通知预警</h1>
      <div class="gap8">
        <el-button type="primary" :loading="pushing" @click="pushDigest">📨 手动推送每日摘要</el-button>
      </div>
    </div>
    <div class="fp-card">
      <el-table :data="jobs" stripe @row-click="showDetail" style="cursor:pointer">
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="{ SLA_OVERDUE: 'danger', NODE_COMPLETED: 'success', DAILY_DIGEST: 'primary', RISK_ALERT: 'warning' }[row.type]">
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="content" label="内容摘要" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ SENT: 'success', PENDING: 'info', FAILED: 'danger', SKIPPED: 'warning' }[row.status]">
              {{ { SENT: '已推送', PENDING: '待推送', FAILED: '失败', SKIPPED: '跳过' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="" width="80">
          <template #default>
            <el-button size="small" text type="primary">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="small muted" style="margin-top:8px">点击任意一行查看完整详情（通知内容、接收人、推送结果）</div>
      <el-pagination v-if="total > 20" style="margin-top:14px;justify-content:flex-end"
                     layout="prev, pager, next" :total="total" :page-size="20"
                     :current-page="page" @current-change="p => { page = p; load() }" />
    </div>

    <!-- 通知详情 -->
    <el-dialog v-model="detailVisible" :title="`🔔 ${detail?.title || '通知详情'}`" width="560px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="类型">{{ typeLabel(detail.type) }}</el-descriptions-item>
          <el-descriptions-item label="关联项目 ID">{{ detail.projectId }}</el-descriptions-item>
          <el-descriptions-item label="推送状态">
            <el-tag :type="{ SENT: 'success', PENDING: 'info', FAILED: 'danger', SKIPPED: 'warning' }[detail.status]">
              {{ { SENT: '已推送', PENDING: '待推送', FAILED: '失败', SKIPPED: '跳过' }[detail.status] }}
            </el-tag>
            <span v-if="detail.errorMsg" class="small" style="color:var(--fp-critical);margin-left:8px">{{ detail.errorMsg }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmt(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="执行时间">{{ fmt(detail.executedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="section-title" style="margin-top:16px"><h2>通知内容</h2></div>
        <pre class="small" style="white-space:pre-wrap;background:var(--fp-page);padding:12px;border-radius:8px;margin:0">{{ detail.content }}</pre>
        <div class="section-title" style="margin-top:16px"><h2>接收干系人</h2></div>
        <div v-if="detailTargets.length" class="gap8" style="flex-wrap:wrap">
          <el-tag v-for="(t, i) in detailTargets" :key="i" size="small">{{ t.name }}（{{ t.role }} · {{ t.contact_type }}）</el-tag>
        </div>
        <div v-else class="small muted">（无指定个人：以群推送/汇总形式发送）</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const jobs = ref([])
const total = ref(0)
const page = ref(1)
const pushing = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const detailTargets = ref([])

function showDetail(row) {
  detail.value = row
  try {
    detailTargets.value = JSON.parse(row.targetsJson || '[]')
  } catch { detailTargets.value = [] }
  detailVisible.value = true
}

async function load() {
  const d = await api.get('/notifications', { params: { page: page.value, size: 20 } })
  jobs.value = d.items
  total.value = d.total
}

async function pushDigest() {
  pushing.value = true
  try {
    const r = await api.post('/notifications/digest')
    ElMessage.success(`已推送，覆盖 ${r.pushedProjects} 个项目`)
    load()
  } finally { pushing.value = false }
}

function typeLabel(t) {
  return { SLA_OVERDUE: 'SLA超时', NODE_COMPLETED: '节点完成', DAILY_DIGEST: '每日摘要', RISK_ALERT: '风险预警' }[t] || t
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }

onMounted(load)
</script>
