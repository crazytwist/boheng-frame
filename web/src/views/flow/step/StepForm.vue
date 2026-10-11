<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { FlowStepApi, FlowStepVO, STEP_TYPES } from '@/api/flow/step'

defineOptions({ name: 'FlowStepForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const devices = ref<DeviceVO[]>([])
const actions = ref<DeviceActionVO[]>([])

/** 设备动作 / 设备任务专属配置：从 typeConfig JSON 拆出的两个选择框 */
const deviceConfig = reactive<{ deviceCode: string; actionCode: string }>({ deviceCode: '', actionCode: '' })

const createEmpty = (): FlowStepVO => ({
  id: undefined,
  stepKey: '',
  name: '',
  stepType: 'device.command',
  typeConfig: '',
  defaultParams: '',
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
  stepType: [{ required: true, message: '步骤类型不能为空', trigger: 'change' }],
  timeoutSec: [{ required: true, message: '等待时间不能为空', trigger: 'change' }],
  maxAttempts: [{ required: true, message: '尝试次数不能为空', trigger: 'change' }]
})
const formRef = ref()

const isDeviceCommand = computed(() => formData.value.stepType === 'device.command')
const isDeviceTask = computed(() => formData.value.stepType === 'device.task')
const isDeviceType = computed(() => isDeviceCommand.value || isDeviceTask.value)

const loadActions = async (deviceCode?: string) => {
  actions.value = []
  const type = deviceCode ? devices.value.find((item) => item.deviceCode === deviceCode)?.deviceTypeCode : undefined
  // 设备任务允许不限设备：没选设备时拉全量动作列表，选了设备则按设备类型过滤
  const list = type
    ? await DeviceActionApi.getActionList({ deviceTypeCode: type })
    : await DeviceActionApi.getActionList()
  actions.value = (list || []).filter((item: DeviceActionVO) => item.status == null || item.status === 0)
}

const onDeviceChange = async () => {
  deviceConfig.actionCode = ''
  await loadActions(deviceConfig.deviceCode)
}

/** 切换类型时清掉旧配置 */
const onTypeChange = () => {
  formData.value.typeConfig = ''
  deviceConfig.deviceCode = ''
  deviceConfig.actionCode = ''
  actions.value = []
}

/** 从 typeConfig 里解析设备动作配置给选择框 */
const parseDeviceConfig = (typeConfig?: string) => {
  try {
    const config = typeConfig ? JSON.parse(typeConfig) : {}
    deviceConfig.deviceCode = config.deviceCode || ''
    deviceConfig.actionCode = config.actionCode || ''
  } catch {
    deviceConfig.deviceCode = ''
    deviceConfig.actionCode = ''
  }
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmpty()
  deviceConfig.deviceCode = ''
  deviceConfig.actionCode = ''
  actions.value = []
  formRef.value?.clearValidate()
  devices.value = (await DeviceInfoApi.getDeviceList()) || []
  if (!id) return
  formLoading.value = true
  try {
    formData.value = await FlowStepApi.get(id)
    if (isDeviceType.value) {
      parseDeviceConfig(formData.value.typeConfig)
      await loadActions(deviceConfig.deviceCode)
    }
  } finally {
    formLoading.value = false
  }
}

/** 提交前把设备选择框收进 typeConfig */
const buildPayload = (): FlowStepVO => {
  const payload = { ...formData.value }
  if (isDeviceType.value) {
    // 设备任务可不限设备，deviceCode 空时不落字段
    payload.typeConfig = JSON.stringify({
      ...(deviceConfig.deviceCode ? { deviceCode: deviceConfig.deviceCode } : {}),
      actionCode: deviceConfig.actionCode
    })
  } else {
    payload.typeConfig = payload.typeConfig?.trim() || undefined
  }
  payload.defaultParams = payload.defaultParams?.trim() || undefined
  payload.stepKey = payload.stepKey?.trim()
  payload.name = payload.name?.trim()
  return payload
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (isDeviceCommand.value && (!deviceConfig.deviceCode || !deviceConfig.actionCode)) {
    message.error('设备动作步骤要选择设备和动作')
    return
  }
  if (isDeviceTask.value && !deviceConfig.actionCode) {
    message.error('设备任务步骤要选择任务类型（动作）')
    return
  }
  formLoading.value = true
  try {
    const payload = buildPayload()
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
      <el-form-item label="步骤类型" prop="stepType">
        <el-select v-model="formData.stepType" placeholder="请选择步骤类型" class="!w-1/1" :disabled="formType !== 'create'" @change="onTypeChange">
          <el-option v-for="item in STEP_TYPES" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <!-- 设备动作 / 设备任务：设备 + 动作选择框，收进 typeConfig -->
      <template v-if="isDeviceType">
        <el-form-item label="设备" :required="isDeviceCommand">
          <el-select v-model="deviceConfig.deviceCode" filterable :clearable="isDeviceTask"
            :placeholder="isDeviceTask ? '请选择设备（可留空不限设备）' : '请选择设备'" class="!w-1/1" @change="onDeviceChange">
            <el-option v-for="item in devices" :key="item.deviceCode" :label="`${item.deviceName}（${item.deviceCode}）`" :value="item.deviceCode!" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作" required>
          <el-select v-model="deviceConfig.actionCode" filterable placeholder="请选择动作" class="!w-1/1" :disabled="isDeviceCommand && !deviceConfig.deviceCode">
            <el-option v-for="item in actions" :key="item.actionCode" :label="`${item.actionName}（${item.actionCode}）`" :value="item.actionCode!" />
          </el-select>
        </el-form-item>
      </template>
      <!-- 其它类型：JSON 配置 -->
      <el-form-item v-else label="类型配置">
        <el-input v-model="formData.typeConfig" type="textarea" :rows="4" maxlength="4096"
          placeholder='类型专属配置 JSON，如 {"beanName":"demoAction","argsJson":"{}"}' />
      </el-form-item>
      <el-form-item label="默认输入">
        <el-input v-model="formData.defaultParams" type="textarea" :rows="2" maxlength="4096"
          placeholder='选填。默认输入 JSON，如 {"speed":3000}' />
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
