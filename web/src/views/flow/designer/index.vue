<template>
  <div class="flow-designer">
    <!-- 顶部工具条 -->
    <div class="designer-toolbar">
      <el-select
        v-model="definitionId"
        placeholder="请选择草稿流程"
        filterable
        clearable
        class="!w-380px"
        @change="onDefinitionChange"
      >
        <el-option
          v-for="item in drafts"
          :key="item.id"
          :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
          :value="item.id!"
        />
      </el-select>
      <el-tag
        v-if="selectedDefinition"
        :type="selectedDefinition.status === 'DRAFT' ? 'warning' : 'info'"
        size="small"
        class="ml-10px"
      >
        {{ statusLabel(selectedDefinition.status) }}
      </el-tag>
      <span v-if="nodes.length" class="ml-10px text-13px color-#909399">
        共 {{ nodes.length }} 个节点 · 拖入能力生成节点 · 连线建立依赖
      </span>
      <div class="ml-auto flex items-center gap-10px">
        <el-button :disabled="!definitionId" @click="handleValidate">
          <Icon icon="ep:circle-check" class="mr-5px" /> 校验
        </el-button>
        <el-button :disabled="!definitionId" @click="handleReload">
          <Icon icon="ep:refresh" class="mr-5px" /> 刷新
        </el-button>
      </div>
    </div>

    <div class="designer-body">
      <!-- 左侧：能力目录 + 控制节点 -->
      <div class="designer-palette">
        <div class="palette-section">
          <div class="palette-title">能力目录</div>
          <div class="palette-tip">拖到画布生成节点</div>
          <div class="palette-scroll">
            <div
              v-for="step in capabilities"
              :key="step.id"
              class="palette-item"
              :class="{ 'is-disabled': !canEdit }"
              @mousedown="startDragStep(step)"
            >
              <span
                class="palette-icon"
                :style="{
                  background: typeMeta(step.stepType).bg,
                  color: typeMeta(step.stepType).color
                }"
              >
                <Icon :icon="typeMeta(step.stepType).icon" />
              </span>
              <span class="palette-text">
                <span class="palette-name">{{ step.name }}</span>
                <span class="palette-type"
                  >{{ typeMeta(step.stepType).label }} · {{ step.stepKey }}</span
                >
              </span>
            </div>
            <el-empty v-if="!capabilities.length" description="暂无启用能力" :image-size="60" />
          </div>
        </div>
        <div class="palette-section">
          <div class="palette-title">控制节点</div>
          <div class="palette-scroll">
            <div
              v-for="item in CONTROL_TYPE_META"
              :key="item.value"
              class="palette-item"
              :class="{ 'is-disabled': !canEdit }"
              @mousedown="startDragControl(item)"
            >
              <span
                class="palette-icon"
                :style="{ background: typeMeta(item.value).bg, color: typeMeta(item.value).color }"
              >
                <Icon :icon="typeMeta(item.value).icon" />
              </span>
              <span class="palette-text">
                <span class="palette-name">{{ item.label }}</span>
                <span class="palette-type">控制类</span>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 中间：LogicFlow 画布 -->
      <div class="designer-canvas-wrap">
        <div v-show="!definitionId" class="canvas-empty">
          <el-empty description="先选择一个草稿流程，再拖入能力节点开始编排" />
        </div>
        <div v-show="!!definitionId" ref="canvasRef" class="designer-canvas"></div>
      </div>
    </div>

    <!-- 控制节点创建弹窗：拖入后收集名称/类型配置 -->
    <Dialog v-model="controlVisible" :title="`新增控制节点 · ${controlTypeLabel}`" width="560px">
      <el-form ref="controlFormRef" :model="controlForm" :rules="controlRules" label-width="90px">
        <el-form-item label="节点编码" prop="nodeId">
          <el-input
            v-model="controlForm.nodeId"
            maxlength="32"
            placeholder="小写字母开头，如 route_od"
          />
        </el-form-item>
        <el-form-item label="节点名称" prop="name">
          <el-input v-model="controlForm.name" maxlength="128" placeholder="如 按 OD 值路由" />
        </el-form-item>
        <template v-if="controlTypeFields.length">
          <SchemaForm :fields="controlTypeFields" v-model="controlForm.typeConfig" />
        </template>
        <el-form-item v-else label="类型配置" prop="typeConfig">
          <el-input
            v-model="controlForm.typeConfig"
            type="textarea"
            :rows="4"
            maxlength="4096"
            :placeholder="controlPlaceholder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="controlVisible = false">取 消</el-button>
        <el-button type="primary" :loading="creating" @click="submitControl">确 定</el-button>
      </template>
    </Dialog>

    <!-- 节点属性抽屉 -->
    <el-drawer
      v-model="editVisible"
      :title="drawerTitle"
      size="680px"
      :close-on-click-modal="false"
    >
      <el-form :model="editForm" label-width="100px" v-loading="editLoading">
        <el-form-item label="节点">
          <span>{{ editForm.nodeId }}（{{ typeMeta(editForm.stepType).label }}）</span>
        </el-form-item>
        <el-form-item v-if="editForm.isControl" label="节点名称" required>
          <el-input v-model="editForm.name" maxlength="128" />
        </el-form-item>
        <el-form-item label="前置节点">
          <el-select
            v-model="editForm.dependsOn"
            multiple
            filterable
            placeholder="空 = 起点"
            class="!w-1/1"
          >
            <el-option
              v-for="opt in editNodeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="汇合策略">
          <el-select
            v-model="editForm.joinPolicy"
            clearable
            placeholder="默认 ALL_SUCCESS"
            class="!w-1/1"
          >
            <el-option label="全部成功（ALL_SUCCESS）" value="ALL_SUCCESS" />
            <el-option label="任一成功（ANY_SUCCESS）" value="ANY_SUCCESS" />
            <el-option label="全部终态（ALL_DONE）" value="ALL_DONE" />
          </el-select>
        </el-form-item>
        <el-form-item label="失败策略">
          <el-select
            v-model="editForm.joinFailurePolicy"
            clearable
            placeholder="默认 FAIL"
            class="!w-1/1"
          >
            <el-option label="跟着失败（FAIL）" value="FAIL" />
            <el-option label="裁剪本节点（SKIP）" value="SKIP" />
          </el-select>
        </el-form-item>
        <template v-if="editTypeFields.length">
          <SchemaForm :fields="editTypeFields" v-model="editForm.typeConfig" />
        </template>
        <el-form-item v-else :label="editForm.isControl ? '类型配置' : '配置覆盖'">
          <el-input
            v-model="editForm.typeConfig"
            type="textarea"
            :rows="4"
            maxlength="4096"
            :placeholder="
              editForm.isControl ? '控制类节点必填' : '选填。节点级覆盖步骤上的配置 JSON'
            "
          />
        </el-form-item>
        <!-- route 分支出口 -->
        <el-form-item v-if="editForm.stepType === 'route'" label="分支出口">
          <div class="w-1/1">
            <div
              v-for="(branch, index) in editForm.branches"
              :key="index"
              class="flex items-center gap-5px mb-5px"
            >
              <el-input v-model="branch.key" maxlength="32" placeholder="出口名" class="!w-120px" />
              <el-select
                v-model="branch.targets"
                multiple
                filterable
                placeholder="目标节点"
                class="!flex-1"
              >
                <el-option
                  v-for="opt in editNodeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <el-button link type="danger" @click="editForm.branches.splice(index, 1)"
                >删</el-button
              >
            </div>
            <el-button link type="primary" @click="editForm.branches.push({ key: '', targets: [] })"
              >加出口</el-button
            >
          </div>
        </el-form-item>
        <!-- 步骤声明的参数：只读回显 -->
        <el-form-item v-if="declaredParams.length" label="可配置参数">
          <div class="w-1/1 declared-params">
            <div v-for="item in declaredParams" :key="item.key" class="declared-row">
              <div class="declared-main">
                <span class="declared-key">{{ item.key }}</span>
                <span v-if="item.comment" class="declared-comment">{{ item.comment }}</span>
              </div>
              <span class="declared-value">基础值：{{ item.value || '未设置' }}</span>
              <el-tag
                :type="isOverridden(item.key) ? 'warning' : 'info'"
                size="small"
                class="!mr-0"
              >
                {{ isOverridden(item.key) ? '节点已覆盖' : '使用基础值' }}
              </el-tag>
            </div>
          </div>
        </el-form-item>
        <!-- 参数映射 -->
        <el-form-item label="节点参数覆盖">
          <div class="w-1/1">
            <div class="mapping-tip"
              >为当前节点指定不同于步骤默认值的来源；未配置的参数继续使用上方基础值。</div
            >
            <div v-for="(mapping, index) in editForm.mappings" :key="index" class="mapping-row">
              <el-select
                v-if="declaredParams.length"
                v-model="mapping.param"
                filterable
                allow-create
                default-first-option
                :reserve-keyword="false"
                placeholder="目标参数"
                class="!w-140px"
              >
                <el-option
                  v-for="item in declaredParams"
                  :key="item.key"
                  :label="`${item.key} = ${item.value}`"
                  :value="item.key"
                />
              </el-select>
              <el-input
                v-else
                v-model="mapping.param"
                maxlength="64"
                placeholder="目标参数"
                class="!w-120px"
              />
              <el-select v-model="mapping.source" class="!w-130px">
                <el-option label="流程启动入参" value="INPUT" />
                <el-option label="前置节点输出" value="NODE" />
                <el-option label="固定值" value="LITERAL" />
              </el-select>
              <el-select
                v-if="mapping.source === 'NODE'"
                v-model="mapping.nodeId"
                filterable
                placeholder="选择前置节点"
                class="!w-180px"
              >
                <el-option
                  v-for="opt in editForm.dependsOn.map(toNodeOption)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
              <el-select
                v-if="mapping.source !== 'LITERAL'"
                v-model="mapping.field"
                filterable
                allow-create
                default-first-option
                :placeholder="mapping.source === 'NODE' ? '输出字段/路径' : '启动入参字段/路径'"
                class="!w-150px"
              >
                <el-option
                  v-for="f in knownFields(mapping.source === 'NODE' ? mapping.nodeId : undefined)"
                  :key="f.value"
                  :label="f.label"
                  :value="f.value"
                />
              </el-select>
              <el-input
                v-if="mapping.source === 'LITERAL'"
                v-model="mapping.value"
                maxlength="512"
                placeholder="固定值"
                class="!w-120px"
              />
              <el-button link type="danger" @click="editForm.mappings.splice(index, 1)"
                >删</el-button
              >
            </div>
            <el-button
              link
              type="primary"
              @click="editForm.mappings.push({ param: '', source: 'INPUT' })"
            >
              + 添加参数覆盖
            </el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="canEdit" link type="danger" class="!mr-auto" @click="handleDeleteNode"
          >移除节点</el-button
        >
        <el-button @click="editVisible = false">取 消</el-button>
        <el-button type="primary" :disabled="!canEdit" :loading="editing" @click="submitEdit"
          >保 存</el-button
        >
      </template>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import LogicFlow from '@logicflow/core'
