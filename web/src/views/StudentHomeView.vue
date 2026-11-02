<script setup>
// 学生端首页：欢迎横幅 + 我的成绩 + 全部课程浏览。
// 数据来源（均已核对后端权限）：
//   - GET /scores/my   @PreAuthorize(hasRole('STUDENT')) → 自己的成绩（含课程名通过 enrollmentId？不——VO 只有学生名，无课程名）
//   - GET /course      登录即可读 → 课程列表浏览
// 注意：/scores/my 返回的 ScoreResponse 不含课程名，课程名列由前端用 enrollmentId 无法映射，
//       因此这里展示 学号/姓名/分数/考试类型/时间，课程归属在二期后端补 courseName 后再展示。
import { onMounted, ref, computed } from 'vue'
import { listScoresMy, listCourses } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loadingScore = ref(false)
const myScores = ref([])

const loadingCourse = ref(false)
const courses = ref([])

// 成绩统计：平均分 / 最高 / 最低 / 门数（均分保留 1 位小数，与后端 decimal(5,1) 一致）
const stats = computed(() => {
  const values = myScores.value.map((s) => Number(s.value)).filter((v) => !Number.isNaN(v))
  if (!values.length) return null
  const avg = values.reduce((a, b) => a + b, 0) / values.length
  return {
    count: values.length,
    avg: avg.toFixed(1),
    max: Math.max(...values),
    min: Math.min(...values),
  }
})

// 分数颜色：及格绿 / 挂科红（60 分制）
function scoreColor(v) {
  return Number(v) >= 60 ? '#34c759' : '#ff3b30'
}

// 数据库里 exam_type 存的是英文代码（final/midterm/makeup，见 V2 建表注释），
// 这里必须用英文做 key。原来的写法用中文当 key（{ 期末: '期末' }），
// 和后端返回的 "final" 永远匹配不上——页面会直接显示英文原文，
// 而且这种错不报错、不白屏，只是"看起来有点怪"，很难被发现。
const examTypeLabel = { final: '期末', midterm: '期中', makeup: '补考' }

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : ''
}

onMounted(async () => {
  loadingScore.value = true
  try {
    myScores.value = await listScoresMy()
  } catch {
    // 拦截器已弹错误提示
  } finally {
    loadingScore.value = false
  }
  loadingCourse.value = true
  try {
    const data = await listCourses({ page: 1, pageSize: 100 })
    courses.value = data.records || []
  } catch {
  } finally {
    loadingCourse.value = false
  }
})
</script>

<template>
  <div class="student-home">
    <!-- 欢迎横幅：渐变 + 大字号问候 -->
    <section class="hero apple-card">
      <div class="hero-text">
        <h1>你好，{{ auth.username }}</h1>
        <p>欢迎回到 ClassHub，以下是你的学习概览。</p>
      </div>
      <div class="hero-badge">🎓 学生端</div>
    </section>

    <!-- 成绩统计卡：四格 -->
    <section v-if="stats" class="stats">
      <div class="apple-card stat">
        <div class="stat-num">{{ stats.count }}</div>
        <div class="stat-label">已出成绩</div>
      </div>
      <div class="apple-card stat">
        <div class="stat-num" :style="{ color: scoreColor(stats.avg) }">{{ stats.avg }}</div>
        <div class="stat-label">平均分</div>
      </div>
      <div class="apple-card stat">
        <div class="stat-num" style="color: #34c759">{{ stats.max }}</div>
        <div class="stat-label">最高分</div>
      </div>
      <div class="apple-card stat">
        <div class="stat-num" style="color: #ff9500">{{ stats.min }}</div>
        <div class="stat-label">最低分</div>
      </div>
    </section>

    <!-- 我的成绩 -->
    <section class="apple-card block">
      <div class="block-head">
        <h2>我的成绩</h2>
        <el-tag v-if="myScores.length" size="small" effect="plain" round>{{ myScores.length }} 条记录</el-tag>
      </div>
      <el-table v-loading="loadingScore" :data="myScores" stripe>
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="studentName" label="姓名" width="120" />
        <el-table-column prop="value" label="分数" width="110">
          <template #default="{ row }">
            <span class="score" :style="{ color: scoreColor(row.value) }">{{ row.value }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="examType" label="考试类型" width="110">
          <template #default="{ row }">{{ examTypeLabel[row.examType] || row.examType }}</template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.updateTime) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingScore && !myScores.length" description="暂无成绩记录，选课并等待老师录入后这里会显示" :image-size="80" />
    </section>

    <!-- 课程浏览：登录即可读（GET /course 无 @PreAuthorize） -->
    <section class="apple-card block">
      <div class="block-head">
        <h2>全部课程</h2>
        <el-tag v-if="courses.length" size="small" effect="plain" round>{{ courses.length }} 门</el-tag>
      </div>
      <el-table v-loading="loadingCourse" :data="courses" stripe>
        <el-table-column prop="name" label="课程名称" min-width="180" />
        <el-table-column prop="credit" label="学分" width="90" />
        <el-table-column prop="teacherId" label="授课教师" width="110">
          <template #default="{ row }">{{ row.teacherId ? `教师 #${row.teacherId}` : '待分配' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingCourse && !courses.length" description="暂无课程" :image-size="80" />
    </section>
  </div>
</template>

<style scoped>
.student-home {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1080px;
}

/* ---- 欢迎横幅：深色玻璃上用蓝色光晕渐变 ---- */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 30px 34px;
  background: linear-gradient(120deg, rgba(10, 132, 255, 0.28), rgba(94, 92, 230, 0.22)), rgba(28, 30, 42, 0.72);
}
.hero h1 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #f5f5f7;
}
.hero p { margin: 0; color: rgba(245, 245, 247, 0.65); font-size: 14px; }
.hero-badge {
  font-size: 13px;
  font-weight: 600;
  color: #7cb8ff;
  background: rgba(10, 132, 255, 0.16);
  padding: 6px 14px;
  border-radius: 999px;
}

/* ---- 统计四格 ---- */
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.stat { padding: 20px 22px; text-align: center; }
.stat-num { font-size: 28px; font-weight: 700; font-variant-numeric: tabular-nums; }
.stat-label { margin-top: 4px; font-size: 12px; color: rgba(245, 245, 247, 0.55); }

/* ---- 内容块 ---- */
.block { padding: 22px 24px; }
.block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.block-head h2 { margin: 0; font-size: 16px; font-weight: 700; color: #f5f5f7; }

.score { font-weight: 700; font-variant-numeric: tabular-nums; }
</style>
