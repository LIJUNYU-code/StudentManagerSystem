// 全局唯一的 axios 实例：所有后端请求都走它。
// 两大职责：①自动带 token ②统一翻译后端的成功/失败。
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const request = axios.create({
  baseURL: '/api', // 被 vite 代理转发到 http://localhost:8080（见 vite.config.js）
  timeout: 10000,
})

// 请求拦截器：每次发请求前，把 localStorage 里的 token 塞进请求头。
// 相当于"每次出门自动带门禁卡"，页面代码永远不用手写 Authorization。
request.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

// 响应拦截器：后端返回真实 HTTP 状态码（200/400/401/403/404/409...）。
// 所以 axios 把 2xx 走"成功"分支、4xx/5xx 走"失败"分支——这跟只看 body 里 code 的写法不同。
request.interceptors.response.use(
  // 成功：剥掉 Result 外壳 {data, code, message}，调用方直接拿到裸数据。
  // 例：后端返回 {data:[...],code:200}，页面拿到的就是 [...]。
  (response) => response.data.data,

  // 失败：统一弹错误消息；401 额外踢回登录页。
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || '网络异常或服务器无响应'

    if (status === 401) {
      // token 缺失/过期/伪造：清登录态回登录页（全站只在这一处处理）
      useAuthStore().logout()
      router.push('/login')
      ElMessage.error('登录已失效，请重新登录')
    } else {
      // 400/403/404/409...：后端的 message 已经是人类可读的（如"班级名已存在"），直接展示
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export default request
