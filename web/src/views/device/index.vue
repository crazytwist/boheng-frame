<template>
  <ContentWrap title="设备台账">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="设备编码" prop="deviceCode">
        <el-input v-model="queryParams.deviceCode" placeholder="请输入设备编码" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="设备名称" prop="deviceName">
        <el-input v-model="queryParams.deviceName" placeholder="请输入设备名称" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="设备类型" prop="deviceTypeCode">
        <el-select v-model="queryParams.deviceTypeCode" placeholder="请选择设备类型" clearable class="!w-180px">
          <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="设备状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择设备状态" clearable class="!w-160px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['device:info:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="图片" align="center" width="80">
        <template #default="scope">
          <el-image
            :src="scope.row.imageUrl || defaultDeviceImage"
            class="w-40px h-40px"
            fit="cover"
            :preview-src-list="[scope.row.imageUrl || defaultDeviceImage]"
            preview-teleported
          />
        </template>
      </el-table-column>
      <el-table-column label="设备编码" align="center" prop="deviceCode" :show-overflow-tooltip="true" />
      <el-table-column label="设备名称" align="center" prop="deviceName" :show-overflow-tooltip="true" />
      <el-table-column label="设备类型" align="center" width="120">
        <template #default="scope">{{ deviceTypeLabel(scope.row.deviceTypeCode) }}</template>
      </el-table-column>
      <el-table-column label="型号" align="center" prop="model" :show-overflow-tooltip="true" width="140" />
      <el-table-column label="厂商" align="center" prop="vendor" :show-overflow-tooltip="true" width="100" />
      <el-table-column label="接入方式" align="center" width="100">
        <template #default="scope">{{ connectionTypeLabel(scope.row.connectionType) }}</template>
      </el-table-column>
      <el-table-column label="设备状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="statusTagType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="并发策略" align="center" width="130">
        <template #default="scope">
          <el-tooltip placement="top">
            <template #content>
              空闲检查：{{ busyCheckLabel(scope.row.busyCheckPolicy) }}<br />
              最大并发数：{{ scope.row.maxInflight ?? 1 }}
            </template>
            <el-tag :type="concurrencyTagType(scope.row.concurrencyPolicy)" size="small">
              {{ concurrencyLabel(scope.row.concurrencyPolicy) }}
            </el-tag>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="占用状态" align="center" width="110">
        <template #default="scope">
          <template v-if="isExclusive(scope.row.concurrencyPolicy)">
            <el-tag v-if="scope.row.lockHolder || scope.row.currentCommandId" type="warning">占用中</el-tag>
            <el-tag v-else type="success">空闲</el-tag>
          </template>
          <span v-else class="text-13px text-gray-500">
            执行中 {{ scope.row.inflightCount ?? 0 }} / {{ scope.row.maxInflight ?? 1 }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['device:info:query']" link type="primary" @click="openDetail(scope.row.id)">
            详情
          </el-button>
          <el-button v-hasPermi="['device:info:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['device:info:delete']" link type="danger" @click="handleDelete(scope.row.id)">
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
  <DeviceForm ref="formRef" @success="getList" />
  <DeviceDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import DeviceForm from './DeviceForm.vue'
import DeviceDetailDrawer from './DeviceDetailDrawer.vue'
import defaultDeviceImage from '@/assets/imgs/device/device-default.svg'
import { deviceTypeLabel, useDeviceTypeDictOptions } from './labels'

defineOptions({ name: 'WmsDeviceInfo' })

const message = useMessage()

const loading = ref(false)
const list = ref<DeviceVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceCode: undefined,
  deviceName: undefined,
  deviceTypeCode: undefined,
  status: undefined
})
const queryFormRef = ref()

const deviceTypeOptions = useDeviceTypeDictOptions()

const statusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离线', value: 'OFFLINE' },
  { label: '维护中', value: 'MAINTENANCE' },
  { label: '故障', value: 'FAULT' }
]
const statusLabel = (v?: string) => statusOptions.find((i) => i.value === v)?.label || v || '—'
const statusTagType = (v?: string): 'success' | 'info' | 'warning' | 'danger' => {
  const map: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    ONLINE: 'success',
    OFFLINE: 'info',
    MAINTENANCE: 'warning',
    FAULT: 'danger'
  }
  return map[v || ''] || 'info'
}

const connectionTypeOptions = [
  { label: 'HTTP 直连', value: 'HTTP' },
  { label: 'MQTT 直连', value: 'MQTT' },
  { label: 'Node-RED 转接', value: 'NODE_RED' },
  { label: '仿真', value: 'SIMULATED' }
]
const connectionTypeLabel = (v?: string) => connectionTypeOptions.find((i) => i.value === v)?.label || v || '—'

const busyCheckOptions = [
  { label: '按动作配置', value: 'AUTO' },
  { label: '下发前必须检查', value: 'ALWAYS' },
  { label: '不检查', value: 'NEVER' }
]
const busyCheckLabel = (v?: string) => busyCheckOptions.find((i) => i.value === v)?.label || v || '—'

const concurrencyOptions = [
  { label: '平台独占', value: 'EXCLUSIVE' },
  { label: '设备本地排队', value: 'DEVICE_QUEUED' },
  { label: '平台排队', value: 'PLATFORM_QUEUED' }
]
const concurrencyLabel = (v?: string) => concurrencyOptions.find((i) => i.value === v)?.label || v || '—'
const concurrencyTagType = (v?: string): 'success' | 'info' | 'warning' | 'danger' => {
  const map: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    EXCLUSIVE: 'info',
    DEVICE_QUEUED: 'success',
    PLATFORM_QUEUED: 'warning'
  }
  return map[v || ''] || 'info'
}
// 独占模式看锁；其余模式看在途数
const isExclusive = (v?: string) => !v || v === 'EXCLUSIVE'

const getList = async () => {
  loading.value = true
  try {
    const data = await DeviceInfoApi.getDevicePage(queryParams)
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
  await DeviceInfoApi.deleteDevice(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
