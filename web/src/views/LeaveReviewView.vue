<script setup>
// 教师端「请假审核」：查看全部申请，通过或驳回。
//
// 关键：审批通过会**自动把区间内的考勤写成「请假」**（后端 LeaveServiceImpl.syncAttendance）。
// 这个联动是请假功能真正的价值所在——否则请假批了，考勤还是记旷课，等于白批。
// 所以通过时要给教师一句明确提示，让他知道这个操作改了什么。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listLeaves, approveLeave, rejectLeave, listCourses } from '@/api'

const STATUS_META = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' },
  3: { label: '已撤销', type: 'info' },
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const courses = ref([])

// 默认筛「待审核」：教师打开这个页面就是要处理待办，不是来翻历史的
const query = reactive({ status: 0, courseId: null, page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const data = await listLeaves({
      ...query,
      status: query.status ?? undefined,
      courseId: query.courseId ?? undefined,
    })
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

function search() {
  query.page = 1
  load()
}

// ---- 审批弹窗（通过/驳回共用，用 action 区分）----
const dialogVisible = ref(false)
const dialogAction = ref('approve') // approve | reject
const currentRow = ref(null)
const remark = ref('')

function openReview(row, action) {
  currentRow.value = row
  dialogAction.value = action
  remark.value = ''
  dialogVisible.value = true
}

async function confirmReview() {
  const row = currentRow.value
  if (dialogAction.value === 'approve') {
    await ElMessageBox.confirm(
      `通过后，${row.startDate} ~ ${row.endDate} 期间该学生这门课的考勤将自动记为「请假」。确定通过吗？`,
      '确认通过',
      { type: 'warning', confirmButtonText: '确定通过', cancelButtonText: '取消' }
    )
    await approveLeave(row.id, { remark: remark.value || null })
    ElMessage.success('已通过，考勤已同步为「请假」')
  } else {
    await rejectLeave(row.id, { remark: remark.value || null })
    ElMessage.success('已驳回')
  }
  dialogVisible.value = false
  await load()
}

onMounted(async () => {
  await Promise.all([load(), loadCourses()])
})
</script>

<template>
  <div>
    <div class="page-head">
      <div>
        <div class="page-title">请假审核</div>
        <div class="page-sub">通过后对应日期的考勤会自动记为「请假」；驳回则不影响考勤</div>
      </div>
    </div>

    <div class="apple-card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" style="width: 140px" @change="search">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
          <el-option label="已撤销" :value="3" />
          <el-option label="全部" :value="null" />
        </el-select>
        <el-select
          v-model="query.courseId"
          placeholder="全部课程"
          clearable
          style="width: 180px"
          @change="search"
        >
          <el-option v-for="c in courses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button @click="search">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="records" stripe style="width: 100%">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="studentName" label="姓名" width="100" />
        <el-table-column prop="courseName" label="课程" min-width="120" />
        <el-table-column label="请假区间" min-width="190">
          <template #default="{ row }">
            {{ row.startDate }} ~ {{ row.endDate }}
            <span class="days">（{{ row.days }} 天）</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="理由" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status]?.type || 'info'" effect="plain" round size="small">
              {{ STATUS_META[row.status]?.label ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审批人/意见" min-width="140">
          <template #default="{ row }">
            <div v-if="row.reviewerName">
              {{ row.reviewerName }}
              <div class="remark">{{ row.reviewRemark || '—' }}</div>
            </div>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="primary" @click="openReview(row, 'approve')">通过</el-button>
              <el-button link type="danger" @click="openReview(row, 'reject')">驳回</el-button>
            </template>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !records.length" description="没有符合条件的请假申请" />

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

    <el-dialog
      v-model="dialogVisible"
      :title="dialogAction === 'approve' ? '通过请假申请' : '驳回请假申请'"
      width="440px"
      append-to-body
    >
      <div v-if="currentRow" class="dialog-info">
        <div><span class="label">学生</span>{{ currentRow.studentName }}（{{ currentRow.studentNo }}）</div>
        <div><span class="label">课程</span>{{ currentRow.courseName }}</div>
        <div><span class="label">区间</span>{{ currentRow.startDate }} ~ {{ currentRow.endDate }}（{{ currentRow.days }} 天）</div>
        <div><span class="label">理由</span>{{ currentRow.reason }}</div>
      </div>
      <el-input
        v-model="remark"
        type="textarea"
        :rows="3"
        maxlength="300"
        show-word-limit
        :placeholder="dialogAction === 'approve' ? '审批意见（选填）' : '驳回原因（建议填写）'"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          :type="dialogAction === 'approve' ? 'primary' : 'danger'"
          @click="confirmReview"
        >
          {{ dialogAction === 'approve' ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
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
.days { font-size: 12px; color: rgba(245, 245, 247, 0.5); }
.muted { color: rgba(245, 245, 247, 0.35); }
.remark { font-size: 12px; color: rgba(245, 245, 247, 0.55); margin-top: 2px; }
.pager { display: flex; justify-content: flex-end; margin-top: 14px; }
.dialog-info {
  margin-bottom: 14px;
  font-size: 13px;
  line-height: 1.9;
  color: rgba(245, 245, 247, 0.85);
}
.dialog-info .label {
  display: inline-block;
  width: 48px;
  color: rgba(245, 245, 247, 0.5);
}
</style>
