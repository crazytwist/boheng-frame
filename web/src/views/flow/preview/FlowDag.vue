<template>
  <div class="flow-dag">
    <div class="dag-viewport" @wheel.prevent="onWheel" @mousedown="onMouseDown">
      <div class="dag-canvas" :style="canvasStyle">
        <!-- 边层 -->
        <svg :width="dag.width" :height="dag.height" class="dag-edges">
          <defs>
            <marker id="dag-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#94a3b8" />
            </marker>
            <marker id="dag-arrow-active" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#38bdf8" />
            </marker>
          </defs>
          <path
            v-for="(edge, i) in dag.edges"
            :key="'e' + i"
            :d="edgePath(edge)"
            class="dag-edge"
            :class="{ 'is-animated': edge.animated }"
            :marker-end="edge.animated ? 'url(#dag-arrow-active)' : 'url(#dag-arrow)'"
          />
          <template v-for="(edge, i) in dag.edges" :key="'t' + i">
            <g v-if="edge.label">
              <rect
                :x="edgeLabelPos(edge).x - edgeLabelWidth(edge) / 2"
                :y="edgeLabelPos(edge).y - 10"
                :width="edgeLabelWidth(edge)"
                height="20"
                rx="4"
                class="dag-edge-label-bg"
              />
              <text :x="edgeLabelPos(edge).x" :y="edgeLabelPos(edge).y + 4" class="dag-edge-label" text-anchor="middle">
                {{ edge.label }}
              </text>
            </g>
          </template>
        </svg>

        <!-- 循环容器层 -->
        <div
          v-for="c in dag.containers"
          :key="c.id"
          class="dag-container"
          :class="c.status ? 'st-' + c.status.toLowerCase() : ''"
          :style="boxStyle(c.x, c.y, c.w, c.h)"
          @click.stop="openDetail(c.node)"
        >
          <div class="dag-container-header">
            <Icon icon="ep:refresh" class="mr-6px" />
            <span>{{ c.node.name }}</span>
            <span class="header-id">{{ c.node.nodeId }}</span>
            <el-badge v-if="c.iterationCount > 1" :value="c.iterationCount" class="ml-6px" type="success" />
          </div>
        </div>

        <!-- 节点层 -->
        <div
          v-for="n in dag.nodes"
          :key="n.id"
          class="dag-node"
          :class="[isControl(n.node.stepType) ? 'is-control' : '', n.status ? 'st-' + n.status.toLowerCase() : '']"
          :style="boxStyle(n.x, n.y, n.w, n.h)"
          @click.stop="openDetail(n.node)"
        >
          <div class="node-icon" :style="{ background: typeMeta(n.node.stepType).bg, color: typeMeta(n.node.stepType).color }">
            <Icon :icon="typeMeta(n.node.stepType).icon" />
          </div>
          <div class="node-body">
            <div class="node-name">{{ n.node.name }}</div>
            <div class="node-id">{{ n.node.nodeId }}</div>
          </div>
          <div class="node-side">
            <div class="node-type" :style="{ color: typeMeta(n.node.stepType).color }">
              {{ typeMeta(n.node.stepType).label }}
            </div>
            <div v-if="n.status" class="node-status" :style="{ color: statusMeta(n.status).color }">
              {{ statusMeta(n.status).label }}
            </div>
          </div>
          <span v-if="n.deviceTaskStatus" class="task-badge" :style="{ color: deviceTaskStatusMeta(n.deviceTaskStatus).color, background: deviceTaskStatusMeta(n.deviceTaskStatus).bg }">
            任务·{{ deviceTaskStatusMeta(n.deviceTaskStatus).label }}
          </span>
          <span v-if="n.iterationCount > 1" class="iter-badge">{{ n.iterationCount }}轮</span>
        </div>
      </div>
    </div>

    <!-- 节点属性抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="460px">
      <template v-if="detailNode">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="节点编码">{{ detailNode.nodeId }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detailNode.name }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag size="small" :type="isControl(detailNode.stepType) ? 'warning' : 'primary'">
              {{ typeMeta(detailNode.stepType).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="detailNode.timeoutSec" label="超时">{{ detailNode.timeoutSec < 0 ? '永不超时' : `${detailNode.timeoutSec} 秒` }}</el-descriptions-item>
          <el-descriptions-item v-if="detailNode.maxAttempts" label="尝试上限">{{ detailNode.maxAttempts }} 次</el-descriptions-item>
          <el-descriptions-item v-if="detailNode.joinPolicy" label="汇合策略">{{ detailNode.joinPolicy }}</el-descriptions-item>
          <el-descriptions-item v-if="detailNode.joinFailurePolicy" label="失败策略">{{ detailNode.joinFailurePolicy }}</el-descriptions-item>
          <el-descriptions-item v-if="detailNode.failStrategy" label="用尽策略">{{ detailNode.failStrategy }}</el-descriptions-item>
        </el-descriptions>

        <!-- 输入映射：参数从哪里来 -->
        <div class="drawer-section">
          <div class="section-title">输入参数（参数传递）</div>
          <el-table v-if="(detailNode.mappings || []).length" :data="detailNode.mappings" border size="small">
            <el-table-column label="参数" prop="param" min-width="90" />
            <el-table-column label="来源" width="70" align="center">
              <template #default="scope">{{ sourceLabel(scope.row.source) }}</template>
            </el-table-column>
            <el-table-column label="取值" min-width="130">
              <template #default="scope">
                <code class="mapping-code">{{ mappingValue(scope.row) }}</code>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="无输入映射" :image-size="48" />
        </div>

        <!-- 类型配置 -->
        <div class="drawer-section">
          <div class="section-title">类型配置</div>
          <el-table v-if="typeConfigRows.length" :data="typeConfigRows" border size="small">
            <el-table-column label="配置项" prop="label" width="110" />
            <el-table-column label="值" min-width="150">
              <template #default="scope">
                <code v-if="scope.row.isJson" class="mapping-code">{{ scope.row.value }}</code>
                <span v-else>{{ scope.row.value }}</span>
              </template>
            </el-table-column>
          </el-table>
          <pre v-else-if="detailNode.typeConfig" class="json-block">{{ prettyJson(detailNode.typeConfig) }}</pre>
          <span v-else class="color-#909399 text-13px">—</span>
        </div>

        <!-- 输出映射 -->
        <div v-if="detailNode.outputMapping" class="drawer-section">
          <div class="section-title">输出映射（配置）</div>
          <pre class="json-block">{{ prettyJson(detailNode.outputMapping) }}</pre>
        </div>

        <!-- 运行态：实际输入输出 -->
        <template v-if="detailRuns.length">
          <div class="drawer-section">
            <div class="section-title">执行记录（{{ detailRuns.length }} 行）</div>
            <el-table :data="detailRuns" border size="small" max-height="220">
              <el-table-column label="轮次" width="55" align="center">
                <template #default="scope">{{ scope.row.iterationNo }}</template>
              </el-table-column>
              <el-table-column label="尝试" width="55" align="center">
                <template #default="scope">{{ scope.row.attempt }}</template>
              </el-table-column>
              <el-table-column label="状态" width="80" align="center">
                <template #default="scope">
                  <span :style="{ color: statusMeta(scope.row.status).color, fontWeight: 600 }">
                    {{ statusMeta(scope.row.status).label }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="耗时" width="120" align="center">
                <template #default="scope">{{ durationOf(scope.row) }}</template>
              </el-table-column>
              <el-table-column label="说明" min-width="120" :show-overflow-tooltip="true">
                <template #default="scope">{{ scope.row.errorMessage || scope.row.waitingFor || '—' }}</template>
              </el-table-column>
            </el-table>
          </div>
          <div class="drawer-section">
            <div class="section-title">实际输入快照（最新轮）</div>
            <pre class="json-block">{{ prettyJson(detailLatest?.inputSnapshot) || '—' }}</pre>
          </div>
          <div class="drawer-section">
            <div class="section-title">实际输出（最新轮）</div>
            <pre class="json-block">{{ prettyJson(detailLatest?.outputData) || '—' }}</pre>
          </div>
        </template>
      </template>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import type { FlowNodeVO } from '@/api/flow/definition'
import type { FlowStepExecutionVO } from '@/api/flow/execution'
import { STEP_TYPE_FIELDS, type ParamField } from '@/api/flow/stepSchema'
import {
  buildDag,
  isControl,
  typeMeta,
  prettyJson,
  mappingValue,
  CONTAINER_HEADER_H,
  type PlacedEdge
} from './flow-dag'

defineOptions({ name: 'FlowDag' })

const props = defineProps<{
  /** 流程定义节点（必填，画布拓扑来源） */
  nodes: FlowNodeVO[]
  /** 运行态：节点执行行。传入即叠加状态着色与流动边 */
  runtime?: FlowStepExecutionVO[]
}>()

const dag = computed(() => buildDag(props.nodes || [], props.runtime))

// ==================== 画布缩放平移 ====================

const viewport = reactive({ x: 30, y: 30, scale: 1 })
const canvasStyle = computed(() => ({
  transform: `translate(${viewport.x}px, ${viewport.y}px) scale(${viewport.scale})`,
  transformOrigin: '0 0'
}))

const dragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })

function onWheel(e: WheelEvent) {
  zoom(e.deltaY > 0 ? -0.1 : 0.1)
}

function zoom(delta: number) {
  viewport.scale = Math.min(2, Math.max(0.3, viewport.scale + delta))
}

function resetView() {
  viewport.x = 30
  viewport.y = 30
  viewport.scale = 1
}

function onMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  dragging.value = true
  dragStart.value = { x: e.clientX - viewport.x, y: e.clientY - viewport.y }
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent) {
  if (!dragging.value) return
  viewport.x = e.clientX - dragStart.value.x
  viewport.y = e.clientY - dragStart.value.y
}

