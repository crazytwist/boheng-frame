<template>
  <ContentWrap title="测量记录">
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="设备编码" prop="deviceCode">
        <el-input v-model="queryParams.deviceCode" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="命令编号" prop="commandId">
        <el-input v-model="queryParams.commandId" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="设备" align="center" prop="deviceCode" :show-overflow-tooltip="true" />
      <el-table-column label="命令" align="center" prop="commandId" :show-overflow-tooltip="true" />
      <el-table-column label="模式" align="center" prop="measureMode" :show-overflow-tooltip="true" />
      <el-table-column label="波长" align="center" width="100">
        <template #default="scope">{{ scope.row.wavelengthNm ? `${scope.row.wavelengthNm} nm` : '—' }}</template>
      </el-table-column>
      <el-table-column label="读数时间" align="center" prop="readTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="90" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.id)">详细</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <MeasurementDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { DeviceMeasurementApi, DeviceMeasurementVO } from '@/api/device/measurement'
import MeasurementDetailDrawer from './MeasurementDetailDrawer.vue'

defineOptions({ name: 'DeviceMeasurement' })

const loading = ref(false)
const list = ref<DeviceMeasurementVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceCode: undefined,
  commandId: undefined as string | undefined
})
const queryFormRef = ref()
const detailRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await DeviceMeasurementApi.getMeasurementPage(queryParams)
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
