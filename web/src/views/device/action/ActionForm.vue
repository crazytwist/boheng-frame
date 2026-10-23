<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceCodecApi, DeviceCodecVO } from '@/api/device/codec'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import ParamSetForm from '../paramSet/ParamSetForm.vue'
import { DeviceParamSetApi, DeviceParamSetVO } from '@/api/device/paramSet'

defineOptions({ name: 'DeviceActionForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): DeviceActionVO => ({
  id: undefined,
  deviceTypeCode: undefined,
  actionCode: undefined,
  actionName: undefined,
  standardFeature: undefined,
  paramSchema: undefined,
  vendorRef: undefined,
  source: 'MANUAL',
  estimateDurationMs: undefined,
  needBusyCheck: false,
  statusCommandCode: undefined,
  httpMethod: 'POST',
  bodyFormat: 'JSON',
  requestPath: undefined,
  pollPath: undefined,
  requestTemplate: undefined,
  codecCode: undefined,
  pollDoneExpr: undefined,
  pollTimeoutSec: undefined,
  pollMaxTimes: undefined,
  resultMode: 'SYNC_RETURN',
  callbackTimeoutSec: undefined,
  status: 0
})

const formData = ref<DeviceActionVO>(createEmptyFormData())
const requestTemplateDialog = ref(false)
const paramDefinitionDialog = ref(false)
const paramSetFormRef = ref()
const paramSets = ref<DeviceParamSetVO[]>([])
const paramSetDetail = ref<DeviceParamSetVO>()
const paramSetDetailVisible = ref(false)
const selectedParamSetId = ref<number>()
type ParamDefinitionRow = { key: string; label: string; type: string; unit?: string; required: boolean; defaultValue?: string; constraints?: string }
const paramDefinitionRows = ref<ParamDefinitionRow[]>([])
const codecOptions = ref<DeviceCodecVO[]>([])
const fixedDeviceTypeCode = ref<string>()

const formRules = reactive({
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  actionCode: [{ required: true, message: '动作编码不能为空', trigger: 'blur' }],
  actionName: [{ required: true, message: '动作名称不能为空', trigger: 'blur' }],
  requestTemplate: [
    {
      validator: (_r: unknown, value: string, cb: (e?: Error) => void) => {
        if (formData.value.bodyFormat === 'XML' && !value?.trim())
          return cb(new Error('XML 报文格式下必须填写请求模板'))
        cb()
      },
      trigger: 'blur'
    }
  ],
  pollDoneExpr: [
    {
      validator: (_r: unknown, value: string, cb: (e?: Error) => void) => {
        if (formData.value.resultMode === 'POLL' && !value)
          return cb(new Error('轮询模式下必须填写任务完成条件'))
        cb()
      },
      trigger: 'blur'
    }
  ],
  pollPath: [
    {
      validator: (_r: unknown, value: string, cb: (e?: Error) => void) => {
        if (formData.value.resultMode === 'POLL' && !value?.trim())
          return cb(new Error('轮询模式下必须填写轮询接口路径'))
        cb()
      },
      trigger: 'blur'
    }
  ],
  pollTimeoutSec: [
    {
      validator: (_r: unknown, value: number, cb: (e?: Error) => void) => {
        if (formData.value.resultMode === 'POLL' && !value)
          return cb(new Error('轮询模式下必须填写最大等待时间'))
        cb()
      },
      trigger: 'change'
    }
  ],
  statusCommandCode: [
    {
      validator: (_r: unknown, value: string, cb: (e?: Error) => void) => {
        if (formData.value.needBusyCheck && !value?.trim())
          return cb(new Error('启用空闲检查后必须指定状态查询动作'))
        cb()
      },
      trigger: 'blur'
    }
  ]
})
const formRef = ref()

const deviceTypeOptions = computed(() => getStrDictOptions(DICT_TYPE.DEVICE_TYPE))

const sourceOptions = [
  { label: '手工录入', value: 'MANUAL' },
  { label: '驱动上报', value: 'DEVICE_DECLARED' }
]

const statusOptions = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 }
]

// 参数模式示例（降低填写门槛）
const PARAM_SCHEMA_PLACEHOLDER = `{
  "wavelength": { "label": "主波长", "type": "INTEGER", "unit": "nm", "required": true, "default": 450 },
  "mode": { "label": "检测模式", "type": "ENUM", "constraints": ["ABSORBANCE", "FLUORESCENCE"] }
}`