import '@logicflow/core/dist/index.css'
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import { FlowRelationApi, FlowRelationVO } from '@/api/flow/relation'
import { FlowStepApi, FlowStepVO } from '@/api/flow/step'
import { STEP_TYPE_FIELDS } from '@/api/flow/stepSchema'
import SchemaForm from '@/components/flow/SchemaForm.vue'
import { typeMeta, isControl } from '../preview/flow-dag'

defineOptions({ name: 'FlowDesigner' })

const message = useMessage()

/** 控制类节点类型：不进能力目录，只能在画布上拖入 */
const CONTROL_TYPE_META = [
  { value: 'route', label: '出口路由' },
  { value: 'loop', label: '循环容器' },
  { value: 'subflow', label: '子流程' },
  { value: 'timer.wait', label: '定时等待' }
]

const CONTROL_PLACEHOLDERS: Record<string, string> = {
  route:
    '{"router":{"kind":"expression","expression":"input.od > 1.0 ? \'high\' : \'default\'"},"branches":{"high":["alarm"],"default":["read"]}}',
  loop: '{"bodyEntryNodeId":"read","bodyExitNodeId":"wash","maxIterations":5,"whileExpression":"sys.needMore == true"}',
  subflow: '{"dispatchMode":"SINGLE","pipelineKey":"elisa-assay","inputJson":"{}"}',
  'timer.wait': '{"waitSeconds":60}'
}

