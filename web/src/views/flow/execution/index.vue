<template>
  <ContentWrap title="流程运行">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="运行号" prop="executionNo">
        <el-input v-model="queryParams.executionNo" placeholder="请输入运行号" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="流程编码" prop="pipelineKey">
        <el-input v-model="queryParams.pipelineKey" placeholder="请输入流程编码" clearable @keyup.enter="handleQuery" class="!w-180px" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-140px">
          <el-option v-for="(label, key) in EXEC_STATUS" :key="key" :label="label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="业务键" prop="businessKey">
        <el-input v-model="queryParams.businessKey" placeholder="如实验单号" clearable @keyup.enter="handleQuery" class="!w-160px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openStart" v-hasPermi="['flow:execution:create']">
          <Icon icon="ep:video-play" class="mr-5px" /> 启动运行
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column label="运行号" align="center" prop="executionNo" min-width="200" :show-overflow-tooltip="true" />
      <el-table-column label="流程" align="center" min-width="140" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.pipelineKey }} v{{ scope.row.version }}</template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="触发" align="center" width="90">
        <template #default="scope">{{ triggerLabel(scope.row.triggerType) }}</template>
      </el-table-column>
      <el-table-column label="业务键" align="center" prop="businessKey" min-width="130" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.businessKey || '—' }}</template>
      </el-table-column>
      <el-table-column label="子运行" align="center" width="90">
        <template #default="scope">
          <el-tag v-if="scope.row.parentExecutionId" type="warning" size="small">
            {{ scope.row.parentNodeId }}#{{ scope.row.parentGroupIndex ?? 0 }}
          </el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="启动时间" align="center" prop="startedAt" width="170" :formatter="dateFormatter" />
      <el-table-column label="结束时间" align="center" prop="finishedAt" width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="180" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['flow:execution:query']" link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
          <el-button
            v-if="scope.row.status === 'RUNNING'"
            v-hasPermi="['flow:execution:update']"
            link
            type="danger"
            @click="handleCancel(scope.row.id)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <!-- 启动运行 -->
  <Dialog v-model="startVisible" title="启动运行" width="560px">
    <el-form ref="startFormRef" :model="startForm" :rules="startRules" label-width="90px">
      <el-form-item label="流程版本" prop="definitionId">
        <el-select v-model="startForm.definitionId" filterable placeholder="只列出已发布版本" class="!w-1/1">
          <el-option
            v-for="item in published"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="业务键">
        <el-input v-model="startForm.businessKey" maxlength="64" placeholder="选填，如实验单号 EXP-001" />
      </el-form-item>
      <el-form-item label="启动入参">
        <el-input v-model="startForm.inputParams" type="textarea" :rows="4" maxlength="4096"
          placeholder='选填。JSON，参数映射里 INPUT 来源从这里取值，如 {"plateId":"PLT-001"}' />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="startVisible = false">取 消</el-button>
      <el-button type="primary" :loading="starting" @click="submitStart">启 动</el-button>
    </template>
  </Dialog>

  <!-- 运行详情：流程图（状态流转）+ 时间线 + 分支决策 -->
  <Dialog v-model="detailVisible" title="运行详情" width="1100px">
    <div v-loading="detailLoading">
      <template v-if="detail">
        <el-descriptions :column="3" border size="small" class="mb-10px">
          <el-descriptions-item label="运行号">{{ detail.executionNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTag(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="流程">{{ detail.pipelineKey }} v{{ detail.version }}</el-descriptions-item>
          <el-descriptions-item label="业务键">{{ detail.businessKey || '—' }}</el-descriptions-item>
          <el-descriptions-item label="失败码">{{ detail.errorCode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="失败说明" :span="3">{{ detail.errorMessage || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.status === 'RUNNING'"
          title="运行进行中，流程图每 3 秒自动刷新"
          type="info"
          :closable="false"
          class="mb-10px"
        />

        <el-tabs v-model="detailTab">
          <el-tab-pane label="流程图" name="graph">
            <FlowDag v-if="detailNodes.length" :nodes="detailNodes" :runtime="detail.steps || []" />
            <el-empty v-else description="流程定义加载中…" :image-size="60" />
          </el-tab-pane>

          <el-tab-pane :label="`节点时间线（${(detail.steps || []).length}）`" name="timeline">
            <el-table :data="detail.steps || []" border size="small" max-height="360">
          <el-table-column label="节点" prop="nodeId" min-width="100" />
          <el-table-column label="类型" min-width="90" align="center">
            <template #default="scope">{{ typeLabel(scope.row.stepType) }}</template>
          </el-table-column>
          <el-table-column label="轮次" prop="iterationNo" width="60" align="center" />
          <el-table-column label="尝试" prop="attempt" width="60" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="scope">
              <el-tag :type="stepStatusTag(scope.row.status)" size="small">{{ stepStatusLabel(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="跳过原因" width="90" align="center">
            <template #default="scope">{{ skipLabel(scope.row.skipReason) || '—' }}</template>
          </el-table-column>
          <el-table-column label="等待" width="90" align="center">
            <template #default="scope">{{ scope.row.waitingFor || '—' }}</template>
          </el-table-column>
          <el-table-column label="出口" prop="selectedBranch" width="80" align="center">
            <template #default="scope">{{ scope.row.selectedBranch || '—' }}</template>
          </el-table-column>
          <el-table-column label="说明" min-width="150" :show-overflow-tooltip="true">
            <template #default="scope">{{ scope.row.errorMessage || scope.row.outputData || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="90" align="center" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.status === 'WAITING' && scope.row.waitingFor === 'MANUAL'"
                v-hasPermi="['flow:execution:signal']"
                link
                type="primary"
                @click="openConfirm(detail, scope.row)"
              >
                确认
              </el-button>
            </template>
          </el-table-column>
        </el-table>

            <div v-if="(detail.branchDecisions || []).length" class="mt-10px mb-5px font-bold">分支决策</div>
            <el-table v-if="(detail.branchDecisions || []).length" :data="detail.branchDecisions" border size="small">
              <el-table-column label="路由节点" prop="routeNodeId" min-width="100" />
              <el-table-column label="轮次" prop="iterationNo" width="60" align="center" />
              <el-table-column label="选中出口" prop="selectedBranch" width="90" align="center" />
              <el-table-column label="激活目标" prop="activatedTargets" min-width="140" :show-overflow-tooltip="true" />
              <el-table-column label="决策快照" prop="decisionData" min-width="180" :show-overflow-tooltip="true" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>
    <template #footer>
      <el-button @click="detailVisible = false">关 闭</el-button>
    </template>
  </Dialog>

  <!-- 人工确认 -->
  <Dialog v-model="confirmVisible" title="人工确认" width="480px">
    <el-alert v-if="confirmNode" :title="`节点【${confirmNode.nodeId}】在等待人工确认`" type="info" :closable="false" class="mb-10px" />
    <el-input v-model="confirmComment" type="textarea" :rows="3" maxlength="512" placeholder="备注。拒绝时建议填写原因" />
    <template #footer>
      <el-button @click="confirmVisible = false">取 消</el-button>
      <el-button type="danger" :loading="confirming" @click="submitConfirm(false)">拒 绝</el-button>
      <el-button type="primary" :loading="confirming" @click="submitConfirm(true)">确认放行</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO, FlowNodeVO } from '@/api/flow/definition'
import { FlowExecutionApi, FlowExecutionVO, FlowStepExecutionVO } from '@/api/flow/execution'
import FlowDag from '@/views/flow/preview/FlowDag.vue'
import { STEP_TYPES } from '@/api/flow/step'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'FlowExecution' })

const EXEC_STATUS: Record<string, string> = {
  PENDING: '待启动',
  RUNNING: '进行中',
  SUCCESS: '成功',
  FAILED: '失败',
  CANCELLED: '已取消'
}
const STEP_STATUS: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '执行中',
  WAITING: '等外部',
  SUCCESS: '成功',
  FAILED: '失败',
  DEAD: '失败终态',
  SKIPPED: '被裁剪',
  CANCELLED: '已取消'
}
const SKIP_LABELS: Record<string, string> = {
  BRANCH: '出口未选中',
  FAILURE: '前置失败',
  UPSTREAM: '上游连带'
}

const message = useMessage()
const loading = ref(false)
const list = ref<FlowExecutionVO[]>([])
const total = ref(0)
const definitions = ref<FlowDefinitionVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  executionNo: undefined,
  pipelineKey: undefined,
  status: undefined,
  businessKey: undefined
})
const queryFormRef = ref()

const published = computed(() => definitions.value.filter((item) => item.status === 'PUBLISHED'))

const statusLabel = (status?: string) => EXEC_STATUS[status ?? ''] ?? status
const statusTag = (status?: string): 'success' | 'info' | 'warning' | 'danger' => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING') return 'warning'
  return 'info'
}
const triggerLabel = (type?: string) => ({ MANUAL: '手动', SCHEDULE: '定时', CHILD: '子流程' }[type ?? ''] ?? type ?? '—')
const stepStatusLabel = (status?: string) => STEP_STATUS[status ?? ''] ?? status
const stepStatusTag = (status?: string): 'success' | 'info' | 'warning' | 'danger' => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED' || status === 'DEAD') return 'danger'
  if (status === 'RUNNING' || status === 'WAITING') return 'warning'
  return 'info'
}
const skipLabel = (reason?: string) => SKIP_LABELS[reason ?? ''] ?? ''
const typeLabel = (type?: string) => STEP_TYPES.find((item) => item.value === type)?.label ?? type ?? '—'

const getList = async () => {
  loading.value = true
  try {
    const data = await FlowExecutionApi.getPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.pageNo = 1
  getList()
}

// ==================== 启动 ====================

const startVisible = ref(false)
const starting = ref(false)
const startFormRef = ref()
const startForm = reactive({
  definitionId: undefined as number | undefined,
  businessKey: '',
  inputParams: ''
})
const startRules = {
  definitionId: [{ required: true, message: '请选择流程版本', trigger: 'change' }]
}

const openStart = async () => {
  if (!definitions.value.length) {
    definitions.value = (await FlowDefinitionApi.simpleList()) || []
  }
  if (!published.value.length) {
    message.warning('没有已发布的流程版本，先到流程定义里发布')
    return
  }
  startForm.definitionId = undefined
  startForm.businessKey = ''
  startForm.inputParams = ''
  startVisible.value = true
}

const submitStart = async () => {
  const valid = await startFormRef.value?.validate().catch(() => false)
  if (!valid) return
  starting.value = true
  try {
    const id = await FlowExecutionApi.start({
      definitionId: startForm.definitionId!,
      businessKey: startForm.businessKey?.trim() || undefined,
      inputParams: startForm.inputParams?.trim() || undefined
    })
    message.success(`已启动，运行编号 ${id}`)
    startVisible.value = false
    await getList()
  } finally {
    starting.value = false
  }
}

// ==================== 详情 / 确认 ====================

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<FlowExecutionVO>()
const detailTab = ref('graph')
const detailNodes = ref<FlowNodeVO[]>([])
let pollTimer: number | undefined

const openDetail = async (id: number) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = undefined
  detailNodes.value = []
  detailTab.value = 'graph'
  try {
    const fresh = await FlowExecutionApi.get(id)
    detail.value = fresh
    if (fresh.definitionId) {
      const def = await FlowDefinitionApi.get(fresh.definitionId)
      detailNodes.value = def.nodes || []
    }
    startPolling(id)
  } finally {
    detailLoading.value = false
  }
}

/** RUNNING 时静默轮询（不转 loading），终态自动停 */
const startPolling = (id: number) => {
  stopPolling()
  pollTimer = window.setInterval(async () => {
    if (!detailVisible.value) return
    const fresh = await FlowExecutionApi.get(id)
    detail.value = fresh
    if (fresh.status !== 'RUNNING') stopPolling()
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = undefined
  }
}

watch(detailVisible, (visible) => {
  if (!visible) stopPolling()
})

onUnmounted(stopPolling)

const confirmVisible = ref(false)
const confirming = ref(false)
const confirmComment = ref('')
const confirmExecutionId = ref(0)
const confirmNode = ref<FlowStepExecutionVO>()

const openConfirm = (execution: FlowExecutionVO, node: FlowStepExecutionVO) => {
  confirmExecutionId.value = execution.id!
  confirmNode.value = node
  confirmComment.value = ''
  confirmVisible.value = true
}

const submitConfirm = async (approve: boolean) => {
  confirming.value = true
  try {
    await FlowExecutionApi.manualConfirm({
      executionId: confirmExecutionId.value,
      nodeId: confirmNode.value!.nodeId!,
      approve,
      comment: confirmComment.value?.trim() || undefined
    })
    message.success(approve ? '已确认放行' : '已拒绝')
    confirmVisible.value = false
    await openDetail(confirmExecutionId.value)
    await getList()
  } finally {
    confirming.value = false
  }
}

const handleCancel = async (id: number) => {
  try {
    await message.confirm('取消后未开始的节点会全部置为已取消，不可恢复。')
  } catch {
    return
  }
  await FlowExecutionApi.cancel(id)
  message.success('已取消')
  await getList()
}

onMounted(async () => {
  definitions.value = (await FlowDefinitionApi.simpleList()) || []
  await getList()
})
</script>
