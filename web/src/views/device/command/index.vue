<template>
  <ContentWrap title="命令记录">
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="命令编号" prop="commandNo">
        <el-input v-model="queryParams.commandNo" placeholder="请输入命令编号" clearable class="!w-200px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="设备编码" prop="deviceCode">
        <el-input v-model="queryParams.deviceCode" placeholder="请输入设备编码" clearable class="!w-180px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="动作编码" prop="actionCode">
        <el-input v-model="queryParams.actionCode" placeholder="请输入动作编码" clearable class="!w-160px" @keyup.enter="handleQuery" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="请选择状态" class="!w-140px">
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
      <el-table-column label="命令编号" align="center" prop="commandNo" :show-overflow-tooltip="true" />
      <el-table-column label="设备编码" align="center" prop="deviceCode" :show-overflow-tooltip="true" />
      <el-table-column label="动作编码" align="center" prop="actionCode" :show-overflow-tooltip="true" />
      <el-table-column label="状态" align="center" width="110">
        <template #default="scope">
          <el-tag :type="commandStatusType(scope.row.status)">{{ commandStatusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="解析规则" align="center" prop="codecCode" :show-overflow-tooltip="true" />
      <el-table-column label="操作人" align="center" prop="operator" width="120" />
      <el-table-column label="备注" align="center" prop="remark" :show-overflow-tooltip="true" />
      <el-table-column label="创建时间" align="center" prop="createTime" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="150" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
          <el-button
            v-if="!isCommandTerminal(scope.row.status)"
            v-hasPermi="['device:command:close']"
            link
            type="warning"
            @click="openClose(scope.row)"
            >手动结束</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <!-- 人工结束：只给结论，不能补投回调（回调令牌只存摘要，补投必然 403） -->
  <el-dialog v-model="closeDialog" title="手动结束命令" width="520px" append-to-body>
    <el-form ref="closeFormRef" :model="closeForm" :rules="closeRules" label-width="100px">
      <el-form-item label="命令编号">
        <span class="font-mono">{{ closeTarget?.commandNo }}</span>
        <span class="ml-10px text-12px text-[var(--el-text-color-secondary)]"
          >当前 {{ commandStatusLabel(closeTarget?.status) }}</span
        >
      </el-form-item>
      <el-form-item label="结束为" prop="status">
        <el-radio-group v-model="closeForm.status">
          <el-radio value="SUCCEEDED">成功</el-radio>
          <el-radio value="FAILED">失败</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="closeForm.status === 'FAILED'" label="失败分类" prop="errorType">
        <el-select v-model="closeForm.errorType" class="!w-200px">
          <el-option v-for="item in errorTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="是否可重试" prop="retryable">
        <el-switch v-model="closeForm.retryable" />
        <span class="field-tip ml-10px">{{
          closeForm.retryable ? '流程会再发一次，确认设备侧可安全重放' : '流程不再重试，直接按失败结算'
        }}</span>
      </el-form-item>
      <el-form-item label="原因" prop="remark">
        <el-input
          v-model="closeForm.remark"
          type="textarea"
          :rows="3"
          maxlength="150"
          show-word-limit
          placeholder="例如：设备面板已显示完成，平台未收到回调"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="closeDialog = false">取消</el-button>
      <el-button type="primary" :loading="closeLoading" @click="submitClose">确定</el-button>
    </template>
  </el-dialog>

  <CommandDetailDrawer ref="detailRef" />
</template>

<script lang="ts" setup>
import { dateFormatter } from '@/utils/formatTime'
import { DeviceCommandApi, DeviceCommandCloseReqVO, DeviceCommandVO } from '@/api/device/command'
import {
  commandStatusLabel,
  commandStatusOptions,
  commandStatusType,
  errorTypeOptions,
  isCommandTerminal
} from '../labels'
import CommandDetailDrawer from './CommandDetailDrawer.vue'

defineOptions({ name: 'DeviceCommand' })

const message = useMessage()
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

const closeDialog = ref(false)
const closeLoading = ref(false)
const closeFormRef = ref()
const closeTarget = ref<DeviceCommandVO>()
const closeForm = reactive<DeviceCommandCloseReqVO>({
  id: 0,
  status: 'FAILED',
  retryable: false,
  errorType: 'BUSINESS',
  remark: ''
})
const closeRules = {
  status: [{ required: true, message: '请选择结束为成功还是失败', trigger: 'change' }],
  remark: [{ required: true, message: '请填写人工结束的原因', trigger: 'blur' }]
}

const openClose = (row: DeviceCommandVO) => {
  closeTarget.value = row
  closeForm.id = row.id!
  // 默认按失败收口：人工介入的前提通常是「等不到结果」，猜成功风险更大
  closeForm.status = 'FAILED'
  closeForm.retryable = false
  closeForm.errorType = 'BUSINESS'
  closeForm.remark = ''
  closeDialog.value = true
  closeFormRef.value?.clearValidate()
}

const submitClose = async () => {
  await closeFormRef.value.validate()
  closeLoading.value = true
  try {
    await DeviceCommandApi.closeCommand({ ...closeForm })
    message.success('已结束')
    closeDialog.value = false
    getList()
  } finally {
    closeLoading.value = false
  }
}

onMounted(() => getList())
onActivated(() => {
  if (!loading.value) getList()
})
</script>
