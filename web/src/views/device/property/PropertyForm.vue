<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="680px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="110px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="设备类型" prop="deviceTypeCode">
            <el-select v-model="formData.deviceTypeCode" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="属性编码" prop="propertyCode">
            <el-input v-model="formData.propertyCode" maxlength="64" placeholder="如 TEMPERATURE" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="属性名称" prop="propertyName">
            <el-input v-model="formData.propertyName" maxlength="128" placeholder="如 温度" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="数据类型" prop="dataType">
            <el-select v-model="formData.dataType" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in dataTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="单位">
            <el-input v-model="formData.unit" maxlength="32" placeholder="如 ℃ / %" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="轮询间隔(秒)">
            <el-input-number v-model="formData.pollIntervalSec" :min="1" controls-position="right" class="!w-1/1"
              placeholder="默认 30" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="可读">
            <el-switch v-model="formData.readable" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="可订阅">
            <el-switch v-model="formData.subscribable" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-select v-model="formData.status" class="!w-1/1">
              <el-option label="启用" :value="0" />
              <el-option label="停用" :value="1" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="能力来源">
        <el-select v-model="formData.source" placeholder="请选择" class="!w-1/1">
          <el-option label="手工录入" value="MANUAL" />
          <el-option label="驱动上报" value="DEVICE_DECLARED" />
        </el-select>
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

const deviceTypeOptions = [
  { label: '酶标仪/读板机', value: 'PLATE_READER' },
  { label: '液体处理工作站', value: 'LIQUID_HANDLER' },
  { label: '机械臂', value: 'ROBOTIC_ARM' },
  { label: '培养箱', value: 'INCUBATOR' },
  { label: '离心机', value: 'CENTRIFUGE' },
  { label: '其他', value: 'OTHER' }
]

const dataTypeOptions = [
  { label: '字符串 STRING', value: 'STRING' },
  { label: '整数 INTEGER', value: 'INTEGER' },
  { label: '小数 DECIMAL', value: 'DECIMAL' },
  { label: '布尔 BOOLEAN', value: 'BOOLEAN' },
  { label: '枚举 ENUM', value: 'ENUM' },
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
