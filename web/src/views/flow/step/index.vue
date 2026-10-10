<template>
  <ContentWrap title="步骤定义">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="步骤编码" prop="stepKey">
        <el-input v-model="queryParams.stepKey" placeholder="请输入步骤编码" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="步骤名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入步骤名称" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="设备" prop="deviceCode">
        <el-select v-model="queryParams.deviceCode" placeholder="请选择设备" clearable filterable class="!w-220px">
          <el-option v-for="item in devices" :key="item.deviceCode" :label="`${item.deviceName}（${item.deviceCode}）`" :value="item.deviceCode!" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-140px">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['flow:step:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="步骤编码" align="center" prop="stepKey" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="步骤名称" align="center" prop="name" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="设备" align="center" prop="deviceCode" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="动作" align="center" prop="actionCode" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="等待(秒)" align="center" prop="timeoutSec" width="100" />
      <el-table-column label="尝试次数" align="center" prop="maxAttempts" width="100" />
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['flow:step:query']" link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
          <el-button v-hasPermi="['flow:step:update']" link type="primary" @click="openForm('update', scope.row.id)">编辑</el-button>
          <el-button v-hasPermi="['flow:step:delete']" link type="danger" @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <StepForm ref="formRef" @success="getList" />
  <StepDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { FlowStepApi, FlowStepVO } from '@/api/flow/step'
import StepForm from './StepForm.vue'
import StepDetailDrawer from './StepDetailDrawer.vue'

defineOptions({ name: 'FlowStep' })

const message = useMessage()
const loading = ref(false)
const list = ref<FlowStepVO[]>([])
const total = ref(0)
const devices = ref<DeviceVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  stepKey: undefined,
  name: undefined,
  deviceCode: undefined,
  status: undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await FlowStepApi.getPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.pageNo = 1
  getList()
}

const formRef = ref()
const detailRef = ref()
const openForm = (type: string, id?: number) => formRef.value.open(type, id)
const openDetail = (id: number) => detailRef.value.open(id)

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
  } catch {
    return
  }
  await FlowStepApi.delete(id)
  message.success('删除成功')
  getList()
}

onMounted(async () => {
  devices.value = (await DeviceInfoApi.getDeviceList()) || []
  getList()
})
</script>
