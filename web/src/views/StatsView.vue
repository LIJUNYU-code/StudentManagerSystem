<script setup>
// 成绩统计分析：课程概览卡片 + 分数段柱状图 + 班级各课对比 + 学生趋势。
// 数据全部由后端聚合（/stats/*），前端只负责画图，不做任何计算——
// 理由：课程维度的统计如果在前端算，意味着要把全班成绩全量传下来。
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listCourses, listClazzes, courseSummary, courseDistribution, clazzCompare } from '@/api'
import BaseChart from '@/components/BaseChart.vue'

// ---- 筛选条件 ----
const courses = ref([])
const clazzes = ref([])
const selectedCourseId = ref(null)
const selectedClazzId = ref(null)

const loading = ref(false)
const summary = ref(null)
const distribution = ref([])
const compare = ref([])

// 分数段柱状图：及格段用绿色、不及格用红色，一眼看出风险
const PASS_COLOR = '#34c759'
const FAIL_COLOR = '#ff3b30'
const ACCENT = '#0a84ff'

const distributionOption = computed(() => ({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 48, right: 24, top: 24, bottom: 36 },
  xAxis: {
    type: 'category',
    data: distribution.value.map((d) => d.bucket),
    axisLine: { lineStyle: { color: 'rgba(255,255,255,0.22)' } },
    axisLabel: { color: 'rgba(245,245,247,0.72)' },
  },
  yAxis: {
    type: 'value',
    minInterval: 1, // 人数是整数，别出现 2.5 人这种刻度
    splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
    axisLabel: { color: 'rgba(245,245,247,0.72)' },
  },
  series: [
    {
      type: 'bar',
      data: distribution.value.map((d) => ({
        value: d.count,
        // 每根柱子单独配色：<60 段标红，其余绿色
        itemStyle: { color: d.bucket === '0-59' ? FAIL_COLOR : PASS_COLOR, borderRadius: [6, 6, 0, 0] },
      })),
      barMaxWidth: 56,
      label: { show: true, position: 'top', color: 'rgba(245,245,247,0.85)' },
    },
  ],
}))

const compareOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 24, top: 24, bottom: 36 },
  xAxis: {
    type: 'category',
    data: compare.value.map((c) => c.courseName),
    axisLine: { lineStyle: { color: 'rgba(255,255,255,0.22)' } },
    axisLabel: { color: 'rgba(245,245,247,0.72)', interval: 0, rotate: compare.value.length > 5 ? 20 : 0 },
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
    axisLabel: { color: 'rgba(245,245,247,0.72)' },
  },
  series: [
    {
      type: 'bar',
      data: compare.value.map((c) => ({
        value: Number(c.avg),
        itemStyle: { color: ACCENT, borderRadius: [6, 6, 0, 0] },
      })),
      barMaxWidth: 56,
      label: { show: true, position: 'top', color: 'rgba(245,245,247,0.85)' },
      markLine: {
        silent: true,
        symbol: 'none',
        data: [{ yAxis: 60, label: { formatter: '及格线 60', color: '#ff9f0a' } }],
        lineStyle: { color: '#ff9f0a', type: 'dashed' },
      },
    },
  ],
}))

// 概览卡片：后端已保证空课程返回 0 而不是 null
const cards = computed(() => {
  const s = summary.value
  if (!s) return []
  return [
    { label: '成绩条数', value: s.count ?? 0, unit: '条' },
    { label: '平均分', value: Number(s.avg ?? 0).toFixed(1), unit: '分' },
    { label: '最高分', value: Number(s.max ?? 0).toFixed(1), unit: '分' },
    { label: '最低分', value: Number(s.min ?? 0).toFixed(1), unit: '分' },
    // passRate 是 0~1 的小数，乘 100 显示成百分比
    { label: '及格率', value: (Number(s.passRate ?? 0) * 100).toFixed(1), unit: '%' },
  ]
})

async function loadCoursesAndClazzes() {
  try {
    const [c, z] = await Promise.all([
      listCourses({ page: 1, pageSize: 100 }),
      listClazzes({ page: 1, pageSize: 100 }),
    ])
    courses.value = c.records || []
    clazzes.value = z.records || []
    // 默认选中第一个，让页面一打开就有内容（空页面很劝退）
    if (courses.value.length) selectedCourseId.value = courses.value[0].id
    if (clazzes.value.length) selectedClazzId.value = clazzes.value[0].id
  } catch {
    // 拦截器已提示
  }
}

async function loadCourseStats() {
  if (!selectedCourseId.value) return
  loading.value = true
  try {
    // 两个接口并发：概览和分布互不依赖
    const [s, d] = await Promise.all([
      courseSummary(selectedCourseId.value),
      courseDistribution(selectedCourseId.value),
    ])
    summary.value = s
    distribution.value = d || []
  } finally {
    loading.value = false
  }
}

async function loadClazzCompare() {
  if (!selectedClazzId.value) return
  try {
    compare.value = (await clazzCompare(selectedClazzId.value)) || []
  } catch {
    compare.value = []
  }
}

onMounted(async () => {
  await loadCoursesAndClazzes()
  await Promise.all([loadCourseStats(), loadClazzCompare()])
})
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">成绩分析</div>
        <div class="page-sub">按课程看分布与及格率，按班级看各课对比</div>
      </div>
    </div>

    <!-- 概览卡片 -->
    <div class="card-row">
      <div v-for="c in cards" :key="c.label" class="stat-card">
        <div class="stat-label">{{ c.label }}</div>
        <div class="stat-value">
          {{ c.value }}<span class="stat-unit">{{ c.unit }}</span>
        </div>
      </div>
      <div v-if="!cards.length" class="stat-card stat-empty">请选择课程</div>
    </div>

    <!-- 课程维度 -->
    <div class="apple-card section">
      <div class="section-head">
        <h2>分数段分布</h2>
        <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 200px" @change="loadCourseStats">
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
      <BaseChart :option="distributionOption" :loading="loading" height="300px" />
    </div>

    <!-- 班级维度 -->
    <div class="apple-card section">
      <div class="section-head">
        <h2>班级各课平均分对比</h2>
        <el-select v-model="selectedClazzId" placeholder="选择班级" style="width: 200px" @change="loadClazzCompare">
          <el-option v-for="c in clazzes" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
      <BaseChart :option="compareOption" height="300px" />
    </div>
  </div>
</template>

<style scoped>
.card-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  background: rgba(28, 30, 42, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14px;
  padding: 16px 18px;
  backdrop-filter: blur(24px);
}
.stat-label {
  font-size: 12px;
  color: rgba(245, 245, 247, 0.55);
  margin-bottom: 6px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #f5f5f7;
  line-height: 1.1;
}
.stat-unit {
  font-size: 13px;
  font-weight: 500;
  color: rgba(245, 245, 247, 0.5);
  margin-left: 4px;
}
.stat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(245, 245, 247, 0.5);
  font-size: 13px;
}

.section { margin-bottom: 18px; }
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-head h2 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: #f5f5f7;
}
</style>
