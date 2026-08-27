<template>
  <div class="fp-layout">
    <aside class="fp-sidebar">
      <div class="fp-brand">
        <span class="logo">▶</span> FlowPilot
      </div>
      <nav class="fp-nav">
        <router-link v-for="item in navs" :key="item.path" :to="item.path"
                     class="nav-item" :class="{ active: isActive(item) }">
          <span>{{ item.icon }}</span><span>{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="fp-sidebar-footer">
        <div style="margin-bottom:6px">{{ user?.displayName || user?.username }}（{{ roleName }}）</div>
        <el-button size="small" text @click="logout">退出登录</el-button>
      </div>
    </aside>
    <main class="fp-main">
      <div v-if="isDemo" class="demo-banner">
        🎭 <b>演示模式</b>：当前为 GitHub Pages 静态预览版，数据为内置模拟，仅用于界面展示与飞书嵌入测试。
        正式版（真实 AI / 聊天同步 / 回调）需部署后端服务，见项目 docs/08。
      </div>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.demo-banner {
  background: #fdf3d8;
  border: 1px solid #fab219;
  color: #8a6100;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 13px;
  margin-bottom: 16px;
}
</style>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const isDemo = import.meta.env.VITE_DEMO_MODE === 'true'

const navs = [
  { path: '/', icon: '📊', label: '项目看板' },
  { path: '/templates', icon: '📚', label: '流程模板' },
  { path: '/channels', icon: '🔌', label: '数据源管理' },
  { path: '/reports', icon: '📄', label: '报告中心' },
  { path: '/notifications', icon: '🔔', label: '通知预警' },
  { path: '/settings', icon: '⚙️', label: '系统设置' }
]

const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('fp_user')) } catch { return null }
})
const roleName = computed(() => ({
  ADMIN: '企业管理者', MANAGER: '流程负责人', MEMBER: '执行人员'
}[user.value?.role] || ''))

function isActive(item) {
  if (item.path === '/') return route.path === '/' || route.path.startsWith('/projects')
  return route.path.startsWith(item.path)
}

function logout() {
  localStorage.removeItem('fp_token')
  localStorage.removeItem('fp_user')
  router.push('/login')
}
</script>
