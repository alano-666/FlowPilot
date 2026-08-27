<template>
  <div>
    <div class="fp-header">
      <h1>报告中心</h1>
      <div class="gap8">
        <el-select v-model="period" style="width:120px">
          <el-option label="周报" value="周报" />
          <el-option label="月报" value="月报" />
        </el-select>
        <el-button type="primary" :loading="generating" @click="generate">生成报告</el-button>
        <el-button :loading="generating" @click="pushDigest">推送每日摘要</el-button>
      </div>
    </div>

    <!-- 核心统计 -->
    <div v-if="summary" class="stat-grid">
      <div class="stat-tile">
        <div class="label">📦 项目总数</div>
        <div class="value">{{ summary.totalProjects }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">🏃 进行中</div>
        <div class="value">{{ summary.activeProjects }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">▲ 风险项目</div>
        <div class="value" style="color:var(--fp-warning)">{{ summary.riskProjectCount }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">⏱️ 项目超时率</div>
        <div class="value">{{ summary.riskRate }}<small>%</small></div>
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:18px" v-if="summary">
      <div class="fp-card">
        <div class="section-title"><h2>高频卡点节点</h2></div>
        <el-table :data="summary.bottleneckNodes" stripe size="small">
          <el-table-column prop="node" label="节点" />
          <el-table-column prop="stuckCount" label="滞留项目数" width="110">
            <template #default="{ row }">
              <span style="color:var(--fp-critical);font-weight:700">{{ row.stuckCount }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <div class="fp-card">
        <div class="section-title"><h2>各流程平均执行耗时</h2></div>
        <el-table :data="summary.flowStats" stripe size="small">
          <el-table-column prop="flowName" label="流程" />
          <el-table-column prop="projectCount" label="项目数" width="90" />
          <el-table-column label="平均耗时(小时)" width="130">
            <template #default="{ row }"><span class="mono">{{ row.avgHours }}</span></template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 已生成报告 -->
    <div class="fp-card">
      <div class="section-title"><h2>已生成报告</h2></div>
      <el-table :data="files" stripe>
        <el-table-column prop="name" label="文件名" min-width="280" />
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ (row.size / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column label="生成时间" width="180">
          <template #default="{ row }">{{ fmt(row.modifiedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="download(row.name)">下载</el-button>
            <el-button size="small" @click="openInline(row.name)">预览</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const period = ref('周报')
const generating = ref(false)
const summary = ref(null)
const files = ref([])

async function load() {
  files.value = await api.get('/reports')
}

async function generate() {
  generating.value = true
  try {
    const r = await api.post('/reports/generate', { period: period.value })
    summary.value = r.summary
    ElMessage.success('报告生成完成（Excel / PDF / HTML）')
    load()
  } finally { generating.value = false }
}

async function pushDigest() {
  generating.value = true
  try {
    const r = await api.post('/notifications/digest')
    ElMessage.success(`每日摘要已推送，覆盖 ${r.pushedProjects} 个项目`)
  } finally { generating.value = false }
}

function download(name) {
  window.open(`/api/v1/reports/download/${name}`, '_blank')
}

function openInline(name) {
  window.open(`/api/v1/reports/download/${name}`, '_blank')
}

function fmt(t) {
  return t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(load)
</script>