// ==================== 数据源 ====================

const definitions = ref<FlowDefinitionVO[]>([])
const steps = ref<FlowStepVO[]>([])
const nodes = ref<FlowRelationVO[]>([])
const definitionId = ref<number>()

const drafts = computed(() => definitions.value.filter((item) => item.status === 'DRAFT'))
const selectedDefinition = computed(() =>
  definitions.value.find((item) => item.id === definitionId.value)
)
const canEdit = computed(() => !!definitionId.value && selectedDefinition.value?.status === 'DRAFT')
/** 能力目录：启用步骤，排除控制类（控制类拒入能力目录） */
const capabilities = computed(() => steps.value.filter((item) => !isControl(item.stepType)))

const statusLabel = (status?: string) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' })[status || ''] || status || ''

// ==================== LogicFlow 实例 ====================

const canvasRef = ref<HTMLElement>()
let lf: LogicFlow | null = null
/** 正在渲染时抑制结构同步事件，避免 render 触发的 edge:add 重复回写 */
let rendering = false

function initCanvas() {
  if (!canvasRef.value || lf) return
  lf = new LogicFlow({
    container: canvasRef.value,
    grid: { size: 10, visible: true },
    keyboard: { enabled: true },
    edgeType: 'polyline',
    guards: {
      beforeDelete: (data: any) => {
        // 画布上删除节点：先同步删后端（依赖它的节点先解除），再删
        if (!rendering && data?.properties?.relationId != null) {
          handleCanvasDelete(data)
          return false
        }
        return true
      }
    }
  })
  lf.setTheme({
    rect: { fill: '#ffffff', stroke: '#cbd5e1', strokeWidth: 1.5, radius: 6 },
    diamond: { fill: '#ffffff', stroke: '#e6a23c', strokeWidth: 1.5 },
    nodeText: { color: '#334155', fontSize: 12 },
    polyline: { stroke: '#94a3b8', strokeWidth: 1.5 }
  })
  lf.on('node:click', ({ data }: any) => openEditById(data.id))
  lf.on('node:drop', ({ data }: any) => onNodeDrop(data))
  lf.on('node:dnd-add', ({ data }: any) => onDndAdd(data))
  lf.on('edge:add', ({ data }: any) => onEdgeAdd(data))
  lf.on('edge:delete', ({ data }: any) => onEdgeDelete(data))
}

