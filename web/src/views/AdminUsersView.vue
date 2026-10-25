<script setup>
// 用户审核（仅管理员）：审核教师注册（0 待审核 → 1 正常）与禁用（1 → 2）。
// 状态机与后端 AdminUserController 完全对应：非法迁移后端会 409。
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAdminUsers, approveUser, disableUser } from '@/api'

const statusMap = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '正常', type: 'success' },
  2: { text: '已禁用', type: 'danger' },
}

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ keyword: '', status: null, page: 1, pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const data = await listAdminUsers({ ...query, status: query.status ?? undefined })
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

async function approve(row) {
  await approveUser(row.id)
  ElMessage.success(`已通过「${row.username}」`)
  load()
}

async function disable(row) {
  await ElMessageBox.confirm(`确定禁用「${row.username}」吗？禁用后无法登录。`, '禁用确认', {
    type: 'warning', confirmButtonText: '禁用', cancelButtonText: '取消',
  })
  await disableUser(row.id)
  ElMessage.success('已禁用')
  load()
}

onMounted(load)
</script>

<template>
  <div class="apple-card">
    <div class="page-head">
      <div>
        <div class="page-title">用户审核</div>
        <div class="page-sub">教师注册后处于待审核状态，需管理员放行才能登录</div>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索用户名 / 姓名"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 150px" @change="search">
        <el-option label="待审核" :value="0" />
        <el-option label="正常" :value="1" />
        <el-option label="已禁用" :value="2" />
      </el-select>
      <el-button @click="search">查询</el-button>
    </div>

    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">{{ row.role === 'ADMIN' ? '管理员' : '教师' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMap[row.status]?.type" size="small" effect="light" round>
            {{ statusMap[row.status]?.text ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="注册时间" width="170" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 0" link type="primary" @click="approve(row)">通过审核</el-button>
          <el-button v-if="row.status === 1" link type="danger" @click="disable(row)">禁用</el-button>
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
  </div>
</template>

<style scoped>
.pager { margin-top: 16px; justify-content: flex-end; }
</style>