function onMouseUp() {
  dragging.value = false
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

defineExpose({ resetView })

// ==================== 边渲染 ====================

interface Box {
  x: number
  y: number
  w: number
  h: number
  cx: number
  cy: number
}

function boxOf(id: string): Box {
  const c = dag.value.containers.find((x) => x.id === id)
  if (c) {
    return { x: c.x, y: c.y, w: c.w, h: CONTAINER_HEADER_H, cx: c.x + c.w / 2, cy: c.y + CONTAINER_HEADER_H / 2 }
  }
  const n = dag.value.nodes.find((x) => x.id === id)
  if (!n) return { x: 0, y: 0, w: 0, h: 0, cx: 0, cy: 0 }
  return { x: n.x, y: n.y, w: n.w, h: n.h, cx: n.x + n.w / 2, cy: n.y + n.h / 2 }
}

function edgePath(edge: PlacedEdge): string {
  const s = boxOf(edge.source)
  const t = boxOf(edge.target)
  if (t.x > s.x + 1) {
    const sx = s.x + s.w
    const sy = s.cy
    const tx = t.x
    const ty = t.cy
    const dx = Math.max(40, (tx - sx) / 2)
    return `M ${sx} ${sy} C ${sx + dx} ${sy}, ${tx - dx} ${ty}, ${tx} ${ty}`
  }
  const sx = s.cx
  const sy = s.y + s.h
  const tx = t.cx
  const ty = t.y
  const dy = Math.max(30, (ty - sy) / 2)
  return `M ${sx} ${sy} C ${sx} ${sy + dy}, ${tx} ${ty - dy}, ${tx} ${ty}`
}

function edgeLabelPos(edge: PlacedEdge): { x: number; y: number } {
  const s = boxOf(edge.source)
  const t = boxOf(edge.target)
  if (t.x > s.x + 1) {
    return { x: (s.x + s.w + t.x) / 2, y: (s.cy + t.cy) / 2 }
  }
  return { x: (s.cx + t.cx) / 2, y: (s.y + s.h + t.y) / 2 }
}

function edgeLabelWidth(edge: PlacedEdge): number {
  return (edge.label?.length ?? 0) * 12 + 12
}

function boxStyle(x: number, y: number, w: number, h: number) {
  return { left: `${x}px`, top: `${y}px`, width: `${w}px`, height: `${h}px` }
}

// ==================== 节点属性抽屉 ====================

const detailVisible = ref(false)
const detailNode = ref<FlowNodeVO>()

const openDetail = (node: FlowNodeVO) => {
  detailNode.value = node
  detailVisible.value = true
}

const detailTitle = computed(() =>
  detailNode.value ? `节点 · ${detailNode.value.name}（${detailNode.value.nodeId}）` : '节点属性'
)

const detailRuns = computed<FlowStepExecutionVO[]>(() => {
  if (!detailNode.value) return []
  return dag.value.nodes.find((n) => n.id === detailNode.value!.nodeId)?.runs ?? []
})

const detailLatest = computed<FlowStepExecutionVO | undefined>(() => detailRuns.value[0])

// ==================== 类型配置结构化展示 ====================

const getPath = (obj: any, key: string): any => key.split('.').reduce((acc: any, k) => acc?.[k], obj)

const formatConfigValue = (f: ParamField, v: any): string => {
  if (f.type === 'boolean') return v ? '是' : '否'
  if (f.type === 'enum') return f.options?.find((o) => o.value === v)?.label ?? String(v)
  if (f.type === 'json') return typeof v === 'string' ? prettyJson(v) : prettyJson(JSON.stringify(v))
  return String(v)
}

/** 把节点 typeConfig 按字段 schema 解析成「配置项-值」行，供抽屉结构化展示 */
const typeConfigRows = computed(() => {
  const type = detailNode.value?.stepType
  const json = detailNode.value?.typeConfig
  if (!type || !json) return []
  let config: Record<string, any> = {}
  try {
    config = JSON.parse(json)
  } catch {
    return []
  }
  if (typeof config !== 'object' || config === null) return []
  const fields = STEP_TYPE_FIELDS[type] ?? []
  const rows: { label: string; value: string; isJson?: boolean }[] = []
  const declared = new Set<string>()
  for (const f of fields) {
    const v = getPath(config, f.key)
    if (v === null || v === undefined || v === '') continue
    declared.add(f.key.split('.')[0])
    rows.push({ label: f.label, value: formatConfigValue(f, v), isJson: f.type === 'json' })
  }
  for (const [k, v] of Object.entries(config)) {
    if (declared.has(k)) continue
    if (v === null || v === undefined) continue
    rows.push({ label: k, value: typeof v === 'object' ? prettyJson(JSON.stringify(v)) : String(v) })
  }
  return rows
})

const SOURCE_LABELS: Record<string, string> = { INPUT: '入参', NODE: '节点', LITERAL: '固定' }
const sourceLabel = (source?: string) => SOURCE_LABELS[source ?? ''] ?? source ?? ''

const STATUS_META: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待执行', color: '#94a3b8' },
  RUNNING: { label: '执行中', color: '#0ea5e9' },
  WAITING: { label: '等外部', color: '#f59e0b' },
  SUCCESS: { label: '成功', color: '#16a34a' },
  FAILED: { label: '失败', color: '#dc2626' },
  DEAD: { label: '失败终态', color: '#dc2626' },
  SKIPPED: { label: '被裁剪', color: '#94a3b8' },
  CANCELLED: { label: '已取消', color: '#94a3b8' }
}
const statusMeta = (status?: string) =>
  STATUS_META[status ?? ''] ?? { label: status ?? '—', color: '#94a3b8' }

