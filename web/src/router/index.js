import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'
import { useAuthStore } from '@/stores/auth'
import StudentHomeView from '@/views/StudentHomeView.vue'

// 按角色决定登录后的落地页：学生进首页（成绩概览），教师/管理员进学生管理
function homeByRole() {
  const auth = useAuthStore()
  return auth.isStudent ? '/home' : '/students'
}

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
      redirect: () => homeByRole(),
      children: [
        { path: 'home', name: 'studentHome', component: StudentHomeView, meta: { title: '首页', roles: ['student'] } },
        { path: 'students', name: 'students', component: () => import('@/views/StudentsView.vue'), meta: { title: '学生管理', roles: ['teacher', 'admin'] } },
        { path: 'clazzes', name: 'clazzes', component: () => import('@/views/ClazzesView.vue'), meta: { title: '班级管理', roles: ['teacher', 'admin'] } },
        { path: 'courses', name: 'courses', component: () => import('@/views/CoursesView.vue'), meta: { title: '课程管理', roles: ['teacher', 'admin'] } },
        { path: 'scores', name: 'scores', component: () => import('@/views/ScoresView.vue'), meta: { title: '选课与成绩', roles: ['teacher', 'admin'] } },
        { path: 'admin/users', name: 'adminUsers', component: () => import('@/views/AdminUsersView.vue'), meta: { title: '用户审核', roles: ['admin'] } },
      ],
    },
  ],
})

// 全局前置守卫：
// 1) 没登录访问任何页面 → 踢回 /login；
// 2) 路由声明了 meta.roles 且当前角色不在其中 → 送回自己的落地页。
// 教训对照：这就是后端 @PreAuthorize 的前端等价物——体验层拦截，后端才是安全边界。
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!auth.isLoggedIn && to.path !== '/login') {
    return '/login'
  }
  if (auth.isLoggedIn && to.path === '/login') {
    return homeByRole()
  }
  const allowed = to.meta.roles
  if (allowed && !allowed.includes(auth.role)) {
    return homeByRole()
  }
})

export default router
