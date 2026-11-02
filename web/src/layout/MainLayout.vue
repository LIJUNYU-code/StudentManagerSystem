<script setup>
// 主布局：左侧导航 + 毛玻璃顶栏 + 内容区。
// 菜单按角色过滤：学生看不到「选课与成绩」（后端也有 @PreAuthorize 兜底，前端只是少显示入口）。
import { computed, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { authLogout } from '@/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// 全部菜单；visible 返回 false 的项按角色隐藏
// 权限对齐后端 @PreAuthorize：管理菜单仅教师/管理员可见；学生端有独立首页
const allMenus = [
  { path: '/home', label: '首页', icon: '🏠', visible: () => auth.isStudent },
  { path: '/students', label: '学生管理', icon: '👤', visible: () => !auth.isStudent },
  { path: '/clazzes', label: '班级管理', icon: '🏫', visible: () => !auth.isStudent },
  { path: '/courses', label: '课程管理', icon: '📚', visible: () => !auth.isStudent },
  { path: '/scores', label: '选课与成绩', icon: '📝', visible: () => !auth.isStudent },
  { path: '/attendance', label: '考勤管理', icon: '🗓️', visible: () => !auth.isStudent },
  { path: '/leave-review', label: '请假审核', icon: '📋', visible: () => !auth.isStudent },
  { path: '/stats', label: '成绩分析', icon: '📊', visible: () => !auth.isStudent },
  { path: '/my-attendance', label: '我的考勤', icon: '🗓️', visible: () => auth.isStudent },
  { path: '/my-leave', label: '我的请假', icon: '📋', visible: () => auth.isStudent },
  { path: '/admin/users', label: '用户审核', icon: '🛡️', visible: () => auth.isAdmin },
]
const menus = computed(() => allMenus.filter((m) => m.visible()))

const roleLabel = computed(() =>
  auth.isStudent ? '学生' : auth.role === 'ADMIN' ? '管理员' : '教师'
)

// 登出两件事：①服务端把当前 token 写进 Redis 黑名单（防 token 泄露后继续用）
// ②清本地状态跳登录页。顺序：先请求后清理——请求失败也照样清（本地退出优先）。
async function handleLogout() {
  try {
    await authLogout()
  } catch {
    // 服务端登出失败（网络/Redis 异常）不阻塞本地退出
  }
  auth.logout()
  router.push('/login')
}

// ---- 修改密码弹窗 ----
const pwdDialogVisible = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6~32 位', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value === pwdForm.oldPassword ? callback(new Error('新密码不能与当前密码相同')) : callback(),
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) =>
        value !== pwdForm.newPassword ? callback(new Error('两次输入的密码不一致')) : callback(),
      trigger: 'blur',
    },
  ],
}

async function submitPassword() {
  await pwdFormRef.value.validate()
  await auth.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
  ElMessage.success('密码修改成功，旧登录已全部失效')
  pwdDialogVisible.value = false
  pwdFormRef.value.resetFields()
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
      <div class="sidebar-foot">学生教师一体化管理平台</div>
    </aside>

    <div class="main-col">
      <!-- 顶栏：毛玻璃（backdrop-filter 是苹果质感的灵魂） -->
      <header class="topbar">
        <div class="topbar-title">{{ route.meta.title || '' }}</div>
        <div class="topbar-user">
          <el-avatar :size="28" class="avatar">{{ auth.username.slice(0, 1).toUpperCase() }}</el-avatar>
          <span class="user-name">{{ auth.username }}</span>
          <el-tag size="small" effect="plain" round>{{ roleLabel }}</el-tag>
          <!-- 学生端无 /auth/password（学生 token 的 id 对应 student 表，不是 user 表），
               改密入口仅教师/管理员可见 -->
          <el-button v-if="!auth.isStudent" link class="topbar-action" @click="pwdDialogVisible = true">改密</el-button>
          <el-button link class="logout" @click="handleLogout">退出</el-button>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>

    <!-- 修改密码弹窗：仅教师/管理员可达（入口已按角色隐藏；后端 @PreAuthorize 兜底） -->
    <el-dialog v-model="pwdDialogVisible" title="修改密码" width="400px" append-to-body>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
}