const DEVICE_TASK_STATUS_META: Record<string, { label: string; color: string; bg: string }> = {
  PENDING: { label: '待领取', color: '#0369a1', bg: '#e0f2fe' },
  CLAIMED: { label: '执行中', color: '#075985', bg: '#bae6fd' },
  DONE: { label: '完成', color: '#166534', bg: '#dcfce7' },
  FAILED: { label: '失败', color: '#991b1b', bg: '#fee2e2' },
  CANCELLED: { label: '已取消', color: '#475569', bg: '#f1f5f9' }
}
const deviceTaskStatusMeta = (status?: string) =>
  DEVICE_TASK_STATUS_META[status ?? ''] ?? { label: status ?? '—', color: '#475569', bg: '#f1f5f9' }

const durationOf = (row: FlowStepExecutionVO): string => {
  if (!row.startedAt || !row.finishedAt) return '—'
  const ms = new Date(row.finishedAt as any).getTime() - new Date(row.startedAt as any).getTime()
  if (ms < 0) return '—'
  return ms < 1000 ? `${ms}ms` : `${(ms / 1000).toFixed(1)}s`
}
</script>

<style lang="scss" scoped>
.flow-dag {
  width: 100%;
}

.dag-viewport {
  position: relative;
  width: 100%;
  height: 620px;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.dag-canvas {
  position: relative;
  width: 0;
  height: 0;
}

.dag-edges {
  position: absolute;
  left: 0;
  top: 0;
  overflow: visible;
  z-index: 2;
}

.dag-edge {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 1.5;

  &.is-animated {
    stroke: #38bdf8;
    stroke-width: 2;
    stroke-dasharray: 7 5;
    animation: dag-flow 0.8s linear infinite;
  }
}

@keyframes dag-flow {
  to {
    stroke-dashoffset: -12;
  }
}

.dag-edge-label-bg {
  fill: #fff;
  stroke: #e2e8f0;
}

.dag-edge-label {
  font-size: 11px;
  fill: #475569;
}

.dag-container {
  position: absolute;
  border: 1.5px dashed #22c55e;
  border-radius: 10px;
  background: rgba(220, 252, 231, 0.35);
  z-index: 1;
  cursor: pointer;

  &.st-running {
    border-color: #0ea5e9;
    background: rgba(224, 242, 254, 0.45);

    .dag-container-header {
      color: #0369a1;
      border-bottom-color: rgba(14, 165, 233, 0.4);
    }
  }

  &.st-success {
    border-color: #16a34a;
  }

  &.st-failed,
  &.st-dead {
    border-color: #dc2626;
    background: rgba(254, 226, 226, 0.4);
  }
}

.dag-container-header {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #15803d;
  border-bottom: 1px dashed rgba(34, 197, 94, 0.4);
}

.header-id {
  margin-left: auto;
  font-size: 11px;
  font-weight: 400;
  color: #86efac;
}

.dag-node {
  position: absolute;
  display: flex;
  align-items: center;
  padding: 8px 10px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  box-sizing: border-box;
  z-index: 3;
  cursor: pointer;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 3px 10px rgba(15, 23, 42, 0.16);
  }

  &.is-control {
    border-style: dashed;
  }

  // ==================== 运行态状态着色 ====================

  &.st-running {
    border-color: #0ea5e9;
    border-style: solid;
    box-shadow: 0 0 0 0 rgba(14, 165, 233, 0.5);
    animation: node-pulse 1.6s ease-out infinite;
  }

  &.st-waiting {
    border-color: #f59e0b;
    border-style: solid;
    background: #fffbeb;
  }

  &.st-success {
    border-color: #16a34a;
    border-style: solid;
    background: #f0fdf4;
  }

  &.st-failed,
  &.st-dead {
    border-color: #dc2626;
    border-style: solid;
    background: #fef2f2;
  }

  &.st-skipped,
  &.st-cancelled {
    border-style: dotted;
    opacity: 0.55;
  }
}