/**
 * 把后端节点列表转成 LogicFlow 图数据渲染。
 * nodeId 作为画布节点 id（因为 dependsOn 存的就是 nodeId），
 * relationId 等业务字段塞进 properties，回写时从 properties 取。
 */
function renderGraph() {
  if (!lf) return
  rendering = true
  try {
    const graphNodes = nodes.value.map((row) => ({
      id: row.nodeId!,
      type: isControl(row.stepType) ? 'diamond' : 'rect',
      x: row.x ?? 0,
      y: row.y ?? 0,
      text: row.name || row.nodeId,
      properties: {
        relationId: row.id,
        stepId: row.stepId,
        stepType: row.stepType,
        stepKey: row.stepKey
      }
    }))
    const graphEdges: any[] = []
    const seen = new Set<string>()
    nodes.value.forEach((row) => {
      ;(row.dependsOn || []).forEach((dep) => {
        const key = `${dep}->${row.nodeId}`
        if (dep && dep !== row.nodeId && !seen.has(key)) {
          seen.add(key)
          graphEdges.push({ sourceNodeId: dep, targetNodeId: row.nodeId, type: 'polyline' })
        }
      })
    })
    lf!.render({ nodes: graphNodes, edges: graphEdges })
  } finally {
    rendering = false
  }
}

// ==================== 数据加载 ====================

async function loadOptions() {
  definitions.value = (await FlowDefinitionApi.simpleList()) || []
  steps.value = (await FlowStepApi.getList()) || []
}

async function loadNodes() {
  if (!definitionId.value) {
    nodes.value = []
    renderGraph()
    return
  }
  const data = await FlowRelationApi.getPage({
    definitionId: definitionId.value,
    pageNo: 1,
    pageSize: 200
  })
  nodes.value = data.list || []
  renderGraph()
}

