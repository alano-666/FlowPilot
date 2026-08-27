<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="fp-brand" style="justify-content:center">
        <span class="logo">▶</span> FlowPilot 流程领航员
      </div>
      <p class="sub">AI 流程跟踪助手{{ isDemo ? ' · 演示模式（任意账号密码可登录）' : ' · 演示账号 admin/admin123' }}</p>
      <el-form @submit.prevent="doLogin">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" size="large"
                    show-password @keyup.enter="doLogin" />
        </el-form-item>
        <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="doLogin">
          登 录
        </el-button>
      </el-form>
      <p class="small muted" style="text-align:center;margin-top:16px">
        默认账号：admin/admin123（管理员）、manager/manager123（流程负责人）
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api'

const router = useRouter()
const isDemo = import.meta.env.VITE_DEMO_MODE === 'true'
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)

async function doLogin() {
  loading.value = true
  try {
    const data = await api.post('/auth/login', { username: username.value, password: password.value })
    localStorage.setItem('fp_token', data.token)
    localStorage.setItem('fp_user', JSON.stringify(data.user))
    ElMessage.success(`欢迎，${data.user.displayName || data.user.username}`)
    router.push('/')
  } catch (e) {
    // 错误提示由拦截器处理
  } finally {
    loading.value = false
  }
}
</script>
