// 登录身份的全局状态（Pinia store）。
// 为什么不用一堆零散的 localStorage.getItem？——集中管理 + 响应式：改这里，顶栏用户名自动更新。
import { defineStore } from 'pinia'
import request from '@/api/request'
import { changePassword as changePasswordApi } from '@/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // 从 localStorage 恢复（key 加 auth: 前缀防与其他项目串）——刷新页面不掉线的关键
    token: localStorage.getItem('auth:token') || '',
    username: localStorage.getItem('auth:username') || '',
    // userType: 'teacher' | 'student'，决定登录后请求走 /auth/login 还是 /student/login
    userType: localStorage.getItem('auth:userType') || '',
    // role: 'TEACHER' | 'ADMIN' | 'STUDENT'（从 JWT payload 解码，仅用于菜单显隐；真正的权限拦截在后端）
    // 注意：恢复时统一转大写——旧版本 localStorage 里可能存着小写 'admin'，不归一化会再次触发守卫死循环
    role: (localStorage.getItem('auth:role') || '').toUpperCase(),
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
        // 统一转大写：后端 DB 里 role 存的是小写（'admin'/'teacher'），
        // 前端所有判断（meta.roles、isAdmin）都基于大写。单一事实来源，避免再犯大小写不一致。
        return String(payload.role || '').toUpperCase()
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

    /**
     * 修改密码（阶段8 配套前端）。
     * 后端成功后返回重新签发的 token（当前会话不掉线）——这里必须用新 token
     * 替换本地存储，否则下一个请求还带着旧 token，会被 iat 划线直接 401。
     * 注意：role 要从新 token 重新解码（旧 token 里的 role 已不可信）。
     */
    async changePassword(oldPassword, newPassword) {
      const data = await changePasswordApi({ oldPassword, newPassword })
      this.token = data.token
      localStorage.setItem('auth:token', data.token)
      if (this.userType !== 'student') {
        this.role = this.decodeRole(data.token)
        localStorage.setItem('auth:role', this.role)
      }
      return data
    },
  },
})