async function handleReload() {
  await loadOptions()
  await loadNodes()
}

function onDefinitionChange() {
  loadNodes()
}

// ==================== 拖拽生成节点 ====================

const startDragStep = (step: FlowStepVO) => {
  if (!canEdit.value) {
    message.warning('只有草稿流程才能编辑')
    return
  }
  lf?.dnd.startDrag({
    type: 'rect',
    text: step.name,
    properties: { stepId: step.id, stepType: step.stepType, stepKey: step.stepKey }
  })
}

const startDragControl = (item: { value: string; label: string }) => {
  if (!canEdit.value) {
    message.warning('只有草稿流程才能编辑')
    return
  }
  lf?.dnd.startDrag({
    type: 'diamond',
    text: item.label,
    properties: { controlType: item.value }
  })
}

/** 拖入落点：先删掉 LogicFlow 临时落点节点，再按能力/控制类走业务（最终以 loadNodes 重绘为准） */
async function onDndAdd(data: any) {
  // 临时节点没有 relationId，beforeDelete 会放行，这里先清掉落点指示
  lf?.deleteNode(data.id)
  if (!definitionId.value) {
    message.warning('请先选择草稿流程')
    return
  }
  const x = Math.round(data.x)
  const y = Math.round(data.y)
  const props = data.properties || {}
  if (props.stepId != null) {
    await createStepNode(props.stepId, x, y)
  } else if (props.controlType) {
    openControlCreate(props.controlType, x, y)
  }
}

async function createStepNode(stepId: number, x: number, y: number) {
  try {
    await FlowRelationApi.create({ definitionId: definitionId.value!, stepId, x, y })
    await loadNodes()
  } catch (e: any) {
    message.error(e?.message || '创建节点失败')
    await loadNodes()
  }
}

// ==================== 控制节点创建 ====================

const controlVisible = ref(false)
const creating = ref(false)
const controlFormRef = ref()
const controlForm = reactive({
  nodeId: '',
  name: '',
  stepType: '',
  typeConfig: '',
  x: 0,
  y: 0
})
const controlTypeLabel = computed(
  () => CONTROL_TYPE_META.find((i) => i.value === controlForm.stepType)?.label ?? ''
)
const controlTypeFields = computed(() => STEP_TYPE_FIELDS[controlForm.stepType] ?? [])
const controlPlaceholder = computed(() => {
  const sample = CONTROL_PLACEHOLDERS[controlForm.stepType]
  return sample ? `配置 JSON，如 ${sample}` : '类型专属配置 JSON'
})
const controlRules = {
  nodeId: [{ required: true, message: '请填写节点编码', trigger: 'blur' }],
  name: [{ required: true, message: '请填写节点名称', trigger: 'blur' }]
}

function openControlCreate(controlType: string, x: number, y: number) {
  controlForm.stepType = controlType
  controlForm.nodeId = suggestNodeId(controlType)
  controlForm.name = CONTROL_TYPE_META.find((i) => i.value === controlType)?.label ?? ''
  controlForm.typeConfig = ''
  controlForm.x = x
  controlForm.y = y
  controlVisible.value = true
}

/** 控制节点编码默认值：类型 key 去掉点号（timer.wait → timer_wait），避免冲突加后缀 */
function suggestNodeId(controlType: string): string {
  const base = controlType.replace(/\./g, '_')
  const taken = new Set(nodes.value.map((n) => n.nodeId))
  if (!taken.has(base)) return base
  for (let i = 2; i <= 61; i++) {
    const suffix = `_${i}`
    const candidate =
      base.length + suffix.length > 32 ? base.slice(0, 32 - suffix.length) + suffix : base + suffix
    if (!taken.has(candidate)) return candidate
  }
  return base
}

