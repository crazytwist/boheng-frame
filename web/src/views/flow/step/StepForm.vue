<script lang="ts" setup>
import { FlowStepApi, FlowStepVO, STEP_TYPES } from '@/api/flow/step'
import { STEP_TYPE_FIELDS } from '@/api/flow/stepSchema'
import SchemaForm from '@/components/flow/SchemaForm.vue'
import JsonKeyValueEditor from '@/components/flow/JsonKeyValueEditor.vue'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceParamSetApi, DeviceParamSetVO } from '@/api/device/paramSet'

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
    {
      pattern: /^[a-z][a-z0-9_]{0,31}$/,
      message: '小写字母开头，只含小写、数字和下划线',
      trigger: 'blur'
    }
  ],
  name: [{ required: true, message: '步骤名称不能为空', trigger: 'blur' }],
  stepType: [{ required: true, message: '步骤类型不能为空', trigger: 'change' }],
  timeoutSec: [
    { required: true, message: '等待时间不能为空', trigger: 'change' },
    {
      validator: (_r, v, cb) =>
        v === -1 || (v >= 1 && v <= 86400)
          ? cb()
          : cb(new Error('等待秒数须为 -1（永不超时）或 1~86400')),
      trigger: 'change'
    }
  ],
  maxAttempts: [{ required: true, message: '尝试次数不能为空', trigger: 'change' }]
})
const formRef = ref()

const isDeviceCommand = computed(() => formData.value.stepType === 'device.command')
const isDeviceTask = computed(() => formData.value.stepType === 'device.task')

/** 新增步骤自动生成一个可持久化、符合编码规则的默认编码；用户仍可手动修改。 */
const generateStepKey = () => `step_${Date.now().toString(36)}`
const devices = ref<DeviceVO[]>([])
const paramSets = ref<DeviceParamSetVO[]>([])
const selectedAction = ref<DeviceActionVO>()
const selectedParamSetId = ref<number | undefined>()
const selectedParamSet = computed(() =>
  paramSets.value.find((item) => String(item.id) === String(selectedParamSetId.value))
)
const selectedTaskDevice = computed(() => {
  const config = parseConfig(formData.value.typeConfig)
  return devices.value.find((item) => String(item.deviceCode) === String(config.deviceCode))
})
const selectedTaskMode = computed(() =>
  String(selectedTaskDevice.value?.taskMode || '').toUpperCase()
)
const isPullDevice = computed(() => {
  const config = parseConfig(formData.value.typeConfig)
  // PULL_TASK 是 PULL 模式的系统任务编码；即使旧接口暂未返回 taskMode，也要保证页面联动。
  return ['PULL', 'PULL_TASK'].includes(selectedTaskMode.value) || config.actionCode === 'PULL_TASK'
})
const isPullTask = computed(() => isDeviceTask.value && isPullDevice.value)

const actionParamSuggestions = computed(() => {
  if (!selectedAction.value?.paramSchema) return []
  try {
    const schema = JSON.parse(selectedAction.value.paramSchema) as Record<string, any>
    return Object.entries(schema).map(([key, raw]) => {
      const definition = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : {}
      const defaultValue = Object.prototype.hasOwnProperty.call(definition, 'default')
        ? definition.default
        : raw && typeof raw === 'object'
          ? undefined
          : raw
      const typeMap: Record<string, string> = {
        INTEGER: 'number',
        DECIMAL: 'number',
        BOOLEAN: 'boolean',
        OBJECT: 'object',
        JSON: 'object',
        ARRAY: 'array'
      }
      const notes = [definition.label || key, definition.required ? '必填' : '', definition.unit]
        .filter(Boolean)
        .join(' · ')
      return {
        key,
        type: typeMap[String(definition.type || '').toUpperCase()] || undefined,
        value: defaultValue,
        comment: notes
      }
    })
  } catch {
    return []
  }
})

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

const updateConfigField = (key: string, value?: any) => {
  const config = parseConfig(formData.value.typeConfig)
  if (value === undefined || value === '') delete config[key]
  else config[key] = value
  formData.value.typeConfig = Object.keys(config).length ? JSON.stringify(config) : ''
}

