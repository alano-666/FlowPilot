<template>
  <div v-if="project">
    <div class="fp-header">
      <div class="gap8">
        <el-button text @click="$router.push('/')">← 返回看板</el-button>
        <h1 style="margin:0">{{ project.name }}</h1>
        <RiskTag :status="project.riskStatus" />
        <el-tag v-if="project.manualLock" type="warning" size="small">🔒 人工锁定中</el-tag>
      </div>
      <div class="gap8">
        <el-button type="primary" :loading="analyzing" @click="analyze">🤖 立即 AI 分析</el-button>
        <el-button :loading="mockLoading" @click="generateMock">🎭 生成演示消息</el-button>
        <el-button type="warning" plain @click="openCast">🎬 虚拟群演</el-button>
      </div>
    </div>

    <div class="small muted" style="margin-bottom:16px">
      编号 {{ project.code }} ｜ 客户：{{ project.customerName || '—' }} ｜
      流程：{{ project.templateName }} ｜ 开始：{{ fmt(project.startedAt) }} ｜
      最近分析：{{ fmt(project.lastAnalyzedAt) }}
    </div>

    <!-- 流程图 -->
    <div class="fp-card">
      <div class="section-title">
        <h2>流程进度</h2>
        <span class="small muted">✅ 已完成 ｜ 🔄 进行中 ｜ ⏳ 待开始</span>
      </div>
      <FlowChart :nodes="nodes" :current-key="project.currentNodeKey" :completed-keys="completedKeys" />
      <div class="fp-progress" style="margin-top:14px">
        <div class="track"><div class="bar" :style="{ width: (project.progress * 100).toFixed(0) + '%' }"></div></div>
        <span class="pct">{{ Math.round(project.progress * 100) }}%</span>
      </div>
      <div class="small muted" style="margin-top:10px">
        {{ project.latestActivity ? '💬 最近动态：' + project.latestActivity : '' }}
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:18px">
      <!-- 干系人 -->
      <div class="fp-card">
        <div class="section-title">
          <h2>节点干系人</h2>
          <el-button size="small" @click="stakeDialog = true">＋ 添加</el-button>
        </div>
        <div v-if="stakeholders.length">
          <div v-for="s in stakeholders" :key="s.id" class="flex-between" style="padding:8px 0;border-bottom:1px dashed var(--fp-gridline)">
            <div class="small">
              <b>{{ s.name }}</b>（{{ s.role }}）
              <span class="muted"> · {{ s.nodeKey || '未绑定节点' }}</span>
            </div>
            <div class="gap8">
              <el-tag size="small" :type="contactTag(s)">{{ contactLabel(s) }}</el-tag>
              <el-button size="small" type="primary" plain @click="contact(s)">联系</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无干系人，AI 分析后自动识别" :image-size="60" />
      </div>

      <!-- 证据链 -->
      <div class="fp-card">
        <div class="section-title"><h2>AI 证据链</h2></div>
        <div v-if="insights.length">
          <div v-for="i in insights.slice(0, 8)" :key="i.id" class="timeline-item">
            <span>🧾</span>
            <div>
              <div>{{ i.summary }}</div>
              <div class="small muted">节点 {{ i.detectedNodeKey }} · 置信度 {{ Math.round(i.confidence * 100) }}%</div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无证据，先触发一次 AI 分析" :image-size="60" />
      </div>
    </div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:18px">
      <!-- 人工校准 -->
      <div class="fp-card">
        <div class="section-title">
          <h2>人工校准（优先级最高）</h2>
          <el-button v-if="project.manualLock" size="small" @click="unlock">解除锁定</el-button>
        </div>
        <div class="gap8" style="flex-wrap:wrap;margin-bottom:12px">
          <el-select v-model="corrForm.field" style="width:170px">
            <el-option label="当前节点" value="current_node" />
            <el-option label="进度" value="progress" />
            <el-option label="风险状态" value="risk_status" />
          </el-select>
          <template v-if="corrForm.field === 'current_node'">
            <el-select v-model="corrForm.newValue" placeholder="选择节点" style="width:150px">
              <el-option v-for="n in nodes" :key="n.key" :label="n.name" :value="n.key" />
            </el-select>
          </template>
          <template v-else-if="corrForm.field === 'risk_status'">
            <el-select v-model="corrForm.newValue" style="width:150px">
              <el-option label="正常" value="NORMAL" />
              <el-option label="预警" value="WARNING" />
              <el-option label="卡顿" value="BLOCKED" />
            </el-select>
          </template>
          <el-input-number v-else v-model="progressValue" :min="0" :max="100" />
          <el-input v-model="corrForm.note" placeholder="修正原因（选填）" style="width:180px" />
          <el-button type="warning" @click="doCorrect">提交修正</el-button>
        </div>
        <div class="small muted">修正后项目自动锁定，AI 结果需人工确认后才生效。</div>
        <el-divider style="margin:12px 0" />
        <div v-if="calibrations.length">
          <div v-for="c in calibrations.slice(0, 6)" :key="c.id" class="timeline-item">
            <span>✏️</span>
            <div>
              <div>{{ c.username }} 修正 <b>{{ c.field }}</b>：{{ c.oldValue || '(空)' }} → {{ c.newValue }}</div>
              <div class="small muted">{{ fmt(c.createdAt) }}{{ c.note ? ' · ' + c.note : '' }}</div>
            </div>
          </div>
        </div>
        <div v-else class="small muted">暂无校准记录</div>
      </div>

      <!-- AI 待确认建议 -->
      <div class="fp-card">
        <div class="section-title"><h2>AI 待确认建议</h2></div>
        <div v-if="suggestions.length">
          <div v-for="s in suggestions" :key="s.id" class="fp-card" style="padding:14px;margin-bottom:10px;background:var(--fp-page)">
            <pre class="small" style="white-space:pre-wrap;margin:0 0 10px">{{ pretty(s.suggestionJson) }}</pre>
            <div class="gap8">
              <el-button size="small" type="primary" @click="handleSuggestion(s.id, true)">采纳（覆盖人工值）</el-button>
              <el-button size="small" @click="handleSuggestion(s.id, false)">驳回</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="无待确认建议" :image-size="60" />
      </div>
    </div>

    <!-- 最近消息 -->
    <div class="fp-card">
      <div class="section-title"><h2>最近聊天消息</h2></div>
      <div v-if="messages.length">
        <div v-for="m in messages" :key="m.id" class="timeline-item">
          <span class="time">{{ fmt(m.sentAt) }}</span>
          <span style="font-weight:600">{{ m.senderName }}</span>
          <span style="flex:1">{{ m.content }}</span>
          <el-tag size="small" type="info">{{ channelLabel(m.channelType) }}</el-tag>
        </div>
      </div>
      <el-empty v-else description="暂无消息" :image-size="60" />
    </div>

    <!-- 时间线 -->
    <div class="fp-card">
      <div class="section-title"><h2>变更历史</h2></div>
      <div v-for="(e, i) in timeline" :key="i" class="timeline-item">
        <span class="time">{{ fmt(e.time) }}</span>
        <span style="flex:1">{{ e.text }}</span>
      </div>
    </div>

    <!-- 虚拟群演对话框 -->
    <el-dialog v-model="castVisible" title="🎬 虚拟群演（无真实飞书用户时的演示方案）" width="620px">
      <p class="small muted" style="margin-top:0">
        按剧本上演：不同虚拟身份（客户/产品/研发/测试/运维）发布刻意模糊的群聊消息，
        每幕自动触发 AI 分析——看 AI 如何从"差不多、应该没问题"里判断进度与风险。
      </p>
      <el-table :data="script" size="small" highlight-current-row @current-change="row => selectedScene = row?.number">
        <el-table-column label="幕" width="60">
          <template #default="{ row }">第{{ row.number }}幕</template>
        </el-table-column>
        <el-table-column prop="title" label="场景" width="120" />
        <el-table-column prop="description" label="剧情" min-width="200" />
        <el-table-column label="台词数" width="70">
          <template #default="{ row }">{{ row.lines.length }} 条</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="castVisible = false">关闭</el-button>
        <el-button :loading="casting" @click="doCast(false)">上演本幕</el-button>
        <el-button type="warning" :loading="casting" @click="doCast(true)">全部连播（每幕 12 秒）</el-button>
      </template>
    </el-dialog>

    <!-- 添加干系人 -->
    <el-dialog v-model="stakeDialog" title="添加干系人" width="480px">
      <el-form label-width="90px">
        <el-form-item label="姓名"><el-input v-model="stakeForm.name" /></el-form-item>
        <el-form-item label="角色"><el-input v-model="stakeForm.role" placeholder="如：客户IT" /></el-form-item>
        <el-form-item label="节点">
          <el-select v-model="stakeForm.nodeKey" clearable style="width:100%">
            <el-option v-for="n in nodes" :key="n.key" :label="n.name" :value="n.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="平台">
          <el-select v-model="stakeForm.contactType" style="width:100%">
            <el-option label="飞书" value="FEISHU" />
            <el-option label="企业微信" value="WECOM" />
            <el-option label="微信个人" value="WECHAT" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系ID">
          <el-input v-model="stakeForm.contactId" placeholder="open_id / userid / 微信号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stakeDialog = false">取消</el-button>
        <el-button type="primary" @click="addStakeholder">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'
