<template>
  <div class="schema-form">
    <el-form-item v-for="field in visibleFields" :key="field.key" :label="field.label" :required="field.required">
      <!-- 单行文本 -->
      <el-input
        v-if="field.type === 'string'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        maxlength="256"
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      />
      <!-- 多行文本 -->
      <el-input
        v-else-if="field.type === 'textarea'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        type="textarea"
        :rows="2"
        maxlength="4096"
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      />
      <!-- 数字 -->
      <el-input-number
        v-else-if="field.type === 'number'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        controls-position="right"
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      />
      <!-- 开关 -->
      <el-switch
        v-else-if="field.type === 'boolean'"
        :model-value="getField(field.key)"
        @update:model-value="(v) => applyField(field.key, v)"
      />
      <!-- 枚举 -->
      <el-select
        v-else-if="field.type === 'enum'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        clearable
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      >
        <el-option v-for="opt in field.options" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <!-- 设备 -->
      <el-select
        v-else-if="field.type === 'device'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        filterable
        clearable
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      >
        <el-option v-for="d in devices" :key="d.deviceCode" :label="`${d.deviceName}（${d.deviceCode}）`" :value="d.deviceCode!" />
      </el-select>
      <!-- 动作 -->
      <el-select
        v-else-if="field.type === 'action'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        filterable
        clearable
        class="!w-1/1"
        :disabled="isActionDisabled(field)"
        @update:model-value="(v) => applyField(field.key, v)"
      >
        <el-option v-for="a in actions" :key="a.actionCode" :label="`${a.actionName}（${a.actionCode}）`" :value="a.actionCode!" />
      </el-select>
      <!-- 编解码 -->
      <el-select
        v-else-if="field.type === 'codec'"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        filterable
        clearable
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      >
        <el-option v-for="c in codecs" :key="c.codecCode" :label="`${c.codecName}（${c.codecCode}）`" :value="c.codecCode!" />
      </el-select>
      <!-- Bean 白名单（bean.invoke / route / validate） -->
      <el-select
        v-else-if="isBeanField(field.type)"
        :model-value="getField(field.key)"
        :placeholder="field.placeholder"
        filterable
        allow-create
        default-first-option
        class="!w-1/1"
        @update:model-value="(v) => applyField(field.key, v)"
      >
        <el-option v-for="name in beanOptions(field.type)" :key="name" :label="name" :value="name" />
      </el-select>
      <!-- 自由 JSON -->
      <JsonKeyValueEditor
        v-else-if="field.type === 'json'"
        :model-value="getField(field.key)"
        @update:model-value="(v) => applyField(field.key, v)"
      />
    </el-form-item>
    <div v-if="hints.length" class="schema-hints">
      <div v-for="h in hints" :key="h" class="hint-item">{{ h }}</div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { DeviceInfoApi, DeviceVO } from '@/api/device'
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceCodecApi, DeviceCodecVO } from '@/api/device/codec'
import { FlowStepTypeApi, isBeanField, type ParamField } from '@/api/flow/stepSchema'
import JsonKeyValueEditor from './JsonKeyValueEditor.vue'

/**
 * Schema 驱动的动态表单。把一份 JSON 字符串（typeConfig / defaultParams）按字段 schema
 * 渲染成结构化表单，编辑结果仍序列化回 JSON 字符串。未在 schema 里声明的字段会被原样保留。
 */
defineOptions({ name: 'SchemaForm' })

