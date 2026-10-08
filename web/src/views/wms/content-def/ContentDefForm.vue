<!-- WMS 内容物定义表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="内容物编码" prop="contentCode">
            <el-input v-model="formData.contentCode" maxlength="64" placeholder="如 PH-BUFFER-7" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="内容物名称" prop="contentName">
            <el-input v-model="formData.contentName" maxlength="128" placeholder="如 pH7 标准缓冲液" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="类型" prop="contentType">
            <el-select v-model="formData.contentType" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in contentTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计量单位" prop="unit">
            <el-select v-model="formData.unit" placeholder="请选择" class="!w-1/1">
              <el-option label="μL" value="UL" />
              <el-option label="mL" value="ML" />
              <el-option label="mg" value="MG" />
              <el-option label="g" value="G" />
              <el-option label="个" value="COUNT" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="标准浓度">
            <el-input v-model="formData.concentration" maxlength="64" placeholder="如 1mol/L / 75% / pH7.0" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="存储条件">
            <el-select v-model="formData.storageCond" placeholder="请选择" clearable class="!w-1/1">
              <el-option label="常温" value="RT" />
              <el-option label="2~8℃" value="C2_8" />
              <el-option label="-20℃" value="F20" />
              <el-option label="-80℃" value="Ultra80" />
              <el-option label="冻融" value="FROZTHAW" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="供应商">
            <el-input v-model="formData.supplier" maxlength="128" placeholder="供应商名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商货号">
            <el-input v-model="formData.catalogNo" maxlength="64" placeholder="目录号" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="CAS 号">
            <el-input v-model="formData.casNo" maxlength="32" placeholder="如 9002-93-1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="危险等级">
            <el-select v-model="formData.hazardLevel" placeholder="请选择" clearable class="!w-1/1">
              <el-option label="无" value="NONE" />
              <el-option label="低危" value="LOW" />
              <el-option label="中危" value="MEDIUM" />
              <el-option label="高危" value="HIGH" />
              <el-option label="易燃" value="FLAMMABLE" />
              <el-option label="有毒" value="TOXIC" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="保质期(天)">
            <el-input-number v-model="formData.shelfLifeDays" :min="0" controls-position="right" class="!w-1/1" placeholder="空为不限" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="开封有效期(天)">
            <el-input-number v-model="formData.openLifeDays" :min="0" controls-position="right" class="!w-1/1" placeholder="空为不限" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="FEFO 出库">
        <el-switch v-model="formData.fefo" active-text="先到期先出" inactive-text="不启用" />
      </el-form-item>

      <el-form-item label="图片">
        <div class="image-upload-wrap">
          <UploadImg v-model="formData.imageUrl" />
          <span class="image-tip">可选，未上传时展示默认图</span>
        </div>
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
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import { UploadImg } from '@/components/UploadFile'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'

defineOptions({ name: 'WmsContentDefForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): ContentDefVO => ({
  id: undefined,
  contentCode: undefined,
  contentName: undefined,
  imageUrl: undefined,
  contentType: undefined,
  unit: undefined,
  supplier: undefined,
  catalogNo: undefined,
  casNo: undefined,
  concentration: undefined,
  storageCond: undefined,
  shelfLifeDays: undefined,
  openLifeDays: undefined,
  hazardLevel: undefined,
  fefo: true,
  specJson: undefined,
  status: 0,
  description: undefined
})

const formData = ref<ContentDefVO>(createEmptyFormData())

const formRules = reactive({
  contentCode: [{ required: true, message: '内容物编码不能为空', trigger: 'blur' }],
  contentName: [{ required: true, message: '内容物名称不能为空', trigger: 'blur' }],
  contentType: [{ required: true, message: '类型不能为空', trigger: 'change' }],
  unit: [{ required: true, message: '计量单位不能为空', trigger: 'change' }],
  status: [{ required: true, message: '状态不能为空', trigger: 'change' }]
})
const formRef = ref()

const contentTypeOptions = [
  { label: '试剂', value: 'REAGENT' },
  { label: '标准品', value: 'STANDARD' },
  { label: '缓冲液', value: 'BUFFER' },
  { label: '样本', value: 'SAMPLE' },
  { label: '废液', value: 'WASTE' },
  { label: '培养基', value: 'MEDIA' },
  { label: '溶剂', value: 'SOLVENT' },
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
      const data = await ContentDefApi.getContentDef(id)
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
      await ContentDefApi.createContentDef(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await ContentDefApi.updateContentDef(formData.value)
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