async function submitControl() {
  const valid = await controlFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!controlForm.typeConfig?.trim()) {
    message.error('控制类节点要填写类型配置')
    return
  }
  creating.value = true
  try {
    await FlowRelationApi.create({
      definitionId: definitionId.value!,
      nodeId: controlForm.nodeId.trim(),
      name: controlForm.name.trim(),
      stepType: controlForm.stepType,
      typeConfig: controlForm.typeConfig.trim(),
      x: controlForm.x,
      y: controlForm.y
    })
    message.success('已添加控制节点')
    controlVisible.value = false
    await loadNodes()
  } catch (e: any) {
    message.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// ==================== 连线 ↔ dependsOn 同步 ====================

/** 边 source→target 表示 target 依赖 source，落到 target.dependsOn */
async function syncDependsOn(targetNodeId: string) {
  const row = nodes.value.find((n) => n.nodeId === targetNodeId)
  if (!row || row.id == null) return
  const incoming = (lf?.getNodeIncomingNode(targetNodeId) || []).map((n: any) => n.id)
  try {
    await FlowRelationApi.update({ id: row.id, dependsOn: incoming })
    row.dependsOn = incoming
  } catch (e: any) {
    message.error(e?.message || '同步依赖失败')
    await loadNodes()
  }
}

async function onEdgeAdd(data: any) {
  if (rendering) return
  await syncDependsOn(data.targetNodeId)
}

async function onEdgeDelete(data: any) {
  if (rendering) return
  await syncDependsOn(data.targetNodeId)
}

// ==================== 坐标持久化 ====================

async function onNodeDrop(data: any) {
  const relationId = data.properties?.relationId
  if (relationId == null) return
  await FlowRelationApi.saveLayout([
    { id: relationId, x: Math.round(data.x), y: Math.round(data.y) }
  ])
}

// ==================== 删除节点 ====================

async function handleCanvasDelete(data: any) {
  await doDeleteNode(data.properties?.relationId, data.id)
}

async function handleDeleteNode() {
  const relationId = nodes.value.find((n) => n.nodeId === editForm.nodeId)?.id
  if (relationId == null) return
  await doDeleteNode(relationId, editForm.nodeId)
}

async function doDeleteNode(relationId: number, nodeId: string) {
  try {
    await message.delConfirm(
      `移除节点「${nodeId}」？依赖它的下游节点会校验失败，请先解除那些依赖。`
    )
  } catch {
    return
  }
  try {
    await FlowRelationApi.delete(relationId)
    message.success('已移除')
    editVisible.value = false
    await loadNodes()
  } catch (e: any) {
    message.error(e?.message || '移除失败')
    await loadNodes()
  }
}

// ==================== 节点属性编辑 ====================

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
const editForm = reactive({
  nodeId: '',
  stepType: '',
  isControl: false,
  name: '',
  stepId: undefined as number | undefined,
  typeConfig: '',
  dependsOn: [] as string[],
  joinPolicy: undefined as string | undefined,
  joinFailurePolicy: undefined as string | undefined,
  branches: [] as BranchRow[],
  mappings: [] as MappingRow[]
})

const editNodeOptions = computed(() =>
  nodes.value
    .filter((n) => n.nodeId !== editForm.nodeId)
    .map((n) => ({ value: n.nodeId!, label: `${n.name || n.nodeId}（${n.nodeId}）` }))
)

const editStep = computed(() => steps.value.find((item) => item.id === editForm.stepId))
const editTypeFields = computed(() => STEP_TYPE_FIELDS[editForm.stepType] ?? [])

const declaredParams = computed(() => {
  const raw = editStep.value?.defaultParams
  if (!raw?.trim()) return []
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
    const comments =
      obj.__comments__ && typeof obj.__comments__ === 'object' ? obj.__comments__ : {}
    return Object.entries(obj)
      .filter(([key]) => key !== '__comments__')
      .map(([key, value]) => ({
        key,
        value: value === null || typeof value === 'object' ? JSON.stringify(value) : String(value),
        comment: String(comments[key] || '')
      }))
  } catch {
    return []
  }
})

const isOverridden = (key: string) => editForm.mappings.some((item) => item.param === key)

const nodeNameOf = (nodeId: string) => nodes.value.find((item) => item.nodeId === nodeId)?.name
const toNodeOption = (nodeId: string) => ({
  value: nodeId,
  label: `${nodeNameOf(nodeId) || nodeId}（${nodeId}）`
})

