<template>
  <Dialog v-model="visible" title="测试接口" width="720px">
    <el-form v-loading="loading" label-width="110px">
      <el-form-item label="动作">
        <span>{{ actionLine }}</span>
      </el-form-item>
      <el-form-item label="目标设备" required>
        <el-select v-model="deviceId" placeholder="选择一台 HTTP 设备" class="!w-1/1" filterable>
          <el-option
            v-for="item in devices"
            :key="item.id"
            :label="deviceOptionLabel(item)"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <template v-if="fields.length">
        <el-form-item v-for="field in fields" :key="field.key" :label="field.label" :required="field.required">
          <el-select
            v-if="enumOptions(field).length"
            v-model="values[field.key]"
            clearable
            class="!w-1/1"
            :placeholder="field.required ? '请选择' : '可选'"
          >
            <el-option v-for="option in enumOptions(field)" :key="option" :label="option" :value="option" />
          </el-select>
          <el-switch v-else-if="field.type === 'BOOLEAN'" v-model="values[field.key]" />
          <el-input-number
            v-else-if="field.type === 'INTEGER' || field.type === 'DECIMAL'"
            v-model="values[field.key]"
            controls-position="right"
            class="!w-1/1"
          />
          <el-input v-else v-model="values[field.key]" clearable :placeholder="field.unit ? `单位 ${field.unit}` : ''" />
        </el-form-item>
      </template>
      <el-form-item v-else label="参数">
        <el-input
          v-model="rawParams"
          type="textarea"
          :rows="5"
          placeholder='参数对象，如 {"wavelength":450}。发出去的格式由动作上的报文格式决定'
        />
      </el-form-item>
    </el-form>
    <div v-if="result" class="test-result">
      <div class="test-result__head">
        <el-tag :type="statusType" effect="dark" size="small">HTTP {{ result.status ?? '—' }}</el-tag>
        <span v-if="result.contentType" class="test-result__meta">{{ result.contentType }}</span>
        <el-tag v-if="result.retriedLogin" type="warning" size="small">已重新登录</el-tag>
        <span v-if="result.commandNo" class="test-result__meta">命令 {{ result.commandNo }}</span>
      </div>
      <pre class="test-result__body">{{ prettyBody }}</pre>
    </div>
    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
      <el-button type="primary" :loading="sending" @click="send">测 试</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { parseSchema, SchemaField } from '../labels'

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const sending = ref(false)
const action = ref<DeviceActionVO>()
const devices = ref<DeviceVO[]>([])
const deviceId = ref<number>()
const fields = ref<SchemaField[]>([])
const values = reactive<Record<string, any>>({})
const rawParams = ref('')
const result = ref<{ status?: number; body?: string; contentType?: string; retriedLogin?: boolean; commandNo?: string }>()

const formatNames: Record<string, string> = { JSON: 'JSON', FORM: '表单', TEXT: '纯文本', XML: 'XML' }

const actionLine = computed(() => {
  if (!action.value) return ''
  const method = action.value.httpMethod || 'POST'
  const path = action.value.requestPath || '未配置路径'
  const format = formatNames[action.value.bodyFormat || 'JSON'] || action.value.bodyFormat || 'JSON'
  return `${action.value.actionName}  ${method} ${path}  · ${format}`
})

const statusType = computed(() => {
  const status = result.value?.status
  if (status == null) return 'info'
  if (status >= 200 && status < 300) return 'success'
  if (status >= 300 && status < 400) return 'warning'
  return 'danger'
})

const prettyBody = computed(() => {
  const body = result.value?.body
  if (!body) return '无响应体'
  const type = result.value?.contentType || ''
  if (type.includes('json') || body.trim().startsWith('{') || body.trim().startsWith('[')) {
    try {
      return JSON.stringify(JSON.parse(body), null, 2)
    } catch {
      return body
    }
  }
  return body
})

const deviceOptionLabel = (item: DeviceVO) =>
  `${item.deviceName || item.deviceCode} · ${item.endpointUrl || '未填地址'}`

const enumOptions = (field: SchemaField) => (Array.isArray(field.constraints) ? field.constraints.map(String) : [])

const resetValues = (schema: SchemaField[]) => {
  Object.keys(values).forEach((key) => delete values[key])
  schema.forEach((field) => {
    if (field.type === 'BOOLEAN') {
      values[field.key] = field.defaultValue === true
      return
    }
    values[field.key] = field.defaultValue ?? undefined
  })
}

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  sending.value = false
  result.value = undefined
  deviceId.value = undefined
  rawParams.value = ''
  action.value = undefined
  devices.value = []
  fields.value = []
  try {
    const data = await DeviceActionApi.getAction(id)
    action.value = data
    const schema = parseSchema(data?.paramSchema)
    fields.value = schema
    resetValues(schema)
    const list = await DeviceInfoApi.getDeviceList()
    const rows = Array.isArray(list) ? list : []
    devices.value = rows.filter(
      (item) =>
        item.deviceTypeCode === data?.deviceTypeCode &&
        (item.connectionType === 'HTTP' || item.connectionType === 'NODE_RED')
    )
    if (devices.value.length === 1) {
      deviceId.value = devices.value[0].id
    }
  } finally {
    loading.value = false
  }
}

const buildParams = () => {
  if (!fields.value.length) {
    if (!rawParams.value.trim()) return undefined
    JSON.parse(rawParams.value)
    return rawParams.value
  }
  const payload: Record<string, unknown> = {}
  for (const field of fields.value) {
    const value = values[field.key]
    const empty = value === undefined || value === null || value === ''
    if (empty) {
      if (field.required) {
        throw new Error(`请填写${field.label}`)
      }
      continue
    }
    payload[field.key] = value
  }
  return JSON.stringify(payload)
}

const send = async () => {
  if (!action.value?.actionCode || !deviceId.value) {
    message.warning('请选择目标设备')
    return
  }
  let paramsJson: string | undefined
  try {
    paramsJson = buildParams()
  } catch (error: any) {
    message.warning(error?.message || '参数不正确')
    return
  }
  sending.value = true
  result.value = undefined
  try {
    result.value = await DeviceInfoApi.invoke({
      deviceId: deviceId.value,
      actionCode: action.value.actionCode,
      paramsJson
    })
  } finally {
    sending.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.test-result {
  margin-top: 4px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.test-result__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.test-result__meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.test-result__body {
  margin: 0;
  max-height: 320px;
  padding: 12px 14px;
  overflow: auto;
  background: var(--el-bg-color);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
