<!-- WMS 容器类型表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="容器编码" prop="typeCode">
            <el-input v-model="formData.typeCode" maxlength="64" placeholder="如 PLATE_96_WELL" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="容器名称" prop="typeName">
            <el-input v-model="formData.typeName" maxlength="128" placeholder="如 96 孔板" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="物理形态" prop="category">
            <el-select v-model="formData.category" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in categoryOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="层级角色" prop="hierarchyRole">
            <el-select v-model="formData.hierarchyRole" placeholder="请选择" class="!w-1/1">
              <el-option label="载体（承载其他容器）" value="CARRIER" />
              <el-option label="容器（直接装内容物）" value="CONTAINER" />
              <el-option label="孔位（最小单元）" value="WELL" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 容量（CONTAINER/WELL） -->
      <el-row v-if="formData.hierarchyRole !== 'CARRIER'" :gutter="20">
        <el-col :span="12">
          <el-form-item label="最大容积(μL)">
            <el-input-number v-model="formData.maxVolUl" :min="0" :precision="2" controls-position="right" class="!w-1/1" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 孔位规格（CARRIER） -->
      <template v-if="formData.hierarchyRole === 'CARRIER'">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="位数">
              <el-input-number v-model="formData.wellCount" :min="1" controls-position="right" class="!w-1/1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="行数">
              <el-input-number v-model="formData.wellRows" :min="1" controls-position="right" class="!w-1/1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="列数">
              <el-input-number v-model="formData.wellCols" :min="1" controls-position="right" class="!w-1/1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="子单元类型编码">
          <el-input v-model="formData.childTypeCode" maxlength="64" placeholder="如 WELL_STANDARD（承载的子单元）" />
        </el-form-item>
      </template>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="位置命名" prop="positionNaming">
            <el-select v-model="formData.positionNaming" placeholder="请选择" class="!w-1/1">
              <el-option label="行+列（A1）" value="ROW_COL" />
              <el-option label="顺序号（1..96）" value="SEQ" />
              <el-option label="行+列+层（A1-L1）" value="ROW_COL_LAYER" />
              <el-option label="无位置概念" value="NONE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="使用类型" prop="usageType">
            <el-select v-model="formData.usageType" placeholder="请选择" class="!w-1/1">
              <el-option label="周转复用" value="REUSABLE" />
              <el-option label="一次性耗材" value="DISPOSABLE" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="复用次数上限">
            <el-input-number v-model="formData.lifeCycles" :min="0" controls-position="right" class="!w-1/1" placeholder="空为不限" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="允许被装入">
            <el-switch v-model="formData.nestable" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="图片">
        <div class="image-upload-wrap">
          <UploadImg v-model="formData.imageUrl" />
          <span class="image-tip">可选，未上传时展示默认图</span>
        </div>
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="formData.status">
              <el-radio v-for="dict in getIntDictOptions(DICT_TYPE.COMMON_STATUS)" :key="dict.value" :value="dict.value">
                {{ dict.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>

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
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import { UploadImg } from '@/components/UploadFile'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'

defineOptions({ name: 'WmsContainerTypeForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): ContainerTypeVO => ({
  id: undefined,
  typeCode: undefined,
  typeName: undefined,
  imageUrl: undefined,
  category: undefined,
  hierarchyRole: undefined,
  maxVolUl: undefined,
  wellCount: undefined,
  wellRows: undefined,
  wellCols: undefined,
  positionNaming: 'ROW_COL',
  childTypeCode: undefined,
  specJson: undefined,
  nestable: true,
  usageType: 'REUSABLE',
  lifeCycles: undefined,
  status: 0,
  description: undefined
})

const formData = ref<ContainerTypeVO>(createEmptyFormData())

const formRules = reactive({
  typeCode: [{ required: true, message: '容器编码不能为空', trigger: 'blur' }],
  typeName: [{ required: true, message: '容器名称不能为空', trigger: 'blur' }],
  category: [{ required: true, message: '物理形态不能为空', trigger: 'change' }],
  hierarchyRole: [{ required: true, message: '层级角色不能为空', trigger: 'change' }],
  positionNaming: [{ required: true, message: '位置命名不能为空', trigger: 'change' }],
  usageType: [{ required: true, message: '使用类型不能为空', trigger: 'change' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})
const formRef = ref()

const categoryOptions = [
  { label: '孔板', value: 'PLATE' },
  { label: '试管/离心管', value: 'TUBE' },
  { label: '瓶', value: 'BOTTLE' },
  { label: '托盘架', value: 'RACK' },
  { label: '小瓶', value: 'VIAL' },
  { label: '吸头', value: 'TIP' },
  { label: '芯片', value: 'CHIP' },
  { label: '滤膜', value: 'FILTER' },
  { label: '盒', value: 'BOX' },
  { label: '袋', value: 'BAG' },
  { label: '其他', value: 'OTHER' }
]

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  if (id) {
    formLoading.value = true
    try {
      const data = await ContainerTypeApi.getContainerType(id)
      Object.assign(formData.value, data)
    } finally {
      formLoading.value = false
    }
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await ContainerTypeApi.createContainerType(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await ContainerTypeApi.updateContainerType(formData.value)
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
.image-upload-wrap {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.image-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 32px;
}
</style>
