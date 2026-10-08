<!-- WMS 物料实例表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="实例编码" prop="instanceCode">
            <el-input v-model="formData.instanceCode" maxlength="64" placeholder="如 PLATE96-000123">
              <template #append>
                <el-button @click="genInstanceCode">生成</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实例名称">
            <el-input v-model="formData.instanceName" maxlength="128" placeholder="如 第 1 块 96 孔板" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="容器类型" prop="containerTypeId">
            <el-select
              v-model="formData.containerTypeId"
              placeholder="请选择容器类型"
              filterable
              class="!w-1/1"
              @change="handleContainerTypeChange"
            >
              <el-option
                v-for="item in containerTypes"
                :key="item.id"
                :label="`${item.typeName}（${item.typeCode}）`"
                :value="item.id!"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="条码">
            <el-input v-model="formData.barcode" maxlength="128" placeholder="条码/二维码（可选）" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="父实例">
            <el-select
              v-model="formData.parentInstanceId"
              placeholder="不选则为顶层实例"
              filterable
              clearable
              class="!w-1/1"
              @change="handleParentChange"
            >
              <el-option
                v-for="item in parentOptions"
                :key="item.id"
                :label="`${item.instanceName || item.instanceCode}`"
                :value="item.id!"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item v-if="formData.parentInstanceId" label="在父容器位置">
            <el-input v-model="formData.parentPositionCode" maxlength="32" placeholder="如 A1" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="落位槽位">
            <!-- 编辑时禁止改槽位：落位必须走「上架 / 下架 / 转移」接口，
                 否则改库不会产生物料流水，货位账会记漏 -->
            <template v-if="isUpdate">
              <el-input :model-value="formData.rootSlotCode || '未落位'" disabled />
              <div class="form-hint">
                落位改动请在「空间管理」页面对槽位执行上架 / 下架 / 转移，以便记录物料流水
              </div>
            </template>
            <el-select
              v-else
              v-model="formData.rootSlotId"
              placeholder="顶层实例可落位（可选）"
              filterable
              clearable
              class="!w-1/1"
              :disabled="!!formData.parentInstanceId"
              @change="handleSlotChange"
            >
              <el-option
                v-for="item in slots"
                :key="item.id"
                :label="item.slotCode!"
                :value="item.id!"
                :disabled="isSlotDisabled(item)"
              >
                <span class="slot-option">
                  <span>{{ item.slotCode }}</span>
                  <el-tag size="small" :type="slotTagType(item)">{{ slotStatusText(item) }}</el-tag>
                </span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实例状态" prop="instanceStatus">
            <el-select v-model="formData.instanceStatus" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 内容物（CARRIER 不装东西，隐藏） -->
      <template v-if="isCarrier === false">
        <el-divider content-position="left">内容物</el-divider>
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
              <el-input-number v-model="formData.currentVolUl" :min="0" :precision="2" controls-position="right" class="!w-1/1" />
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

      <el-form-item label="备注">
        <el-input v-model="formData.description" maxlength="512" placeholder="请输入备注" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import { SlotApi, SlotVO } from '@/api/wms/space/slot'

defineOptions({ name: 'WmsInstanceForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): MaterialInstanceVO => ({
  id: undefined,
  instanceCode: undefined,
  instanceName: undefined,
  barcode: undefined,
  containerTypeId: undefined,
  containerTypeCode: undefined,
  parentInstanceId: undefined,
  parentPositionCode: undefined,
  rootSlotId: undefined,
  rootSlotCode: undefined,
  contentDefId: undefined,
  contentDefCode: undefined,
  contentType: undefined,
  currentVolUl: undefined,
  currentCount: undefined,
  instanceStatus: 'AVAILABLE',
  description: undefined
})

const formData = ref<MaterialInstanceVO>(createEmptyFormData())

const formRules = reactive({
  instanceCode: [{ required: true, message: '实例编码不能为空', trigger: 'blur' }],
  containerTypeId: [{ required: true, message: '容器类型不能为空', trigger: 'change' }],
  instanceStatus: [{ required: true, message: '实例状态不能为空', trigger: 'change' }]
})
const formRef = ref()

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
const parentOptions = ref<MaterialInstanceVO[]>([])
const slots = ref<SlotVO[]>([])

/** 当前所选容器类型是否为 CARRIER（载体不装内容物） */
const isCarrier = computed(() => {
  const ct = containerTypes.value.find((c) => c.id === formData.value.containerTypeId)
  return ct?.hierarchyRole === 'CARRIER'
})

