<template>
  <ContentWrap title="仿真配置">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-12px"
      title="为设备动作配置仿真后，执行该动作将直接返回此处配置的报文，不再访问真实设备，适用于真实设备未就绪时的联调测试。"
    />
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="设备" prop="deviceId">
        <el-select v-model="queryParams.deviceId" placeholder="请选择设备" clearable filterable class="!w-200px">
          <el-option v-for="item in devices" :key="item.id" :label="item.deviceName || item.deviceCode" :value="item.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作编码" prop="actionCode">
        <el-input v-model="queryParams.actionCode" placeholder="请输入动作编码" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="返回方式" prop="returnMode">
        <el-select v-model="queryParams.returnMode" placeholder="请选择返回方式" clearable class="!w-150px">
          <el-option v-for="(item, key) in modeMeta" :key="key" :label="item.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-120px">
          <el-option label="启用" :value="0" />
          <el-option label="停用" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openCreate" v-hasPermi="['device:simulate:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <template #empty>
        <el-empty description="暂无仿真配置">
          <el-button v-hasPermi="['device:simulate:create']" type="primary" @click="openCreate">新增仿真配置</el-button>
        </el-empty>
      </template>
      <el-table-column label="设备" align="center" min-width="160" :show-overflow-tooltip="true">
        <template #default="scope">
          <div>{{ deviceName(scope.row.deviceId, scope.row.deviceCode) }}</div>
          <div class="cell-sub">{{ scope.row.deviceCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="设备动作" align="center" min-width="160" :show-overflow-tooltip="true">
        <template #default="scope">
          <div>{{ actionName(scope.row) }}</div>
          <div class="cell-sub">{{ scope.row.actionCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="返回方式" align="center" width="130">
        <template #default="scope">
          <el-tag :type="modeMeta[modeOf(scope.row)]?.type || 'info'">
            {{ modeMeta[modeOf(scope.row)]?.label || scope.row.returnMode }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时序说明" align="center" min-width="220">
        <template #default="scope">{{ timingText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="返回报文" align="center" min-width="200" :show-overflow-tooltip="true">
        <template #default="scope">
          <span class="cell-mono">{{ scope.row.responseBody || '默认：{"status":"DONE"}' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.status === 0"
            :disabled="!canUpdate"
            :loading="switching === scope.row.id"
            @change="(v: boolean) => toggleStatus(scope.row, v)"
          />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" align="center" prop="createTime" width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="200" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['device:info:invoke']" link type="primary" :disabled="scope.row.status !== 0" @click="openTest(scope.row)">
            调试
          </el-button>
          <el-button v-hasPermi="['device:simulate:query']" link type="primary" @click="openEdit(scope.row)">
            {{ canUpdate ? '编辑' : '查看' }}
          </el-button>
          <el-button v-hasPermi="['device:simulate:delete']" link type="danger" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <ActionSimulateDialog ref="simulateRef" @success="getList" @test="openTestById" />
  <ActionTestDialog ref="testRef" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { checkPermi } from '@/utils/permission'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceSimulateApi, DeviceSimulateProfileVO } from '@/api/device/simulate'
import ActionSimulateDialog from '../action/ActionSimulateDialog.vue'
import ActionTestDialog from '../action/ActionTestDialog.vue'

defineOptions({ name: 'DeviceSimulate' })

const message = useMessage()

const loading = ref(false)
const list = ref<DeviceSimulateProfileVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceId: undefined,
  actionCode: undefined,
  returnMode: undefined,
  status: undefined
})
const queryFormRef = ref()
const simulateRef = ref()
const testRef = ref()
const devices = ref<DeviceVO[]>([])
const actions = ref<DeviceActionVO[]>([])
const switching = ref<number>()

const canUpdate = computed(() => checkPermi(['device:simulate:update']))

const modeMeta: Record<string, { label: string; type: 'success' | 'warning' | 'primary' }> = {
  SYNC: { label: '同步返回', type: 'success' },
  CALLBACK: { label: '设备回调', type: 'warning' },
  POLL: { label: '平台轮询', type: 'primary' }
}

const seconds = (ms?: number) => `${((ms ?? 0) / 1000).toFixed(ms && ms % 1000 ? 1 : 0)} 秒`

const modeOf = (row: DeviceSimulateProfileVO) => (row.returnMode === 'ASYNC' ? 'CALLBACK' : row.returnMode || '')

const timingText = (row: DeviceSimulateProfileVO) => {
  if (modeOf(row) === 'CALLBACK') return `受理后 ${seconds(row.asyncWaitMs)}回调平台`
  if (row.returnMode === 'POLL') {
    return `${seconds(row.asyncWaitMs)}后出结果，轮询间隔 ${seconds(row.pollIntervalMs)}，最多 ${row.pollMaxTimes ?? 30} 次`
  }
  return '立即返回'
}

const deviceOf = (id?: number) => devices.value.find((item) => item.id === id)

const deviceName = (id?: number, code?: string) => {
  const device = deviceOf(id)
  return device?.deviceName || device?.deviceCode || code || '—'
}

const actionOf = (row: DeviceSimulateProfileVO) => {
  const typeCode = deviceOf(row.deviceId)?.deviceTypeCode
  return actions.value.find((item) => item.actionCode === row.actionCode && item.deviceTypeCode === typeCode)
}

const actionName = (row: DeviceSimulateProfileVO) => actionOf(row)?.actionName || row.actionCode || '—'

const loadOptions = async () => {
  const [deviceRows, actionRows] = await Promise.all([
    DeviceInfoApi.getDeviceList().catch(() => []),
    DeviceActionApi.getActionList().catch(() => [])
  ])
  devices.value = Array.isArray(deviceRows) ? deviceRows : []
  actions.value = Array.isArray(actionRows) ? actionRows : []
}

const getList = async () => {
  loading.value = true
  try {
    const data = await DeviceSimulateApi.getProfilePage(queryParams)
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
  handleQuery()
}

const openCreate = () => {
  simulateRef.value.open()
}

const openEdit = (row: DeviceSimulateProfileVO) => {
  const action = actionOf(row)
  if (!action?.id) {
    message.warning('未找到该配置对应的设备动作，可能已被删除')
    return
  }
  simulateRef.value.open(action.id, row.deviceId)
}

const openTest = (row: DeviceSimulateProfileVO) => {
  const action = actionOf(row)
  if (!action?.id || !row.deviceId) {
    message.warning('未找到该配置对应的设备动作，可能已被删除')
    return
  }
  testRef.value.open(action.id, row.deviceId)
}

const openTestById = (actionId: number, deviceId: number) => {
  testRef.value.open(actionId, deviceId)
}

const toggleStatus = async (row: DeviceSimulateProfileVO, enabled: boolean) => {
  switching.value = row.id
  try {
    await DeviceSimulateApi.updateProfile({ ...row, status: enabled ? 0 : 1 })
    row.status = enabled ? 0 : 1
    message.success(enabled ? '已启用，该设备将返回仿真报文' : '已停用，该设备恢复访问真实地址')
  } finally {
    switching.value = undefined
  }
}

const handleDelete = async (row: DeviceSimulateProfileVO) => {
  try {
    await message.confirm(`删除后，设备【${deviceName(row.deviceId, row.deviceCode)}】执行动作【${actionName(row)}】将恢复访问真实地址，是否确认删除？`)
  } catch {
    return
  }
  await DeviceSimulateApi.deleteProfile(row.id!)
  message.success('删除成功')
  getList()
}

onMounted(async () => {
  await loadOptions()
  getList()
})
</script>

<style scoped>
.cell-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.cell-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}
</style>
