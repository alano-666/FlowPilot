<template>
  <div>
    <div class="fp-header">
      <h1>通知预警</h1>
      <div class="gap8">
        <el-button type="primary" :loading="pushing" @click="pushDigest">📨 手动推送每日摘要</el-button>
      </div>
    </div>
    <div class="fp-card">
      <el-table :data="jobs" stripe>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag :type="{ SLA_OVERDUE: 'danger', NODE_COMPLETED: 'success', DAILY_DIGEST: 'primary', RISK_ALERT: 'warning' }[row.type]">
              {{ typeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
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
      </el-table>
      <el-pagination v-if="total > 20" style="margin-top:14px;justify-content:flex-end"
                     layout="prev, pager, next" :total="total" :page-size="20"
                     :current-page="page" @current-change="p => { page = p; load() }" />
    </div>
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