@keyframes node-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(14, 165, 233, 0.45);
  }
  70% {
    box-shadow: 0 0 0 8px rgba(14, 165, 233, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(14, 165, 233, 0);
  }
}

.iter-badge {
  position: absolute;
  top: -8px;
  right: -8px;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  color: #fff;
  background: #16a34a;
  border-radius: 8px;
}

.task-badge {
  position: absolute;
  top: -8px;
  left: -8px;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 8px;
  border: 1px solid currentColor;
}

.node-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  margin-right: 10px;
  border-radius: 8px;
  font-size: 18px;
  flex-shrink: 0;
}

.node-body {
  flex: 1;
  min-width: 0;
}

.node-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-id {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-side {
  margin-left: 8px;
  text-align: right;
  flex-shrink: 0;
}

.node-type {
  font-size: 11px;
  white-space: nowrap;
}

.node-status {
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

// ==================== 属性抽屉 ====================

.drawer-section {
  margin-top: 14px;
}

.section-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.json-block {
  max-height: 220px;
  padding: 10px;
  font-family: Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  overflow: auto;
}

.mapping-code {
  padding: 1px 6px;
  font-family: Monaco, Consolas, monospace;
  font-size: 12px;
  color: #7c3aed;
  background: #ede9fe;
  border-radius: 4px;
}
</style>