const props = defineProps<{
  fields: ParamField[]
  modelValue?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const model = ref<Record<string, any>>({})
let lastEmitted = ''

// ==================== 远程数据源 ====================

const devices = ref<DeviceVO[]>([])
const actions = ref<DeviceActionVO[]>([])
const codecs = ref<DeviceCodecVO[]>([])
const beans = ref<{ beanActions: string[]; routers: string[]; validators: string[] }>({
  beanActions: [],
  routers: [],
  validators: []
})

const loadDevices = async () => {
  devices.value = (await DeviceInfoApi.getDeviceList()) || []
}
const loadCodecs = async () => {
  codecs.value = (await DeviceCodecApi.getCodecList()) || []
}
const loadBeans = async () => {
  const resp = await FlowStepTypeApi.getBeans()
  beans.value = {
    beanActions: resp?.beanActions || [],
    routers: resp?.routers || [],
    validators: resp?.validators || []
  }
}
const loadActions = async (deviceCode?: string) => {
  const type = deviceCode ? devices.value.find((d) => d.deviceCode === deviceCode)?.deviceTypeCode : undefined
  const list = type
    ? await DeviceActionApi.getActionList({ deviceTypeCode: type })
    : await DeviceActionApi.getActionList()
  actions.value = (list || []).filter((a) => a.status == null || a.status === 0)
}

// ==================== 路径读写 ====================

const getField = (key: string): any => key.split('.').reduce((acc: any, k) => acc?.[k], model.value)

const setByPath = (obj: Record<string, any>, key: string, value: any) => {
  const keys = key.split('.')
  let cur: any = obj
  // 逐层通过响应式代理访问，保证新建的中间对象也是响应式的
  for (let i = 0; i < keys.length - 1; i++) {
    const k = keys[i]
    if (cur[k] === null || typeof cur[k] !== 'object') {
      cur[k] = {}
    }
    cur = cur[k]
  }
  cur[keys[keys.length - 1]] = value
}

// ==================== 序列化 ====================

const isEmpty = (v: any): boolean =>
  v === null || v === undefined || v === '' || (typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length === 0)

const prune = (obj: Record<string, any>): Record<string, any> => {
  const result: Record<string, any> = {}
  for (const [k, v] of Object.entries(obj)) {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      const nested = prune(v)
      if (Object.keys(nested).length) result[k] = nested
    } else if (!isEmpty(v)) {
      result[k] = v
    }
  }
  return result
}

const emitNow = () => {
  const pruned = prune(model.value)
  const json = Object.keys(pruned).length ? JSON.stringify(pruned) : ''
  lastEmitted = json
  emit('update:modelValue', json)
}

// 外部值变化时重新解析（自产更新不会触发）
watch(
  () => props.modelValue,
  (val) => {
    if (val === lastEmitted) return
    try {
      model.value = val ? JSON.parse(val) : {}
    } catch {
      model.value = {}
    }
    lastEmitted = val ?? ''
  },
  { immediate: true }
)

// ==================== 字段联动 ====================

const visibleFields = computed(() =>
  props.fields.filter((f) => {
    if (!f.when) return true
    return getField(f.when.key) === f.when.value
  })
)

const hints = computed(() => props.fields.filter((f) => f.hint).map((f) => f.hint!))

const isActionDisabled = (field: ParamField) => {
  if (!field.dependsOn) return false
  const deviceField = props.fields.find((f) => f.key === field.dependsOn)
  return !!deviceField?.required && !getField(field.dependsOn)
}

const beanOptions = (type: ParamField['type']) => {
  if (type === 'router') return beans.value.routers
  if (type === 'validator') return beans.value.validators
  return beans.value.beanActions
}

const applyField = (key: string, value: any) => {
  setByPath(model.value, key, value)
  // 设备变化：清空依赖的动作并重载
  const actionField = props.fields.find((f) => f.type === 'action' && f.dependsOn === key)
  if (actionField && actionField.key !== key) {
    setByPath(model.value, actionField.key, undefined)
    loadActions(value)
  }
  emitNow()
}

onMounted(async () => {
  const types = props.fields.map((f) => f.type)
  const jobs: Promise<void>[] = []
  if (types.includes('device') || types.includes('action')) jobs.push(loadDevices())
  if (types.includes('codec')) jobs.push(loadCodecs())
  if (types.some(isBeanField)) jobs.push(loadBeans())
  await Promise.all(jobs)
  // 有 action 字段时先加载全量动作（设备可选场景）
  if (types.includes('action')) await loadActions(getField('deviceCode'))
})
</script>

<style lang="scss" scoped>
.schema-hints {
  margin: -4px 0 8px;
  .hint-item {
    font-size: 12px;
    color: #909399;
    line-height: 1.6;
  }
}
</style>
