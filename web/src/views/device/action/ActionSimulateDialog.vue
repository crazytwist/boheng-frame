<template>
  <Dialog v-model="visible" :title="title" width="760px">
    <el-form v-loading="loading" :model="form" label-width="110px">
      <el-divider content-position="left">仿真对象</el-divider>
      <el-form-item label="设备动作" :required="pickAction">
        <el-select
          v-if="pickAction"
          v-model="actionId"
          placeholder="请选择需要仿真的设备动作"
          class="!w-1/1"
          filterable
          @change="onActionChange"
        >
          <el-option
            v-for="item in actions"
            :key="item.id"
            :label="`${deviceTypeLabel(item.deviceTypeCode)} · ${item.actionName}（${item.actionCode}）`"
            :value="item.id!"
          />
        </el-select>
        <span v-else>{{ actionLine }}</span>
      </el-form-item>

      <el-form-item label="仿真设备" required>
        <div class="w-1/1">
          <el-select
            v-model="deviceId"
            :placeholder="action ? '请选择同类型设备' : '请先选择设备动作'"
            :disabled="!action"
            class="!w-1/1"
            filterable
            @change="loadProfile"
          >
            <el-option v-for="item in devices" :key="item.id" :label="deviceOptionLabel(item)" :value="item.id!" />
          </el-select>
          <div v-if="action && !devices.length && !loading" class="sim-tip">
            暂无该类型的设备，请先在设备台账中新增。
          </div>
        </div>
      </el-form-item>

      <template v-if="deviceId">
        <el-alert
          :type="profileId ? 'success' : 'info'"
          :closable="false"
          show-icon
          class="sim-alert"
          :title="profileId ? '该设备已有仿真配置，修改保存后立即生效' : '该设备尚无仿真配置，保存后生效'"
          description="启用后，该设备执行此动作时将返回以下配置的报文，不再访问真实设备。"
        />

        <el-divider content-position="left">返回方式</el-divider>
        <el-form-item label="结果返回方式">
          <div class="mode-cards">
            <div
              v-for="item in modeOptions"
              :key="item.value"
              :class="['mode-card', { 'mode-card--active': form.returnMode === item.value }]"
              @click="form.returnMode = item.value"
            >
              <div class="mode-card__title">
                <Icon :icon="item.icon" class="mr-5px" />
                {{ item.label }}
              </div>
              <div class="mode-card__desc">{{ item.desc }}</div>
            </div>
          </div>
        </el-form-item>

        <template v-if="form.returnMode === 'CALLBACK'">
          <el-form-item label="回调延迟">
            <el-input-number v-model="waitSec" :min="0" :max="600" :step="1" :precision="1" controls-position="right" />
            <span class="sim-unit">秒</span>
            <span class="sim-tip sim-tip--inline">先返回“已受理”，延迟到期后将返回报文推送至平台回调地址</span>
          </el-form-item>
          <el-form-item label=" ">
            <div :class="['sim-tip', { 'sim-tip--warn': callbackWarn }]">{{ callbackSummary }}</div>
          </el-form-item>
        </template>

        <template v-if="form.returnMode === 'POLL'">
          <el-form-item label="出结果耗时">
            <el-input-number v-model="waitSec" :min="0" :max="120" :step="1" :precision="1" controls-position="right" />
            <span class="sim-unit">秒</span>
            <span class="sim-tip sim-tip--inline">仿真设备受理请求后产生最终结果所需的时间</span>
          </el-form-item>
          <el-form-item label="轮询间隔">
            <el-input-number v-model="intervalSec" :min="0.1" :max="60" :step="0.5" :precision="1" controls-position="right" />
            <span class="sim-unit">秒</span>
          </el-form-item>
          <el-form-item label="最大轮询次数">
            <el-input-number v-model="form.pollMaxTimes" :min="1" :max="200" controls-position="right" />
            <span class="sim-unit">次</span>
          </el-form-item>
          <el-form-item label=" ">
            <div :class="['sim-tip', { 'sim-tip--warn': pollWarn }]">{{ pollSummary }}</div>
          </el-form-item>
        </template>

        <el-divider content-position="left">报文配置</el-divider>
        <el-form-item label="请求报文">
          <div class="w-1/1">
            <el-input
              v-model="form.requestTemplate"
              type="textarea"
              :rows="4"
              placeholder='选填。为空时使用动作的请求模板，均未配置则原样发送调用参数。使用 ${参数名} 引用参数，如 {"wavelength":"${wavelength}"}'
            />
            <div class="sim-actions">
              <el-button link type="primary" @click="fillRequestFromSchema">按参数定义生成</el-button>
              <el-button link type="primary" :disabled="!action?.requestTemplate" @click="fillRequestFromAction">
                使用动作模板
              </el-button>
              <el-button link type="primary" @click="formatField('requestTemplate')">格式化</el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="返回报文">
          <div class="w-1/1">
            <el-input
              v-model="form.responseBody"
              type="textarea"
              :rows="6"
              :placeholder="responsePlaceholder"
            />
            <div class="sim-actions">
              <el-button link type="primary" @click="fillResponseExample">填入示例</el-button>
              <el-button link type="primary" @click="formatField('responseBody')">格式化</el-button>
            </div>
          </div>
        </el-form-item>

        <el-collapse v-if="form.returnMode !== 'SYNC'" class="sim-collapse">
          <el-collapse-item
            :title="form.returnMode === 'POLL' ? '受理报文与进行中报文（选填，为空时使用默认值）' : '受理报文（选填，为空时使用默认值）'"
            name="poll"
          >
            <el-form-item label="受理报文">
              <el-input
                v-model="form.ackBody"
                type="textarea"
                :rows="2"
                :placeholder="form.returnMode === 'POLL' ? '默认 {&quot;status&quot;:&quot;ACCEPTED&quot;}，自动附带 jobId' : '默认 {&quot;status&quot;:&quot;ACCEPTED&quot;}，收到请求时立即返回'"
              />
            </el-form-item>
            <el-form-item v-if="form.returnMode === 'POLL'" label="进行中报文">
              <el-input v-model="form.pendingBody" type="textarea" :rows="2" placeholder='默认 {"status":"RUNNING"}，结果产生前每次轮询均返回此报文' />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <el-divider content-position="left">状态</el-divider>
        <el-form-item label="启用仿真">
          <el-switch v-model="enabled" active-text="启用" inactive-text="停用" />
          <span class="sim-tip sim-tip--inline">停用后，该设备恢复访问真实地址</span>
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <el-button v-if="profileId && canDelete" type="danger" plain :disabled="saving" @click="remove">删 除</el-button>
      <el-button @click="visible = false">取 消</el-button>
      <template v-if="canSave">
        <el-button :loading="saving" :disabled="!deviceId" @click="save(false)">保 存</el-button>
        <el-button type="primary" :loading="saving" :disabled="!deviceId" @click="save(true)">保存并测试</el-button>
      </template>
      <span v-else-if="deviceId" class="sim-tip sim-tip--inline">
        您没有{{ profileId ? '修改' : '新增' }}仿真配置的权限，仅可查看
      </span>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { DeviceSimulateApi, DeviceSimulateProfileVO } from '@/api/device/simulate'
