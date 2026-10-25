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
    router.push('/students')
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
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 柔和渐变底：淡灰到淡蓝，苹果发布会页同款气质 */
  background: linear-gradient(160deg, #f5f5f7 0%, #e8eef5 55%, #dfe9f3 100%);
}

.login-card {
  width: 380px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(24px);
  border-radius: 20px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.10);
  padding: 40px 36px 28px;
  text-align: center;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 22px;
  font-weight: 700;
}
.logo-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 5px rgba(0, 113, 227, 0.14);
}
.slogan { color: var(--text-2); font-size: 13px; margin: 6px 0 22px; }

.segment {
  display: flex;
  background: rgba(120, 120, 128, 0.12);
  border-radius: 9px;
  padding: 3px;
  margin-bottom: 22px;
}
.segment button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 7px 0;
  font-size: 13px;
  font-family: inherit;
  border-radius: 7px;
  cursor: pointer;
  color: var(--text);
  transition: all 0.15s;
}
.segment button.on {
  background: #fff;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.12);
}

.login-btn {
  width: 100%;
  border-radius: 10px;
  font-weight: 600;
  letter-spacing: 4px;
  margin-top: 4px;
}

.hint { font-size: 12px; color: var(--text-2); margin-top: 16px; }
</style>
