<template>
  <div class="kv-editor">
    <div v-for="(row, index) in rows" :key="index" class="kv-row">
      <el-input v-model="row.key" :maxlength="64" :placeholder="mode === 'desc' ? '输出字段名' : '键'" class="!w-160px" />
      <template v-if="mode === 'param'">
        <el-select v-model="row.type" class="!w-82px">
          <el-option v-for="t in TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-input v-model="row.value" maxlength="512" placeholder="值" class="kv-value" />
        <el-input v-model="row.comment" maxlength="200" placeholder="注释：参数含义" class="kv-comment" />
      </template>
      <el-input
        v-else
        v-model="row.value"
        maxlength="200"
        placeholder="说明：字段含义/示例/取值范围"
        class="!flex-1"
      />
      <el-button link type="danger" @click="remove(index)">删</el-button>
    </div>
    <div class="kv-actions">
      <el-button link type="primary" @click="add">+ 加{{ mode === 'desc' ? '输出字段' : '参数' }}</el-button>
      <el-button link type="primary" @click="openJsonDialog">JSON 格式转换</el-button>
    </div>
    <!-- JSON 转换弹框：粘贴一段 JSON，确定后解析进参数行 -->
    <Dialog v-model="jsonDialogVisible" title="JSON 格式转换" width="560px">
      <el-input
        v-model="jsonText"
        type="textarea"
        :rows="12"
        :placeholder="mode === 'desc' ? descDialogHint : paramDialogHint"
      />
      <template #footer>
        <el-button type="primary" @click="applyJson">确 定</el-button>
        <el-button @click="jsonDialogVisible = false">取 消</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
/**
 * 自由 JSON 键值对编辑器。
 * - mode='param'（默认）：用于 argsJson / inputJson / defaultParams 这类字段不固定的 JSON 配置，
 *   以「键 + 类型 + 值 + 注释」的行式表单编辑，对外仍是 JSON 字符串。
 * - mode='desc'：用于输出声明这类「键=说明」语义，编辑时只显示键与说明，存为 {key: 文本}。
 * 注释存保留键 __comments__ 里（{参数键: 注释文本}），消费方解析参数时会剥掉（仅 param 模式）。
 */
defineOptions({ name: 'JsonKeyValueEditor' })

interface KvRow {
  key: string
  type: string
  value: string
  comment: string
}

