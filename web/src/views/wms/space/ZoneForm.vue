<!-- WMS 区域表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-form-item label="区域名称" prop="zoneName">
        <el-input v-model="formData.zoneName" maxlength="128" placeholder="请输入区域名称" />
      </el-form-item>
      <el-form-item label="区域编码" prop="zoneCode">
        <el-input v-model="formData.zoneCode" maxlength="64" placeholder="如 ZONE_LAB_01" />
      </el-form-item>
      <el-form-item label="区域类型" prop="zoneType">
        <el-select v-model="formData.zoneType" placeholder="请选择区域类型" class="!w-1/1">
          <el-option v-for="item in zoneTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="父区域">
        <el-tree-select
          v-model="formData.parentZoneCode"
          class="!w-1/1"
          :data="parentTreeOptions"
          :props="{ label: 'zoneName', children: 'children' }"
          node-key="zoneCode"
          value-key="zoneCode"
          check-strictly
          :render-after-expand="false"
          clearable
          placeholder="不选则为根区域"
        />
      </el-form-item>

      <!-- 温控区 / 冷藏区：显示温区 -->
      <template v-if="showTempFields">
        <el-form-item label="温区下限(℃)">
          <el-input-number v-model="formData.tempMin" :precision="2" :step="1" controls-position="right" class="!w-1/1" />
        </el-form-item>
        <el-form-item label="温区上限(℃)">
          <el-input-number v-model="formData.tempMax" :precision="2" :step="1" controls-position="right" class="!w-1/1" />
        </el-form-item>
      </template>

      <el-form-item label="生物安全等级">
        <el-select v-model="formData.biosafetyLevel" placeholder="无要求可不选" clearable class="!w-1/1">
          <el-option v-for="n in 4" :key="n" :label="`BSL-${n}`" :value="n" />
        </el-select>
      </el-form-item>

      <!-- 货架：显示货架布局参数 -->
      <template v-if="formData.zoneType === 'RACK'">
        <el-divider content-position="left">货架布局</el-divider>
        <el-form-item label="货架行数" prop="rackRows">
          <el-input-number v-model="formData.rackRows" :min="1" :max="50" controls-position="right" class="!w-1/1" />
        </el-form-item>
        <el-form-item label="货架列数" prop="rackCols">
          <el-input-number v-model="formData.rackCols" :min="1" :max="50" controls-position="right" class="!w-1/1" />
        </el-form-item>
        <el-form-item label="单槽容量">
          <el-input-number v-model="formData.slotCapacity" :min="1" controls-position="right" class="!w-1/1" />
        </el-form-item>
        <el-form-item label="物理地址">
          <el-input v-model="formData.address" maxlength="255" placeholder="如 一楼 B 区 3 号架" />
        </el-form-item>
      </template>

      <el-form-item label="显示顺序" prop="sortNo">
        <el-input-number v-model="formData.sortNo" :min="0" controls-position="right" class="!w-1/1" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="formData.status">
          <el-radio v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)" :key="dict.value" :value="dict.value">
            {{ dict.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
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
import { ZoneApi, ZoneVO } from '@/api/wms/space/zone'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'

/** WMS 区域表单 */
defineOptions({ name: 'WmsZoneForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

/** 表单数据类型：ZoneVO + 货架布局字段（rack 字段仅用于编辑，提交时序列化进 extData） */
interface ZoneFormData extends ZoneVO {
  rackRows?: number
  rackCols?: number
  slotCapacity?: number
  address?: string
}

const createEmptyFormData = (): ZoneFormData => ({
  id: undefined,
  zoneCode: undefined,
  parentZoneCode: undefined,
  zoneName: undefined,
  zoneType: undefined,
  tempMin: undefined,
  tempMax: undefined,
  biosafetyLevel: undefined,
  extData: undefined,
  rackRows: undefined,
  rackCols: undefined,
  slotCapacity: undefined,
  address: undefined,
  sortNo: 0,
  status: 0,
  description: undefined
})

const formData = ref<ZoneFormData>(createEmptyFormData())

const formRules = reactive({
  zoneCode: [{ required: true, message: '区域编码不能为空', trigger: 'blur' }],
  zoneName: [{ required: true, message: '区域名称不能为空', trigger: 'blur' }],
  zoneType: [{ required: true, message: '区域类型不能为空', trigger: 'change' }],
  sortNo: [{ required: true, message: '显示顺序不能为空', trigger: 'blur' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }],
  rackRows: [{ required: true, message: '货架行数不能为空', trigger: 'change' }],
  rackCols: [{ required: true, message: '货架列数不能为空', trigger: 'change' }]
})
const formRef = ref()

/** 区域类型选项 */
const zoneTypeOptions = [
  { label: '实验室', value: 'LAB' },
  { label: '房间', value: 'ROOM' },
  { label: '功能区', value: 'FUNCTION' },
  { label: '温控区', value: 'TEMP' },
  { label: '安全区', value: 'SAFETY' },
  { label: '料架', value: 'RACK' },
  { label: '台面', value: 'BENCH' },
  { label: '设备工位', value: 'DEVICE' },
  { label: '自定义', value: 'CUSTOM' }
]

/** 是否显示温区字段 */
const showTempFields = computed(() => formData.value.zoneType === 'TEMP')

/** 父区域下拉树（用于选择挂载点） */
const parentTreeOptions = ref<ZoneVO[]>([])

/** 解析 ext_data 到 formData（货架布局字段） */
const parseExtData = (extData?: string) => {
  formData.value.rackRows = undefined
  formData.value.rackCols = undefined
  formData.value.slotCapacity = undefined
  formData.value.address = undefined
  if (!extData) return
  try {
    const obj = JSON.parse(extData)
    formData.value.rackRows = obj.rackRows
    formData.value.rackCols = obj.rackCols
    formData.value.slotCapacity = obj.slotCapacity
    formData.value.address = obj.address
  } catch {
    // ignore
  }
}

/** 序列化货架布局字段到 extData */
const buildExtData = () => {
  if (formData.value.zoneType !== 'RACK') return undefined
  const obj: Record<string, any> = {}
  if (formData.value.rackRows != null) obj.rackRows = formData.value.rackRows
  if (formData.value.rackCols != null) obj.rackCols = formData.value.rackCols
  if (formData.value.slotCapacity != null) obj.slotCapacity = formData.value.slotCapacity
  if (formData.value.address) obj.address = formData.value.address
  return JSON.stringify(obj)
}

/** 打开弹窗 */
const open = async (type: string, id?: number, parentZoneCode?: string) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  resetForm()
  // 新增时，可预设父区域
  if (parentZoneCode) {
    formData.value.parentZoneCode = parentZoneCode
  }
  // 加载父区域树（用于选择挂载点）
  await loadParentTree()
  // 修改时，回显数据
  if (id) {
    formLoading.value = true
    try {
      const data = await ZoneApi.getZone(id)
      Object.assign(formData.value, data)
      parseExtData(data.extData)
    } finally {
      formLoading.value = false
    }
  }
}

/** 加载父区域下拉树 */
const loadParentTree = async () => {
  const list = await ZoneApi.getZoneList()
  parentTreeOptions.value = buildTree(list || [])
}

/** 平铺列表 → 树 */
const buildTree = (list: ZoneVO[]): ZoneVO[] => {
  const map = new Map<string, ZoneVO>()
  list.forEach((node) => map.set(node.zoneCode!, { ...node, children: [] }))
  const roots: ZoneVO[] = []
  map.forEach((node) => {
    if (node.parentZoneCode && map.has(node.parentZoneCode)) {
      map.get(node.parentZoneCode)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

/** 提交 */
const submitForm = async () => {
  // 表单校验（失败时 validate 会 reject，catch 归一为 false）
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  formLoading.value = true
  try {
    // 序列化货架布局到 ext_data（非 RACK 时为 undefined），并剥离临时字段
    formData.value.extData = buildExtData()
    const { rackRows, rackCols, slotCapacity, address, ...payload } = formData.value

    if (formType.value === 'create') {
      await ZoneApi.createZone(payload)
      message.success(t('common.createSuccess'))
    } else {
      await ZoneApi.updateZone(payload)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 重置表单 */
const resetForm = () => {
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
}

/** 对外暴露 open 方法 */
defineExpose({ open })

const emit = defineEmits<{ success: [] }>()
</script>