const loadParamSets = async () => {
  paramSets.value = []
  selectedAction.value = undefined
  selectedParamSetId.value = undefined
  if (isPullTask.value) {
    return
  }
  if (!isDeviceCommand.value && !isDeviceTask.value) return
  const config = parseConfig(formData.value.typeConfig)
  if (!config.deviceCode || !config.actionCode) return
  const device = devices.value.find((item) => String(item.deviceCode) === String(config.deviceCode))
  if (!device?.deviceTypeCode) return
  const actions = await DeviceActionApi.getActionList({ deviceTypeCode: device.deviceTypeCode })
  selectedAction.value = (actions || []).find(
    (item: DeviceActionVO) => String(item.actionCode) === String(config.actionCode)
  )
  const list = await DeviceParamSetApi.getParamSetList({
    deviceTypeCode: device.deviceTypeCode,
    actionCode: config.actionCode
  })
  paramSets.value = (list || []).filter(
    (item: DeviceParamSetVO) => item.validated !== false && item.status !== 1
  )
  const configuredId =
    config.paramSetId === undefined || config.paramSetId === null || config.paramSetId === ''
      ? undefined
      : Number(config.paramSetId)
  selectedParamSetId.value = Number.isFinite(configuredId) ? configuredId : undefined
  // 编辑历史步骤时，即使参数集后来被停用/撤销验证，也保留当前绑定项，避免下拉框回显为空。
  if (
    selectedParamSetId.value &&
    !paramSets.value.some((item) => String(item.id) === String(selectedParamSetId.value))
  ) {
    const configured = await DeviceParamSetApi.getParamSet(selectedParamSetId.value)
    if (
      configured &&
      String(configured.deviceTypeCode) === String(device.deviceTypeCode) &&
      String(configured.actionCode) === String(config.actionCode)
    ) {
      paramSets.value = [configured, ...paramSets.value]
    }
  }
}

const onConfigChanged = async (value: string) => {
  formData.value.typeConfig = value
  // PULL 设备不能配置成“设备动作”，选择设备后自动切换到异步设备任务。
  if (isPullDevice.value && isDeviceCommand.value) {
    formData.value.stepType = 'device.task'
  }
  if (isPullTask.value) {
    updateConfigField('paramSetId', undefined)
    selectedParamSetId.value = undefined
  }
  if (isDeviceCommand.value || isDeviceTask.value) await loadParamSets()
}

const onParamSetChange = (value?: number) => {
  selectedParamSetId.value = value
  updateConfigField('paramSetId', value)
}

