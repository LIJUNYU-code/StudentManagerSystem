<script setup>
// 考勤管理：选课程 + 选日期 → 列出全班名单 → 逐个点状态 → 一次提交。
//
// 关键交互：名单里包含「还没点名」的学生（status 为 null）。
// 后端用 LEFT JOIN 保证这一点——如果只显示已点名的人，老师根本没法给新人点名。
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { listCourses, listAttendanceRoster, batchAttendance, attendanceStats } from '@/api'

// 五种状态与配色（与后端 status 0-4 一一对应）
const STATUS_OPTIONS = [
  { value: 0, label: '出勤', type: 'success' },
  { value: 1, label: '迟到', type: 'warning' },
  { value: 2, label: '早退', type: 'warning' },
  { value: 3, label: '请假', type: 'info' },
  { value: 4, label: '旷课', type: 'danger' },
]
const STATUS_LABEL = Object.fromEntries(STATUS_OPTIONS.map((s) => [s.value, s.label]))

const courses = ref([])
const selectedCourseId = ref(null)
const selectedDate = ref(today())
const sessionNo = ref(1)

const loading = ref(false)
const saving = ref(false)
const roster = ref([])
const stats = ref([])
const tab = ref('roster')

function today() {
  // 用本地时间构造 YYYY-MM-DD，不能用 toISOString()——
  // 那是 UTC，东八区早上 8 点前会算成前一天。
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

// 未点名的学生数（提示老师还有多少没点）
const unmarkedCount = computed(() => roster.value.filter((r) => r.status === null).length)

async function loadCourses() {
  try {
    const data = await listCourses({ page: 1, pageSize: 100 })
    courses.value = data.records || []
    if (courses.value.length) {
      selectedCourseId.value = courses.value[0].id
      await loadRoster()
    }
  } catch {
    // 拦截器已提示
  }
}

async function loadRoster() {
  if (!selectedCourseId.value || !selectedDate.value) return
  loading.value = true
  try {
    roster.value = await listAttendanceRoster({
      courseId: selectedCourseId.value,
      attendDate: selectedDate.value,
      sessionNo: sessionNo.value,
    })
    // 未点名的默认给出勤：老师最常做的是"大部分人都在"，默认值对了能少点很多次
    roster.value.forEach((r) => {
      if (r.status === null || r.status === undefined) r.status = 0
    })
  } catch {
    roster.value = []
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  if (!selectedCourseId.value) return
  try {
    stats.value = (await attendanceStats(selectedCourseId.value)) || []
  } catch {
    stats.value = []
  }
}

// 一键全勤：30 个学生逐个点太慢，这是最常用的操作
function markAllPresent() {
  roster.value.forEach((r) => {
    r.status = 0
    r.remark = ''
  })
  ElMessage.success('已全部标记为出勤')
}

async function submit() {
  if (!roster.value.length) {
    ElMessage.warning('名单为空，无法提交')
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId: selectedCourseId.value,
      attendDate: selectedDate.value,
      sessionNo: sessionNo.value,
      items: roster.value.map((r) => ({
        studentId: r.studentId,
        status: r.status,
        remark: r.remark || null,
      })),
    }
    const affected = await batchAttendance(payload)
    ElMessage.success(`点名已保存（${affected} 条记录）`)
    await loadRoster()
    await loadStats()
  } finally {
    saving.value = false
  }
}

// 出勤率配色：>=90% 绿、>=70% 橙、否则红。只影响显示，不影响数据。
function rateClass(rate) {
  const v = Number(rate || 0)
  if (v >= 0.9) return 'rate-good'
  if (v >= 0.7) return 'rate-warn'
  return 'rate-bad'
}

onMounted(loadCourses)
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">考勤管理</div>
        <div class="page-sub">选课程和日期，为全班点名；重复提交会覆盖，不会报错</div>
      </div>
      <el-button type="primary" @click="submit" :loading="saving">保存点名</el-button>
    </div>

    <el-tabs v-model="tab" class="apple-card" style="padding: 6px 24px 20px">
      <!-- ========== 点名 ========== -->
      <el-tab-pane label="点名" name="roster">
        <div class="toolbar">
          <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 180px" @change="loadRoster">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-date-picker
            v-model="selectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="上课日期"
            style="width: 160px"
            @change="loadRoster"
          />
          <el-input-number v-model="sessionNo" :min="1" :max="20" style="width: 110px" @change="loadRoster" />
          <span class="hint">第几节</span>
          <div class="spacer"></div>
          <el-tag v-if="unmarkedCount" type="warning" effect="plain" round>
            {{ unmarkedCount }} 人未点名
          </el-tag>
          <el-button @click="markAllPresent">全部出勤</el-button>
        </div>

        <el-table v-loading="loading" :data="roster" stripe style="width: 100%">
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="120" />
          <el-table-column label="状态" width="360">
            <template #default="{ row }">
              <el-radio-group v-model="row.status" size="small">
                <el-radio-button v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">
                  {{ s.label }}
                </el-radio-button>
              </el-radio-group>
            </template>
          </el-table-column>
          <el-table-column label="备注">
            <template #default="{ row }">
              <el-input v-model="row.remark" placeholder="选填，如请假原因" size="small" maxlength="200" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ========== 统计 ========== -->
      <el-tab-pane label="出勤统计" name="stats">
        <div class="toolbar">
          <el-select v-model="selectedCourseId" placeholder="选择课程" style="width: 180px" @change="loadStats">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <el-button @click="loadStats">刷新</el-button>
        </div>
        <el-table :data="stats" stripe style="width: 100%">
          <el-table-column prop="studentNo" label="学号" width="130" />
          <el-table-column prop="studentName" label="姓名" width="120" />
          <el-table-column prop="total" label="总次数" width="90" />
          <el-table-column prop="present" label="出勤" width="80" />
          <el-table-column prop="late" label="迟到" width="80" />
          <el-table-column prop="earlyLeave" label="早退" width="80" />
          <el-table-column prop="leaveCount" label="请假" width="80" />
          <el-table-column prop="absent" label="旷课" width="80" />
          <el-table-column label="出勤率" width="120">
            <template #default="{ row }">
              <!-- 出勤率只算出勤，不含迟到早退，所以要显示原始百分比而不是笼统的"合格" -->
              <span :class="rateClass(row.attendanceRate)">
                {{ (Number(row.attendanceRate || 0) * 100).toFixed(1) }}%
              </span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.toolbar .spacer { flex: 1; }
.hint { font-size: 12px; color: rgba(245, 245, 247, 0.5); }
.rate-good { color: #34c759; font-weight: 600; }
.rate-warn { color: #ff9f0a; font-weight: 600; }
.rate-bad { color: #ff3b30; font-weight: 600; }
</style>