const knownFields = (nodeId?: string): { label: string; value: string }[] => {
  if (!nodeId) return []
  const type = nodes.value.find((item) => item.nodeId === nodeId)?.stepType
  if (type === 'loop') return [{ label: 'iterations · 循环轮数', value: 'iterations' }]
  if (type === 'subflow') {
    return [
      { label: 'children · 子流程数', value: 'children' },
      { label: 'detail[0].executionId · 首个子运行ID', value: 'detail[0].executionId' },
      { label: 'detail[0].status · 首个子运行状态', value: 'detail[0].status' }
    ]
  }
  return []
}

const drawerTitle = computed(() =>
  editForm.nodeId ? `节点编排 · ${editForm.name || editForm.nodeId}` : '节点编排'
)

const openEditById = (canvasNodeId: string) => {
  const row = nodes.value.find((n) => n.nodeId === canvasNodeId)
  if (!row) return
  editId.value = row.id!
  editForm.nodeId = row.nodeId ?? ''
  editForm.stepType = row.stepType ?? ''
  editForm.isControl = isControl(row.stepType)
  editForm.name = row.name ?? ''
  editForm.stepId = row.stepId
  editForm.typeConfig = row.typeConfig ?? ''
  editForm.dependsOn = [...(row.dependsOn ?? [])]
  editForm.joinPolicy = row.joinPolicy || undefined
  editForm.joinFailurePolicy = row.joinFailurePolicy || undefined
  editForm.branches = Object.entries(row.branches ?? {}).map(([key, targets]) => ({
    key,
    targets: [...targets]
  }))
  editForm.mappings = (row.mappings ?? []).map((item) => ({ ...item }))
  editVisible.value = true
}

async function submitEdit() {
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
    if (branch.key?.trim()) branches[branch.key.trim()] = branch.targets
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
    await loadNodes()
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    editing.value = false
  }
}

// ==================== 校验 ====================

async function handleValidate() {
  if (!definitionId.value) return
  try {
    await FlowDefinitionApi.validate(definitionId.value)
    message.success('结构校验通过')
  } catch (e: any) {
    message.error(e?.message || '校验失败')
  }
}

// ==================== 生命周期 ====================

onMounted(async () => {
  await loadOptions()
  // 默认选中第一个草稿
  if (drafts.value.length) {
    definitionId.value = drafts.value[0].id
  }
  await loadNodes()
  // 画布初始化在 DOM 就绪后
  await nextTick()
  initCanvas()
  renderGraph()
})

onBeforeUnmount(() => {
  lf?.destroy()
  lf = null
})
</script>

<style lang="scss" scoped>
.flow-designer {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 84px);
  overflow: hidden;
}

.designer-toolbar {
  display: flex;
  align-items: center;
  padding: 10px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

.designer-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.designer-palette {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fafafa;
  border-right: 1px solid #e5e7eb;
  overflow: hidden;
}

.palette-section {
  display: flex;
  flex-direction: column;
  min-height: 0;

  &:first-child {
    flex: 1;
  }
}

.palette-title {
  padding: 12px 14px 2px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.palette-tip {
  padding: 0 14px 6px;
  font-size: 11px;
  color: #94a3b8;
}

.palette-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 10px 12px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  margin-bottom: 6px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: grab;
  user-select: none;
  transition:
    box-shadow 0.15s,
    border-color 0.15s;

  &:hover {
    border-color: #409eff;
    box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
  }

  &:active {
    cursor: grabbing;
  }

  &.is-disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.palette-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 7px;
  font-size: 16px;
  flex-shrink: 0;
}

.palette-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.palette-name {
  font-size: 12px;
  font-weight: 500;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.palette-type {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.designer-canvas-wrap {
  flex: 1;
  position: relative;
  min-width: 0;
}

.designer-canvas {
  width: 100%;
  height: 100%;
}

.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.declared-params {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color-lighter);
}

.declared-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  line-height: 22px;

  & + .declared-row {
    border-top: 1px dashed var(--el-border-color-lighter);
  }
}

.declared-main {
  display: flex;
  flex: 0 0 190px;
  flex-direction: column;
  min-width: 0;
}

.declared-key {
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  color: var(--el-text-color-primary);
}

.declared-comment,
.mapping-tip {
  color: var(--el-text-color-secondary);
}

.declared-comment {
  overflow: hidden;
  line-height: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.declared-value {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

.mapping-tip {
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 18px;
}

.mapping-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 6px;
  padding-bottom: 6px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
</style>