/** 切换类型时清掉旧配置 */
const onTypeChange = () => {
  if (isPullDevice.value && isDeviceCommand.value) {
    formData.value.stepType = 'device.task'
    return
  }
  formData.value.typeConfig = ''
  selectedParamSetId.value = undefined
  paramSets.value = []
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmpty()
  if (!id) formData.value.stepKey = generateStepKey()
  if (!devices.value.length) devices.value = (await DeviceInfoApi.getDeviceList()) || []
  formRef.value?.clearValidate()
  if (!id) return
  formLoading.value = true
  try {
    formData.value = await FlowStepApi.get(id)
    selectedParamSetId.value = parseConfig(formData.value.typeConfig).paramSetId
      ? Number(parseConfig(formData.value.typeConfig).paramSetId)
      : undefined
    await loadParamSets()
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
  if (isDeviceTask.value && !isPullTask.value && !selectedParamSetId.value) {
    message.error('PUSH 设备任务必须绑定参数集')
    return
  }
  if (isPullTask.value) updateConfigField('paramSetId', undefined)
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
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="步骤编码" prop="stepKey">
        <el-input
          v-model="formData.stepKey"
          maxlength="32"
          placeholder="系统自动生成，也可手动修改"
          :disabled="formType !== 'create'"
        >
          <template #append>
            <el-button v-if="formType === 'create'" @click="formData.stepKey = generateStepKey()"
              >重新生成</el-button
            >
          </template>
        </el-input>
        <div class="field-tip">系统已自动生成步骤编码；编码用于流程内部引用，创建后不可修改。</div>
      </el-form-item>
      <el-form-item label="步骤名称" prop="name">
        <el-input v-model="formData.name" maxlength="128" placeholder="请输入步骤名称，如 离心" />
      </el-form-item>
      <el-form-item label="步骤类型" prop="stepType">
        <el-select
          v-model="formData.stepType"
          placeholder="请选择步骤类型"
          class="!w-1/1"
          :disabled="formType !== 'create'"
          @change="onTypeChange"
        >
          <el-option
            v-for="item in STEP_TYPES"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <!-- 类型配置：按类型字段表动态渲染，收进 typeConfig JSON -->
      <template v-if="typeFields.length">
        <SchemaForm
          :fields="typeFields"
          v-model="formData.typeConfig"
          @update:model-value="onConfigChanged"
        />
      </template>
      <el-form-item v-else label="类型配置">
        <el-input
          v-model="formData.typeConfig"
          type="textarea"
          :rows="3"
          maxlength="4096"
          placeholder="类型专属配置 JSON"
        />
      </el-form-item>
      <template v-if="(isDeviceCommand || isDeviceTask) && !isPullTask">
        <el-form-item :label="isDeviceTask ? '任务参数集' : '参数集'" :required="isDeviceTask">
          <el-select
            v-model="selectedParamSetId"
            class="!w-1/1"
            clearable
            filterable
            :disabled="!paramSets.length"
            :placeholder="paramSets.length ? '请选择已验证参数集' : '请先选择设备和任务类型'"
            @change="onParamSetChange"
          >
            <el-option
              v-for="item in paramSets"
              :key="item.id"
              :label="`${item.paramSetName}（${item.paramSetCode}）`"
              :value="Number(item.id)"
            />
          </el-select>
          <div class="field-tip">
            {{
              isDeviceTask
                ? '任务参数集按设备类型和任务类型筛选；流程映射和默认输入可覆盖参数集默认值。'
                : '参数集按设备类型和动作自动筛选，仅显示已验证且启用的参数集；运行时会作为基础参数。'
            }}
            <span v-if="selectedParamSet">当前：{{ selectedParamSet.paramSetName }}</span>
          </div>
        </el-form-item>
      </template>
      <!-- PULL 也需要在步骤定义中声明入参和默认值；节点编排时可覆盖这些值。 -->
      <el-form-item v-if="isPullTask" label="任务入参">
        <JsonKeyValueEditor v-model="formData.defaultParams" />
        <div class="field-tip"
          >在这里定义任务参数和默认值；流程节点可通过参数映射覆盖。此处不关联参数定义或参数集。</div
        >
      </el-form-item>
      <!-- PUSH 默认入参：自由键值对 -->
      <el-form-item v-else label="默认输入">
        <div class="w-1/1">
          <el-alert
            v-if="actionParamSuggestions.length"
            :title="`已读取动作输入契约：${actionParamSuggestions.length} 个参数`"
            description="可按参考字段补齐；只添加缺失项，不覆盖当前内容。参数集仍是基础值，这里只配置步骤级默认覆盖。"
            type="info"
            :closable="false"
            class="mb-8px"
            show-icon
          />
          <JsonKeyValueEditor
            v-model="formData.defaultParams"
            :suggestions="actionParamSuggestions"
          />
          <div class="field-tip">
            执行顺序：参数集基础值 → 步骤默认覆盖 → 节点参数覆盖；__comments__ 仅用于界面说明。
          </div>
        </div>
      </el-form-item>
      <!-- 输出声明：键=字段名，值=说明；发布期作为契约与节点输出映射校验 -->
      <el-form-item v-if="!isPullTask" label="输出声明">
        <JsonKeyValueEditor v-model="formData.outputSchema" mode="desc" />
        <div class="field-tip"
          >声明这一步会往流程上下文里写哪些字段。配了输出映射时，源字段必须落在声明里；留空 =
          输出动态，不做契约校验。</div
        >
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="等待(秒)" prop="timeoutSec">
            <el-input-number
              v-model="formData.timeoutSec"
              :min="-1"
              :max="86400"
              controls-position="right"
              class="!w-1/1"
            />
            <div class="field-tip">-1 = 永不超时，其余 1~86400</div>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="尝试次数" prop="maxAttempts">
            <el-input-number
              v-model="formData.maxAttempts"
              :min="1"
              :max="5"
              controls-position="right"
              class="!w-1/1"
            />
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
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="2"
          maxlength="512"
          placeholder="选填"
        />
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
