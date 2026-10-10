<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="760px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-divider content-position="left">基本信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="规则编码" prop="codecCode">
            <el-input v-model="formData.codecCode" maxlength="64" placeholder="请输入规则编码，如 TECAN_READER_RESULT_V1" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="规则名称" prop="codecName">
            <el-input v-model="formData.codecName" maxlength="128" placeholder="请输入规则名称" />
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
      </el-row>
      <el-form-item label="备注">
        <el-input v-model="formData.remark" maxlength="512" placeholder="请输入备注" />
      </el-form-item>

      <el-divider content-position="left">解析配置</el-divider>
      <el-form-item label="解析类型" prop="parseType">
        <el-radio-group v-model="formData.parseType">
          <el-radio-button value="JSON">JSON 路径</el-radio-button>
          <el-radio-button value="REGEX">正则表达式</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="formData.parseType === 'REGEX'" label="正则表达式" prop="regexPattern">
        <el-input v-model="formData.regexPattern" maxlength="1024" placeholder="请输入正则表达式；字段映射中以 group:1 或 group:名称 引用捕获组" />
      </el-form-item>
      <el-form-item label="字段映射">
        <el-input
          v-model="formData.fieldMapping"
          type="textarea"
          :rows="6"
          placeholder='JSON 对象，如 {"wells":"data.list"}。孔位字段识别 wellPosition 或 parentPositionCode，读数字段识别 readValue 或 currentVolUl'
        />
      </el-form-item>

      <el-divider content-position="left">解析测试</el-divider>
      <el-form-item label="样例报文">
        <el-input v-model="formData.sampleRaw" type="textarea" :rows="6" placeholder="请粘贴设备返回的原始报文，用于测试解析并留作对照" />
      </el-form-item>
      <el-form-item v-if="previewText" label="解析结果">
        <pre class="preview">{{ previewText }}</pre>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" @click="runPreview">测试解析</el-button>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { DeviceCodecApi, DeviceCodecVO } from '@/api/device/codec'

defineOptions({ name: 'DeviceCodecForm' })

const { t } = useI18n()
const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const previewText = ref('')

const createEmpty = (): DeviceCodecVO => ({
  id: undefined,
  codecCode: undefined,
  codecName: undefined,
  parseType: 'JSON',
  fieldMapping: undefined,
  regexPattern: undefined,
  sampleRaw: undefined,
  status: 0,
  remark: undefined
})

const formData = ref<DeviceCodecVO>(createEmpty())
const formRules = reactive({
  codecCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
  codecName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  parseType: [{ required: true, message: '解析类型不能为空', trigger: 'change' }],
  regexPattern: [
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (formData.value.parseType === 'REGEX' && !value) return callback(new Error('正则表达式不能为空'))
        callback()
      },
      trigger: 'blur'
    }
  ]
})
const formRef = ref()
const emit = defineEmits(['success'])

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  previewText.value = ''
  formData.value = createEmpty()
  formRef.value?.clearValidate()
  if (!id) return
  formLoading.value = true
  try {
    formData.value = await DeviceCodecApi.getCodec(id)
  } finally {
    formLoading.value = false
  }
}

const runPreview = async () => {
  if (!formData.value.sampleRaw) {
    message.warning('请先填写样例报文')
    return
  }
  previewText.value = ''
  const data = await DeviceCodecApi.preview({
    parseType: formData.value.parseType,
    fieldMapping: formData.value.fieldMapping,
    regexPattern: formData.value.regexPattern,
    sampleRaw: formData.value.sampleRaw
  })
  previewText.value = JSON.stringify(data, null, 2)
}

const submitForm = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await DeviceCodecApi.createCodec(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await DeviceCodecApi.updateCodec(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.preview {
  margin: 0;
  width: 100%;
  max-height: 240px;
  overflow: auto;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}
</style>
