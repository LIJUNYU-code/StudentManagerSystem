<script setup>
// 课程管理：与班级页同构；credit 用数字输入 + 步进 0.5。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listCourses, addCourse, updateCourse, deleteCourse } from '@/api'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', page: 1, pageSize: 10 }) // 注意：课程接口的分页参数名是 pageSize

async function load() {
  loading.value = true
  try {
    const data = await listCourses({ ...query })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ name: '', credit: 1 })

const rules = {
  name: [
    { required: true, message: '请输入课程名称', trigger: 'blur' },
    { max: 100, message: '不超过 100 个字符', trigger: 'blur' },
  ],
  credit: [{ required: true, message: '请填写学分', trigger: 'blur' }],
}

function openAdd() {
  editingId.value = null
  Object.assign(form, { name: '', credit: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, { name: row.name, credit: Number(row.credit) })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  if (editingId.value) {
    await updateCourse(editingId.value, { ...form })
    ElMessage.success('已保存')
  } else {
    await addCourse({ ...form })
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除课程「${row.name}」吗？`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteCourse(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="apple-card">
    <div class="page-head">
      <div>
        <div class="page-title">课程管理</div>
        <div class="page-sub">共 {{ total }} 门课程</div>
      </div>
      <el-button type="primary" round @click="openAdd">＋ 新建课程</el-button>
    </div>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索课程名称"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-button @click="search">查询</el-button>
    </div>

    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="name" label="课程名称" min-width="180" />
      <el-table-column prop="credit" label="学分" width="100" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="pager"
      layout="total, prev, pager, next, sizes"
      :total="total"
      :current-page="query.page"
      :page-size="query.pageSize"
      :page-sizes="[10, 20, 50]"
      @current-change="(p) => ((query.page = p), load())"
      @size-change="(s) => ((query.pageSize = s), (query.page = 1), load())"
    />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑课程' : '新建课程'" width="420px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：Java Programming" />
        </el-form-item>
        <el-form-item label="学分" prop="credit">
          <el-input-number v-model="form.credit" :min="0.5" :max="10" :step="0.5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