import { checkPermi } from '@/utils/permission'
import { connectionLabel, deviceTypeLabel, parseSchema } from '../labels'

const emit = defineEmits<{ test: [actionId: number, deviceId: number]; success: [] }>()

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const action = ref<DeviceActionVO>()
const actions = ref<DeviceActionVO[]>([])
const actionId = ref<number>()
const pickAction = ref(false)
const allDevices = ref<DeviceVO[]>([])
const devices = ref<DeviceVO[]>([])
const deviceId = ref<number>()
const profileId = ref<number>()

const canSave = computed(() => checkPermi([profileId.value ? 'device:simulate:update' : 'device:simulate:create']))
const canDelete = computed(() => checkPermi(['device:simulate:delete']))

type ReturnMode = 'SYNC' | 'CALLBACK' | 'POLL'

const modeOptions: Array<{ value: ReturnMode; label: string; icon: string; desc: string }> = [
  { value: 'SYNC', label: '同步返回', icon: 'ep:lightning', desc: '调用后立即返回结果，适用于开门、查询状态等快速动作' },
  { value: 'CALLBACK', label: '设备回调', icon: 'ep:bell', desc: '先返回“已受理”，延迟一段时间后由设备主动推送结果' },
  { value: 'POLL', label: '平台轮询', icon: 'ep:refresh', desc: '先返回“已受理”，平台按间隔查询，直至获得结果' }
]

const createEmpty = (): DeviceSimulateProfileVO => ({
  returnMode: 'SYNC',
  asyncWaitMs: 0,
  pollIntervalMs: 1000,
  pollMaxTimes: 30,
  requestTemplate: undefined,
  responseBody: undefined,
  ackBody: undefined,
  pendingBody: undefined,
  status: 0
})

const form = reactive<DeviceSimulateProfileVO>(createEmpty())

const title = computed(() => (action.value?.actionName ? `仿真配置 · ${action.value.actionName}` : '仿真配置'))

const actionLine = computed(() => {
  if (!action.value) return ''
  return `${action.value.actionName}（${action.value.actionCode}）`
})

const waitSec = computed({
  get: () => (form.asyncWaitMs ?? 0) / 1000,
  set: (v: number | undefined) => {
    form.asyncWaitMs = Math.round((v ?? 0) * 1000)
  }
})