const TYPES = [
  { label: '文本', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔', value: 'boolean' },
  { label: '对象', value: 'object' },
  { label: '数组', value: 'array' }
]

/** 注释保留键，与后端 FlowConstants.PARAM_COMMENTS_KEY 对应 */
const COMMENTS_KEY = '__comments__'

const props = withDefaults(
  defineProps<{ modelValue?: string; mode?: 'param' | 'desc' }>(),
  { mode: 'param' }
)
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const message = useMessage()

/** 转换弹框的占位文案放这里：模板里直接写 JSON 大括号会被 Vue 解析为 mustache */
const paramDialogHint =
  '粘贴 JSON 文本，例如 {"speed": 3000, "mode": "fast"}。点击确定后会解析到上方参数列表，可再逐项补注释。'
const descDialogHint =
  '粘贴 JSON 对象，例如 {"plateRead": "读板结果", "result": "设备返回值"}。确定后会按「键=说明」解析到上方。'

const rows = ref<KvRow[]>([])
let lastEmitted = ''

const typeOf = (v: unknown): string => {
  if (typeof v === 'number') return 'number'
  if (typeof v === 'boolean') return 'boolean'
  if (Array.isArray(v)) return 'array'
  if (typeof v === 'object' && v !== null) return 'object'
  return 'string'
}

const stringifyValue = (v: unknown): string => {
  if (v === null || v === undefined) return ''
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

const parseValue = (row: KvRow): unknown => {
  // desc 模式：value 即说明文本，原样字符串
  if (props.mode === 'desc') {
    return row.value ?? ''
  }
  const v = row.value?.trim()
  switch (row.type) {
    case 'number':
      return v === '' ? null : Number(v)
    case 'boolean':
      return v === 'true' || v === '1'
    case 'object':
    case 'array': {
      if (!v) return null
      try {
        return JSON.parse(v)
      } catch {
        return v
      }
    }
    default:
      return v ?? ''
  }
}

/** 把一个已解析的 JSON 对象转成参数行，注释保留键单独拎出来 */
const toRows = (obj: Record<string, unknown>): KvRow[] => {
  const comments =
    typeof obj[COMMENTS_KEY] === 'object' && obj[COMMENTS_KEY] !== null
      ? (obj[COMMENTS_KEY] as Record<string, string>)
      : {}
  return Object.entries(obj)
    .filter(([key]) => key !== COMMENTS_KEY)
    .map(([key, value]) => ({
      key,
      type: props.mode === 'desc' ? 'string' : typeOf(value),
      value: stringifyValue(value),
      comment: comments[key] ?? ''
    }))
}

const fromJson = (json?: string): KvRow[] => {
  if (!json) return []
  try {
    const obj = JSON.parse(json)
    if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) return []
    return toRows(obj)
  } catch {
    return []
  }
}

const toJson = (): string => {
  const obj: Record<string, unknown> = {}
  const comments: Record<string, string> = {}
  for (const row of rows.value) {
    const key = row.key?.trim()
    if (!key || key === COMMENTS_KEY) continue
    obj[key] = parseValue(row)
    if (props.mode === 'param' && row.comment?.trim()) {
      comments[key] = row.comment.trim()
    }
  }
  if (!Object.keys(obj).length) return ''
  if (Object.keys(comments).length) obj[COMMENTS_KEY] = comments
  return JSON.stringify(obj)
}

const emitNow = () => {
  const json = toJson()
  lastEmitted = json
  emit('update:modelValue', json)
}

// 外部值变化时同步（内部编辑后自产的更新不会触发重新解析）
watch(
  () => props.modelValue,
  (val) => {
    if (val !== lastEmitted) {
      rows.value = fromJson(val)
      lastEmitted = val ?? ''
    }
  },
  { immediate: true }
)

const add = () => {
  rows.value.push({ key: '', type: 'string', value: '', comment: '' })
}

const remove = (index: number) => {
  rows.value.splice(index, 1)
}

// ==================== JSON 格式转换 ====================

const jsonDialogVisible = ref(false)
const jsonText = ref('')

const openJsonDialog = () => {
  // 预填当前参数的格式化 JSON，双向可读
  try {
    jsonText.value = props.modelValue ? JSON.stringify(JSON.parse(props.modelValue), null, 2) : ''
  } catch {
    jsonText.value = props.modelValue ?? ''
  }
  jsonDialogVisible.value = true
}

const applyJson = () => {
  const text = jsonText.value?.trim()
  if (!text) {
    rows.value = []
    jsonDialogVisible.value = false
    emitNow()
    return
  }
  let obj: unknown
  try {
    obj = JSON.parse(text)
  } catch {
    message.error('JSON 格式不合法，请检查后重试')
    return
  }
  if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) {
    message.error('需要一段 JSON 对象（{...}），不支持数组或标量')
    return
  }
  rows.value = toRows(obj as Record<string, unknown>)
  jsonDialogVisible.value = false
}

// 行内容变化即序列化回父组件
watch(
  rows,
  () => emitNow(),
  { deep: true }
)
</script>

<style lang="scss" scoped>
/* 键/类型/值/注释同一行；容器窄时注释自动折行，不挤死 */
.kv-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 6px;
}

.kv-value {
  flex: 1 1 130px;
  min-width: 110px;
}

.kv-comment {
  flex: 1 1 150px;
  min-width: 130px;

  :deep(.el-input__inner) {
    font-size: 12px;
    color: #909399;
  }
}

.kv-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
