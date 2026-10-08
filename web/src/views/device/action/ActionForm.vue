<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'

defineOptions({ name: 'DeviceActionForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): DeviceActionVO => ({
  id: undefined,
  deviceTypeCode: undefined,
  actionCode: undefined,
  actionName: undefined,
  standardFeature: undefined,
  paramSchema: undefined,
  vendorRef: undefined,
  source: 'MANUAL',
  estimateDurationMs: undefined,
  needBusyCheck: false,
  statusCommandCode: undefined,
  requestTemplate: undefined,
  pollDoneExpr: undefined,
  pollMaxTimes: undefined,
  status: 0
})

const formData = ref<DeviceActionVO>(createEmptyFormData())

const formRules = reactive({
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  actionCode: [{ required: true, message: '动作编码不能为空', trigger: 'blur' }],
  actionName: [{ required: true, message: '动作名称不能为空', trigger: 'blur' }]
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

const sourceOptions = [
  { label: '手工录入', value: 'MANUAL' },
  { label: '驱动上报', value: 'DEVICE_DECLARED' }
]

const statusOptions = [
  { label: '启用', value: 0 },
  { label: '停用', value: 1 }
]

// 参数模式示例（降低填写门槛）
const PARAM_SCHEMA_PLACEHOLDER = `{
  "wavelength": { "label": "主波长", "type": "INTEGER", "unit": "nm", "required": true, "default": 450 },
  "mode": { "label": "检测模式", "type": "ENUM", "constraints": ["ABSORBANCE", "FLUORESCENCE"] }
}`

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  if (id) {
    formLoading.value = true
    try {
      const data = await DeviceActionApi.getAction(id)
      Object.assign(formData.value, data)
    } finally {
      formLoading.value = false
    }
  }
}

// 校验并美化 JSON（参数模式）
const formatParamSchema = () => {
  if (!formData.value.paramSchema) return
  try {
    formData.value.paramSchema = JSON.stringify(JSON.parse(formData.value.paramSchema), null, 2)
  } catch {
    message.error('参数模式不是合法的 JSON')
  }
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // 参数模式若填写，必须是合法 JSON
  if (formData.value.paramSchema) {
    try {
      JSON.parse(formData.value.paramSchema)
    } catch {
      message.error('参数模式不是合法的 JSON，请检查或点击「格式化」')
      return
    }
  }
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await DeviceActionApi.createAction(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await DeviceActionApi.updateAction(formData.value)
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

<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
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
          <el-form-item label="动作编码" prop="actionCode">
            <el-input v-model="formData.actionCode" maxlength="64" placeholder="如 READ_PLATE" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="动作名称" prop="actionName">
            <el-input v-model="formData.actionName" maxlength="128" placeholder="如 读板" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预估耗时">
            <el-input-number v-model="formData.estimateDurationMs" :min="0" :step="1000" controls-position="right"
              class="!w-1/1" placeholder="毫秒；<3000 才允许同步下发" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="SiLA 标准特性">
        <el-input v-model="formData.standardFeature" maxlength="128"
          placeholder="如 com.sila_standard.run_control（私有动作留空）" />
      </el-form-item>

      <el-form-item label="厂商映射">
        <el-input v-model="formData.vendorRef" maxlength="255" placeholder="如 i-control 脚本名 / 方法名" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="能力来源">
            <el-select v-model="formData.source" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-select v-model="formData.status" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="下发前查空闲">
            <el-switch v-model="formData.needBusyCheck" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="忙闲查询动作">
            <el-input v-model="formData.statusCommandCode" maxlength="64" placeholder="如 GET_STATUS，可空" :disabled="!formData.needBusyCheck" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="请求模板">
        <el-input v-model="formData.requestTemplate" type="textarea" :rows="3" placeholder="支持 ${参数名} 占位，可空" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-form-item label="完成判定">
            <el-input v-model="formData.pollDoneExpr" maxlength="255" placeholder="如 $.status == &quot;DONE&quot;" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="最多轮询">
            <el-input-number v-model="formData.pollMaxTimes" :min="1" controls-position="right" class="!w-1/1" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="参数模式">
        <div class="json-editor-wrap">
          <el-input v-model="formData.paramSchema" type="textarea" :rows="8" :placeholder="PARAM_SCHEMA_PLACEHOLDER" />
          <div class="json-editor-tip">
            <span>JSON Schema，前端按它动态渲染参数表单</span>
            <el-button link type="primary" @click="formatParamSchema">格式化</el-button>
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
