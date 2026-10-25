<script setup>
// 选课与成绩：选课程 → 看选课名单（可退课）→ 录入成绩 → 已录成绩列表。
// 数据契约：enrollment = 选课记录（studentId+courseId），score 挂在 enrollmentId 上且一人一条。
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listCourses, listEnrollments, addEnrollment, deleteEnrollment,
  listScores, addScore,
} from '@/api'
import { listStudents } from '@/api'

// ---- 顶部的课程选择 ----
const courses = ref([])
const courseId = ref(null)

// ---- 左栏：选课名单 ----
const enrLoading = ref(false)
const enrollments = ref([])
const enrTotal = ref(0)
const enrQuery = reactive({ page: 1, pageSize: 10 })

// ---- 右栏：已录成绩 ----
const scoreLoading = ref(false)
const scores = ref([])
const scoreTotal = ref(0)
const scoreQuery = reactive({ page: 1, pageSize: 10 })

async function loadEnrollments() {
  if (!courseId.value) { enrollments.value = []; enrTotal.value = 0; return }
  enrLoading.value = true
  try {
    const data = await listEnrollments({ courseId: courseId.value, ...enrQuery })
    enrollments.value = data.records
    enrTotal.value = data.total
  } finally {
    enrLoading.value = false
  }
}

async function loadScores() {
  if (!courseId.value) { scores.value = []; scoreTotal.value = 0; return }
  scoreLoading.value = true
  try {
    const data = await listScores({ courseId: courseId.value, ...scoreQuery })
    scores.value = data.records
    scoreTotal.value = data.total
  } finally {
    scoreLoading.value = false
  }
}

watch(courseId, () => {
  enrQuery.page = 1
  scoreQuery.page = 1
  loadEnrollments()
  loadScores()
})

// ---- 选课：从学生列表挑人 ----
const enrollDialog = ref(false)
const studentOptions = ref([])
const enrollStudentId = ref(null)

async function openEnroll() {
  enrollStudentId.value = null
  const data = await listStudents({ page: 1, size: 200 })
  studentOptions.value = data.records
  enrollDialog.value = true
}

async function submitEnroll() {
  if (!enrollStudentId.value) { ElMessage.warning('请选择学生'); return }
  await addEnrollment({ studentId: enrollStudentId.value, courseId: courseId.value })
  ElMessage.success('选课成功')
  enrollDialog.value = false
  loadEnrollments()
}

async function removeEnrollment(row) {
  await ElMessageBox.confirm(`确定让「${row.studentName}」退选这门课吗？（退课记录不保留）`, '退课确认', {
    type: 'warning', confirmButtonText: '退课', cancelButtonText: '取消',
  })
  await deleteEnrollment(row.id)
  ElMessage.success('已退课')
  loadEnrollments()
  loadScores() // 退课后已录成绩可能级联变化，刷新右栏
}

// ---- 录入成绩：一条选课记录一条成绩 ----
const scoreDialog = ref(false)
const scoreFormRef = ref()
const scoreForm = reactive({ enrollmentId: null, studentName: '', value: null, examType: '期末' })

const scoreRules = {
  value: [
    { required: true, message: '请输入分数', trigger: 'blur' },
    { type: 'number', min: 0, max: 100, message: '0 - 100 分', trigger: 'blur' },
  ],
}

function openScore(row) {
  Object.assign(scoreForm, { enrollmentId: row.id, studentName: row.studentName, value: null, examType: '期末' })
  scoreDialog.value = true
}

async function submitScore() {
  await scoreFormRef.value.validate()
  await addScore({ enrollmentId: scoreForm.enrollmentId, value: scoreForm.value, examType: scoreForm.examType })
  ElMessage.success('成绩已录入')
  scoreDialog.value = false
  loadScores()
}

onMounted(async () => {
  const data = await listCourses({ page: 1, size: 100 })
  courses.value = data.records
})
</script>

<template>
  <div>
    <div class="apple-card">
      <div class="page-head">
        <div>
          <div class="page-title">选课与成绩</div>
          <div class="page-sub">先选课程，再管理该课程的选课名单与成绩</div>
        </div>
      </div>
      <el-select v-model="courseId" placeholder="选择课程" size="large" style="width: 320px">
        <el-option v-for="c in courses" :key="c.id" :label="`${c.name}（${c.credit} 学分）`" :value="c.id" />
      </el-select>
    </div>

    <template v-if="courseId">
      <div class="two-col">
        <!-- 左：选课名单 -->
        <div class="apple-card">
          <div class="page-head">
            <div class="col-title">选课名单<span class="count">{{ enrTotal }} 人</span></div>
            <el-button type="primary" round @click="openEnroll">＋ 选课</el-button>
          </div>
          <el-table :data="enrollments" v-loading="enrLoading" stripe>
            <el-table-column prop="studentName" label="学生" min-width="90" />
            <el-table-column prop="createTime" label="选课时间" width="160" />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openScore(row)">录成绩</el-button>
                <el-button link type="danger" @click="removeEnrollment(row)">退课</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            layout="prev, pager, next"
            :total="enrTotal"
            :current-page="enrQuery.page"
            :page-size="enrQuery.pageSize"
            @current-change="(p) => ((enrQuery.page = p), loadEnrollments())"
          />
        </div>

        <!-- 右：已录成绩 -->
        <div class="apple-card">
          <div class="page-head">
            <div class="col-title">已录成绩<span class="count">{{ scoreTotal }} 条</span></div>
          </div>
          <el-table :data="scores" v-loading="scoreLoading" stripe>
            <el-table-column prop="studentNo" label="学号" width="120" />
            <el-table-column prop="studentName" label="学生" min-width="90" />
            <el-table-column prop="value" label="分数" width="80" />
            <el-table-column prop="examType" label="类型" width="80" />
          </el-table>
          <el-pagination
            class="pager"
            layout="prev, pager, next"
            :total="scoreTotal"
            :current-page="scoreQuery.page"
            :page-size="scoreQuery.pageSize"
            @current-change="(p) => ((scoreQuery.page = p), loadScores())"
          />
        </div>
      </div>
    </template>

    <!-- 选课弹窗 -->
    <el-dialog v-model="enrollDialog" title="添加选课" width="400px" destroy-on-close>
      <el-select v-model="enrollStudentId" placeholder="选择学生" size="large" style="width: 100%" filterable>
        <el-option
          v-for="s in studentOptions"
          :key="s.id"
          :label="`${s.name}（${s.studentNo}）`"
          :value="s.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="enrollDialog = false">取消</el-button>
        <el-button type="primary" @click="submitEnroll">确认选课</el-button>
      </template>
    </el-dialog>

    <!-- 录成绩弹窗 -->
    <el-dialog v-model="scoreDialog" :title="`录入成绩 — ${scoreForm.studentName}`" width="400px" destroy-on-close>
      <el-form ref="scoreFormRef" :model="scoreForm" :rules="scoreRules" label-width="70px">
        <el-form-item label="分数" prop="value">
          <el-input-number v-model="scoreForm.value" :min="0" :max="100" :step="0.5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="scoreForm.examType">
            <el-radio value="期末">期末</el-radio>
            <el-radio value="期中">期中</el-radio>
            <el-radio value="平时">平时</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="scoreDialog = false">取消</el-button>
        <el-button type="primary" @click="submitScore">录入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-top: 20px;
}
.col-title { font-size: 16px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.count { font-size: 12px; color: var(--text-2); font-weight: 400; }
.pager { margin-top: 14px; justify-content: flex-end; }
</style>
