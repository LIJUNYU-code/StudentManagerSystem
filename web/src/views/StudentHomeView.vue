<script setup>
// 学生登录后的临时落地页：当前系统管理端仅教师/管理员可用。
// 二期开发"学生自助端"（我的成绩、我的课程）时替换本页。
</script>

<template>
  <div class="placeholder">
    <div class="apple-card placeholder-card">
      <div class="icon">🎒</div>
      <h2>学生端功能建设中</h2>
      <p>课程查询、我的成绩等自助功能将在后续版本开放。</p>
      <p class="hint">当前如需帮助，请联系老师或管理员。</p>
    </div>
  </div>
</template>

<style scoped>
.placeholder {
  display: flex;
  justify-content: center;
  padding-top: 10vh;
}
.placeholder-card {
  text-align: center;
  padding: 48px 56px;
  max-width: 420px;
}
.icon { font-size: 44px; margin-bottom: 14px; }
h2 {
  margin: 0 0 10px;
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
}
p {
  margin: 0 0 6px;
  font-size: 14px;
  color: var(--text-2);
}
.hint { font-size: 12px; opacity: 0.75; }
</style>
