// 登录身份的全局状态（Pinia store）。
// 为什么不用一堆零散的 localStorage.getItem？——集中管理 + 响应式：改这里，顶栏用户名自动更新。
import { defineStore } from 'pinia'
import request from '@/api/request'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 从 localStorage 恢复（key 加 auth: 前缀防与其他项目串）——刷新页面不掉线的关键
    token: localStorage.getItem('auth:token') || '',
    username: localStorage.getItem('auth:username') || '',
    // userType: 'teacher' | 'student'，决定登录后请求走 /auth/login 还是 /student/login
    userType: localStorage.getItem('auth:userType') || '',
    // role: 'TEACHER' | 'ADMIN' | 'STUDENT'（从 JWT payload 解码，仅用于菜单显隐；真正的权限拦截在后端）
    role: localStorage.getItem('auth:role') || '',
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN',
    isStudent: (state) => state.userType === 'student',
  },

  actions: {
    /**
     * 从 JWT 中解出 payload（第二段 base64）。
     * 注意：这不是安全校验，只是让菜单知道该显示什么；伪造 token 也骗不过后端的 @PreAuthorize。
     */
    decodeRole(jwt) {
      try {
        const payload = JSON.parse(atob(jwt.split('.')[1]))
        return payload.role || ''
      } catch {
        return ''
      }
    },

    /**
     * 统一登录入口。
     * @param userType 'teacher' → POST /auth/login {username,password}
     *                 'student' → POST /student/login {studentNo,password}
     * @returns 登录响应的裸 data（拦截器已剥壳）
     */
    async login(userType, form) {
      const path = userType === 'teacher' ? '/auth/login' : '/student/login'
      const data = await request.post(path, form)
      // 学生和教师的响应字段不同（学生有 name/studentNo，教师有 username），统一收敛成我们的格式
      this.token = data.token
      this.username = userType === 'teacher' ? data.username : data.name
      this.userType = userType
      this.role = userType === 'student' ? 'STUDENT' : this.decodeRole(data.token)
      localStorage.setItem('auth:token', data.token)
      localStorage.setItem('auth:username', this.username)
      localStorage.setItem('auth:userType', userType)
      localStorage.setItem('auth:role', this.role)
      return data
    },

    logout() {
      this.token = ''
      this.username = ''
      this.userType = ''
      this.role = ''
      localStorage.removeItem('auth:token')
      localStorage.removeItem('auth:username')
      localStorage.removeItem('auth:userType')
      localStorage.removeItem('auth:role')
    },
  },
})
