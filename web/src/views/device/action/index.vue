<template>
  <ContentWrap title="设备动作定义">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card shadow="never" class="device-selector-card">
          <template #header><span>设备</span></template>
          <el-input v-model="deviceKeyword" clearable placeholder="搜索设备" class="!mb-12px" />
          <div v-loading="deviceLoading" class="device-selector-list">
            <div
              v-for="device in filteredDevices"
              :key="device.id"
              class="device-selector-item"
              :class="{ 'is-active': selectedDevice?.id === device.id }"
              @click="selectDevice(device)"
            >
              <div class="device-selector-name">{{ device.deviceName || device.deviceCode }}</div>
              <div class="device-selector-meta">
                {{ device.deviceCode }} · {{ device.taskMode === 'PULL' ? '设备拉取' : '平台接收' }}
              </div>
            </div>
            <el-empty v-if="!filteredDevices.length && !deviceLoading" description="暂无设备" :image-size="60" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="18">
        <el-card shadow="never">
          <template #header>
            <div class="action-panel-header">
              <div>
                <div class="action-panel-title">{{ selectedDevice?.deviceName || '请选择设备' }}</div>
                <div class="action-panel-meta" v-if="selectedDevice">
                  {{ selectedDevice.deviceCode }} · {{ selectedDevice.taskMode === 'PULL' ? '设备拉取任务' : '平台接收任务' }}
                </div>
              </div>
              <el-tag v-if="selectedDevice" :type="selectedDevice.taskMode === 'PULL' ? 'warning' : 'success'">
                {{ selectedDevice.taskMode === 'PULL' ? 'PULL' : 'PUSH' }}
              </el-tag>
            </div>
          </template>
          <el-form v-if="selectedDevice" class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
            <el-form-item label="动作编码" prop="actionCode"><el-input v-model="queryParams.actionCode" placeholder="请输入动作编码" clearable @keyup.enter="handleQuery" class="!w-180px" /></el-form-item>
            <el-form-item label="动作名称" prop="actionName"><el-input v-model="queryParams.actionName" placeholder="请输入动作名称" clearable @keyup.enter="handleQuery" class="!w-180px" /></el-form-item>
            <el-form-item label="状态" prop="status"><el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-120px"><el-option label="启用" :value="0" /><el-option label="停用" :value="1" /></el-select></el-form-item>
            <el-form-item><el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button><el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button></el-form-item>
          </el-form>
          <el-table v-loading="loading" :data="list">
      <el-table-column label="设备类型" align="center" width="140">
        <template #default="scope">{{ deviceTypeLabel(scope.row.deviceTypeCode) }}</template>
      </el-table-column>
      <el-table-column
        label="动作编码"
        align="center"
        prop="actionCode"
        :show-overflow-tooltip="true"
      />
      <el-table-column
        label="动作名称"
        align="center"
        prop="actionName"
        :show-overflow-tooltip="true"
      />
      <el-table-column label="请求路径" align="center" :show-overflow-tooltip="true">
        <template #default="scope">
          <span v-if="scope.row.requestPath"
            >{{ scope.row.httpMethod || 'POST' }} {{ scope.row.requestPath }}</span
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="结果方式" align="center" width="110">
        <template #default="scope">{{ resultModeLabel(scope.row.resultMode) }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{
            scope.row.status === 0 ? '启用' : '停用'
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="320" fixed="right">
        <template #default="scope">
          <el-button
            v-if="selectedDevice?.taskMode !== 'PULL'"
            v-hasPermi="['device:info:invoke']"
            link
            type="primary"
            @click="openTest(scope.row.id)"
          >
            调试
          </el-button>
          <el-button
            v-if="selectedDevice?.taskMode !== 'PULL'"
            v-hasPermi="['device:simulate:query']"
            link
            type="primary"
            @click="openSimulate(scope.row.id)"
          >
            仿真配置
          </el-button>
          <el-button
            v-hasPermi="['device:action:query']"
            link
            type="primary"
            @click="openDetail(scope.row.id)"
          >
            详情
          </el-button>
          <el-button
            v-hasPermi="['device:action:update']"
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['device:action:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
          </el-table>
          <Pagination v-if="selectedDevice" :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
          <el-empty v-if="!selectedDevice" description="请先选择设备" />
        </el-card>
      </el-col>
    </el-row>
  </ContentWrap>

  <!-- 表单 -->
  <ActionForm ref="formRef" @success="getList" />
  <ActionDetailDrawer ref="detailRef" />
  <ActionTestDialog ref="testRef" />
  <ActionSimulateDialog ref="simulateRef" @test="openTest" />
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import ActionForm from './ActionForm.vue'
import ActionDetailDrawer from './ActionDetailDrawer.vue'
import ActionTestDialog from './ActionTestDialog.vue'
import ActionSimulateDialog from './ActionSimulateDialog.vue'

defineOptions({ name: 'DeviceAction' })

const message = useMessage()
const route = useRoute()

const loading = ref(false)
const deviceLoading = ref(false)
const devices = ref<DeviceVO[]>([])
const selectedDevice = ref<DeviceVO>()
const deviceKeyword = ref('')
const list = ref<DeviceActionVO[]>([])
const total = ref(0)
const filteredDevices = computed(() => {
  const keyword = deviceKeyword.value.trim().toLowerCase()
  if (!keyword) return devices.value
  return devices.value.filter((item) => `${item.deviceName || ''} ${item.deviceCode || ''}`.toLowerCase().includes(keyword))
})
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceTypeCode: undefined as string | undefined,
  actionCode: undefined,
  actionName: undefined,
  status: undefined
})
const queryFormRef = ref()

