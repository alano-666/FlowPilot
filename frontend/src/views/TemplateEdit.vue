<template>
  <div v-if="template">
    <div class="fp-header">
      <div class="gap8">
        <el-button text @click="$router.push('/templates')">← 返回</el-button>
        <h1 style="margin:0">{{ template.name }}</h1>
        <el-tag :type="{ DRAFT: 'info', ACTIVE: 'success', ARCHIVED: 'warning' }[template.status]">
          {{ { DRAFT: '草稿', ACTIVE: '已发布', ARCHIVED: '已停用' }[template.status] }}
        </el-tag>
        <span class="small muted">v{{ template.version }}</span>
      </div>
      <div class="gap8">
        <el-button :loading="saving" @click="save">保存</el-button>
        <el-button v-if="template.status !== 'ACTIVE'" type="primary" :loading="saving" @click="publish">
          保存并发布
        </el-button>
      </div>
    </div>

    <div class="fp-card">
      <el-form label-width="90px" style="max-width:640px">
        <el-form-item label="模板名称">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="流程说明">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 节点编辑 -->
    <div class="fp-card">
      <div class="section-title">
        <h2>流程节点（{{ form.nodes.length }}）</h2>
        <el-button size="small" type="primary" @click="addNode">＋ 添加节点</el-button>
      </div>
      <el-table :data="form.nodes" stripe>
        <el-table-column label="排序" width="70">
          <template #default="{ $index }">
            <span class="muted">{{ $index + 1 }}</span>
          </template>
        </el-table-column>
        <el-table-column label="节点名称" min-width="140">
          <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
        </el-table-column>
        <el-table-column label="Key" width="150">
          <template #default="{ row }"><el-input v-model="row.key" size="small" /></template>
        </el-table-column>
        <el-table-column label="完成标准" min-width="220">
          <template #default="{ row }"><el-input v-model="row.completion_criteria" size="small" /></template>
        </el-table-column>
        <el-table-column label="责任角色" min-width="170">
          <template #default="{ row }">
            <el-input v-model="row.rolesText" size="small" placeholder="逗号分隔" />
          </template>
        </el-table-column>
        <el-table-column label="SLA(h)" width="90">
          <template #default="{ row }"><el-input-number v-model="row.sla_hours" size="small" :min="0" /></template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" @click="move($index, -1)" :disabled="$index === 0">↑</el-button>
            <el-button size="small" @click="move($index, 1)" :disabled="$index === form.nodes.length - 1">↓</el-button>
            <el-button size="small" type="danger" @click="form.nodes.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分支编辑 -->
    <div class="fp-card">
      <div class="section-title">
        <h2>分支规则（{{ form.branches.length }}）</h2>
        <el-button size="small" type="primary" @click="form.branches.push({ condition: '', from: '', to: '' })">＋ 添加分支</el-button>
      </div>
      <el-table :data="form.branches" stripe>
        <el-table-column label="条件" min-width="220">
          <template #default="{ row }"><el-input v-model="row.condition" size="small" placeholder="如：如果客户已购买远程授权" /></template>
        </el-table-column>
        <el-table-column label="从节点" width="190">
          <template #default="{ row }">
            <el-select v-model="row.from" size="small" style="width:100%">
              <el-option v-for="n in form.nodes" :key="n.key" :label="n.name" :value="n.key" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="到节点" width="190">
          <template #default="{ row }">
            <el-select v-model="row.to" size="small" style="width:100%">
              <el-option v-for="n in form.nodes" :key="n.key" :label="n.name" :value="n.key" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" type="danger" @click="form.branches.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 词库 -->
    <div class="fp-card">
      <div class="section-title">
        <h2>专业词库（{{ form.glossary.length }}）</h2>
        <el-button size="small" type="primary" @click="form.glossary.push({ term: '', synonymsText: '', explanation: '' })">＋ 添加词汇</el-button>
      </div>
      <el-table :data="form.glossary" stripe>
        <el-table-column label="词汇" width="150">
          <template #default="{ row }"><el-input v-model="row.term" size="small" /></template>
        </el-table-column>
        <el-table-column label="同义词（逗号分隔）" min-width="200">
          <template #default="{ row }"><el-input v-model="row.synonymsText" size="small" /></template>
        </el-table-column>
        <el-table-column label="解释" min-width="200">
          <template #default="{ row }"><el-input v-model="row.explanation" size="small" /></template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button size="small" type="danger" @click="form.glossary.splice($index, 1)">删</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 版本历史 -->
    <div class="fp-card">
      <div class="section-title"><h2>版本历史</h2></div>
      <el-timeline>
        <el-timeline-item v-for="v in versions" :key="v.id" :timestamp="fmt(v.createdAt)">
          v{{ v.version }} · {{ v.note || '编辑' }} · {{ v.createdBy }}
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const route = useRoute()
const router = useRouter()
const id = route.params.id

const template = ref(null)
const versions = ref([])
const saving = ref(false)
const form = reactive({ name: '', description: '', nodes: [], branches: [], glossary: [] })

async function load() {
  const t = await api.get(`/templates/${id}`)
  template.value = t
  form.name = t.name
  form.description = t.description || ''
  form.nodes = JSON.parse(t.nodesJson || '[]').map(n => ({ ...n, rolesText: (n.responsible_roles || []).join(',') }))
  form.branches = JSON.parse(t.branchesJson || '[]')
  form.glossary = JSON.parse(t.glossaryJson || '[]').map(g => ({ ...g, synonymsText: (g.synonyms || []).join(',') }))
  versions.value = await api.get(`/templates/${id}/versions`)
}

function addNode() {
  form.nodes.push({
    key: `node_${form.nodes.length + 1}`, name: `新节点${form.nodes.length + 1}`,
    type: 'normal', completion_criteria: '', responsible_roles: [], sla_hours: null, rolesText: ''
  })
}

function move(index, dir) {
  const arr = form.nodes
  const [item] = arr.splice(index, 1)
  arr.splice(index + dir, 0, item)
}

function buildPayload() {
  return {
    name: form.name,
    description: form.description,
    nodesJson: JSON.stringify(form.nodes.map(({ rolesText, ...n }) => ({
      ...n,
      responsible_roles: (rolesText || '').split(/[,，]/).map(s => s.trim()).filter(Boolean)
    }))),
    branchesJson: JSON.stringify(form.branches),
    glossaryJson: JSON.stringify(form.glossary.map(({ synonymsText, ...g }) => ({
      ...g,
      synonyms: (synonymsText || '').split(/[,，]/).map(s => s.trim()).filter(Boolean)
    }))),
    note: '编辑器修改'
  }
}

async function save() {
  saving.value = true
  try {
    await api.put(`/templates/${id}`, buildPayload())
    ElMessage.success('已保存，版本 +1')
    load()
  } finally { saving.value = false }
}

async function publish() {
  saving.value = true
  try {
    await api.put(`/templates/${id}`, buildPayload())
    await api.post(`/templates/${id}/publish`)
    ElMessage.success('已发布，可用于创建项目')
    load()
  } finally { saving.value = false }
}

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }

onMounted(load)
</script>
