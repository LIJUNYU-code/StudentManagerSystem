<script setup>
// 学生端「我的请假」：提交申请 + 查看自己的请假记录 + 撤销待审核的申请。
//
// 状态机：0待审核 1已通过 2已驳回 3已撤销
// 只有 0 状态可以撤销——这是后端的硬约束（终态不可逆），前端只是把按钮藏起来。
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMyLeaves, createLeave, cancelLeave, listCourses } from '@/api'

const STATUS_META = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' },
  3: { label: '已撤销', type: 'info' },
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10 })
const courses = ref([])

async function load() {
  loading.value = true
  try {
    const data = await listMyLeaves({ ...query })
    records.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function loadCourses() {
  try {
    const data = await listCourses({ page: 1, pageSize: 100 })
    courses.value = data.records || []
  } catch {
    courses.value = []
  }
}

// ---- 提交弹窗 ----
const dialogVisible = ref(false)
const formRef = ref()
const form = reactive({ courseId: null, range: [], reason: '' })

const rules = {
  courseId: [{ required: true, message: '请选择课程', trigger: 'change' }],
  range: [{ required: true, message: '请选择请假日期区间', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写请假理由', trigger: 'blur' },
    { min: 2, max: 300, message: '2-300 个字符', trigger: 'blur' },
  ],
}

function openCreate() {
  Object.assign(form, { courseId: null, range: [], reason: '' })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  const [startDate, endDate] = form.range
  await createLeave({
    courseId: form.courseId,
    startDate,
    endDate,
    reason: form.reason,
  })
  ElMessage.success('申请已提交，等待教师审批')
  dialogVisible.value = false
  query.page = 1
  await load()
}

async function cancel(row) {
  await ElMessageBox.confirm(
    `确定撤销 ${row.startDate} ~ ${row.endDate} 的请假申请吗？撤销后不能恢复。`,
    '撤销申请',
    { type: 'warning', confirmButtonText: '确定撤销', cancelButtonText: '取消' }
  )
  await cancelLeave(row.id)
  ElMessage.success('已撤销')
  await load()
}

// 统计卡片：已通过的总天数
const stats = computed(() => {
  const s = { pending: 0, approved: 0, rejected: 0, approvedDays: 0 }
  records.value.forEach((r) => {
    if (r.status === 0) s.pending++
    else if (r.status === 1) {
      s.approved++
      s.approvedDays += Number(r.days || 0)
    } else if (r.status === 2) s.rejected++
  })
  return s
})

onMounted(async () => {
  await Promise.all([load(), loadCourses()])
})
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">我的请假</div>
        <div class="page-sub">提交请假申请，等待教师审批；审批通过后对应日期自动记为「请假」</div>
      </div>
      <el-button type="primary" @click="openCreate">申请请假</el-button>
    </div>

    <div class="card-row">
      <div class="stat-card">
        <div class="stat-label">待审核</div>
        <div class="stat-value warn">{{ stats.pending }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已通过</div>
        <div class="stat-value good">{{ stats.approved }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已驳回</div>
        <div class="stat-value bad">{{ stats.rejected }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">累计批准天数</div>
        <div class="stat-value">{{ stats.approvedDays }}<span class="unit">天</span></div>
      </div>
    </div>

    <div class="apple-card">
      <el-table v-loading="loading" :data="records" stripe style="width: 100%">
        <el-table-column prop="courseName" label="课程" min-width="130" />
        <el-table-column label="请假区间" min-width="200">
          <template #default="{ row }">
            {{ row.startDate }} ~ {{ row.endDate }}
            <span class="days">（{{ row.days }} 天）</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="理由" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status]?.type || 'info'" effect="plain" round size="small">
              {{ STATUS_META[row.status]?.label ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批意见" min-width="150">
          <template #default="{ row }">
            <span v-if="row.reviewRemark">{{ row.reviewRemark }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <!-- 只有待审核能撤销：终态不可逆是后端的硬约束，这里只是不显示按钮 -->
            <el-button v-if="row.status === 0" link type="danger" @click="cancel(row)">撤销</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !records.length" description="还没有请假记录" />

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>

    <!-- 提交弹窗 -->
    <el-dialog v-model="dialogVisible" title="申请请假" width="460px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="课程" prop="courseId">
          <el-select v-model="form.courseId" placeholder="选择要请假的课程" style="width: 100%">
            <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="请假区间" prop="range">
          <el-date-picker
            v-model="form.range"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="理由" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            maxlength="300"
            show-word-limit
            placeholder="如：家中有事需要回家处理"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">提交申请</el-button>
      </template>
    </el-dialog>
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
  color: #f5f5f7;
}
.stat-value.good { color: #34c759; }
.stat-value.warn { color: #ff9f0a; }
.stat-value.bad { color: #ff3b30; }
.unit { font-size: 13px; color: rgba(245, 245, 247, 0.5); margin-left: 4px; }
.days { font-size: 12px; color: rgba(245, 245, 247, 0.5); }
.muted { color: rgba(245, 245, 247, 0.35); }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
