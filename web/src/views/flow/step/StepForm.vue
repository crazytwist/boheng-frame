<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { FlowStepApi, FlowStepVO } from '@/api/flow/step'

defineOptions({ name: 'FlowStepForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const devices = ref<DeviceVO[]>([])
const actions = ref<DeviceActionVO[]>([])

const createEmpty = (): FlowStepVO => ({
  id: undefined,
  stepKey: '',
  name: '',
  deviceCode: '',
  actionCode: '',
  timeoutSec: 300,
  maxAttempts: 1,
  remark: '',
  status: 0
})

const formData = ref<FlowStepVO>(createEmpty())
const formRules = reactive({
  stepKey: [
    { required: true, message: '步骤编码不能为空', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{0,31}$/, message: '小写字母开头，只含小写、数字和下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '步骤名称不能为空', trigger: 'blur' }],
  deviceCode: [{ required: true, message: '设备不能为空', trigger: 'change' }],
  actionCode: [{ required: true, message: '动作不能为空', trigger: 'change' }],
  timeoutSec: [{ required: true, message: '等待时间不能为空', trigger: 'change' }],
  maxAttempts: [{ required: true, message: '尝试次数不能为空', trigger: 'change' }]
})
const formRef = ref()

const loadActions = async (deviceCode?: string) => {
  actions.value = []
  const type = devices.value.find((item) => item.deviceCode === deviceCode)?.deviceTypeCode
  if (!type) return
  const list = await DeviceActionApi.getActionList({ deviceTypeCode: type })
  actions.value = (list || []).filter((item: DeviceActionVO) => item.status == null || item.status === 0)
}

const onDeviceChange = async () => {
  formData.value.actionCode = ''
  await loadActions(formData.value.deviceCode)
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmpty()
  actions.value = []
  formRef.value?.clearValidate()
  devices.value = (await DeviceInfoApi.getDeviceList()) || []
  if (!id) return
  formLoading.value = true
  try {
    formData.value = await FlowStepApi.get(id)
    await loadActions(formData.value.deviceCode)
  } finally {
    formLoading.value = false
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    const payload = {
      ...formData.value,
      stepKey: formData.value.stepKey?.trim(),
      name: formData.value.name?.trim(),
      deviceCode: formData.value.deviceCode?.trim(),
      actionCode: formData.value.actionCode?.trim()
    }
    if (formType.value === 'create') {
      await FlowStepApi.create(payload)
      message.success(t('common.createSuccess'))
    } else {
      await FlowStepApi.update(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open })
const emit = defineEmits<{ success: [] }>()
</script>

<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="步骤编码" prop="stepKey">
        <el-input v-model="formData.stepKey" maxlength="32" placeholder="小写字母开头，如 spin" :disabled="formType !== 'create'" />
      </el-form-item>
      <el-form-item label="步骤名称" prop="name">
        <el-input v-model="formData.name" maxlength="128" placeholder="请输入步骤名称，如 离心" />
      </el-form-item>
      <el-form-item label="设备" prop="deviceCode">
        <el-select v-model="formData.deviceCode" filterable placeholder="请选择设备" class="!w-1/1" @change="onDeviceChange">
          <el-option v-for="item in devices" :key="item.deviceCode" :label="`${item.deviceName}（${item.deviceCode}）`" :value="item.deviceCode!" />
        </el-select>
      </el-form-item>
      <el-form-item label="动作" prop="actionCode">
        <el-select v-model="formData.actionCode" filterable placeholder="请选择动作" class="!w-1/1" :disabled="!formData.deviceCode">
          <el-option v-for="item in actions" :key="item.actionCode" :label="`${item.actionName}（${item.actionCode}）`" :value="item.actionCode!" />
        </el-select>
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="等待(秒)" prop="timeoutSec">
            <el-input-number v-model="formData.timeoutSec" :min="1" :max="86400" controls-position="right" class="!w-1/1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="尝试次数" prop="maxAttempts">
            <el-input-number v-model="formData.maxAttempts" :min="1" :max="5" controls-position="right" class="!w-1/1" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="状态">
        <el-radio-group v-model="formData.status">
          <el-radio :value="0">启用</el-radio>
          <el-radio :value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="512" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
