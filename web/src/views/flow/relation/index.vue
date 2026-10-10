<template>
  <ContentWrap title="流程步骤">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="流程" prop="definitionId">
        <el-select v-model="queryParams.definitionId" placeholder="请选择流程" clearable filterable class="!w-280px">
          <el-option
            v-for="item in definitions"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openCreate" v-hasPermi="['flow:relation:create']" :disabled="!canEdit">
          <Icon icon="ep:plus" class="mr-5px" /> 关联步骤
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="流程编码" align="center" prop="pipelineKey" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="流程名称" align="center" prop="definitionName" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="版本" align="center" width="70">
        <template #default="scope">v{{ scope.row.version }}</template>
      </el-table-column>
      <el-table-column label="顺序" align="center" width="70">
        <template #default="scope">{{ (scope.row.sortNo ?? 0) + 1 }}</template>
      </el-table-column>
      <el-table-column label="节点编码" align="center" prop="nodeId" min-width="110" />
      <el-table-column label="步骤编码" align="center" prop="stepKey" min-width="110" />
      <el-table-column label="步骤名称" align="center" prop="stepName" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="设备" align="center" prop="deviceCode" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="动作" align="center" prop="actionCode" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template #default="scope">
          <el-button
            v-if="scope.row.definitionStatus === 'DRAFT'"
            v-hasPermi="['flow:relation:update']"
            link
            type="primary"
            :disabled="scope.row.sortNo === 0"
            @click="move(scope.row.id, -1)"
          >
            上移
          </el-button>
          <el-button
            v-if="scope.row.definitionStatus === 'DRAFT'"
            v-hasPermi="['flow:relation:update']"
            link
            type="primary"
            @click="move(scope.row.id, 1)"
          >
            下移
          </el-button>
          <el-button
            v-if="scope.row.definitionStatus === 'DRAFT'"
            v-hasPermi="['flow:relation:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            解除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <Dialog v-model="createVisible" title="关联步骤" width="520px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
      <el-form-item label="流程" prop="definitionId">
        <el-select v-model="createForm.definitionId" filterable placeholder="请选择草稿" class="!w-1/1">
          <el-option
            v-for="item in drafts"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="步骤" prop="stepId">
        <el-select v-model="createForm.stepId" filterable placeholder="请选择已有步骤" class="!w-1/1">
          <el-option
            v-for="item in steps"
            :key="item.id"
            :label="`${item.name}（${item.stepKey}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="节点编码" prop="nodeId">
        <el-input v-model="createForm.nodeId" maxlength="32" placeholder="空着则使用步骤编码；同一步用两次时另写" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取 消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">确 定</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import { FlowRelationApi, FlowRelationVO } from '@/api/flow/relation'
import { FlowStepApi, FlowStepVO } from '@/api/flow/step'

defineOptions({ name: 'FlowRelation' })

const message = useMessage()
const route = useRoute()

const loading = ref(false)
const list = ref<FlowRelationVO[]>([])
const total = ref(0)
const definitions = ref<FlowDefinitionVO[]>([])
const steps = ref<FlowStepVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 50,
  definitionId: undefined as number | undefined
})
const queryFormRef = ref()

const selectedDefinition = computed(() => definitions.value.find((item) => item.id === queryParams.definitionId))
const drafts = computed(() => definitions.value.filter((item) => item.status === 'DRAFT'))
const canEdit = computed(() => !queryParams.definitionId || selectedDefinition.value?.status === 'DRAFT')

const getList = async () => {
  loading.value = true
  try {
    const data = await FlowRelationApi.getPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  definitions.value = (await FlowDefinitionApi.simpleList()) || []
  steps.value = (await FlowStepApi.getList()) || []
}

const applyRoute = () => {
  const raw = route.query.definitionId
  if (raw) queryParams.definitionId = Number(raw)
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.definitionId = undefined
  queryParams.pageNo = 1
  getList()
}

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = reactive({ definitionId: undefined as number | undefined, stepId: undefined as number | undefined, nodeId: '' })
const createRules = {
  definitionId: [{ required: true, message: '请选择流程', trigger: 'change' }],
  stepId: [{ required: true, message: '请选择步骤', trigger: 'change' }],
  nodeId: [{ pattern: /^$|^[a-z][a-z0-9_]{0,31}$/, message: '小写字母开头，只含小写、数字和下划线', trigger: 'blur' }]
}

const openCreate = () => {
  createForm.definitionId = selectedDefinition.value?.status === 'DRAFT' ? queryParams.definitionId : undefined
  createForm.stepId = undefined
  createForm.nodeId = ''
  createVisible.value = true
}

const submitCreate = async () => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await FlowRelationApi.create({
      definitionId: createForm.definitionId!,
      stepId: createForm.stepId!,
      nodeId: createForm.nodeId?.trim() || undefined
    })
    message.success('已关联')
    createVisible.value = false
    queryParams.definitionId = createForm.definitionId
    await getList()
  } finally {
    creating.value = false
  }
}

const move = async (id: number, offset: number) => {
  await FlowRelationApi.move(id, offset)
  await getList()
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm('解除后，这条步骤还在步骤定义里。')
  } catch {
    return
  }
  await FlowRelationApi.delete(id)
  message.success('已解除')
  await getList()
}

watch(
  () => route.query.definitionId,
  () => {
    applyRoute()
    queryParams.pageNo = 1
    getList()
  }
)

onMounted(async () => {
  applyRoute()
  await loadOptions()
  await getList()
})
</script>
