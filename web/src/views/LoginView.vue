<script setup>
// 登录页：教师/管理员（用户名）与学生（学号）双入口，分段切换。
// 登录逻辑全部在 auth store 的 login() 里，页面只负责表单与校验。
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

// 'teacher' 走 /auth/login（用户名），'student' 走 /student/login（学号）
const mode = ref('teacher')
const loading = ref(false)
const formRef = ref()

const form = reactive({ account: '', password: '' })

const rules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 18, message: '密码长度 6-18 位', trigger: 'blur' },
  ],
}

async function handleLogin() {
  await formRef.value.validate()
  loading.value = true
  try {
    // account 字段按模式映射成后端要的字段名
    const payload =
      mode.value === 'teacher'
        ? { username: form.account, password: form.password }
        : { studentNo: form.account, password: form.password }
    await auth.login(mode.value, payload)
    ElMessage.success('欢迎回来')
    router.push(mode.value === 'student' ? '/home' : '/students')
  } catch {
    // 错误提示已由 axios 拦截器统一弹出（401 会显示后端的 message）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="logo">
        <span class="logo-dot"></span>
        ClassHub
      </div>
      <p class="slogan">学生一体化管理平台</p>

      <!-- 分段控件：苹果设置页同款 -->
      <div class="segment">
        <button :class="{ on: mode === 'teacher' }" @click="mode = 'teacher'">教师 / 管理员</button>
        <button :class="{ on: mode === 'student' }" @click="mode = 'student'">学生</button>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="account">
          <el-input v-model="form.account" :placeholder="mode === 'teacher' ? '用户名' : '学号（如 S202500001）'" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button class="login-btn" type="primary" size="large" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

      <p class="hint">教师账号需管理员审核通过后才能登录</p>
    </div>

    <footer class="page-foot">ClassHub · 学生一体化管理平台</footer>
  </div>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  /* 背景：程序生成的 macOS Sonoma 风格渐变图（assets/login-bg.png），
     cover 铺满 + 深色遮罩压暗边缘，保证卡片和文字对比度 */
  background:
    linear-gradient(rgba(8, 10, 18, 0.35), rgba(8, 10, 18, 0.5)),
    url('@/assets/login-bg.png') center / cover no-repeat,
    #0b0d14; /* 图加载失败时的兜底色 */
}

/* ---- 登录卡：加大加宽，深色玻璃 ---- */
.login-card {
  position: relative;
  z-index: 1;
  width: 480px;
  max-width: calc(100vw - 48px);
  background: rgba(28, 30, 40, 0.62);
  backdrop-filter: blur(28px);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 24px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.45);
  padding: 52px 56px 40px;
  text-align: center;
  color: #f5f5f7;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 26px;
  font-weight: 700;
  color: #fff;
}
.logo-dot {
  width: 13px;
  height: 13px;
  border-radius: 50%;
  background: #0a84ff;
  box-shadow: 0 0 0 6px rgba(10, 132, 255, 0.22);
}
.slogan { color: rgba(245, 245, 247, 0.65); font-size: 14px; margin: 8px 0 26px; }

.segment {
  display: flex;
  background: rgba(120, 120, 128, 0.24);
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 26px;
}
.segment button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 9px 0;
  font-size: 14px;
  font-family: inherit;
  border-radius: 8px;
  cursor: pointer;
  color: rgba(245, 245, 247, 0.75);
  transition: all 0.15s;
}
.segment button.on {
  background: rgba(255, 255, 255, 0.92);
  color: #1d1d1f;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
}

/* 深色卡内的输入框：覆盖 Element Plus 浅色默认 */
.login-card :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.10);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.16) inset;
  border-radius: 10px;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1.5px #0a84ff inset;
}
.login-card :deep(.el-input__inner) { color: #f5f5f7; }
.login-card :deep(.el-input__inner::placeholder) { color: rgba(245, 245, 247, 0.42); }
.login-card :deep(.el-input__suffix) { color: rgba(245, 245, 247, 0.55); }

.login-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 6px;
  margin-top: 6px;
  background: linear-gradient(180deg, #0a84ff, #0071e3);
  border: none;
}
.login-btn:hover { background: linear-gradient(180deg, #2492ff, #0a84ff); }

.hint { font-size: 12px; color: rgba(245, 245, 247, 0.45); margin-top: 18px; }

.page-foot {
  position: relative;
  z-index: 1;
  margin-top: 40px;
  font-size: 12px;
  color: rgba(245, 245, 247, 0.35);
}
</style>
