<template>
  <ContentWrap title="命令记录">
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="命令号" prop="commandNo">
        <el-input v-model="queryParams.commandNo" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备编码" prop="deviceCode">
        <el-input v-model="queryParams.deviceCode" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="动作" prop="actionCode">
        <el-input v-model="queryParams.actionCode" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="全部" class="!w-140px">
          <el-option v-for="item in commandStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="命令号" align="center" prop="commandNo" :show-overflow-tooltip="true" />
      <el-table-column label="设备" align="center" prop="deviceCode" :show-overflow-tooltip="true" />
      <el-table-column label="动作" align="center" prop="actionCode" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" width="110">
        <template #default="scope">
          <el-tag :type="commandStatusType(scope.row.status)">{{ commandStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解析规则" align="center" prop="codecCode" :show-overflow-tooltip="true" />
      <el-table-column label="操作人" align="center" prop="operator" width="120" />
      <el-table-column label="时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.id)">详细</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <CommandDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { DeviceCommandApi, DeviceCommandVO } from '@/api/device/command'
import { commandStatusLabel, commandStatusOptions, commandStatusType } from '../labels'
import CommandDetailDrawer from './CommandDetailDrawer.vue'

defineOptions({ name: 'DeviceCommand' })

const loading = ref(false)
const list = ref<DeviceCommandVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  commandNo: undefined,
  deviceCode: undefined,
  actionCode: undefined,
  status: undefined
})
const queryFormRef = ref()
const detailRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await DeviceCommandApi.getCommandPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value.resetFields()
  handleQuery()
}

const openDetail = (id: number) => detailRef.value.open(id)

onMounted(() => getList())
onActivated(() => {
  if (!loading.value) getList()
})
</script>
