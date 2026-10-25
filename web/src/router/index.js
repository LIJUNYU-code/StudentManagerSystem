import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // 登录页：独立于主布局（没有侧边栏）
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    // 业务页：全部挂在 MainLayout 下
    {
      path: '/',
      component: MainLayout,
      redirect: '/students',
      children: [
        { path: 'students', name: 'students', component: () => import('@/views/StudentsView.vue'), meta: { title: '学生管理' } },
        { path: 'clazzes', name: 'clazzes', component: () => import('@/views/ClazzesView.vue'), meta: { title: '班级管理' } },
        { path: 'courses', name: 'courses', component: () => import('@/views/CoursesView.vue'), meta: { title: '课程管理' } },
        { path: 'scores', name: 'scores', component: () => import('@/views/ScoresView.vue'), meta: { title: '选课与成绩' } },
        { path: 'admin/users', name: 'adminUsers', component: () => import('@/views/AdminUsersView.vue'), meta: { title: '用户审核' } },
      ],
    },
  ],
})

// 全局前置守卫：没登录访问任何页面 → 踢回 /login。
// 教训对照：这就是后端 JwtAuthenticationFilter 的前端等价物——只是体验层拦截，后端才是安全边界。
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.isLoggedIn && to.path !== '/login') {
    return '/login'
  }
  if (auth.isLoggedIn && to.path === '/login') {
    return '/students'
  }
})

export default router