import RiskTag from '../components/RiskTag.vue'
import FlowChart from '../components/FlowChart.vue'

const route = useRoute()
const projectId = route.params.id

const project = ref(null)
const stakeholders = ref([])
const insights = ref([])
const calibrations = ref([])
const suggestions = ref([])
const messages = ref([])
const timeline = ref([])
const analyzing = ref(false)
const mockLoading = ref(false)
const castVisible = ref(false)
const casting = ref(false)
const script = ref([])
const selectedScene = ref(1)

async function openCast() {
  script.value = await api.get('/casting/script')
  selectedScene.value = script.value[0]?.number || 1
  castVisible.value = true
}

async function doCast(autoPlay) {
  casting.value = true
  try {
    const scenes = autoPlay ? script.value : script.value.filter(s => s.number === selectedScene.value)
    for (let i = 0; i < scenes.length; i++) {
      const s = scenes[i]
      const r = await api.post(`/projects/${projectId}/cast`, { scene: s.number, delivery: 'virtual' })
      ElMessage.success(`第${s.number}幕「${s.title}」上演：${r.casted} 条消息，AI 分析已触发`)
      if (autoPlay && i < scenes.length - 1) {
        await new Promise(resolve => setTimeout(resolve, 12000))
      }
      load()
    }
  } catch (e) { /* 拦截器已提示 */ } finally { casting.value = false }
}

