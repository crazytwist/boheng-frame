<template>
  <Dialog v-model="visible" title="动作调试" width="720px">
    <el-form v-loading="loading" label-width="110px">
      <el-form-item label="设备动作">
        <span>{{ actionLine }}</span>
      </el-form-item>
      <el-form-item label="目标设备" required>
        <el-select v-model="deviceId" placeholder="请选择 HTTP 接入或仿真设备" class="!w-1/1" filterable>
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
            :placeholder="field.required ? '请选择' : '选填'"
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
          <el-input v-else v-model="values[field.key]" clearable :placeholder="field.unit ? `单位：${field.unit}` : '请输入'" />
        </el-form-item>
      </template>
      <el-form-item v-else label="调用参数">
        <el-input
          v-model="rawParams"
          type="textarea"
          :rows="5"
          placeholder='JSON 对象，如 {"wavelength":450}；实际发送格式由动作的报文格式决定'
        />
      </el-form-item>
    </el-form>
    <el-alert
      v-if="sending"
      type="info"
      :closable="false"
      show-icon
      class="test-waiting"
      :title="waitingText"
    />
    <div v-if="result" class="test-result">
      <div class="test-result__head">
        <el-tag :type="statusType" effect="dark" size="small">HTTP {{ result.status ?? '—' }}</el-tag>
        <el-tag v-if="result.commandStatus === 'WAITING'" type="warning" size="small">等待设备回调</el-tag>
        <span v-if="result.contentType" class="test-result__meta">{{ result.contentType }}</span>
        <el-tag v-if="result.retriedLogin" type="warning" size="small">已重新登录</el-tag>
        <span v-if="result.commandNo" class="test-result__meta">命令编号：{{ result.commandNo }}</span>
      </div>
      <pre class="test-result__body">{{ prettyBody }}</pre>
    </div>
    <template #footer>
      <el-button @click="visible = false">关 闭</el-button>
      <el-button type="primary" :loading="sending" @click="send">发 送</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceInfoApi, DeviceInvokeResult, DeviceVO } from '@/api/device'
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
const result = ref<DeviceInvokeResult>()

const formatNames: Record<string, string> = { JSON: 'JSON', FORM: '表单', TEXT: '纯文本', XML: 'XML' }

const actionLine = computed(() => {
  if (!action.value) return ''
  const method = action.value.httpMethod || 'POST'
  const path = action.value.requestPath || '未配置请求路径'
  const format = formatNames[action.value.bodyFormat || 'JSON'] || action.value.bodyFormat || 'JSON'
  return `${action.value.actionName}  ${method} ${path}  · ${format}`
})

const elapsed = ref(0)
let elapsedTimer: ReturnType<typeof setInterval> | undefined

const waitingText = computed(() => {
  const head =
    action.value?.resultMode === 'CALLBACK'
      ? '正在向设备下发回调命令'
      : '正在等待设备返回结果，回调或轮询方式耗时较长'
  return `${head}（已等待 ${elapsed.value} 秒）`
})

const startElapsed = () => {
  elapsed.value = 0
  stopElapsed()
  elapsedTimer = setInterval(() => elapsed.value++, 1000)
}

const stopElapsed = () => {
  if (elapsedTimer) clearInterval(elapsedTimer)
  elapsedTimer = undefined
}

onBeforeUnmount(stopElapsed)

const statusType = computed(() => {
  const status = result.value?.status
  if (status == null) return 'info'
  if (status >= 200 && status < 300) return 'success'
  if (status >= 300 && status < 400) return 'warning'
  return 'danger'
})

const prettyBody = computed(() => {
  if (result.value?.commandStatus === 'WAITING') {
    return '设备已受理命令，执行结果将通过 callback 写入命令记录。可以关闭当前窗口。'
  }
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

const isSimulated = (item: DeviceVO) =>
  item.connectionType === 'SIMULATED' || item.driverType === 'SIMULATED' || Boolean(item.simulationMode)

const deviceOptionLabel = (item: DeviceVO) =>
  `${item.deviceName || item.deviceCode} · ${isSimulated(item) ? '仿真' : item.endpointUrl || '未配置接入地址'}`

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

const open = async (id: number, presetDeviceId?: number) => {
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
        (item.connectionType === 'HTTP' || item.connectionType === 'NODE_RED' || isSimulated(item) ||
          item.id === presetDeviceId)
    )
    if (presetDeviceId && devices.value.some((item) => item.id === presetDeviceId)) {
      deviceId.value = presetDeviceId
    } else if (devices.value.length === 1) {
      deviceId.value = devices.value[0].id
    }
  } finally {
    loading.value = false
  }
}

const buildParams = () => {
  if (!fields.value.length) {
    if (!rawParams.value.trim()) return undefined
    try {
      JSON.parse(rawParams.value)
    } catch {
      throw new Error('调用参数不是合法的 JSON')
    }
    return rawParams.value
  }
  const payload: Record<string, unknown> = {}
  for (const field of fields.value) {
    const value = values[field.key]
    const empty = value === undefined || value === null || value === ''
    if (empty) {
      if (field.required) {
        throw new Error(`${field.label}不能为空`)
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
    message.warning(error?.message || '调用参数不正确')
    return
  }
  sending.value = true
  result.value = undefined
  startElapsed()
  try {
    result.value = await DeviceInfoApi.invoke({
      deviceId: deviceId.value,
      actionCode: action.value.actionCode,
      paramsJson
    })
  } finally {
    sending.value = false
    stopElapsed()
  }
}

defineExpose({ open })
</script>

<style scoped>
.test-waiting {
  margin-bottom: 8px;
}
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