const resultModeChanged = () => {
  if (formData.value.resultMode !== 'POLL') {
    formData.value.pollDoneExpr = undefined
    formData.value.pollTimeoutSec = undefined
    formData.value.pollPath = undefined
  }
  if (formData.value.resultMode !== 'CALLBACK') formData.value.callbackTimeoutSec = undefined
  formRef.value?.clearValidate(['pollPath', 'pollDoneExpr', 'pollTimeoutSec', 'callbackTimeoutSec'])
}

const bodyFormatChanged = () => formRef.value?.validateField('requestTemplate')

const open = async (
  type: string,
  id?: number,
  context?: {
    deviceTypeCode?: string
    callbackEnabled?: boolean
    pollEnabled?: boolean
    callbackTimeoutSec?: number
  }
) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  paramDefinitionRows.value = []
  fixedDeviceTypeCode.value = context?.deviceTypeCode
  if (fixedDeviceTypeCode.value) formData.value.deviceTypeCode = fixedDeviceTypeCode.value
  if (type === 'create' && context) {
    formData.value.resultMode = context.callbackEnabled
      ? 'CALLBACK'
      : context.pollEnabled
        ? 'POLL'
        : 'SYNC_RETURN'
    formData.value.callbackTimeoutSec = context.callbackTimeoutSec
  }
  formRef.value?.clearValidate()
  DeviceCodecApi.getCodecList()
    .then((list) => {
      codecOptions.value = Array.isArray(list) ? list : []
    })
    .catch(() => {
      codecOptions.value = []
    })
  if (id) {
    formLoading.value = true
    try {
      const data = await DeviceActionApi.getAction(id)
      Object.assign(formData.value, data)
      loadParamDefinitionRows()
      await loadParamSets()
      loadParamDefinitionRows()
      if (fixedDeviceTypeCode.value) formData.value.deviceTypeCode = fixedDeviceTypeCode.value
      if (!formData.value.bodyFormat) formData.value.bodyFormat = 'JSON'
      if (!formData.value.resultMode) formData.value.resultMode = 'SYNC_RETURN'
    } finally {
      formLoading.value = false
    }
  }
}

const loadParamDefinitionRows = () => {
  try {
    const obj = formData.value.paramSchema ? JSON.parse(formData.value.paramSchema) : {}
    paramDefinitionRows.value = Object.entries(obj).map(([key, raw]: [string, any]) => ({
      key, label: raw?.label || key, type: raw?.type || 'STRING', unit: raw?.unit,
      required: !!raw?.required,
      defaultValue: raw?.defaultValue ?? raw?.default,
      constraints: Array.isArray(raw?.constraints) ? raw.constraints.join(', ') : raw?.constraints
    }))
  } catch { paramDefinitionRows.value = [] }
}

const syncParamSchema = () => {
  const result: Record<string, any> = {}
  for (const row of paramDefinitionRows.value) {
    const key = row.key?.trim()
    if (!key) continue
    const item: Record<string, any> = { label: row.label?.trim() || key, type: row.type || 'STRING', required: !!row.required }
    if (row.unit?.trim()) item.unit = row.unit.trim()
    if (row.defaultValue !== undefined && row.defaultValue !== '') item.default = row.defaultValue
    if (row.constraints?.trim()) item.constraints = row.constraints.split(',').map((v) => v.trim()).filter(Boolean)
    result[key] = item
  }
  formData.value.paramSchema = Object.keys(result).length ? JSON.stringify(result, null, 2) : undefined
}

const addParamDefinition = () => paramDefinitionRows.value.push({ key: '', label: '', type: 'STRING', required: false })
const removeParamDefinition = (index: number) => { paramDefinitionRows.value.splice(index, 1); syncParamSchema() }

const loadParamSets = async () => {
  if (!formData.value.deviceTypeCode || !formData.value.actionCode) { paramSets.value = []; return }
  paramSets.value = await DeviceParamSetApi.getParamSetList({ deviceTypeCode: formData.value.deviceTypeCode, actionCode: formData.value.actionCode }) || []
  selectedParamSetId.value = paramSets.value[0]?.id
}
const showParamSetDetail = (item: DeviceParamSetVO) => { paramSetDetail.value = item; selectedParamSetId.value = item.id; paramSetDetailVisible.value = true }
const openParamSetConfig = () => {
  const existing = paramSets.value[0]
  if (existing?.id) showParamSetDetail(existing)
  else paramSetFormRef.value?.open('create', undefined, { deviceTypeCode: formData.value.deviceTypeCode, actionCode: formData.value.actionCode })
}
const handleParamSetDetailChange = async (id: number) => {
  paramSetDetail.value = await DeviceParamSetApi.getParamSet(id)
}

