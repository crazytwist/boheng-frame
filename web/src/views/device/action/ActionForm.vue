<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceCodecApi, DeviceCodecVO } from '@/api/device/codec'
import { useDeviceTypeDictOptions } from '../labels'

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
  httpMethod: 'POST',
  bodyFormat: 'JSON',
  requestPath: undefined,
  requestTemplate: undefined,
  codecCode: undefined,
  pollDoneExpr: undefined,
  pollMaxTimes: undefined,
  resultMode: 'SYNC_RETURN',
  callbackTimeoutSec: undefined,
  status: 0
})

const formData = ref<DeviceActionVO>(createEmptyFormData())
const codecOptions = ref<DeviceCodecVO[]>([])

const formRules = reactive({
  deviceTypeCode: [{ required: true, message: '设备类型不能为空', trigger: 'change' }],
  actionCode: [{ required: true, message: '动作编码不能为空', trigger: 'blur' }],
  actionName: [{ required: true, message: '动作名称不能为空', trigger: 'blur' }]
})
const formRef = ref()

const deviceTypeOptions = useDeviceTypeDictOptions()

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
  DeviceCodecApi.getCodecList().then((list) => {
    codecOptions.value = Array.isArray(list) ? list : []
  }).catch(() => {
    codecOptions.value = []
  })
  if (id) {
    formLoading.value = true
    try {
      const data = await DeviceActionApi.getAction(id)
      Object.assign(formData.value, data)
      if (!formData.value.bodyFormat) formData.value.bodyFormat = 'JSON'
      if (!formData.value.resultMode) formData.value.resultMode = 'SYNC_RETURN'
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
    message.error('参数定义不是合法的 JSON')
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
      message.error('参数定义不是合法的 JSON，请检查后重试')
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
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
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
              <el-radio v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="动作编码" prop="actionCode">
            <el-input v-model="formData.actionCode" maxlength="64" placeholder="请输入动作编码，如 READ_PLATE" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="动作名称" prop="actionName">
            <el-input v-model="formData.actionName" maxlength="128" placeholder="请输入动作名称，如 读板" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="能力来源">
            <el-select v-model="formData.source" placeholder="请选择能力来源" class="!w-1/1">
              <el-option v-for="item in sourceOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预估耗时(毫秒)">
            <el-input-number v-model="formData.estimateDurationMs" :min="0" :step="1000" controls-position="right" class="!w-1/1" placeholder="小于 3000 方可同步下发" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="SiLA 标准特性">
        <el-input v-model="formData.standardFeature" maxlength="128" placeholder="如 com.sila_standard.run_control；私有动作无需填写" />
      </el-form-item>
      <el-form-item label="厂商映射">
        <el-input v-model="formData.vendorRef" maxlength="255" placeholder="厂商侧对应的脚本名或方法名" />
      </el-form-item>

      <el-divider content-position="left">请求配置</el-divider>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="请求方法">
            <el-select v-model="formData.httpMethod" placeholder="请选择" clearable class="!w-1/1">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="PATCH" value="PATCH" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="请求路径" label-width="90px">
            <el-input v-model="formData.requestPath" maxlength="255" placeholder="相对设备接入地址的路径，如 /api/read" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="报文格式">
            <el-select v-model="formData.bodyFormat" class="!w-1/1">
              <el-option label="JSON" value="JSON" />
              <el-option label="表单" value="FORM" />
              <el-option label="纯文本" value="TEXT" />
              <el-option label="XML" value="XML" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="请求模板">
        <el-input
          v-model="formData.requestTemplate"
          type="textarea"
          :rows="3"
          placeholder="选填，使用 ${参数名} 引用调用参数；报文格式为 XML 时必须填写"
        />
      </el-form-item>
      <el-form-item label="参数定义">
        <div class="json-editor-wrap">
          <el-input v-model="formData.paramSchema" type="textarea" :rows="8" :placeholder="PARAM_SCHEMA_PLACEHOLDER" />
          <div class="json-editor-tip">
            <span>JSON 格式，调用时据此生成参数表单</span>
            <el-button link type="primary" @click="formatParamSchema">格式化</el-button>
          </div>
        </div>
      </el-form-item>

      <el-divider content-position="left">结果处理</el-divider>
      <el-form-item label="结果获取方式">
        <el-radio-group v-model="formData.resultMode">
          <el-radio-button value="SYNC_RETURN">同步返回</el-radio-button>
          <el-radio-button value="CALLBACK">设备回调</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="formData.resultMode === 'CALLBACK'">
        <el-form-item label="回调超时(秒)">
          <el-input-number v-model="formData.callbackTimeoutSec" :min="1" :max="600" controls-position="right" placeholder="为空时取设备配置" />
          <span class="form-tip">最长 600 秒</span>
        </el-form-item>
        <el-alert class="!mb-18px" type="info" :closable="false" show-icon>
          <template #title>
            设备收到请求后先返回“已受理”，执行完成后将结果推送至平台回调地址，平台收到回调后命令才算完成。
            使用此方式前，请在设备台账中开启“结果回调”。
          </template>
        </el-alert>
      </template>
      <el-form-item label="解析规则">
        <el-select v-model="formData.codecCode" clearable filterable placeholder="选填，调用成功后按此规则解析测量数据" class="!w-1/1">
          <el-option v-for="item in codecOptions" :key="item.codecCode" :label="`${item.codecName} (${item.codecCode})`" :value="item.codecCode!" />
        </el-select>
      </el-form-item>
      <el-row :gutter="20">
        <el-col :span="16">
          <el-form-item label="完成判定表达式">
            <el-input v-model="formData.pollDoneExpr" maxlength="255" placeholder="轮询时使用，如 $.status == &quot;DONE&quot;" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="最大轮询次数" label-width="110px">
            <el-input-number v-model="formData.pollMaxTimes" :min="1" controls-position="right" class="!w-1/1" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">下发前检查</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="空闲检查">
            <el-switch v-model="formData.needBusyCheck" active-text="需要" inactive-text="不需要" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态查询动作">
            <el-input v-model="formData.statusCommandCode" maxlength="64" placeholder="如 GET_STATUS，选填" :disabled="!formData.needBusyCheck" />
          </el-form-item>
        </el-col>
      </el-row>
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
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  line-height: 32px;
  color: var(--el-text-color-secondary);
}
.json-editor-tip {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 24px;
}
</style>