const deviceTypeOptions = computed(() => getStrDictOptions(DICT_TYPE.DEVICE_TYPE))
const deviceTypeLabel = (v?: string) =>
  deviceTypeOptions.value.find((i) => i.value === v)?.label || v || '—'
const resultModeLabel = (v?: string) =>
  ({ SYNC_RETURN: '同步返回', CALLBACK: '设备回调', POLL: '平台轮询' })[v || ''] || v || '—'

const getList = async () => {
  if (!selectedDevice.value?.deviceTypeCode) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const data = await DeviceActionApi.getActionPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const selectDevice = (device: DeviceVO) => {
  selectedDevice.value = device
  queryParams.deviceTypeCode = device.deviceTypeCode
  queryParams.pageNo = 1
  getList()
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.deviceTypeCode = selectedDevice.value?.deviceTypeCode
  queryParams.pageNo = 1
  getList()
}

const formRef = ref()
const detailRef = ref()
const testRef = ref()
const simulateRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id, {
    deviceTypeCode: selectedDevice.value?.deviceTypeCode,
    callbackEnabled: selectedDevice.value?.callbackEnabled,
    pollEnabled: selectedDevice.value?.pollEnabled,
    callbackTimeoutSec: selectedDevice.value?.callbackTimeoutSec,
    taskMode: selectedDevice.value?.taskMode
  })
}
const openDetail = (id: number) => {
  detailRef.value.open(id)
}
const openTest = (id: number, deviceId?: number) => {
  testRef.value.open(id, deviceId || selectedDevice.value?.id)
}
const openSimulate = (id: number) => {
  simulateRef.value.open(id)
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

onMounted(async () => {
  deviceLoading.value = true
  try {
    devices.value = (await DeviceInfoApi.getDeviceList()) || []
    const routeDeviceId = Number(route.query.deviceId)
    const initial = devices.value.find((item) => item.id === routeDeviceId) || devices.value[0]
    if (initial) selectDevice(initial)
  } finally {
    deviceLoading.value = false
  }
})
</script>

<style lang="scss" scoped>
.device-selector-card,
.device-selector-list {
  min-height: 420px;
}
.device-selector-list {
  max-height: 560px;
  overflow-y: auto;
}
.device-selector-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}
.device-selector-item:hover,
.device-selector-item.is-active {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.device-selector-name,
.action-panel-title {
  font-weight: 600;
}
.device-selector-meta,
.action-panel-meta {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.action-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
