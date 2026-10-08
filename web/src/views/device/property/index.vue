<template>
  <ContentWrap title="设备属性（遥测）">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="设备类型" prop="deviceTypeCode">
        <el-select v-model="queryParams.deviceTypeCode" placeholder="全部" clearable class="!w-180px">
          <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="属性编码" prop="propertyCode">
        <el-input v-model="queryParams.propertyCode" placeholder="请输入属性编码" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="属性名称" prop="propertyName">
        <el-input v-model="queryParams.propertyName" placeholder="请输入属性名称" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['device:property:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="设备类型" align="center" width="150">
        <template #default="scope">{{ deviceTypeLabel(scope.row.deviceTypeCode) }}</template>
      </el-table-column>
      <el-table-column label="属性编码" align="center" prop="propertyCode" :show-overflow-tooltip="true" width="180" />
      <el-table-column label="属性名称" align="center" prop="propertyName" :show-overflow-tooltip="true" width="150" />
      <el-table-column label="数据类型" align="center" width="120">
        <template #default="scope">{{ scope.row.dataType || '—' }}</template>
      </el-table-column>
      <el-table-column label="单位" align="center" prop="unit" width="90" />
      <el-table-column label="可读" align="center" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.readable ? 'success' : 'info'">{{ scope.row.readable ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="可订阅" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.subscribable ? 'success' : 'info'">{{ scope.row.subscribable ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="轮询间隔" align="center" width="100">
        <template #default="scope">{{ scope.row.pollIntervalSec ? scope.row.pollIntervalSec + ' s' : '—' }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="210" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['device:property:query']" link type="primary" @click="openDetail(scope.row.id)">
            详细
          </el-button>
          <el-button v-hasPermi="['device:property:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['device:property:delete']" link type="danger" @click="handleDelete(scope.row.id)">
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
  <PropertyForm ref="formRef" @success="getList" />
  <PropertyDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { DevicePropertyApi, DevicePropertyVO } from '@/api/device/property'
import PropertyForm from './PropertyForm.vue'
import PropertyDetailDrawer from './PropertyDetailDrawer.vue'

defineOptions({ name: 'DeviceProperty' })

const message = useMessage()

const loading = ref(false)
const list = ref<DevicePropertyVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  deviceTypeCode: undefined,
  propertyCode: undefined,
  propertyName: undefined
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

const getList = async () => {
  loading.value = true
  try {
    const data = await DevicePropertyApi.getPropertyPage(queryParams)
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
  await DevicePropertyApi.deleteProperty(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
