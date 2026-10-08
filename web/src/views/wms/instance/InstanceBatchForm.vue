<!-- WMS 物料实例 - 批量新增子实例 -->
<template>
  <Dialog v-model="dialogVisible" title="批量新增子实例" width="720px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
      <!-- 父实例（只读） -->
      <el-form-item label="父实例">
        <el-tag type="primary" size="large">{{ parentLabel }}</el-tag>
        <span class="hint ml-8px">
          <template v-if="parentHasWells">该容器为 {{ wellRows }} 行 × {{ wellCols }} 列</template>
          <template v-else>在该实例下批量生成子实例</template>
        </span>
      </el-form-item>

      <!-- 子实例容器类型 -->
      <el-form-item label="子实例容器" prop="containerTypeId">
        <el-select
          v-model="formData.containerTypeId"
          placeholder="请选择子实例的容器类型"
          filterable
          class="!w-1/1"
          @change="handleChildContainerChange"
        >
          <el-option
            v-for="item in containerTypes"
            :key="item.id"
            :label="`${item.typeName}（${item.typeCode}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>

      <!-- 生成方式 -->
      <el-form-item label="生成方式">
        <el-radio-group v-model="mode" @change="handleModeChange">
          <el-radio-button value="wells" :disabled="!parentHasWells">
            按容器孔位（{{ wellTotal }} 个）
          </el-radio-button>
          <el-radio-button value="count">按数量</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <!-- 按数量 -->
      <el-form-item v-if="mode === 'count'" label="生成数量" prop="count">
        <el-input-number v-model="formData.count" :min="1" :max="500" controls-position="right" />
        <span class="hint ml-8px">最多 500 条</span>
      </el-form-item>

      <!-- 编码前缀 -->
      <el-form-item label="编码前缀" prop="codePrefix">
        <el-input v-model="formData.codePrefix" maxlength="48" placeholder="子实例编码前缀">
          <template #append>
            <el-button @click="resetCodePrefix">默认</el-button>
          </template>
        </el-input>
      </el-form-item>

      <!-- 统一内容物 -->
      <template v-if="isChildCarrier === false">
        <el-divider content-position="left">统一内容物（可选）</el-divider>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="内容物定义">
              <el-select
                v-model="formData.contentDefId"
                placeholder="不选则为空容器"
                filterable
                clearable
                class="!w-1/1"
                @change="handleContentDefChange"
              >
                <el-option
                  v-for="item in contentDefs"
                  :key="item.id"
                  :label="`${item.contentName}（${item.contentCode}）`"
                  :value="item.id!"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="当前体积(μL)">
              <el-input-number
                v-model="formData.currentVolUl"
                :min="0"
                :precision="2"
                controls-position="right"
                class="!w-1/1"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="当前数量(个)">
              <el-input-number v-model="formData.currentCount" :min="0" controls-position="right" class="!w-1/1" />
            </el-form-item>
          </el-col>
        </el-row>
      </template>

      <!-- 实例状态 -->
      <el-form-item label="实例状态" prop="instanceStatus">
        <el-select v-model="formData.instanceStatus" placeholder="请选择" class="!w-1/1">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>

      <!-- 生成预览 -->
      <el-form-item label="生成预览">
        <div class="batch-preview">
          <div class="preview-meta">
            <span>将新增 <b>{{ previewItems.length }}</b> 条子实例</span>
            <span v-if="skippedCount > 0" class="preview-skip">已存在 {{ skippedCount }} 个孔位，自动跳过</span>
            <span v-if="previewItems.length === 0" class="preview-empty">没有可生成的位置</span>
          </div>
          <div v-if="previewItems.length" class="preview-codes">
            <el-tag v-for="item in previewCodes" :key="item.code" size="small" type="info">{{ item.code }}</el-tag>
            <span v-if="previewItems.length > previewCodes.length" class="preview-more">
              … 等共 {{ previewItems.length }} 条
            </span>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading || previewItems.length === 0" type="primary" @click="submitForm">
        确 定
      </el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'

defineOptions({ name: 'WmsInstanceBatchForm' })

const message = useMessage()

const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()

