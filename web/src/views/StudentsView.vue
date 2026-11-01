<script setup>
// 学生管理：分页表格 + 关键词/班级筛选 + 新建/编辑/删除。
// 交互模式是全项目通用的「列表页三件套」，后面班级/课程页同构。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listStudents, addStudent, updateStudent, deleteStudent, listClazzes } from '@/api'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canEdit = !auth.isStudent // 学生只读；教师/管理员可增删改（后端 @PreAuthorize 双保险）

// ---- 列表状态 ----
const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', clazzId: null, page: 1, size: 10 })
const clazzOptions = ref([]) // 班级下拉数据

async function load() {
  loading.value = true
  try {
    const data = await listStudents({ ...query, clazzId: query.clazzId ?? undefined })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1 // 条件变了回到第一页，否则可能停在第 5 页看空数据
  load()
}

// ---- 新建/编辑弹窗（同一张表单复用：有 id 是编辑，没有是新建）----
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ studentNo: '', name: '', gender: 0, age: null, phone: '', password: '', clazzId: null })

const rules = {
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^S\d{9}$/, message: '格式：S + 9 位数字', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { min: 2, max: 100, message: '2-100 个字符', trigger: 'blur' },
  ],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [{ required: true, message: '请输入年龄', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '11 位手机号', trigger: 'blur' },
  ],
  password: [{ min: 6, max: 18, message: '密码长度 6-18 位（编辑时留空=不改密码）', trigger: 'blur' }],
}

function openAdd() {
  editingId.value = null
  Object.assign(form, { studentNo: '', name: '', gender: 0, age: null, phone: '', password: '', clazzId: null })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    studentNo: row.studentNo,
    name: row.name,
    gender: row.gender,
    age: row.age,
    phone: row.phone,
    password: '', // 后端 VO 不回传密码，留空=不改
    clazzId: row.clazzId,
  })
  dialogVisible.value = true
}

async function submit() {
  await formRef.value.validate()
  if (editingId.value) {
    // 编辑：不发密码（留空），后端 DTO null 字段自动跳过
    const { password, ...rest } = form
    await updateStudent(editingId.value, { ...rest, clazzId: rest.clazzId ?? undefined })
    ElMessage.success('已保存')
  } else {
    if (!form.password) {
      ElMessage.warning('新建学生需要设置初始密码')
      return
    }
    await addStudent({ ...form, clazzId: form.clazzId ?? undefined })
    ElMessage.success('已创建')
  }
  dialogVisible.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确定删除学生「${row.name}」吗？删除后不可见。`, '删除确认', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteStudent(row.id)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  // 班级下拉：一次拉 100 条够用（M3 不做班级搜索下拉）
  const data = await listClazzes({ page: 1, size: 100 })
  clazzOptions.value = data.records
})
</script>

<template>
  <div class="apple-card">
    <div class="page-head">
      <div>
        <div class="page-title">学生管理</div>
        <div class="page-sub">共 {{ total }} 名学生</div>
      </div>
      <el-button v-if="canEdit" type="primary" round @click="openAdd">＋ 新建学生</el-button>
    </div>

    <div class="toolbar">
      <el-input v-model="query.keyword" placeholder="搜索姓名 / 学号" clearable style="width: 240px" @keyup.enter="search"
        @clear="search" />
      <el-select v-model="query.clazzId" placeholder="全部班级" clearable style="width: 180px" @change="search">
        <el-option v-for="c in clazzOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-button @click="search">查询</el-button>
    </div>

    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="studentNo" label="学号" width="130" />
      <el-table-column prop="name" label="姓名" min-width="100" />
            <el-table-column label="性别" width="80">
        <template #default="{ row }">{{ row.gender === 0 ? '男' : '女' }}</template>
      </el-table-column>
      <el-table-column prop="age" label="年龄" width="80" />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column v-if="canEdit" label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination class="pager" layout="total, prev, pager, next, sizes" :total="total" :current-page="query.page"
      :page-size="query.size" :page-sizes="[10, 20, 50]" @current-change="(p) => ((query.page = p), load())"
      @size-change="(s) => ((query.size = s), (query.page = 1), load())" />

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑学生' : '新建学生'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="学号" prop="studentNo">
          <el-input v-model="form.studentNo" :disabled="!!editingId" placeholder="S202500001" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">男</el-radio>
            <el-radio :value="1">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="form.age" :min="6" :max="100" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="班级" prop="clazzId">
          <el-select v-model="form.clazzId" placeholder="未分配班级" clearable style="width: 100%">
            <el-option v-for="c in clazzOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editingId" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="6-18 位" />
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
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
