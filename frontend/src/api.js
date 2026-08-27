import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from './router'
import mockApi from './mockApi'

/**
 * API 客户端：统一携带 JWT、统一错误提示与登录失效跳转。
 * 演示模式（VITE_DEMO_MODE=true，GitHub Pages 静态版）：切换为内置模拟数据层。
 */
const isDemo = import.meta.env.VITE_DEMO_MODE === 'true'

let api

if (isDemo) {
  // 演示模式：包装 mock，关键操作给出成功提示
  api = {
    get: mockApi.get,
    post: (url, data) => {
      if (url === '/channels/sync') return mockApi.post(url, data).then(r => { ElMessage.success('同步完成，新增 2 条消息'); return r })
      if (url === '/notifications/digest') return mockApi.post(url, data).then(r => { ElMessage.success('每日摘要已推送'); return r })
      if (String(url).includes('/correction')) return mockApi.post(url, data).then(r => { ElMessage.success('修正成功，项目已锁定'); return r })
      if (String(url).includes('/mock/generate')) return mockApi.post(url, data).then(r => { ElMessage.success(`已生成 ${r.generated} 条演示消息`); return r })
      if (url === '/templates/parse-text') return mockApi.post(url, data).then(r => { ElMessage.success('AI 建模完成，请确认草稿'); return r })
      if (url === '/reports/generate') return mockApi.post(url, data).then(r => { ElMessage.success('报告生成完成'); return r })
      return mockApi.post(url, data)
    },
    put: mockApi.put,
    delete: mockApi.delete
  }
} else {
  api = axios.create({
    baseURL: '/api/v1',
    timeout: 180000
  })

  api.interceptors.request.use(config => {
    const token = localStorage.getItem('fp_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  api.interceptors.response.use(
    resp => {
      const body = resp.data
      if (body && typeof body.code !== 'undefined' && body.code !== 0) {
        if (body.code === 40100 || body.code === 40101) {
          localStorage.removeItem('fp_token')
          localStorage.removeItem('fp_user')
          router.push('/login')
        }
        ElMessage.error(body.message || '请求失败')
        return Promise.reject(new Error(body.message))
      }
      return body.data
    },
    err => {
      const msg = err.response?.data?.message || err.message || '网络错误'
      ElMessage.error(msg)
      return Promise.reject(err)
    }
  )
}

export default api
