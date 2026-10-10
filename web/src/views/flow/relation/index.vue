<template>
  <ContentWrap title="流程节点编排">
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
          <Icon icon="ep:plus" class="mr-5px" /> 添加节点
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="流程编码" align="center" prop="pipelineKey" min-width="110" :show-overflow-tooltip="true" />
      <el-table-column label="版本" align="center" width="70">
        <template #default="scope">v{{ scope.row.version }}</template>
      </el-table-column>
      <el-table-column label="序" align="center" width="50">
        <template #default="scope">{{ (scope.row.sortNo ?? 0) + 1 }}</template>
      </el-table-column>
      <el-table-column label="节点编码" align="center" prop="nodeId" min-width="110" />
      <el-table-column label="名称" align="center" prop="name" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" min-width="110">
        <template #default="scope">
          <el-tag :type="isControl(scope.row.stepType) ? 'warning' : 'primary'">
            {{ typeLabel(scope.row.stepType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="前置" align="center" min-width="130" :show-overflow-tooltip="true">
        <template #default="scope">
          {{ (scope.row.dependsOn || []).length ? (scope.row.dependsOn || []).join('、') : '起点' }}
        </template>
      </el-table-column>
      <el-table-column label="汇合" align="center" min-width="110">
        <template #default="scope">{{ joinLabel(scope.row.joinPolicy) }}</template>
      </el-table-column>
      <el-table-column label="等待(秒)" align="center" prop="timeoutSec" width="90" />
      <el-table-column label="操作" align="center" width="220" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['flow:relation:update']" link type="primary" :disabled="scope.row.definitionStatus !== 'DRAFT'" @click="openEdit(scope.row)">
            编排
          </el-button>
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
            v-hasPermi="['flow:relation:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <!-- 添加节点：挂能力 或 控制类节点 -->
  <Dialog v-model="createVisible" title="添加节点" width="560px">
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
      <el-form-item label="节点形式">
        <el-radio-group v-model="createForm.mode">
          <el-radio value="step">挂能力（步骤）</el-radio>
          <el-radio value="control">控制类节点</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="createForm.mode === 'step'">
        <el-form-item label="步骤" prop="stepId">
          <el-select v-model="createForm.stepId" filterable placeholder="请选择已有步骤" class="!w-1/1">
            <el-option
              v-for="item in steps"
              :key="item.id"
              :label="`${item.name}（${item.stepKey}·${typeLabel(item.stepType)}）`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点编码" prop="nodeId">
          <el-input v-model="createForm.nodeId" maxlength="32" placeholder="空着则使用步骤编码；同一能力用两次时另写" />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="节点编码" prop="nodeId">
          <el-input v-model="createForm.nodeId" maxlength="32" placeholder="小写字母开头，如 route_od" />
        </el-form-item>
        <el-form-item label="节点名称" prop="name">
          <el-input v-model="createForm.name" maxlength="128" placeholder="如 按 OD 值路由" />
        </el-form-item>
        <el-form-item label="节点类型" prop="stepType">
          <el-select v-model="createForm.stepType" placeholder="请选择控制类型" class="!w-1/1">
            <el-option v-for="item in CONTROL_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型配置" prop="typeConfig">
          <el-input
            v-model="createForm.typeConfig"
            type="textarea"
            :rows="4"
            maxlength="4096"
            :placeholder="controlPlaceholder"
          />
        </el-form-item>
      </template>
      <el-form-item label="前置节点">
        <el-select v-model="createForm.dependsOn" multiple filterable placeholder="空 = 起点" class="!w-1/1">
          <el-option v-for="id in nodeOptions(createForm.definitionId)" :key="id" :label="id" :value="id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取 消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">确 定</el-button>
    </template>
  </Dialog>

  <!-- 编排编辑：前置 / 汇合 / 分支 / 配置 / 参数映射 -->
  <Dialog v-model="editVisible" title="节点编排" width="640px">
    <el-form :model="editForm" label-width="100px" v-loading="editLoading">
      <el-form-item label="节点">
        <span>{{ editForm.nodeId }}（{{ typeLabel(editForm.stepType) }}）</span>
      </el-form-item>
      <el-form-item v-if="editForm.isControl" label="节点名称" required>
        <el-input v-model="editForm.name" maxlength="128" />
      </el-form-item>
      <el-form-item label="前置节点">
        <el-select v-model="editForm.dependsOn" multiple filterable placeholder="空 = 起点" class="!w-1/1">
          <el-option v-for="id in editNodeOptions" :key="id" :label="id" :value="id" />
        </el-select>
      </el-form-item>
      <el-form-item label="汇合策略">
        <el-select v-model="editForm.joinPolicy" clearable placeholder="默认 ALL_SUCCESS" class="!w-1/1">
          <el-option label="全部成功（ALL_SUCCESS）" value="ALL_SUCCESS" />
          <el-option label="任一成功（ANY_SUCCESS）" value="ANY_SUCCESS" />
          <el-option label="全部终态（ALL_DONE）" value="ALL_DONE" />
        </el-select>
      </el-form-item>
      <el-form-item label="失败策略">
        <el-select v-model="editForm.joinFailurePolicy" clearable placeholder="默认 FAIL" class="!w-1/1">
          <el-option label="跟着失败（FAIL）" value="FAIL" />
          <el-option label="裁剪本节点（SKIP）" value="SKIP" />
        </el-select>
      </el-form-item>
      <el-form-item :label="editForm.isControl ? '类型配置' : '配置覆盖'">
        <el-input
          v-model="editForm.typeConfig"
          type="textarea"
          :rows="4"
          maxlength="4096"
          :placeholder="editForm.isControl ? '控制类节点必填' : '选填。节点级覆盖步骤上的配置 JSON'"
        />
      </el-form-item>
      <!-- route 分支出口 -->
      <el-form-item v-if="editForm.stepType === 'route'" label="分支出口">
        <div class="w-1/1">
          <div v-for="(branch, index) in editForm.branches" :key="index" class="flex items-center gap-5px mb-5px">
            <el-input v-model="branch.key" maxlength="32" placeholder="出口名" class="!w-120px" />
            <el-select v-model="branch.targets" multiple filterable placeholder="目标节点" class="!flex-1">
              <el-option v-for="id in editNodeOptions" :key="id" :label="id" :value="id" />
            </el-select>
            <el-button link type="danger" @click="editForm.branches.splice(index, 1)">删</el-button>
          </div>
          <el-button link type="primary" @click="editForm.branches.push({ key: '', targets: [] })">加出口</el-button>
        </div>
      </el-form-item>
      <!-- 参数映射 -->
      <el-form-item label="参数映射">
        <div class="w-1/1">
          <div v-for="(mapping, index) in editForm.mappings" :key="index" class="flex items-center gap-5px mb-5px">
            <el-input v-model="mapping.param" maxlength="64" placeholder="参数名" class="!w-100px" />
            <el-select v-model="mapping.source" class="!w-90px">
              <el-option label="入参" value="INPUT" />
              <el-option label="节点" value="NODE" />
              <el-option label="固定" value="LITERAL" />
            </el-select>
            <el-select
              v-if="mapping.source !== 'LITERAL'"
              v-model="mapping.field"
              filterable
              allow-create
              default-first-option
              placeholder="字段/路径"
              class="!w-150px"
            >
              <el-option
                v-for="f in knownFields(mapping.source === 'NODE' ? mapping.nodeId : undefined)"
                :key="f.value"
                :label="f.label"
                :value="f.value"
              />
            </el-select>
            <el-select v-if="mapping.source === 'NODE'" v-model="mapping.nodeId" filterable placeholder="前置节点" class="!w-110px">
              <el-option v-for="id in editForm.dependsOn" :key="id" :label="id" :value="id" />
            </el-select>
            <el-input v-if="mapping.source === 'LITERAL'" v-model="mapping.value" maxlength="512" placeholder="固定值" class="!w-120px" />
            <el-button link type="danger" @click="editForm.mappings.splice(index, 1)">删</el-button>
          </div>
          <el-button link type="primary" @click="editForm.mappings.push({ param: '', source: 'INPUT' })">加映射</el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取 消</el-button>
      <el-button type="primary" :loading="editing" @click="submitEdit">确 定</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import { FlowRelationApi, FlowRelationVO } from '@/api/flow/relation'
import { FlowStepApi, FlowStepVO, STEP_TYPES } from '@/api/flow/step'

defineOptions({ name: 'FlowRelation' })

/** 控制类节点类型：只能在流程节点上配置，不进能力目录 */
const CONTROL_TYPES = [
  { value: 'route', label: '出口路由' },
  { value: 'loop', label: '循环容器' },
  { value: 'subflow', label: '子流程' },
  { value: 'timer.wait', label: '定时等待' }
] as const

const CONTROL_PLACEHOLDERS: Record<string, string> = {
  route: '{"router":{"kind":"expression","expression":"input.od > 1.0 ? \'high\' : \'default\'"},"branches":{"high":["alarm"],"default":["read"]}}',
  loop: '{"bodyEntryNodeId":"read","bodyExitNodeId":"wash","maxIterations":5,"whileExpression":"sys.needMore == true"}',
  subflow: '{"dispatchMode":"SINGLE","pipelineKey":"elisa-assay","inputJson":"{}"}',
  'timer.wait': '{"waitSeconds":60}'
}

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

const isControl = (type?: string) => !!CONTROL_TYPES.find((item) => item.value === type)
const typeLabel = (type?: string) =>
  STEP_TYPES.find((item) => item.value === type)?.label ??
  CONTROL_TYPES.find((item) => item.value === type)?.label ??
  type ??
  '—'
const joinLabel = (policy?: string) =>
  policy === 'ANY_SUCCESS' ? '任一成功' : policy === 'ALL_DONE' ? '全部终态' : '全部成功'

/**
 * 已知结构化字段：按前置节点类型给出可下钻的路径。
 * loop → iterations；subflow → children / detail[0].*（子运行详情数组）。
 * 其它类型（device/bean/script 等）输出动态，无静态 schema，留给用户手写路径。
 */
const knownFields = (nodeId?: string): { label: string; value: string }[] => {
  if (!nodeId) return []
  const type = list.value.find((item) => item.nodeId === nodeId)?.stepType
  if (type === 'loop') {
    return [{ label: 'iterations · 循环轮数', value: 'iterations' }]
  }
  if (type === 'subflow') {
    return [
      { label: 'children · 子流程数', value: 'children' },
      { label: 'detail[0].executionId · 首个子运行ID', value: 'detail[0].executionId' },
      { label: 'detail[0].executionNo · 首个子运行业务号', value: 'detail[0].executionNo' },
      { label: 'detail[0].status · 首个子运行状态', value: 'detail[0].status' },
      { label: 'detail[0].groupIndex · 首个子运行组号', value: 'detail[0].groupIndex' }
    ]
  }
  return []
}

/** 某流程版本下可选的前置节点编码 */
const nodeOptions = (definitionId?: number) =>
  list.value.filter((item) => item.definitionId === definitionId).map((item) => item.nodeId!)

const getList = async () => {
  loading.value = true
  try {
    const data = await FlowRelationApi.getPage({ ...queryParams, pageSize: 100 })
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

// ==================== 添加节点 ====================

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = reactive({
  mode: 'step' as 'step' | 'control',
  definitionId: undefined as number | undefined,
  stepId: undefined as number | undefined,
  nodeId: '',
  name: '',
  stepType: undefined as string | undefined,
  typeConfig: '',
  dependsOn: [] as string[]
})
const createRules = {
  definitionId: [{ required: true, message: '请选择流程', trigger: 'change' }],
  stepId: [{ required: true, message: '请选择步骤', trigger: 'change' }],
  name: [{ required: true, message: '请填写节点名称', trigger: 'blur' }],
  stepType: [{ required: true, message: '请选择节点类型', trigger: 'change' }],
  nodeId: [{ pattern: /^$|^[a-z][a-z0-9_]{0,31}$/, message: '小写字母开头，只含小写、数字和下划线', trigger: 'blur' }]
}
const controlPlaceholder = computed(() => {
  const sample = CONTROL_PLACEHOLDERS[createForm.stepType ?? '']
  return sample ? `配置 JSON，如 ${sample}` : '类型专属配置 JSON'
})

const openCreate = () => {
  createForm.mode = 'step'
  createForm.definitionId = selectedDefinition.value?.status === 'DRAFT' ? queryParams.definitionId : undefined
  createForm.stepId = undefined
  createForm.nodeId = ''
  createForm.name = ''
  createForm.stepType = undefined
  createForm.typeConfig = ''
  createForm.dependsOn = []
  createVisible.value = true
}

const submitCreate = async () => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (createForm.mode === 'control') {
    if (!createForm.nodeId?.trim()) {
      message.error('控制类节点要填写节点编码')
      return
    }
    if (!createForm.typeConfig?.trim()) {
      message.error('控制类节点要填写类型配置')
      return
    }
  }
  creating.value = true
  try {
    await FlowRelationApi.create(
      createForm.mode === 'step'
        ? {
            definitionId: createForm.definitionId!,
            stepId: createForm.stepId!,
            nodeId: createForm.nodeId?.trim() || undefined,
            dependsOn: createForm.dependsOn
          }
        : {
            definitionId: createForm.definitionId!,
            nodeId: createForm.nodeId!.trim(),
            name: createForm.name!.trim(),
            stepType: createForm.stepType!,
            typeConfig: createForm.typeConfig!.trim(),
            dependsOn: createForm.dependsOn
          }
    )
    message.success('已添加')
    createVisible.value = false
    queryParams.definitionId = createForm.definitionId
    await getList()
  } finally {
    creating.value = false
  }
}

// ==================== 编排编辑 ====================

interface BranchRow {
  key: string
  targets: string[]
}
interface MappingRow {
  param: string
  source: string
  field?: string
  nodeId?: string
  value?: string
}

const editVisible = ref(false)
const editing = ref(false)
const editLoading = ref(false)
const editId = ref(0)
const editDefinitionId = ref<number>()
const editForm = reactive({
  nodeId: '',
  stepType: '',
  isControl: false,
  name: '',
  typeConfig: '',
  dependsOn: [] as string[],
  joinPolicy: undefined as string | undefined,
  joinFailurePolicy: undefined as string | undefined,
  branches: [] as BranchRow[],
  mappings: [] as MappingRow[]
})
const editNodeOptions = computed(() => nodeOptions(editDefinitionId.value))

const openEdit = async (row: FlowRelationVO) => {
  editId.value = row.id!
  editDefinitionId.value = row.definitionId
  editVisible.value = true
  editLoading.value = true
  try {
    const detail = list.value.find((item) => item.id === row.id) ?? row
    editForm.nodeId = detail.nodeId ?? ''
    editForm.stepType = detail.stepType ?? ''
    editForm.isControl = isControl(detail.stepType)
    editForm.name = detail.name ?? ''
    editForm.typeConfig = detail.typeConfig ?? ''
    editForm.dependsOn = [...(detail.dependsOn ?? [])]
    editForm.joinPolicy = detail.joinPolicy || undefined
    editForm.joinFailurePolicy = detail.joinFailurePolicy || undefined
    // 分支出口：route 节点的 branches 直接来自列表行
    editForm.branches = Object.entries(detail.branches ?? {}).map(([key, targets]) => ({
      key,
      targets: [...targets]
    }))
    editForm.mappings = (detail.mappings ?? []).map((item) => ({ ...item }))
  } finally {
    editLoading.value = false
  }
}

const submitEdit = async () => {
  if (editForm.isControl && !editForm.typeConfig?.trim()) {
    message.error('控制类节点要填写类型配置')
    return
  }
  if (editForm.isControl && !editForm.name?.trim()) {
    message.error('控制类节点要填写名称')
    return
  }
  const branches: Record<string, string[]> = {}
  for (const branch of editForm.branches) {
    if (branch.key?.trim()) {
      branches[branch.key.trim()] = branch.targets
    }
  }
  editing.value = true
  try {
    await FlowRelationApi.update({
      id: editId.value,
      name: editForm.isControl ? editForm.name?.trim() : undefined,
      typeConfig: editForm.typeConfig?.trim() || undefined,
      dependsOn: editForm.dependsOn,
      joinPolicy: editForm.joinPolicy || '',
      joinFailurePolicy: editForm.joinFailurePolicy || '',
      branches,
      mappings: editForm.mappings.filter((item) => item.param?.trim())
    })
    message.success('已保存')
    editVisible.value = false
    await getList()
  } finally {
    editing.value = false
  }
}

const move = async (id: number, offset: number) => {
  await FlowRelationApi.move(id, offset)
  await getList()
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm('移除后，还要依赖它的节点会校验失败，请先解除那些依赖。')
  } catch {
    return
  }
  await FlowRelationApi.delete(id)
  message.success('已移除')
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
