<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="参数集编码" prop="paramSetCode">
            <el-input v-model="formData.paramSetCode" maxlength="64" placeholder="请输入参数集编码，如 PS-ABSORB-450" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="参数集名称" prop="paramSetName">
            <el-input v-model="formData.paramSetName" maxlength="128" placeholder="请输入参数集名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceTypeCode">
            <el-select v-model="formData.deviceTypeCode" placeholder="请选择设备类型" class="!w-1/1" @change="handleTypeChange">
              <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="关联动作" prop="actionCode">
            <el-select
              v-model="formData.actionCode"
              :placeholder="formData.deviceTypeCode ? '请选择关联动作' : '请先选择设备类型'"
              clearable
              filterable
              allow-create
              default-first-option
              class="!w-1/1"
            >
              <el-option v-for="item in actionOptions" :key="item.actionCode" :label="actionLabel(item)" :value="item.actionCode!" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-radio-group v-model="formData.status">
              <el-radio :value="0">启用</el-radio>
              <el-radio :value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="验证状态">
            <el-switch v-model="formData.validated" active-text="已验证" inactive-text="未验证" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">参数配置</el-divider>
      <el-form-item label="参数取值" prop="paramsJson">
        <div class="json-editor-wrap">
          <el-input v-model="formData.paramsJson" type="textarea" :rows="8" :placeholder="paramsPlaceholder" />
          <div class="json-editor-tip">
            <span>须与关联动作的参数定义一致；仅已验证的参数集可用于下发命令</span>
            <el-button link type="primary" @click="formatParamsJson">格式化</el-button>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceParamSetApi, DeviceParamSetVO } from '@/api/device/paramSet'
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { useDeviceTypeDictOptions } from '../labels'

defineOptions({ name: 'DeviceParamSetForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): DeviceParamSetVO => ({
  id: undefined,
  paramSetCode: undefined,
  paramSetName: undefined,
  deviceTypeCode: undefined,
  actionCode: undefined,
  paramsJson: undefined,
  validated: false,
  validatedBy: undefined,
  validatedTime: undefined,
  status: 0
})

const formData = ref<DeviceParamSetVO>(createEmptyFormData())

const formRules = reactive({
  paramSetCode: [{ required: true, message: '参数集编码不能为空', trigger: 'blur' }],
  paramSetName: [{ required: true, message: '参数集名称不能为空', trigger: 'blur' }],
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  actionCode: [{ required: true, message: '关联动作不能为空', trigger: 'change' }],
  paramsJson: [{ required: true, message: '参数取值不能为空', trigger: 'blur' }]
})
const formRef = ref()

const deviceTypeOptions = useDeviceTypeDictOptions()

// 按设备类型拉取动作，供「关联动作」下拉
const actionOptions = ref<DeviceActionVO[]>([])
const actionLabel = (item: DeviceActionVO) => (item.actionName ? `${item.actionCode}（${item.actionName}）` : item.actionCode!)

const paramsPlaceholder = `{
  "wavelength": 450,
  "mode": "ABSORBANCE"
}`

const loadActions = async () => {
  if (!formData.value.deviceTypeCode) {
    actionOptions.value = []
    return
  }
  const data = await DeviceActionApi.getActionList({ deviceTypeCode: formData.value.deviceTypeCode })
  actionOptions.value = data || []
}

const handleTypeChange = () => {
  formData.value.actionCode = undefined
  loadActions()
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  actionOptions.value = []
  if (id) {
    formLoading.value = true
    try {
      const data = await DeviceParamSetApi.getParamSet(id)
      Object.assign(formData.value, data)
      await loadActions()
    } finally {
      formLoading.value = false
    }
  }
}

// 校验并美化 JSON（参数取值）
const formatParamsJson = () => {
  if (!formData.value.paramsJson) return
  try {
    formData.value.paramsJson = JSON.stringify(JSON.parse(formData.value.paramsJson), null, 2)
  } catch {
    message.error('参数取值不是合法的 JSON')
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 参数取值必须是合法 JSON
  try {
    JSON.parse(formData.value.paramsJson!)
  } catch {
    message.error('参数取值不是合法的 JSON，请检查后重试')
    return
  }
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await DeviceParamSetApi.createParamSet(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await DeviceParamSetApi.updateParamSet(formData.value)
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
.json-editor-wrap {
  width: 100%;
}
.json-editor-tip {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 24px;
}
</style>
