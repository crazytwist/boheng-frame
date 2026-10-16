<!-- WMS 样本登记 / 编辑表单 -->
<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="720px">
    <el-form ref="formRef" v-loading="formLoading" :model="formData" :rules="formRules" label-width="120px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="样本编号" prop="sampleId">
            <el-input v-model="formData.sampleId" maxlength="128" placeholder="如 SP-20261011-001（不填自动生成）" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="样本名称" prop="sampleName">
            <el-input v-model="formData.sampleName" maxlength="128" placeholder="如 地表水样 A1" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="样本类型" prop="sampleType">
            <el-select v-model="formData.sampleType" placeholder="请选择" class="!w-1/1">
              <el-option v-for="item in sampleTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="父样本编号">
            <el-select v-model="formData.parentSampleId" filterable clearable placeholder="分样/分装时选母样本" class="!w-1/1">
              <el-option
                v-for="item in parentSampleOptions"
                :key="item.id"
                :label="`${item.sampleName || item.sampleId}（${item.sampleId}）`"
                :value="item.sampleId!"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="衍生类型">
            <el-select v-model="formData.deriveType" clearable placeholder="有父样本时选择，原始样本留空" class="!w-1/1">
              <el-option label="拆分" value="SPLIT" />
              <el-option label="分装" value="ALIQUOT" />
              <el-option label="合并" value="MERGE" />
              <el-option label="加试剂" value="ADD_REAGENT" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="内容物编码">
            <el-select
              v-model="formData.contentDefCode"
              filterable
              clearable
              placeholder="请选择内容物"
              class="!w-1/1"
              @change="handleContentDefChange"
            >
              <el-option
                v-for="item in contentOptions"
                :key="item.id"
                :label="`${item.contentName || item.contentCode}（${item.contentCode}）`"
                :value="item.contentCode!"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="期望容器类型">
            <el-select v-model="formData.containerTypeCode" filterable clearable placeholder="请选择容器类型" class="!w-1/1">
              <el-option
                v-for="item in containerTypeOptions"
                :key="item.id"
                :label="`${item.typeName || item.typeCode}（${item.typeCode}）`"
                :value="item.typeCode!"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="初始体积(μL)">
            <el-input-number v-model="formData.initialVolUl" :min="0" :precision="2" controls-position="right" class="!w-1/1" placeholder="登记承诺量" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级">
            <el-select v-model="formData.priority" placeholder="请选择" class="!w-1/1">
              <el-option label="普通" :value="0" />
              <el-option label="加急" :value="1" />
              <el-option label="特急" :value="2" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="样本批次号">
            <el-input v-model="formData.batchNo" maxlength="64" placeholder="同一批送检样本共享" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="检测单号">
            <el-input v-model="formData.orderNo" maxlength="64" placeholder="如 ORD-20261011-001" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="来源">
            <el-select v-model="formData.source" placeholder="请选择" class="!w-1/1">
              <el-option label="手动" value="MANUAL" />
              <el-option label="导入" value="IMPORT" />
              <el-option label="LIS 对接" value="LIS" />
              <el-option label="设备采集" value="DEVICE" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="状态">
            <el-tag v-if="formData.status" :type="statusTagType(formData.status)">{{ statusLabel(formData.status) }}</el-tag>
            <span v-else class="color-#909399 text-13px">—</span>
            <div class="text-12px color-#909399 mt-4px">状态由流程驱动流转，此处仅展示</div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="实验参数">
        <el-input v-model="formData.experimentParams" type="textarea" :rows="3" placeholder='JSON，如 {"targetPh":7.2,"volumeUl":500}' />
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
import { SampleApi, SampleVO } from '@/api/wms/sample'
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'

defineOptions({ name: 'WmsSampleForm' })

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')

const createEmptyFormData = (): SampleVO => ({
  id: undefined,
  sampleId: undefined,
  parentSampleId: undefined,
  deriveType: undefined,
  sampleName: undefined,
  sampleType: undefined,
  contentDefId: undefined,
  contentDefCode: undefined,
  containerTypeCode: undefined,
  initialVolUl: undefined,
  experimentParams: undefined,
  batchNo: undefined,
  orderNo: undefined,
  priority: 0,
  source: 'MANUAL',
  status: 'REGISTERED',
  description: undefined
})