const corrForm = reactive({ field: 'current_node', newValue: '', note: '' })
const progressValue = ref(0)
const stakeDialog = ref(false)
const stakeForm = reactive({ name: '', role: '', nodeKey: null, contactType: 'WECHAT', contactId: '' })

const nodes = computed(() => {
  try {
    const snap = JSON.parse(project.value?.templateSnapshotJson || '{}')
    return snap.nodes || []
  } catch { return [] }
})

// 已完成节点：从最近一次成功分析的 evidence 推断
const completedKeys = computed(() => {
  try {
    const last = analyses.value.find(a => a.status === 'SUCCESS')
    if (!last?.resultJson) return []
    const r = JSON.parse(last.resultJson)
    return r.completed_nodes || []
  } catch { return [] }
})
const analyses = ref([])

async function load() {
  const d = await api.get(`/projects/${projectId}`)
  project.value = d.project
  stakeholders.value = d.stakeholders
  timeline.value = d.timeline
  analyses.value = d.runs
  const [msgs, ins, cals, sugs] = await Promise.all([
    api.get(`/projects/${projectId}/messages`, { params: { size: 20 } }),
    api.get(`/projects/${projectId}/insights`),
    api.get(`/projects/${projectId}/calibrations`),
    api.get(`/projects/${projectId}/suggestions`)
  ])
  messages.value = msgs.items
  insights.value = ins
  calibrations.value = cals
  suggestions.value = sugs
}

async function analyze() {
  analyzing.value = true
  try {
    await api.post(`/projects/${projectId}/analyze`)
    ElMessage.success('AI 分析完成')
    load()
  } catch (e) { /* 拦截器已提示 */ } finally { analyzing.value = false }
}

async function generateMock() {
  mockLoading.value = true
  try {
    const r = await api.post(`/channels/mock/generate?projectId=${projectId}`, {})
    ElMessage.success(`已生成 ${r.generated} 条演示消息，可点击 AI 分析`)
    load()
  } finally { mockLoading.value = false }
}

async function doCorrect() {
  const newValue = corrForm.field === 'progress' ? String(progressValue.value / 100) : corrForm.newValue
  if (!newValue && corrForm.field !== 'progress') { ElMessage.warning('请填写修正值'); return }
  await api.post(`/projects/${projectId}/correction`, {
    field: corrForm.field, newValue, note: corrForm.note, lock: true
  })
  ElMessage.success('修正成功，项目已锁定')
  load()
}

async function unlock() {
  await api.post(`/projects/${projectId}/unlock`)
  ElMessage.success('已解除锁定')
  load()
}

async function handleSuggestion(id, confirm) {
  await api.post(`/projects/suggestions/${id}/${confirm ? 'confirm' : 'reject'}`)
  ElMessage.success(confirm ? '已采纳 AI 建议' : '已驳回')
  load()
}

async function addStakeholder() {
  if (!stakeForm.name) { ElMessage.warning('请填写姓名'); return }
  await api.post(`/projects/${projectId}/stakeholders`, { ...stakeForm })
  ElMessage.success('已添加')
  stakeDialog.value = false
  Object.assign(stakeForm, { name: '', role: '', nodeKey: null, contactType: 'WECHAT', contactId: '' })
  load()
}

function contact(s) {
  if (s.contactType === 'FEISHU' && s.contactId) {
    window.open(`https://applink.feishu.cn/client/chat/open?openId=${s.contactId}`, '_blank')
  } else if (s.contactType === 'WECOM' && s.contactId) {
    window.location.href = `wxwork://message?username=${s.contactId}`
  } else {
    ElMessage.info(`微信用户 ${s.name}：请通过微信客户端搜索微信号 ${s.wechatId || s.contactId || '—'} 发起沟通`)
  }
}

function contactLabel(s) {
  return { FEISHU: '飞书', WECOM: '企业微信', WECHAT: '微信' }[s.contactType] || '未知'
}
function contactTag(s) {
  return { FEISHU: 'primary', WECOM: 'success', WECHAT: 'info' }[s.contactType] || 'info'
}
function channelLabel(t) {
  return { FEISHU: '飞书', WECOM: '企微', WECHAT_IMPORT: '微信导入', EMAIL: '邮件', MOCK: '演示' }[t] || t
}
function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}
function pretty(json) {
  try { return JSON.stringify(JSON.parse(json), null, 2) } catch { return json }
}

onMounted(load)
</script>
