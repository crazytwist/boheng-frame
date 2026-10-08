<template>
  <ContentWrap title="设备动作定义">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="设备类型" prop="deviceTypeCode">
        <el-select v-model="queryParams.deviceTypeCode" placeholder="全部" clearable class="!w-180px">
          <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作编码" prop="actionCode">
        <el-input v-model="queryParams.actionCode" placeholder="请输入动作编码" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="动作名称" prop="actionName">
        <el-input v-model="queryParams.actionName" placeholder="请输入动作名称" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-140px">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['device:action:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="设备类型" align="center" width="140">
        <template #default="scope">{{ deviceTypeLabel(scope.row.deviceTypeCode) }}</template>
      </el-table-column>
      <el-table-column label="动作编码" align="center" prop="actionCode" :show-overflow-tooltip="true" width="160" />
      <el-table-column label="动作名称" align="center" prop="actionName" :show-overflow-tooltip="true" width="140" />
      <el-table-column label="SiLA 标准特性" align="center" prop="standardFeature" :show-overflow-tooltip="true" width="220" />
      <el-table-column label="厂商映射" align="center" prop="vendorRef" :show-overflow-tooltip="true" width="180" />
      <el-table-column label="预估耗时" align="center" width="110">
        <template #default="scope">{{ durationLabel(scope.row.estimateDurationMs) }}</template>
      </el-table-column>
      <el-table-column label="能力来源" align="center" width="110">
        <template #default="scope">{{ sourceLabel(scope.row.source) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['device:action:query']" link type="primary" @click="openDetail(scope.row.id)">
            详细
          </el-button>
          <el-button v-hasPermi="['device:action:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['device:action:delete']" link type="danger" @click="handleDelete(scope.row.id)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 表单 -->
  <ActionForm ref="formRef" @success="getList" />
  <ActionDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import ActionForm from './ActionForm.vue'
import ActionDetailDrawer from './ActionDetailDrawer.vue'

defineOptions({ name: 'DeviceAction' })

const message = useMessage()

const loading = ref(false)
const list = ref<DeviceActionVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceTypeCode: undefined,
  actionCode: undefined,
  actionName: undefined,
  status: undefined
})
const queryFormRef = ref()

const deviceTypeOptions = [
  { label: '酶标仪/读板机', value: 'PLATE_READER' },
  { label: '液体处理工作站', value: 'LIQUID_HANDLER' },
  { label: '机械臂', value: 'ROBOTIC_ARM' },
  { label: '培养箱', value: 'INCUBATOR' },
  { label: '离心机', value: 'CENTRIFUGE' },
  { label: '其他', value: 'OTHER' }
]
const deviceTypeLabel = (v?: string) => deviceTypeOptions.find((i) => i.value === v)?.label || v || '—'

const sourceLabel = (v?: string) => ({ MANUAL: '手工录入', DEVICE_DECLARED: '驱动上报' }[v || ''] || v || '—')

const durationLabel = (v?: number) => {
  if (v === undefined || v === null) return '—'
  if (v < 1000) return `${v} ms`
  return `${(v / 1000).toFixed(1)} s`
}

const getList = async () => {
  loading.value = true
  try {
    const data = await DeviceActionApi.getActionPage(queryParams)
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
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}
const openDetail = (id: number) => {
  detailRef.value.open(id)
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
  } catch {
    return
  }
  await DeviceActionApi.deleteAction(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
