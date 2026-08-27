import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router'

// 演示模式（GitHub Pages 静态托管）：无服务端路由重写，使用 hash 路由
const isDemo = import.meta.env.VITE_DEMO_MODE === 'true'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  {
    path: '/',
    component: () => import('./views/Layout.vue'),
    children: [
      { path: '', name: 'dashboard', component: () => import('./views/Dashboard.vue'), meta: { title: '项目看板' } },
      { path: 'projects/:id', name: 'projectDetail', component: () => import('./views/ProjectDetail.vue'), meta: { title: '项目详情' } },
      { path: 'templates', name: 'templates', component: () => import('./views/Templates.vue'), meta: { title: '流程模板' } },
      { path: 'templates/:id/edit', name: 'templateEdit', component: () => import('./views/TemplateEdit.vue'), meta: { title: '模板编辑' } },
      { path: 'channels', name: 'channels', component: () => import('./views/Channels.vue'), meta: { title: '数据源管理' } },
      { path: 'reports', name: 'reports', component: () => import('./views/Reports.vue'), meta: { title: '报告中心' } },
      { path: 'notifications', name: 'notifications', component: () => import('./views/Notifications.vue'), meta: { title: '通知预警' } },
      { path: 'settings', name: 'settings', component: () => import('./views/Settings.vue'), meta: { title: '系统设置' } }
    ]
  }
]

const router = createRouter({
  history: isDemo ? createWebHashHistory() : createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !localStorage.getItem('fp_token')) {
    return '/login'
  }
  if (to.path === '/login' && localStorage.getItem('fp_token')) {
    return '/'
  }
})

export default router
