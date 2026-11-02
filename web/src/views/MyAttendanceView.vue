<script setup>
// 学生端「我的考勤」：列出自己的每条出勤记录 + 汇总统计。
// 后端 GET /attendance/my 只给 STUDENT（教师没有"自己的考勤"），身份从 token 取。
import { onMounted, ref, computed } from 'vue'
import { myAttendance } from '@/api'

const loading = ref(false)
const records = ref([])

const STATUS_LABEL = { 0: '出勤', 1: '迟到', 2: '早退', 3: '请假', 4: '旷课' }
const STATUS_TYPE = { 0: 'success', 1: 'warning', 2: 'warning', 3: 'info', 4: 'danger' }

// 汇总：只统计出勤/迟到/请假/旷课四类主要状态（早退并入迟到一起看）
const summary = computed(() => {
  const s = { total: records.value.length, present: 0, late: 0, leave: 0, absent: 0 }
  records.value.forEach((r) => {
    if (r.status === 0) s.present++
    else if (r.status === 1 || r.status === 2) s.late++
    else if (r.status === 3) s.leave++
    else if (r.status === 4) s.absent++
  })
  s.rate = s.total ? (s.present / s.total) * 100 : 0
  return s
})

function fmtDate(d) {
  return d ? String(d) : ''
}

onMounted(async () => {
  loading.value = true
  try {
    records.value = (await myAttendance()) || []
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">我的考勤</div>
        <div class="page-sub">共 {{ summary.total }} 条记录，出勤率 {{ summary.rate.toFixed(1) }}%</div>
      </div>
    </div>

    <div class="card-row">
      <div class="stat-card">
        <div class="stat-label">出勤</div>
        <div class="stat-value good">{{ summary.present }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">迟到/早退</div>
        <div class="stat-value warn">{{ summary.late }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">请假</div>
        <div class="stat-value muted">{{ summary.leave }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">旷课</div>
        <div class="stat-value bad">{{ summary.absent }}</div>
      </div>
    </div>

    <div class="apple-card">
      <el-table v-loading="loading" :data="records" stripe style="width: 100%">
        <el-table-column prop="courseName" label="课程" min-width="140" />
        <el-table-column label="日期" width="130">
          <template #default="{ row }">{{ fmtDate(row.attendDate) }}</template>
        </el-table-column>
        <el-table-column prop="sessionNo" label="节次" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.status] || 'info'" effect="plain" round size="small">
              {{ STATUS_LABEL[row.status] ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" />
      </el-table>
      <el-empty v-if="!loading && !records.length" description="还没有考勤记录" />
    </div>
  </div>
</template>

<style scoped>
.card-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
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
  line-height: 1.1;
}
.good { color: #34c759; }
.warn { color: #ff9f0a; }
.bad { color: #ff3b30; }
.muted { color: rgba(245, 245, 247, 0.7); }
</style>