/** 父实例 */
const parent = ref<MaterialInstanceVO>()

const parentLabel = computed(() => parent.value?.instanceName || parent.value?.instanceCode || '—')

/** 生成方式：wells 按容器孔位 / count 按数量 */
const mode = ref<'wells' | 'count'>('count')

const createEmptyFormData = () => ({
  containerTypeId: undefined as number | undefined,
  containerTypeCode: undefined as string | undefined,
  codePrefix: '',
  count: 1,
  contentDefId: undefined as number | undefined,
  contentDefCode: undefined as string | undefined,
  contentType: undefined as string | undefined,
  currentVolUl: undefined as number | undefined,
  currentCount: undefined as number | undefined,
  instanceStatus: 'AVAILABLE'
})

const formData = ref(createEmptyFormData())

const formRules = reactive({
  containerTypeId: [{ required: true, message: '子实例容器不能为空', trigger: 'change' }],
  codePrefix: [{ required: true, message: '编码前缀不能为空', trigger: 'blur' }],
  instanceStatus: [{ required: true, message: '实例状态不能为空', trigger: 'change' }]
})

const statusOptions = [
  { label: '可用', value: 'AVAILABLE' },
  { label: '已预留', value: 'RESERVED' },
  { label: '使用中', value: 'IN_USE' },
  { label: '已用完', value: 'USED' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '已废弃', value: 'DISCARDED' }
]

// ========== 下拉数据源 ==========
const containerTypes = ref<ContainerTypeVO[]>([])
const contentDefs = ref<ContentDefVO[]>([])

/** 父实例的容器类型（决定孔位布局） */
const parentContainerType = computed(() =>
  containerTypes.value.find((c) => c.id === parent.value?.containerTypeId)
)

const wellRows = computed(() => parentContainerType.value?.wellRows || 0)
const wellCols = computed(() => parentContainerType.value?.wellCols || 0)
const wellTotal = computed(() => wellRows.value * wellCols.value)
const parentHasWells = computed(() => wellRows.value > 0 && wellCols.value > 0)

/** 子实例容器类型 */
const childContainerType = computed(() => containerTypes.value.find((c) => c.id === formData.value.containerTypeId))
const isChildCarrier = computed(() => childContainerType.value?.hierarchyRole === 'CARRIER')

/** 父实例下已存在的位置码（批量生成时自动跳过） */
const existingPositions = computed(() => {
  const set = new Set<string>()
  ;(parent.value?.children || []).forEach((c) => {
    if (c.parentPositionCode) set.add(c.parentPositionCode)
  })
  return set
})

// ========== 位置码 / 编码生成 ==========
/** 按父容器的位置命名规则生成孔位编码 */
const buildWellPositions = (): string[] => {
  const naming = parentContainerType.value?.positionNaming || 'ROW_COL'
  const rows = wellRows.value
  const cols = wellCols.value
  const positions: string[] = []
  if (naming === 'ROW_COL' || naming === 'ROW_COL_LAYER') {
    // 行字母（A/B/C…）× 列号（1/2/3…），行优先：A1 A2 … B1 …
    for (let r = 0; r < rows; r++) {
      const rowName = String.fromCharCode(65 + r)
      for (let c = 0; c < cols; c++) {
        positions.push(`${rowName}${c + 1}`)
      }
    }
  } else {
    // SEQ / NONE：退化为纯序号
    const total = rows * cols
    for (let i = 1; i <= total; i++) positions.push(String(i))
  }
  return positions
}

/** 待生成的条目（positionCode + 实例编码） */
const previewItems = computed(() => {
  const prefix = formData.value.codePrefix || ''
  if (!dialogVisible.value || !prefix) return []
  if (mode.value === 'wells') {
    return buildWellPositions()
      .filter((pos) => !existingPositions.value.has(pos))
      .map((pos) => ({ positionCode: pos, code: `${prefix}-${pos}` }))
  }
  const n = formData.value.count || 0
  return Array.from({ length: n }, (_, i) => ({
    positionCode: undefined as string | undefined,
    code: `${prefix}-${String(i + 1).padStart(3, '0')}`
  }))
})