const formData = ref<SampleVO>(createEmptyFormData())

// 下拉选项数据
const contentOptions = ref<ContentDefVO[]>([])
const containerTypeOptions = ref<ContainerTypeVO[]>([])
const allSampleOptions = ref<SampleVO[]>([])

// 父样本下拉：排除自身，避免循环谱系
const parentSampleOptions = computed(() =>
  allSampleOptions.value.filter((s) => s.id !== formData.value.id)
)

const loadOptions = async () => {
  const [contentList, containerList, sampleList] = await Promise.all([
    ContentDefApi.getContentDefList(),
    ContainerTypeApi.getContainerTypeList(),
    SampleApi.getSampleList()
  ])
  contentOptions.value = contentList || []
  containerTypeOptions.value = containerList || []
  allSampleOptions.value = sampleList || []
}

// 选内容物编码 → 联动带出 contentDefId（样本既存编码也存主键，供「生成实例」反查）
const handleContentDefChange = (code?: string) => {
  const cd = contentOptions.value.find((c) => c.contentCode === code)
  formData.value.contentDefId = cd?.id
}

const formRules = reactive({
  sampleId: [{ required: true, message: '样本编号不能为空', trigger: 'blur' }],
  sampleType: [{ required: true, message: '样本类型不能为空', trigger: 'change' }]
})
const formRef = ref()

const sampleTypeOptions = [
  { label: '水样', value: 'WATER' },
  { label: '血样', value: 'BLOOD' },
  { label: '尿样', value: 'URINE' },
  { label: '组织', value: 'TISSUE' },
  { label: '土样', value: 'SOIL' },
  { label: '气样', value: 'AIR' },
  { label: '食品', value: 'FOOD' },
  { label: '混合物', value: 'MIXTURE' },
  { label: '其他', value: 'OTHER' }
]

const STATUS_META: Record<string, { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }> = {
  REGISTERED: { label: '已登记', type: 'info' },
  LOADED: { label: '已装载', type: 'primary' },
  IN_PROCESS: { label: '处理中', type: 'warning' },
  SPLIT: { label: '已拆分', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
  REJECTED: { label: '不合格', type: 'danger' },
  LOST: { label: '丢失', type: 'danger' }
}
const statusLabel = (v?: string) => STATUS_META[v || '']?.label || v || '—'
const statusTagType = (v?: string) => STATUS_META[v || '']?.type || 'info'

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  // 先并行加载下拉数据，再回填详情（编辑时父样本下拉会自动排除自身）
  await loadOptions()
  if (id) {
    formLoading.value = true
    try {
      const data = await SampleApi.getSample(id)
      Object.assign(formData.value, data)
    } finally {
      formLoading.value = false
    }
  }
}

/**
 * 从母样本「拆分」入口打开：生成子样本（登记态），预填谱系并继承母样本物质。
 * 子样本登记后，可再对它「生成实例」，实现独立走流程。
 */
const openForSplit = async (parent: SampleVO) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.create')
  formType.value = 'create'
  formData.value = createEmptyFormData()
  formRef.value?.clearValidate()
  formLoading.value = true
  try {
    await loadOptions()
    // 谱系：父样本 + 默认拆分（用户可改）
    formData.value.parentSampleId = parent.sampleId
    formData.value.deriveType = 'SPLIT'
    // 继承母样本物质与业务属性（子样本物质相同）
    formData.value.sampleType = parent.sampleType
    formData.value.contentDefId = parent.contentDefId
    formData.value.contentDefCode = parent.contentDefCode
    formData.value.containerTypeCode = parent.containerTypeCode
    formData.value.batchNo = parent.batchNo
    formData.value.orderNo = parent.orderNo
    formData.value.source = parent.source
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
      await SampleApi.createSample(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await SampleApi.updateSample(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

defineExpose({ open, openForSplit })
const emit = defineEmits<{ success: [] }>()
</script>