const intervalSec = computed({
  get: () => (form.pollIntervalMs ?? 1000) / 1000,
  set: (v: number | undefined) => {
    form.pollIntervalMs = Math.round((v ?? 1) * 1000)
  }
})

const enabled = computed({
  get: () => form.status === 0,
  set: (v: boolean) => {
    form.status = v ? 0 : 1
  }
})

const pollWarn = computed(() => {
  const interval = form.pollIntervalMs || 1000
  const max = form.pollMaxTimes || 1
  return (form.asyncWaitMs || 0) > interval * max || interval * max > 10 * 60 * 1000
})

const pollSummary = computed(() => {
  const interval = form.pollIntervalMs || 1000
  const max = form.pollMaxTimes || 1
  const total = interval * max
  if (total > 10 * 60 * 1000) {
    return `轮询间隔 ${interval / 1000} 秒 × ${max} 次已超过 10 分钟上限，请减小间隔或次数`
  }
  const wait = form.asyncWaitMs || 0
  const hit = Math.max(1, Math.ceil(wait / interval))
  if (hit > max) {
    return `结果需 ${wait / 1000} 秒产生，但轮询总时长仅 ${(total / 1000).toFixed(1)} 秒，将判定为超时`
  }
  return `预计第 ${hit} 次轮询获得结果（约 ${((hit * interval) / 1000).toFixed(1)} 秒）；轮询 ${max} 次仍无结果则判定为超时`
})

const DEFAULT_CALLBACK_SEC = 300

const callbackLimit = computed(() => {
  if (action.value?.callbackTimeoutSec) {
    return { sec: action.value.callbackTimeoutSec, from: '动作配置的' }
  }
  const device = allDevices.value.find((item) => item.id === deviceId.value)
  if (device?.callbackTimeoutSec) {
    return { sec: device.callbackTimeoutSec, from: '设备台账配置的' }
  }
  return { sec: DEFAULT_CALLBACK_SEC, from: '默认' }
})

const callbackWarn = computed(() => (form.asyncWaitMs || 0) / 1000 >= callbackLimit.value.sec)

const callbackSummary = computed(() => {
  const wait = (form.asyncWaitMs || 0) / 1000
  const { sec, from } = callbackLimit.value
  if (callbackWarn.value) {
    return `平台回调超时为${from} ${sec} 秒，设备回调延迟 ${wait} 秒，将判定为超时（可用于测试超时场景）`
  }
  return `预计 ${wait} 秒后收到回调；平台回调超时为${from} ${sec} 秒，超时未回调将判定为超时`
})

const responsePlaceholder = computed(() =>
  form.returnMode === 'CALLBACK'
    ? '设备回调时推送给平台的内容，可使用 ${参数名}、${commandNo} 占位符；为空时推送 {"status":"DONE"}'
    : '设备最终返回的内容，可使用 ${参数名} 回填请求参数；为空时返回 {"status":"DONE"}'
)

const deviceOptionLabel = (item: DeviceVO) => {
  const name = item.deviceName || item.deviceCode
  return `${name} · ${connectionLabel(item.connectionType)}${item.simulationMode ? ' · 仿真模式' : ''}`
}

const resetForm = (data?: DeviceSimulateProfileVO) => {
  Object.assign(form, createEmpty(), data || {})
  if (form.returnMode === 'ASYNC') form.returnMode = 'CALLBACK'
  profileId.value = data?.id
}

/**
 * 打开弹窗。传了动作就锁定动作；不传则先让用户选动作。传了设备就直接定位到这台设备的配置。
 */
const open = async (presetActionId?: number, presetDeviceId?: number) => {
  visible.value = true
  loading.value = true
  saving.value = false
  action.value = undefined
  actionId.value = presetActionId
  pickAction.value = !presetActionId
  devices.value = []
  deviceId.value = undefined
  resetForm()
  try {
    const list = await DeviceInfoApi.getDeviceList()
    allDevices.value = Array.isArray(list) ? list : []
    if (pickAction.value) {
      const rows = await DeviceActionApi.getActionList()
      actions.value = (Array.isArray(rows) ? rows : []).filter((item) => item.status !== 1)
      return
    }
    await applyAction(await DeviceActionApi.getAction(presetActionId!), presetDeviceId)
  } finally {
    loading.value = false
  }
}

const onActionChange = async () => {
  const picked = actions.value.find((item) => item.id === actionId.value)
  deviceId.value = undefined
  resetForm()
  await applyAction(picked)
}