// 校验并美化 JSON（参数模式）
const formatParamSchema = () => {
  if (!formData.value.paramSchema) return
  try {
    formData.value.paramSchema = JSON.stringify(JSON.parse(formData.value.paramSchema), null, 2)
  } catch {
    message.error('参数定义不是合法的 JSON')
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 参数模式若填写，必须是合法 JSON
  if (formData.value.paramSchema) {
    try {
      JSON.parse(formData.value.paramSchema)
    } catch {
      message.error('参数定义不是合法的 JSON，请检查后重试')
      return
    }
  }
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await DeviceActionApi.createAction(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await DeviceActionApi.updateAction(formData.value)
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
      label-width="120px"
    >
      <el-alert
        title="按顺序完成：先定义参数，再设置默认值，最后配置设备请求模板"
        description="参数定义决定允许传入什么；参数集提供可复用的默认值；请求模板负责把参数转换成设备报文。"
        type="info"
        :closable="false"
        show-icon
        class="setup-guide"
      />
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceTypeCode">
            <el-select
              v-model="formData.deviceTypeCode"
              placeholder="请选择设备类型"
              :disabled="!!fixedDeviceTypeCode"
              class="!w-1/1"
            >
              <el-option
                v-for="item in deviceTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="formData.status">
              <el-radio v-for="item in statusOptions" :key="item.value" :value="item.value">{{
                item.label
              }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="动作编码" prop="actionCode">
            <el-input
              v-model="formData.actionCode"
              maxlength="64"
              placeholder="请输入动作编码，如 READ_PLATE"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="动作名称" prop="actionName">
            <el-input
              v-model="formData.actionName"
              maxlength="128"
              placeholder="请输入动作名称，如 读板"
            />
          </el-form-item>
        </el-col>
        <el-col v-if="false" :span="12">
          <el-form-item label="能力来源">
            <el-select v-model="formData.source" placeholder="请选择能力来源" class="!w-1/1">
              <el-option
                v-for="item in sourceOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col v-if="false" :span="12">
          <el-form-item label="预估耗时(毫秒)">
            <el-input-number
              v-model="formData.estimateDurationMs"
              :min="0"
              :step="1000"
              controls-position="right"
              class="!w-1/1"
              placeholder="小于 3000 方可同步下发"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item v-if="false" label="SiLA 标准特性">
        <el-input
          v-model="formData.standardFeature"
          maxlength="128"
          placeholder="如 com.sila_standard.run_control；私有动作无需填写"
        />
      </el-form-item>
      <el-form-item v-if="false" label="厂商映射">
        <el-input
          v-model="formData.vendorRef"
          maxlength="255"
          placeholder="厂商侧对应的脚本名或方法名"
        />
      </el-form-item>

      <el-divider content-position="left">请求配置</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="请求方法">
            <el-select v-model="formData.httpMethod" placeholder="请选择" clearable class="!w-1/1">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="PATCH" value="PATCH" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="请求路径" label-width="90px">
            <el-input
              v-model="formData.requestPath"
              maxlength="255"
              placeholder="相对设备接入地址的路径，如 /api/read"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="报文格式">
            <el-select v-model="formData.bodyFormat" class="!w-1/1" @change="bodyFormatChanged">
              <el-option label="JSON" value="JSON" />
              <el-option label="表单" value="FORM" />
              <el-option label="纯文本" value="TEXT" />
              <el-option label="XML" value="XML" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">请求配置</el-divider>
      <el-form-item label="请求模板" prop="requestTemplate">
        <el-button type="primary" plain @click="requestTemplateDialog = true">配置请求模板</el-button>
        <span class="field-tip inline-tip">{{ formData.requestTemplate ? '已配置请求模板' : '尚未配置' }}</span>
      </el-form-item>
      <el-dialog v-model="requestTemplateDialog" title="配置请求模板" width="720px" append-to-body>
        <el-input v-model="formData.requestTemplate" type="textarea" :rows="10" placeholder="使用 ${参数名} 引用参数；XML 报文格式下必须填写" />
        <div class="field-tip">可用参数示例：${sample.id}、${temperature}。请求模板只负责把参数转换为设备报文。</div>
      </el-dialog>
      <el-divider content-position="left">参数定义</el-divider>
      <el-form-item label="参数定义" class="compact-config-item">
        <el-button type="primary" plain @click="paramDefinitionDialog = true">配置参数定义</el-button>
        <span class="field-tip inline-tip">{{ paramDefinitionRows.length ? `已配置 ${paramDefinitionRows.length} 个参数` : '尚未配置' }}</span>
      </el-form-item>
      <el-dialog v-model="paramDefinitionDialog" title="配置参数定义" width="980px" append-to-body>
        <div class="param-definition-editor">
          <div class="section-intro"><strong>定义动作允许接收哪些输入</strong><span>参数集、流程节点和调试页面都会依据这里生成输入项。</span></div>
          <div v-for="(row, index) in paramDefinitionRows" :key="index" class="param-definition-row">
            <el-input v-model="row.key" placeholder="参数路径，如 sample.id" @change="syncParamSchema" />
            <el-input v-model="row.label" placeholder="显示名称，如 样本编号" @change="syncParamSchema" />
            <el-select v-model="row.type" placeholder="类型" @change="syncParamSchema">
              <el-option label="字符串" value="STRING" /><el-option label="整数" value="INTEGER" />
              <el-option label="小数" value="DECIMAL" /><el-option label="布尔" value="BOOLEAN" />
              <el-option label="对象" value="OBJECT" /><el-option label="数组" value="ARRAY" />
            </el-select>
            <el-input v-model="row.defaultValue" placeholder="默认值（可选）" @change="syncParamSchema" />
            <el-checkbox v-model="row.required" @change="syncParamSchema">必填</el-checkbox>
            <el-button link type="danger" @click="removeParamDefinition(index)">删除</el-button>
          </div>
          <el-button link type="primary" @click="addParamDefinition">+ 添加参数</el-button>
          <div class="json-editor-tip">复杂对象或数组可在下方高级 JSON 中补充；保存时系统会自动生成参数定义。</div>
          <el-input v-model="formData.paramSchema" type="textarea" :rows="4" :placeholder="PARAM_SCHEMA_PLACEHOLDER" @change="loadParamDefinitionRows" />
          <el-button link type="primary" @click="formatParamSchema">格式化高级 JSON</el-button>
        </div>
      </el-dialog>
      <el-form-item label="参数集" class="compact-config-item">
        <el-button type="primary" plain :disabled="!formData.deviceTypeCode || !formData.actionCode" @click="openParamSetConfig">配置参数集</el-button>
        <span class="field-tip inline-tip">{{ paramSets.length ? `已配置 ${paramSets.length} 个参数集` : '尚未配置参数集' }}</span>
      </el-form-item>
      <el-dialog v-model="paramSetDetailVisible" title="参数集详情" width="880px" append-to-body>
        <el-form v-if="paramSetDetail" label-width="105px">
          <el-divider content-position="left">基本信息</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="参数集编码">
                <el-select v-model="selectedParamSetId" class="!w-1/1" @change="handleParamSetDetailChange">
                  <el-option v-for="item in paramSets" :key="item.id" :label="`${item.paramSetCode}（${item.paramSetName}）`" :value="item.id!" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12"><el-form-item label="参数集名称"><el-input :model-value="paramSetDetail.paramSetName" disabled /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="设备类型"><el-input :model-value="paramSetDetail.deviceTypeCode" disabled /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="关联动作"><el-input :model-value="paramSetDetail.actionCode" disabled /></el-form-item></el-col>
          </el-row>
          <el-divider content-position="left">参数配置</el-divider>
          <el-form-item label="参数取值"><pre class="param-set-json">{{ paramSetDetail.paramsJson || '{}' }}</pre></el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="paramSetDetailVisible = false">关 闭</el-button>
          <el-button type="primary" @click="paramSetFormRef?.open('update', paramSetDetail.id)">编 辑</el-button>
        </template>
      </el-dialog>

      <el-divider content-position="left">结果处理</el-divider>
      <el-form-item label="结果获取方式">
        <el-radio-group v-model="formData.resultMode" @change="resultModeChanged">
          <el-radio-button value="SYNC_RETURN">同步返回</el-radio-button>
          <el-radio-button value="CALLBACK">设备回调</el-radio-button>
          <el-radio-button value="POLL">平台轮询</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="formData.resultMode === 'CALLBACK'">
        <el-form-item label="回调超时(秒)">
          <el-input-number
            v-model="formData.callbackTimeoutSec"
            :min="1"
            :max="600"
            controls-position="right"
            placeholder="为空时取设备配置"
          />
          <span class="form-tip">最长 600 秒</span>
        </el-form-item>
        <el-alert class="!mb-18px" type="info" :closable="false" show-icon>
          <template #title>
            设备收到请求后先返回“已受理”，执行完成后将结果推送至平台回调地址，平台收到回调后命令才算完成。
            使用此方式前，请在设备台账中开启“结果回调”。
          </template>
        </el-alert>
      </template>
      <el-form-item label="解析规则">
        <el-select
          v-model="formData.codecCode"
          clearable
          filterable
          placeholder="选填，调用成功后按此规则解析测量数据"
          class="!w-1/1"
        >
          <el-option
            v-for="item in codecOptions"
            :key="item.codecCode"
            :label="`${item.codecName} (${item.codecCode})`"
            :value="item.codecCode!"
          />
        </el-select>
      </el-form-item>
      <template v-if="formData.resultMode === 'POLL'">
        <el-form-item label="轮询接口路径" prop="pollPath">
          <el-input
            v-model="formData.pollPath"
            placeholder="相对设备接入地址，如 /api/measure/status"
          />
          <span class="form-tip">平台按设备轮询间隔调用此接口获取任务状态</span>
        </el-form-item>
        <el-row :gutter="20" class="poll-config-row">
          <el-col :span="12">
            <el-form-item label="任务完成条件" prop="pollDoneExpr">
              <el-input
                v-model="formData.pollDoneExpr"
                maxlength="255"
                placeholder='轮询时使用，如 $.status == "DONE"'
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最大等待时间(秒)" label-width="140px" prop="pollTimeoutSec">
              <el-input-number
                v-model="formData.pollTimeoutSec"
                :min="1"
                :max="86400"
                controls-position="right"
                class="!w-1/1"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-alert class="!mb-18px" type="info" :closable="false" show-icon>
          <template #title
            >系统会根据设备轮询间隔自动计算最大查询次数，命令详情中仍会展示实际查询次数。</template
          >
        </el-alert>
      </template>

      <el-divider content-position="left">下发前检查</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="空闲检查">
            <el-switch
              v-model="formData.needBusyCheck"
              active-text="需要"
              inactive-text="不需要"
              @change="() => formRef?.clearValidate('statusCommandCode')"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态查询动作" prop="statusCommandCode">
            <el-input
              v-model="formData.statusCommandCode"
              maxlength="64"
              placeholder="如 GET_STATUS，启用空闲检查时必填"
              :disabled="!formData.needBusyCheck"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
  <ParamSetForm ref="paramSetFormRef" />
</template>

<style lang="scss" scoped>
.json-editor-wrap {
  width: 100%;
}
.poll-config-row :deep(.el-form-item__label) {
  white-space: nowrap;
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 32px;
  color: var(--el-text-color-secondary);
}
.json-editor-tip {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 24px;
}

.param-definition-editor {
  width: 100%;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-lighter);
}
.setup-guide { margin-bottom: 18px; }
.section-intro { display: flex; flex-direction: column; gap: 4px; margin-bottom: 12px; color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.5; }
.section-intro strong { color: var(--el-text-color-primary); font-size: 14px; }
.inline-tip { display: inline-block; margin-left: 10px; }
.param-set-summary { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
.param-set-tag { cursor: pointer; }
.param-set-manager { display: flex; gap: 12px; align-items: center; }
.param-set-json { max-height: 520px; overflow: auto; margin: 0; padding: 16px; border-radius: 6px; background: #f6f8fa; white-space: pre-wrap; font-family: monospace; }
:deep(.compact-config-item) { display: inline-flex; width: 49%; vertical-align: top; box-sizing: border-box; }
.param-definition-row {
  display: grid;
  grid-template-columns: 1.25fr 1.1fr 0.9fr 1fr auto auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
}
</style>