/** 是否编辑态（有 id 即为编辑）。编辑态不允许改落位槽位，只能走流水接口 */
const isUpdate = computed(() => !!formData.value.id)

const loadOptions = async () => {
  const [cts, cds, instances, slotList] = await Promise.all([
    ContainerTypeApi.getContainerTypeList(),
    ContentDefApi.getContentDefList(),
    MaterialInstanceApi.getMaterialInstanceList(),
    SlotApi.getSlotList()
  ])
  containerTypes.value = cts || []
  contentDefs.value = cds || []
  // 父实例候选：排除自己（编辑时不可选自己为父）
  parentOptions.value = (instances || []).filter((i) => i.id !== formData.value.id)
  slots.value = slotList || []
}

// ========== 联动 ==========
/** 选容器类型 → 带出编码，并重置内容物语义 */
const handleContainerTypeChange = (id: number) => {
  const ct = containerTypes.value.find((c) => c.id === id)
  formData.value.containerTypeCode = ct?.typeCode
  if (ct?.hierarchyRole === 'CARRIER') {
    // 载体不装内容物
    formData.value.contentDefId = undefined
    formData.value.contentDefCode = undefined
    formData.value.contentType = undefined
  } else if (!formData.value.contentDefId) {
    // 空容器
    formData.value.contentType = 'EMPTY'
  }
}

/** 选内容物定义 → 带出编码与类型快照 */
const handleContentDefChange = (id?: number) => {
  const cd = contentDefs.value.find((c) => c.id === id)
  formData.value.contentDefCode = cd?.contentCode
  formData.value.contentType = cd ? cd.contentType : 'EMPTY'
}

/** 选父实例 → 清空落位（仅顶层可落位） */
const handleParentChange = (id?: number) => {
  if (id) {
    formData.value.rootSlotId = undefined
    formData.value.rootSlotCode = undefined
  }
}

/** 选槽位 → 带出槽位编码 */
const handleSlotChange = (id?: number) => {
  const slot = slots.value.find((s) => s.id === id)
  formData.value.rootSlotCode = slot?.slotCode
}

/** 槽位状态文案 */
const slotStatusText = (slot: SlotVO) => {
  if (slot.status === 1) return '已停用'
  const map: Record<string, string> = {
    FREE: '空闲',
    OCCUPIED: '已占用',
    LOCKED: '锁定',
    CHECKING: '盘点中'
  }
  return map[slot.slotStatus || 'FREE'] || slot.slotStatus || '空闲'
}

const slotTagType = (slot: SlotVO) => {
  if (slot.status === 1) return 'info'
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    FREE: 'success',
    OCCUPIED: 'danger',
    LOCKED: 'warning',
    CHECKING: 'warning'
  }
  return map[slot.slotStatus || 'FREE'] || 'info'
}

/** 已停用 / 已被其他实例占用的槽位不可选（编辑时自己当前的槽位仍可选） */
const isSlotDisabled = (slot: SlotVO) => {
  if (slot.id === formData.value.rootSlotId) return false
  if (slot.status === 1) return true
  return ['OCCUPIED', 'LOCKED', 'CHECKING'].includes(slot.slotStatus || '')
}

/** 生成实例编码（类型编码前缀 + 随机） */
const genInstanceCode = () => {
  const ct = containerTypes.value.find((c) => c.id === formData.value.containerTypeId)
  const prefix = ct?.typeCode ? ct.typeCode.replace(/[^A-Za-z0-9]/g, '').slice(0, 12) : 'INST'
  const rand = Math.floor(Math.random() * 1000000)
    .toString()
    .padStart(6, '0')
  formData.value.instanceCode = `${prefix}-${rand}`
}

// ========== 打开 / 提交 ==========
const open = async (type: string, id?: number, presetParentId?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  // 新增子实例时预设父实例
  if (presetParentId) {
    formData.value.parentInstanceId = presetParentId
  }
  formLoading.value = true
  try {
    if (id) {
      const data = await MaterialInstanceApi.getMaterialInstance(id)
      Object.assign(formData.value, data)
    }
    await loadOptions()
  } finally {
    formLoading.value = false
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await MaterialInstanceApi.createMaterialInstance(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await MaterialInstanceApi.updateMaterialInstance(formData.value)
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

<style lang="scss" scoped>
.slot-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.form-hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
}
</style>
