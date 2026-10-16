<script lang="ts" setup>
import { FlowStepApi, FlowStepVO, STEP_TYPES } from '@/api/flow/step'
import { STEP_TYPE_FIELDS } from '@/api/flow/stepSchema'
import SchemaForm from '@/components/flow/SchemaForm.vue'
import JsonKeyValueEditor from '@/components/flow/JsonKeyValueEditor.vue'

defineOptions({ name: 'FlowStepForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmpty = (): FlowStepVO => ({
  id: undefined,
  stepKey: '',
  name: '',
  stepType: 'device.command',
  typeConfig: '',
  defaultParams: '',
  outputSchema: '',
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
  timeoutSec: [{ required: true, message: '等待时间不能为空', trigger: 'change' },
    { validator: (_r, v, cb) => (v === -1 || (v >= 1 && v <= 86400) ? cb() : cb(new Error('等待秒数须为 -1（永不超时）或 1~86400'))), trigger: 'change' }],
  maxAttempts: [{ required: true, message: '尝试次数不能为空', trigger: 'change' }]
})
const formRef = ref()

const isDeviceCommand = computed(() => formData.value.stepType === 'device.command')
const isDeviceTask = computed(() => formData.value.stepType === 'device.task')

/** 当前类型的类型配置字段表（驱动 SchemaForm） */
const typeFields = computed(() => STEP_TYPE_FIELDS[formData.value.stepType ?? ''] ?? [])

/** 把 typeConfig JSON 解析成对象，供提交前校验 */
const parseConfig = (json?: string): Record<string, any> => {
  try {
    return json ? JSON.parse(json) : {}
  } catch {
    return {}
  }
}

/** 切换类型时清掉旧配置 */
const onTypeChange = () => {
  formData.value.typeConfig = ''
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmpty()
  formRef.value?.clearValidate()
  if (!id) return
  formLoading.value = true
  try {
    formData.value = await FlowStepApi.get(id)
  } finally {
    formLoading.value = false
  }
}

/** 提交前把表单收进 payload（typeConfig / defaultParams 已是 JSON 字符串，直接透传） */
const buildPayload = (): FlowStepVO => {
  const payload = { ...formData.value }
  payload.typeConfig = payload.typeConfig?.trim() || undefined
  payload.defaultParams = payload.defaultParams?.trim() || undefined
  payload.outputSchema = payload.outputSchema?.trim() || undefined
  payload.stepKey = payload.stepKey?.trim()
  payload.name = payload.name?.trim()
  return payload
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const config = parseConfig(formData.value.typeConfig)
  if (isDeviceCommand.value && (!config.deviceCode || !config.actionCode)) {
    message.error('设备动作步骤要选择设备和动作')
    return
  }
  if (isDeviceTask.value && !config.actionCode) {
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
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
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
      <!-- 类型配置：按类型字段表动态渲染，收进 typeConfig JSON -->
      <template v-if="typeFields.length">
        <SchemaForm :fields="typeFields" v-model="formData.typeConfig" />
      </template>
      <el-form-item v-else label="类型配置">
        <el-input v-model="formData.typeConfig" type="textarea" :rows="3" maxlength="4096" placeholder="类型专属配置 JSON" />
      </el-form-item>
      <!-- 默认入参：自由键值对 -->
      <el-form-item label="默认输入">
        <JsonKeyValueEditor v-model="formData.defaultParams" />
        <div class="field-tip">节点引用这一步时会先按这些默认参数起手，再用参数映射覆盖同名键。__comments__ 是给人看的说明，运行期不参与入参。</div>
      </el-form-item>
      <!-- 输出声明：键=字段名，值=说明；发布期作为契约与节点输出映射校验 -->
      <el-form-item label="输出声明">
        <JsonKeyValueEditor v-model="formData.outputSchema" mode="desc" />
        <div class="field-tip">声明这一步会往流程上下文里写哪些字段。配了输出映射时，源字段必须落在声明里；留空 = 输出动态，不做契约校验。</div>
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="等待(秒)" prop="timeoutSec">
            <el-input-number v-model="formData.timeoutSec" :min="-1" :max="86400" controls-position="right" class="!w-1/1" />
            <div class="field-tip">-1 = 永不超时，其余 1~86400</div>
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

<style lang="scss" scoped>
.field-tip {
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
  color: #909399;
}
</style>
