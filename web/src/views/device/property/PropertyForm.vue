<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceTypeCode">
            <el-select v-model="formData.deviceTypeCode" placeholder="请选择设备类型" class="!w-1/1">
              <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          <el-form-item label="属性编码" prop="propertyCode">
            <el-input v-model="formData.propertyCode" maxlength="64" placeholder="请输入属性编码，如 TEMPERATURE" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="属性名称" prop="propertyName">
            <el-input v-model="formData.propertyName" maxlength="128" placeholder="请输入属性名称，如 温度" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据类型" prop="dataType">
            <el-select v-model="formData.dataType" placeholder="请选择数据类型" class="!w-1/1">
              <el-option v-for="item in dataTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位">
            <el-input v-model="formData.unit" maxlength="32" placeholder="如 ℃、%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="能力来源">
            <el-select v-model="formData.source" placeholder="请选择能力来源" class="!w-1/1">
              <el-option label="手工录入" value="MANUAL" />
              <el-option label="驱动上报" value="DEVICE_DECLARED" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">采集方式</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="支持读取">
            <el-switch v-model="formData.readable" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="支持订阅">
            <el-switch v-model="formData.subscribable" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="轮询间隔(秒)">
        <el-input-number v-model="formData.pollIntervalSec" :min="1" controls-position="right" placeholder="默认 30" />
        <span class="form-tip">设备不支持订阅时，平台按此间隔主动读取</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DevicePropertyApi, DevicePropertyVO } from '@/api/device/property'
import { useDeviceTypeDictOptions } from '../labels'

defineOptions({ name: 'DevicePropertyForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): DevicePropertyVO => ({
  id: undefined,
  deviceTypeCode: undefined,
  propertyCode: undefined,
  propertyName: undefined,
  dataType: 'STRING',
  unit: undefined,
  readable: true,
  subscribable: false,
  pollIntervalSec: 30,
  source: 'MANUAL',
  status: 0
})

const formData = ref<DevicePropertyVO>(createEmptyFormData())

const formRules = reactive({
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  propertyCode: [{ required: true, message: '属性编码不能为空', trigger: 'blur' }],
  propertyName: [{ required: true, message: '属性名称不能为空', trigger: 'blur' }],
  dataType: [{ required: true, message: '数据类型不能为空', trigger: 'change' }]
})
const formRef = ref()

const deviceTypeOptions = useDeviceTypeDictOptions()

const dataTypeOptions = [
  { label: '字符串', value: 'STRING' },
  { label: '整数', value: 'INTEGER' },
  { label: '小数', value: 'DECIMAL' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: '枚举', value: 'ENUM' },
  { label: 'JSON', value: 'JSON' }
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
      const data = await DevicePropertyApi.getProperty(id)
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
      await DevicePropertyApi.createProperty(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await DevicePropertyApi.updateProperty(formData.value)
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
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 32px;
  color: var(--el-text-color-secondary);
}
</style>
