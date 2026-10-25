<script setup>
// 主布局：左侧导航 + 毛玻璃顶栏 + 内容区。
// 菜单按角色过滤：学生看不到「选课与成绩」（后端也有 @PreAuthorize 兜底，前端只是少显示入口）。
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// 全部菜单；visible 返回 false 的项按角色隐藏
// 权限对齐后端 @PreAuthorize：学生端仅二期开发自助查询，当前所有管理菜单仅教师/管理员可见
const allMenus = [
  { path: '/students', label: '学生管理', icon: '👤', visible: () => !auth.isStudent },
  { path: '/clazzes', label: '班级管理', icon: '🏫', visible: () => !auth.isStudent },
  { path: '/courses', label: '课程管理', icon: '📚', visible: () => !auth.isStudent },
  { path: '/scores', label: '选课与成绩', icon: '📝', visible: () => !auth.isStudent },
  { path: '/admin/users', label: '用户审核', icon: '🛡️', visible: () => auth.isAdmin },
]
const menus = computed(() => allMenus.filter((m) => m.visible()))

const roleLabel = computed(() =>
  auth.isStudent ? '学生' : auth.role === 'ADMIN' ? '管理员' : '教师'
)

function handleLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <!-- 侧边导航：半透明白 + 细分隔线 -->
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-dot"></span>
        ClassHub
      </div>
      <nav class="nav">
        <router-link
          v-for="m in menus"
          :key="m.path"
          :to="m.path"
          class="nav-item"
          :class="{ active: route.path.startsWith(m.path) }"
        >
          <span class="nav-icon">{{ m.icon }}</span>
          {{ m.label }}
        </router-link>
      </nav>
      <div class="sidebar-foot">学生一体化管理平台</div>
    </aside>

    <div class="main-col">
      <!-- 顶栏：毛玻璃（backdrop-filter 是苹果质感的灵魂） -->
      <header class="topbar">
        <div class="topbar-title">{{ route.meta.title || '' }}</div>
        <div class="topbar-user">
          <el-avatar :size="28" class="avatar">{{ auth.username.slice(0, 1).toUpperCase() }}</el-avatar>
          <span class="user-name">{{ auth.username }}</span>
          <el-tag size="small" effect="plain" round>{{ roleLabel }}</el-tag>
          <el-button link class="logout" @click="handleLogout">退出</el-button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
}

/* ---- 侧边栏 ---- */
.sidebar {
  width: 216px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.65);
  border-right: 1px solid var(--line);
  backdrop-filter: blur(20px);
  padding: 18px 12px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  padding: 6px 10px 18px;
}
.brand-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(0, 113, 227, 0.15);
}
.nav { display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 9px;
  color: var(--text);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s;
}
.nav-item:hover { background: rgba(0, 0, 0, 0.05); }
.nav-item.active {
  background: var(--accent);
  color: #fff;
}
.nav-icon { font-size: 15px; }
.sidebar-foot {
  margin-top: auto;
  font-size: 11px;
  color: var(--text-2);
  text-align: center;
  padding: 10px 0 4px;
}

/* ---- 顶栏 ---- */
.main-col { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--line);
}
.topbar-title { font-size: 15px; font-weight: 600; }
.topbar-user { display: flex; align-items: center; gap: 10px; }
.avatar { background: var(--accent); font-size: 13px; }
.user-name { font-size: 13px; font-weight: 500; }
.logout { color: var(--text-2); font-size: 13px; }

/* ---- 内容区 ---- */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 26px 28px;
}
</style>
