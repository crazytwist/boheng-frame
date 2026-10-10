<script lang="ts" setup>
import { FlowDefinitionApi } from '@/api/flow/definition'

defineOptions({ name: 'FlowDefinitionForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const formData = ref({
  id: undefined as number | undefined,
  pipelineKey: '',
  name: '',
  remark: ''
})

const formRules = reactive({
  pipelineKey: [
    { required: true, message: '流程编码不能为空', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '小写字母开头，只含小写、数字和下划线', trigger: 'blur' }
  ],
  name: [{ required: true, message: '流程名称不能为空', trigger: 'blur' }]
})
const formRef = ref()

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = { id: undefined, pipelineKey: '', name: '', remark: '' }
  formRef.value?.clearValidate()
  if (!id) return
  formLoading.value = true
  try {
    const data = await FlowDefinitionApi.get(id)
    formData.value = {
      id: data.id,
      pipelineKey: data.pipelineKey || '',
      name: data.name || '',
      remark: data.remark || ''
    }
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
      await FlowDefinitionApi.create({
        pipelineKey: formData.value.pipelineKey.trim(),
        name: formData.value.name.trim(),
        remark: formData.value.remark
      })
      message.success(t('common.createSuccess'))
    } else {
      await FlowDefinitionApi.save({
        id: formData.value.id!,
        name: formData.value.name.trim(),
        remark: formData.value.remark
      })
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
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="流程编码" prop="pipelineKey">
        <el-input v-model="formData.pipelineKey" maxlength="64" placeholder="小写字母开头，如 plate_read" :disabled="formType !== 'create'" />
      </el-form-item>
      <el-form-item label="流程名称" prop="name">
        <el-input v-model="formData.name" maxlength="128" placeholder="请输入流程名称，如 离心后读板" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="formData.remark" type="textarea" :rows="3" maxlength="512" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>
