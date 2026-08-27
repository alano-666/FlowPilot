<template>
  <div>
    <div class="fp-header"><h1>系统设置</h1></div>

    <div style="display:grid;grid-template-columns:1fr 1fr;gap:18px">
      <!-- 系统配置 -->
      <div class="fp-card">
        <div class="section-title"><h2>运行配置（只读）</h2></div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="AI Provider">{{ settings.ai?.activeProvider }}
            <span class="muted small">（配置: {{ settings.ai?.provider }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="Anthropic 模型">{{ settings.ai?.anthropicModel }}</el-descriptions-item>
          <el-descriptions-item label="OpenAI 兼容模型">{{ settings.ai?.openaiModel }}</el-descriptions-item>
          <el-descriptions-item label="分析并发数">{{ settings.ai?.parallelism }}</el-descriptions-item>
          <el-descriptions-item label="每日摘要时间">{{ settings.notify?.digestCron }}</el-descriptions-item>
          <el-descriptions-item label="渠道同步频率">{{ settings.notify?.syncCron }}</el-descriptions-item>
          <el-descriptions-item label="微信监控目录">{{ settings.wechat?.watchDir }}
            <span class="muted small">（{{ settings.wechat?.watchEnabled ? '监控中' : '已停用' }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="截图 OCR">{{ settings.wechat?.ocrProvider }}</el-descriptions-item>
          <el-descriptions-item label="原始数据留存">{{ settings.data?.retentionDays }} 天</el-descriptions-item>
          <el-descriptions-item label="系统版本">{{ settings.version }}</el-descriptions-item>
        </el-descriptions>
        <p class="small muted" style="margin-top:12px">
          修改配置：编辑 backend/src/main/resources/application.yml 或设置
          FLOWPILOT_ 开头的环境变量后重启服务。完整说明见 docs/05-部署指南.md。
        </p>
      </div>

      <!-- 用户管理 -->
      <div class="fp-card">
        <div class="section-title">
          <h2>用户管理（管理员）</h2>
          <el-button size="small" type="primary" @click="userDialog = true" :disabled="!isAdmin">＋ 新建用户</el-button>
        </div>
        <el-table :data="users" stripe size="small">
          <el-table-column prop="username" label="用户名" width="110" />
          <el-table-column prop="displayName" label="姓名" width="110" />
          <el-table-column label="角色" width="120">
            <template #default="{ row }">
              <el-tag :type="{ ADMIN: 'danger', MANAGER: 'warning', MEMBER: 'info' }[row.role]">
                {{ { ADMIN: '企业管理者', MANAGER: '流程负责人', MEMBER: '执行人员' }[row.role] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="飞书ID" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.feishuOpenId || '—' }}</template>
          </el-table-column>
          <el-table-column label="手机号" width="120">
            <template #default="{ row }">{{ row.phone || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- 新建用户 -->
    <el-dialog v-model="userDialog" title="新建用户" width="440px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="userForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="userForm.password" type="password" placeholder="至少6位" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="userForm.displayName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role" style="width:100%">
            <el-option label="企业管理者" value="ADMIN" />
            <el-option label="流程负责人" value="MANAGER" />
            <el-option label="执行人员" value="MEMBER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialog = false">取消</el-button>
        <el-button type="primary" @click="createUser">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const settings = ref({})
const users = ref([])
const userDialog = ref(false)
const userForm = reactive({ username: '', password: '', displayName: '', role: 'MEMBER' })

const isAdmin = computed(() => {
  try { return JSON.parse(localStorage.getItem('fp_user'))?.role === 'ADMIN' } catch { return false }
})

async function load() {
  settings.value = await api.get('/settings')
  users.value = await api.get('/users')
}

async function createUser() {
  if (!userForm.username || (userForm.password || '').length < 6) {
    ElMessage.warning('用户名必填，密码至少 6 位')
    return
  }
  await api.post('/users', { ...userForm })
  ElMessage.success('用户创建成功')
  userDialog.value = false
  Object.assign(userForm, { username: '', password: '', displayName: '', role: 'MEMBER' })
  load()
}

onMounted(load)
</script>
