<template>
  <ContentWrap title="参数集预设">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="参数集编码" prop="paramSetCode">
        <el-input v-model="queryParams.paramSetCode" placeholder="请输入编码" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="名称" prop="paramSetName">
        <el-input v-model="queryParams.paramSetName" placeholder="请输入名称" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="设备类型" prop="deviceTypeCode">
        <el-select v-model="queryParams.deviceTypeCode" placeholder="全部" clearable class="!w-180px">
          <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="已验证" prop="validated">
        <el-select v-model="queryParams.validated" placeholder="全部" clearable class="!w-140px">
          <el-option label="已验证" :value="true" />
          <el-option label="未验证" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['device:param-set:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="参数集编码" align="center" prop="paramSetCode" :show-overflow-tooltip="true" width="170" />
      <el-table-column label="名称" align="center" prop="paramSetName" :show-overflow-tooltip="true" />
      <el-table-column label="设备类型" align="center" width="150">
        <template #default="scope">{{ deviceTypeLabel(scope.row.deviceTypeCode) }}</template>
      </el-table-column>
      <el-table-column label="关联动作" align="center" prop="actionCode" width="150" />
      <el-table-column label="参数取值" align="center" prop="paramsJson" :show-overflow-tooltip="true" />
      <el-table-column label="验证" align="center" width="100">
        <template #default="scope">
          <el-tag :type="scope.row.validated ? 'success' : 'warning'">{{ scope.row.validated ? '已验证' : '未验证' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="90">
        <template #default="scope">
          <el-tag :type="scope.row.status === 0 ? 'success' : 'info'">{{ scope.row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" fixed="right">
        <template #default="scope">
          <el-button
            v-hasPermi="['device:param-set:update']"
            link
            :type="scope.row.validated ? 'warning' : 'success'"
            @click="handleToggleValidated(scope.row)"
          >
            {{ scope.row.validated ? '取消验证' : '标记验证' }}
          </el-button>
          <el-button v-hasPermi="['device:param-set:update']" link type="primary" @click="openForm('update', scope.row.id)">
            编辑
          </el-button>
          <el-button v-hasPermi="['device:param-set:delete']" link type="danger" @click="handleDelete(scope.row.id)">
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
  <ParamSetForm ref="formRef" @success="getList" />
</template>

<script lang="ts" setup>
import { DeviceParamSetApi, DeviceParamSetVO } from '@/api/device/paramSet'
import ParamSetForm from './ParamSetForm.vue'

defineOptions({ name: 'DeviceParamSet' })

const message = useMessage()

const loading = ref(false)
const list = ref<DeviceParamSetVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  paramSetCode: undefined,
  paramSetName: undefined,
  deviceTypeCode: undefined,
  validated: undefined as boolean | undefined
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
    const data = await DeviceParamSetApi.getParamSetPage(queryParams)
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
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

// 标记 / 取消验证（只有已验证的参数集才能用于发起命令）
const handleToggleValidated = async (row: DeviceParamSetVO) => {
  const next = !row.validated
  try {
    await message.confirm(next ? `确认标记【${row.paramSetName}】为已验证？` : `确认取消【${row.paramSetName}】的验证？`)
  } catch {
    return
  }
  await DeviceParamSetApi.updateValidated(row.id!, next)
  message.success(next ? '已标记为验证通过' : '已取消验证')
  getList()
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
  } catch {
    return
  }
  await DeviceParamSetApi.deleteParamSet(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>
