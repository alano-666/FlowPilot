<template>
  <div>
    <div class="fp-header">
      <h1>流程模板（知识库）</h1>
      <div class="gap8">
        <el-button type="primary" @click="parseVisible = true">📄 上传文档 AI 建模</el-button>
        <el-button @click="pasteVisible = true">📋 粘贴文本建模</el-button>
      </div>
    </div>

    <div class="fp-card">
      <div class="gap8" style="margin-bottom:14px">
        <el-input v-model="keyword" placeholder="搜索模板名" clearable style="width:240px"
                  @keyup.enter="load" @clear="load" />
        <el-button @click="load">搜索</el-button>
      </div>
      <el-table :data="templates" stripe>
        <el-table-column prop="name" label="模板名称" min-width="180">
          <template #default="{ row }">
            <router-link :to="`/templates/${row.id}/edit`" style="color:var(--fp-blue-450);font-weight:600">
              {{ row.name }}
            </router-link>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ DRAFT: 'info', ACTIVE: 'success', ARCHIVED: 'warning' }[row.status]">
              {{ { DRAFT: '草稿', ACTIVE: '已发布', ARCHIVED: '已停用' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceDocName" label="来源文档" min-width="160" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="150">
          <template #default="{ row }">{{ fmt(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="$router.push(`/templates/${row.id}/edit`)">编辑</el-button>
            <el-button v-if="row.status !== 'ACTIVE'" size="small" type="success" @click="publish(row)">发布</el-button>
            <el-button v-if="row.status === 'ACTIVE'" size="small" type="warning" @click="archive(row)">停用</el-button>
            <el-button size="small" @click="duplicate(row)">复制</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-if="total > 20" style="margin-top:14px;justify-content:flex-end"
                     layout="prev, pager, next" :total="total" :page-size="20"
                     :current-page="page" @current-change="p => { page = p; load() }" />
    </div>

    <!-- 上传解析 -->
    <el-dialog v-model="parseVisible" title="上传流程文档，AI 自动建模" width="520px">
      <p class="small muted">支持 Word(.docx) / PDF / Markdown / TXT / 飞书文档链接。
        AI 将自动抽取流程节点、分支条件、责任角色、完成标准与专业词库，生成草稿后请人工确认。</p>
      <el-upload drag :auto-upload="false" :limit="1" accept=".txt,.md,.docx,.pdf"
                 :on-change="f => file = f.raw" :on-remove="() => file = null">
        <div style="padding:20px">
          <div style="font-size:32px">📄</div>
          <div class="small">将文件拖到此处，或点击选择</div>
        </div>
      </el-upload>
      <el-input v-model="feishuUrl" placeholder="或粘贴飞书文档链接" style="margin-top:10px" />
      <template #footer>
        <el-button @click="parseVisible = false">取消</el-button>
        <el-button type="primary" :loading="parsing" @click="doParse">开始 AI 建模</el-button>
      </template>
    </el-dialog>

    <!-- 粘贴文本 -->
    <el-dialog v-model="pasteVisible" title="粘贴文本 AI 建模" width="560px">
      <el-input v-model="pasteDocName" placeholder="文档名（选填）" style="margin-bottom:10px" />
      <el-input v-model="pasteText" type="textarea" :rows="10"
                placeholder="粘贴流程文档全文，例如：&#10;1. 开通策略：客户后台显示策略已生效&#10;2. 开启远程权限：远程桌面可连接&#10;..." />
      <template #footer>
        <el-button @click="pasteVisible = false">取消</el-button>
        <el-button type="primary" :loading="parsing" @click="doParseText">开始 AI 建模</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const templates = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')

const parseVisible = ref(false)
const pasteVisible = ref(false)
const parsing = ref(false)
const file = ref(null)
const feishuUrl = ref('')
const pasteDocName = ref('')
const pasteText = ref('')

async function load() {
  const d = await api.get('/templates', { params: { page: page.value, size: 20, keyword: keyword.value || undefined } })
  templates.value = d.items
  total.value = d.total
}

async function doParse() {
  if (!file.value && !feishuUrl.value) { ElMessage.warning('请选择文件或填写飞书文档链接'); return }
  parsing.value = true
  try {
    let t
    if (file.value) {
      const form = new FormData()
      form.append('file', file.value)
      t = await api.post('/templates/parse', form, { headers: { 'Content-Type': 'multipart/form-data' } })
    } else {
      t = await api.post('/templates/parse-feishu', { docUrl: feishuUrl.value })
    }
    ElMessage.success('AI 建模完成，请确认草稿')
    parseVisible.value = false
    file.value = null
    feishuUrl.value = ''
    router.push(`/templates/${t.id}/edit`)
  } catch (e) { /* 拦截器已提示 */ } finally { parsing.value = false }
}

async function doParseText() {
  if (!pasteText.value.trim()) { ElMessage.warning('请粘贴文档内容'); return }
  parsing.value = true
  try {
    const t = await api.post('/templates/parse-text', { docName: pasteDocName.value, text: pasteText.value })
    ElMessage.success('AI 建模完成，请确认草稿')
    pasteVisible.value = false
    pasteText.value = ''
    router.push(`/templates/${t.id}/edit`)
  } catch (e) { /* 拦截器已提示 */ } finally { parsing.value = false }
}

async function publish(row) {
  await api.post(`/templates/${row.id}/publish`)
  ElMessage.success('模板已发布')
  load()
}
async function archive(row) {
  await api.post(`/templates/${row.id}/archive`)
  ElMessage.success('模板已停用')
  load()
}
async function duplicate(row) {
  await api.post(`/templates/${row.id}/duplicate`)
  ElMessage.success('已复制为新草稿')
  load()
}

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }

onMounted(load)
</script>