const applyAction = async (data?: DeviceActionVO, presetDeviceId?: number) => {
  action.value = data
  devices.value = allDevices.value.filter((item) => item.deviceTypeCode === data?.deviceTypeCode)
  const preferred =
    devices.value.find((item) => item.id === presetDeviceId) ||
    devices.value.find((item) => item.connectionType === 'SIMULATED' || item.simulationMode) ||
    (devices.value.length === 1 ? devices.value[0] : undefined)
  if (preferred?.id) {
    deviceId.value = preferred.id
    await loadProfile()
  }
}

const loadProfile = async () => {
  if (!deviceId.value || !action.value?.actionCode) return
  loading.value = true
  try {
    const data = await DeviceSimulateApi.findProfile(deviceId.value, action.value.actionCode)
    resetForm(data)
  } finally {
    loading.value = false
  }
}

const prettyJson = (text?: string) => {
  if (!text?.trim()) return text
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return undefined
  }
}

const formatField = (key: 'requestTemplate' | 'responseBody') => {
  const value = form[key]
  if (!value?.trim()) return
  const pretty = prettyJson(value)
  if (pretty === undefined) {
    message.warning('内容不是 JSON 格式，已保留原文')
    return
  }
  form[key] = pretty
}

const fillRequestFromSchema = () => {
  const fields = parseSchema(action.value?.paramSchema)
  if (!fields.length) {
    message.info('该动作未定义参数，请手动填写')
    return
  }
  const body: Record<string, string> = {}
  fields.forEach((field) => {
    body[field.key] = '${' + field.key + '}'
  })
  form.requestTemplate = JSON.stringify(body, null, 2)
}

const fillRequestFromAction = () => {
  form.requestTemplate = action.value?.requestTemplate
}

const fillResponseExample = () => {
  const fields = parseSchema(action.value?.paramSchema)
  const echo: Record<string, string> = {}
  fields.forEach((field) => {
    echo[field.key] = '${' + field.key + '}'
  })
  const example: Record<string, unknown> = { status: 'DONE', ...echo }
  if (action.value?.deviceTypeCode === 'PLATE_READER') {
    example.data = [
      { well: 'A1', value: 0.42 },
      { well: 'A2', value: 0.38 }
    ]
  } else {
    example.message = 'ok'
  }
  form.responseBody = JSON.stringify(example, null, 2)
}

const validateJsonLike = (label: string, text?: string) => {
  if (!text?.trim()) return true
  const trimmed = text.trim()
  if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) return true
  try {
    JSON.parse(trimmed.replace(/"\$\{[^}]+\}"/g, '""').replace(/\$\{[^}]+\}/g, '0'))
    return true
  } catch {
    message.warning(`${label}的 JSON 格式不正确，请检查括号和引号`)
    return false
  }
}

const save = async (thenTest: boolean) => {
  if (!deviceId.value || !action.value?.actionCode || !action.value.id) {
    message.warning('请选择仿真设备')
    return
  }
  if (form.returnMode === 'POLL' && pollWarn.value) {
    message.warning(pollSummary.value)
    return
  }
  if (!validateJsonLike('请求报文', form.requestTemplate) || !validateJsonLike('返回报文', form.responseBody)) {
    return
  }
  saving.value = true
  try {
    const data: DeviceSimulateProfileVO = {
      ...form,
      id: profileId.value,
      deviceId: deviceId.value,
      actionCode: action.value.actionCode
    }
    if (profileId.value) {
      await DeviceSimulateApi.updateProfile(data)
    } else {
      profileId.value = await DeviceSimulateApi.createProfile(data)
    }
    message.success('保存成功')
    emit('success')
    if (thenTest) {
      visible.value = false
      emit('test', action.value.id, deviceId.value)
    }
  } finally {
    saving.value = false
  }
}

const remove = async () => {
  if (!profileId.value) return
  try {
    await message.confirm('删除后，该设备执行此动作将恢复访问真实地址，是否确认删除？')
  } catch {
    return
  }
  await DeviceSimulateApi.deleteProfile(profileId.value)
  message.success('删除成功')
  resetForm()
  emit('success')
}

defineExpose({ open })
</script>

<style scoped>
.sim-alert {
  margin: 0 0 16px 110px;
  width: calc(100% - 110px);
}
.mode-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  width: 100%;
}
.mode-card {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1.5;
}
.mode-card:hover {
  border-color: var(--el-color-primary-light-5);
}
.mode-card--active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.mode-card__title {
  display: flex;
  align-items: center;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.mode-card__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.sim-unit {
  margin-left: 8px;
  color: var(--el-text-color-regular);
}
.sim-tip {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}
.sim-tip--inline {
  margin-left: 12px;
}
.sim-tip--warn {
  color: var(--el-color-warning);
}
.sim-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
}
.sim-collapse {
  margin: 0 0 12px 110px;
  border-top: none;
}
</style>