const previewCodes = computed(() => previewItems.value.slice(0, 12))

const skippedCount = computed(() =>
  mode.value === 'wells' ? wellTotal.value - previewItems.value.length : 0
)

// ========== 联动 ==========
const loadOptions = async () => {
  const [cts, cds] = await Promise.all([ContainerTypeApi.getContainerTypeList(), ContentDefApi.getContentDefList()])
  containerTypes.value = cts || []
  contentDefs.value = cds || []
}

const handleChildContainerChange = (id: number) => {
  const ct = containerTypes.value.find((c) => c.id === id)
  formData.value.containerTypeCode = ct?.typeCode
  if (ct?.hierarchyRole === 'CARRIER') {
    formData.value.contentDefId = undefined
    formData.value.contentDefCode = undefined
    formData.value.contentType = undefined
  } else if (!formData.value.contentDefId) {
    formData.value.contentType = 'EMPTY'
  }
}

const handleContentDefChange = (id?: number) => {
  const cd = contentDefs.value.find((c) => c.id === id)
  formData.value.contentDefCode = cd?.contentCode
  formData.value.contentType = cd ? cd.contentType : 'EMPTY'
}

const handleModeChange = () => {
  // 切换方式时不改变数量输入，仅刷新预览
}

/** 前缀重置为父实例编码 */
const resetCodePrefix = () => {
  formData.value.codePrefix = parent.value?.instanceCode || ''
}

// ========== 打开 / 提交 ==========
const open = async (parentInstance: MaterialInstanceVO) => {
  dialogVisible.value = true
  parent.value = parentInstance
  formData.value = createEmptyFormData()
  formData.value.codePrefix = parentInstance.instanceCode || ''
  formRef.value?.clearValidate()
  formLoading.value = true
  try {
    await loadOptions()
    // 智能默认：优先取父容器类型声明的子单元类型
    const childTypeCode = parentContainerType.value?.childTypeCode
    const matched = containerTypes.value.find((c) => c.typeCode === childTypeCode)
    if (matched) {
      formData.value.containerTypeId = matched.id
      formData.value.containerTypeCode = matched.typeCode
    }
    // 有孔位布局则默认按孔位生成
    mode.value = parentHasWells.value ? 'wells' : 'count'
    formData.value.count = parentHasWells.value ? wellTotal.value : 1
  } finally {
    formLoading.value = false
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (previewItems.value.length === 0) {
    message.warning('没有可生成的子实例')
    return
  }
  if (!isChildCarrier.value && formData.value.contentDefId && !formData.value.contentType) {
    message.warning('请选择有效的内容物定义')
    return
  }

  const payload: MaterialInstanceVO[] = previewItems.value.map((item) => ({
    instanceCode: item.code,
    instanceName: item.positionCode,
    containerTypeId: formData.value.containerTypeId,
    containerTypeCode: formData.value.containerTypeCode,
    parentInstanceId: parent.value?.id,
    parentPositionCode: item.positionCode,
    contentDefId: isChildCarrier.value ? undefined : formData.value.contentDefId,
    contentDefCode: isChildCarrier.value ? undefined : formData.value.contentDefCode,
    contentType: isChildCarrier.value ? undefined : formData.value.contentType || 'EMPTY',
    currentVolUl: isChildCarrier.value ? undefined : formData.value.currentVolUl,
    currentCount: isChildCarrier.value ? undefined : formData.value.currentCount,
    instanceStatus: formData.value.instanceStatus
  }))

  formLoading.value = true
  try {
    await MaterialInstanceApi.createMaterialInstanceBatch(payload)
    message.success(`已生成 ${payload.length} 个子实例`)
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open })
const emit = defineEmits<{ success: [] }>()
</script>

<style lang="scss" scoped>
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.batch-preview {
  width: 100%;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.preview-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  margin-bottom: 8px;

  b {
    color: var(--el-color-primary);
  }
}

.preview-skip {
  color: var(--el-color-warning);
  font-size: 12px;
}

.preview-empty {
  color: var(--el-color-danger);
  font-size: 12px;
}

.preview-codes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.preview-more {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  align-self: center;
}
</style>