/* ---- 侧边栏：深色玻璃，与内容区背景连成一体 ---- */
.sidebar {
  width: 216px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: rgba(20, 22, 34, 0.82);
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(24px);
  padding: 18px 12px;
}
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  padding: 6px 10px 18px;
  color: #f5f5f7;
}
.brand-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #0a84ff;
  box-shadow: 0 0 0 4px rgba(10, 132, 255, 0.22);
}
.nav { display: flex; flex-direction: column; gap: 2px; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 9px;
  color: rgba(245, 245, 247, 0.78);
  text-decoration: none;
  font-size: 14px;
  transition: background 0.15s;
}
.nav-item:hover { background: rgba(255, 255, 255, 0.08); }
.nav-item.active {
  background: linear-gradient(180deg, #0a84ff, #0071e3);
  color: #fff;
}
.nav-icon { font-size: 15px; }
.sidebar-foot {
  margin-top: auto;
  font-size: 11px;
  color: rgba(245, 245, 247, 0.4);
  text-align: center;
  padding: 10px 0 4px;
}

/* ---- 顶栏：深色玻璃 ---- */
.main-col { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.topbar {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(20, 22, 34, 0.72);
  backdrop-filter: blur(24px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.topbar-title { font-size: 15px; font-weight: 600; color: #f5f5f7; }
.topbar-user { display: flex; align-items: center; gap: 10px; }
.avatar { background: linear-gradient(180deg, #0a84ff, #0071e3); font-size: 13px; }
.user-name { font-size: 13px; font-weight: 500; color: #f5f5f7; }
.topbar-action { color: rgba(245, 245, 247, 0.6); font-size: 13px; margin-left: 0; }
.logout { color: rgba(245, 245, 247, 0.6); font-size: 13px; }

/* ---- 内容区：全站渐变背景图（与登录页同一张），卡片浮在上面 ---- */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 26px 28px;
  background:
    linear-gradient(rgba(10, 12, 22, 0.72), rgba(10, 12, 22, 0.78)),
    url('@/assets/login-bg.png') center / cover no-repeat fixed,
    #0b0d14;
}

/* 内容区里的通用卡片在深色背景上改为玻璃浮层 */
.content :deep(.apple-card) {
  background: rgba(28, 30, 42, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.10);
  backdrop-filter: blur(24px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.content :deep(.apple-card h1),
.content :deep(.apple-card h2) {
  color: #f5f5f7;
}

/* 深色内容区的 Element Plus 组件暗色化 */
.content :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(255, 255, 255, 0.05);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-table-border-color: rgba(255, 255, 255, 0.10);
  --el-table-text-color: rgba(245, 245, 247, 0.85);
  --el-table-header-text-color: rgba(245, 245, 247, 0.55);
  /* 斑马纹：Element Plus 用全局填充色变量画条纹，不覆盖就是白色条块 */
  --el-fill-color-lighter: rgba(255, 255, 255, 0.04);
  --el-fill-color: rgba(255, 255, 255, 0.06);
  background: transparent;
}
.content :deep(.el-table__empty-text) { color: rgba(245, 245, 247, 0.5); }
.content :deep(.el-empty__description p) { color: rgba(245, 245, 247, 0.5); }
.content :deep(.el-tag) { background-color: rgba(255, 255, 255, 0.08); border-color: rgba(255, 255, 255, 0.15); color: rgba(245, 245, 247, 0.85); }
.content :deep(.el-input__wrapper),
.content :deep(.el-select__wrapper) {
  background-color: rgba(255, 255, 255, 0.08);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.14) inset;
}
.content :deep(.el-input__inner),
.content :deep(.el-select__placeholder) { color: rgba(245, 245, 247, 0.9); }
.content :deep(.el-input__inner::placeholder) { color: rgba(245, 245, 247, 0.4); }
.content :deep(.el-pagination) { --el-pagination-text-color: rgba(245,245,247,0.9); --el-pagination-button-disabled-bg-color: transparent; }
.content :deep(.el-pagination button) { background-color: transparent !important; color: rgba(245,245,247,0.85); }
.content :deep(.el-pagination__total),
.content :deep(.el-pagination__jump) { color: rgba(245,245,247,0.85); }
.content :deep(.el-pager li) { background: transparent; color: rgba(245,245,247,0.85); }
.content :deep(.el-pager li.is-active) { color: #fff; background: rgba(10, 132, 255, 0.4); border-radius: 6px; }
.content :deep(.el-dialog) { background: #1e202e; border: 1px solid rgba(255,255,255,0.10); }
.content :deep(.el-dialog__title) { color: #f5f5f7; }
.content :deep(.el-form-item__label) { color: rgba(245, 245, 247, 0.7); }
/* 下拉/日期等弹层的下拉框、10/page 选择器暗色化 */
.content :deep(.el-select-dropdown) { background: #1e202e; border-color: rgba(255,255,255,0.1); }
.content :deep(.el-select-dropdown__item) { color: rgba(245,245,247,0.85); }
.content :deep(.el-select-dropdown__item.hover),
.content :deep(.el-select-dropdown__item:hover) { background: rgba(255,255,255,0.08); }
.content :deep(.el-select-dropdown__item.selected) { color: #7cb8ff; }
/* 默认按钮（查询等 plain/default 按钮在深色底上） */
.content :deep(.el-button--default) {
  background-color: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.18);
  color: rgba(245, 245, 247, 0.9);
}
.content :deep(.el-button--default:hover) {
  background-color: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.3);
  color: #fff;
}
</style>
