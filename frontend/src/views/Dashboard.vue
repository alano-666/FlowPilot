<template>
  <div>
    <div class="fp-header">
      <h1>项目看板</h1>
      <div class="gap8">
        <el-button type="primary" @click="openCreate">＋ 新建项目</el-button>
        <el-button :loading="syncing" @click="refresh">刷新</el-button>
      </div>
    </div>

    <!-- 渠道接入状态一览 -->
    <div class="fp-card" style="padding:12px 16px">
      <div class="gap8" style="flex-wrap:wrap">
        <span class="small muted">渠道接入：</span>
        <el-tag v-for="(v, k) in channelStatus" :key="k" size="small"
                :type="v.configured ? 'success' : 'info'" effect="plain">
          {{ channelIcon(k) }} {{ channelName(k) }}{{ v.configured ? ' 已接入' : ' 未接入' }}
        </el-tag>
        <el-button size="small" text type="primary" @click="$router.push('/channels')">管理渠道 →</el-button>
      </div>
    </div>

    <!-- 概览统计（英雄数字） -->
    <div class="stat-grid">
      <div class="stat-tile">
        <div class="label">🏃 进行中项目</div>
        <div class="value">{{ overview.activeCount ?? '—' }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">▲ 预警项目</div>
        <div class="value" style="color:var(--fp-warning)">{{ overview.warningCount ?? '—' }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">⛔ 卡顿项目</div>
        <div class="value" style="color:var(--fp-critical)">{{ overview.blockedCount ?? '—' }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">📦 已归档</div>
        <div class="value">{{ overview.archivedCount ?? '—' }}</div>
      </div>
      <div class="stat-tile">
        <div class="label">📈 平均进度</div>
        <div class="value">{{ Math.round((overview.avgProgress || 0) * 100) }}<small>%</small></div>
      </div>
      <div class="stat-tile">
        <div class="label">🤖 今日已分析</div>
        <div class="value">{{ overview.todayAnalyzed ?? '—' }}</div>
      </div>
    </div>

    <!-- 筛选行 -->
    <div class="fp-card" style="padding:14px 16px">
      <div class="gap8" style="flex-wrap:wrap">
        <el-input v-model="filters.keyword" placeholder="搜索项目/客户/流程" clearable
                  style="width:240px" @keyup.enter="load" @clear="load" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width:130px" @change="load">
          <el-option label="进行中" value="ACTIVE" />
          <el-option label="已暂停" value="PAUSED" />
          <el-option label="已归档" value="ARCHIVED" />
        </el-select>
        <el-select v-model="filters.riskStatus" placeholder="全部风险" clearable style="width:130px" @change="load">
          <el-option label="正常" value="NORMAL" />
          <el-option label="预警" value="WARNING" />
          <el-option label="卡顿" value="BLOCKED" />
        </el-select>
        <el-button @click="load">筛选</el-button>
        <span class="muted small">共 {{ total }} 个项目</span>
      </div>
    </div>

    <!-- 项目卡片网格（悬停右上角显示 🗑 删除键） -->
    <div v-if="projects.length" class="project-grid">
      <ProjectCard v-for="p in projects" :key="p.id" :project="p" @delete="removeProject" />
    </div>
    <el-empty v-else description="暂无项目，点击右上角新建" />

    <el-pagination v-if="total > pageSize" style="margin-top:18px;justify-content:flex-end"
                   layout="prev, pager, next" :total="total" :page-size="pageSize"
                   :current-page="page" @current-change="p => { page = p; load() }" />

    <!-- 新建项目对话框 -->
    <el-dialog v-model="createVisible" title="新建项目（AI 事件）" width="560px">
      <el-form label-width="100px">
        <el-form-item label="项目名称" required>
          <el-input v-model="createForm.name" placeholder="如：上海某某科技远程安装" />
        </el-form-item>
        <el-form-item label="流程模板" required>
          <el-select v-model="createForm.templateId" placeholder="选择已发布的流程模板" style="width:100%">
            <el-option v-for="t in activeTemplates" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="createForm.customerName" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import ProjectCard from '../components/ProjectCard.vue'

const overview = ref({})
const projects = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 12
const syncing = ref(false)
const filters = reactive({ keyword: '', status: '', riskStatus: '' })
const channelStatus = ref({})

function channelIcon(k) {
  return { feishu: '🕊️', wechat: '💬', email: '📧', mock: '🎭' }[k] || '🔌'
}
function channelName(k) {
  return { feishu: '飞书', wechat: '微信导入', email: '邮件', mock: '演示' }[k] || k
}

const createVisible = ref(false)
const creating = ref(false)
const activeTemplates = ref([])
const createForm = reactive({ name: '', templateId: null, customerName: '' })

async function load() {
  const params = { page: page.value, size: pageSize }
  if (filters.keyword) params.keyword = filters.keyword
  if (filters.status) params.status = filters.status
  if (filters.riskStatus) params.riskStatus = filters.riskStatus
  const d = await api.get('/projects', { params })
  projects.value = d.items
  total.value = d.total
  overview.value = await api.get('/dashboard/overview')
  channelStatus.value = await api.get('/channels/status')
}

async function refresh() {
  syncing.value = true
  try {
    const r = await api.post('/channels/sync')
    ElMessage.success(`同步完成，新增 ${r.syncedMessages} 条消息`)
  } catch (e) { /* 提示已由拦截器处理 */ }
  await load()
  syncing.value = false
}

/** 卡片删除键：确认后级联删除（不可恢复） */
async function removeProject(project) {
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${project.name}」吗？将级联删除其全部渠道、消息、分析记录与校准日志，不可恢复。\n建议仅用于清理测试数据，正式项目请用「归档」。`,
      '删除项目', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
  } catch { return }
  await api.delete(`/projects/${project.id}`)
  ElMessage.success('项目已删除')
  load()
}

async function openCreate() {
  const t = await api.get('/templates', { params: { size: 100 } })
  activeTemplates.value = t.items.filter(x => x.status === 'ACTIVE')
  createVisible.value = true
}

async function doCreate() {
  if (!createForm.name || !createForm.templateId) {
    ElMessage.warning('请填写项目名称并选择流程模板')
    return
  }
  creating.value = true
  try {
    await api.post('/projects', { ...createForm })
    ElMessage.success('项目创建成功')
    createVisible.value = false
    Object.assign(createForm, { name: '', templateId: null, customerName: '' })
    load()
  } finally {
    creating.value = false
  }
}

onMounted(load)
</script>
