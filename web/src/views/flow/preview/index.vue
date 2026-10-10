<template>
  <ContentWrap title="流程预览">
    <!-- 选择流程定义 -->
    <el-form class="-mb-15px" :inline="true" label-width="80px">
      <el-form-item label="流程">
        <el-select
          v-model="definitionId"
          placeholder="请选择要预览的流程定义"
          clearable
          filterable
          class="!w-380px"
          @change="loadDefinition"
        >
          <el-option
            v-for="item in definitions"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="definition">
        <el-tag :type="statusTag(definition.status)" size="small">{{ statusLabel(definition.status) }}</el-tag>
        <span class="ml-10px text-13px color-#909399">共 {{ definition.nodes?.length || 0 }} 个节点</span>
      </el-form-item>
      <el-form-item class="float-right">
        <el-button-group>
          <el-button :icon="ZoomIn" @click="zoom(0.2)" />
          <el-button :icon="ZoomOut" @click="zoom(-0.2)" />
          <el-button :icon="FullScreen" @click="resetView" />
        </el-button-group>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div v-if="!definitionId" class="flow-empty">
      <el-empty description="从上方选择一个流程定义，即可看到 Dify 风格的编排图" />
    </div>
    <div
      v-else
      class="flow-viewport"
      @wheel.prevent="onWheel"
      @mousedown="onMouseDown"
    >
      <div class="flow-canvas" :style="canvasStyle">
        <!-- 边层 -->
        <svg :width="graph.width" :height="graph.height" class="flow-edges">
          <defs>
            <marker id="flow-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#94a3b8" />
            </marker>
          </defs>
          <path
            v-for="(edge, i) in graph.edges"
            :key="'e' + i"
            :d="edgePath(edge)"
            class="flow-edge"
            marker-end="url(#flow-arrow)"
          />
          <template v-for="(edge, i) in graph.edges" :key="'t' + i">
            <g v-if="edge.label">
              <rect
                :x="edgeLabelPos(edge).x - edgeLabelWidth(edge) / 2"
                :y="edgeLabelPos(edge).y - 10"
                :width="edgeLabelWidth(edge)"
                height="20"
                rx="4"
                class="flow-edge-label-bg"
              />
              <text :x="edgeLabelPos(edge).x" :y="edgeLabelPos(edge).y + 4" class="flow-edge-label" text-anchor="middle">
                {{ edge.label }}
              </text>
            </g>
          </template>
        </svg>

        <!-- 循环容器层 -->
        <div
          v-for="c in graph.containers"
          :key="c.id"
          class="loop-container"
          :style="boxStyle(c.x, c.y, c.w, c.h)"
        >
          <div class="loop-header">
            <Icon icon="ep:refresh" class="mr-6px" />
            <span>{{ c.node.name }}</span>
            <span class="loop-header-id">{{ c.node.nodeId }}</span>
          </div>
        </div>

        <!-- 节点层 -->
        <div
          v-for="n in graph.nodes"
          :key="n.id"
          class="flow-node"
          :class="{ 'is-control': isControl(n.node.stepType) }"
          :style="boxStyle(n.x, n.y, n.w, n.h)"
        >
          <div class="node-icon" :style="{ background: typeMeta(n.node.stepType).bg, color: typeMeta(n.node.stepType).color }">
            <Icon :icon="typeMeta(n.node.stepType).icon" />
          </div>
          <div class="node-body">
            <div class="node-name">{{ n.node.name }}</div>
            <div class="node-id">{{ n.node.nodeId }}</div>
          </div>
          <div class="node-type" :style="{ color: typeMeta(n.node.stepType).color }">
            {{ typeMeta(n.node.stepType).label }}
          </div>
        </div>
      </div>
    </div>
  </ContentWrap>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO, FlowNodeVO } from '@/api/flow/definition'
import { ZoomIn, ZoomOut, FullScreen } from '@element-plus/icons-vue'

defineOptions({ name: 'FlowPreview' })

// ==================== 节点类型元数据 ====================

interface TypeMeta {
  label: string
  color: string
  bg: string
  icon: string
}

const TYPE_META: Record<string, TypeMeta> = {
  'device.command': { label: '设备动作', color: '#2563eb', bg: '#dbeafe', icon: 'ep:cpu' },
  'bean.invoke': { label: 'JavaBean', color: '#7c3aed', bg: '#ede9fe', icon: 'ep:grid' },
  'script.run': { label: '脚本', color: '#0891b2', bg: '#cffafe', icon: 'ep:document' },
  'manual.task': { label: '人工确认', color: '#d97706', bg: '#fef3c7', icon: 'ep:user' },
  'notification.send': { label: '通知', color: '#db2777', bg: '#fce7f3', icon: 'ep:bell' },
  route: { label: '出口路由', color: '#ca8a04', bg: '#fef9c3', icon: 'ep:share' },
  loop: { label: '循环容器', color: '#16a34a', bg: '#dcfce7', icon: 'ep:refresh' },
  subflow: { label: '子流程', color: '#4f46e5', bg: '#e0e7ff', icon: 'ep:connection' },
  'timer.wait': { label: '定时等待', color: '#64748b', bg: '#f1f5f9', icon: 'ep:clock' }
}

const CONTROL_TYPES = ['route', 'loop', 'subflow', 'timer.wait']
const isControl = (type?: string) => !!type && CONTROL_TYPES.includes(type)
const typeMeta = (type?: string): TypeMeta =>
  TYPE_META[type ?? ''] ?? { label: type ?? '未知', color: '#64748b', bg: '#f1f5f9', icon: 'ep:question-filled' }

// ==================== 布局常量 ====================

const NODE_W = 176
const NODE_H = 60
const H_GAP = 250
const V_GAP = 150
const CONTAINER_HEADER_H = 36
const CONTAINER_PAD = 16

// ==================== 图构建 ====================

interface PlacedNode {
  id: string
  node: FlowNodeVO
  x: number
  y: number
  w: number
  h: number
}

interface PlacedEdge {
  source: string
  target: string
  label?: string
}

interface PlacedContainer {
  id: string
  node: FlowNodeVO
  x: number
  y: number
  w: number
  h: number
}

interface Graph {
  nodes: PlacedNode[]
  edges: PlacedEdge[]
  containers: PlacedContainer[]
  width: number
  height: number
}

const definitions = ref<FlowDefinitionVO[]>([])
const definitionId = ref<number>()
const definition = ref<FlowDefinitionVO>()
const graph = reactive<Graph>({ nodes: [], edges: [], containers: [], width: 0, height: 0 })

const statusLabel = (status?: string) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' }[status || ''] || status || '')
const statusTag = (status?: string): 'success' | 'info' | 'warning' => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'RETIRED') return 'info'
  return 'warning'
}

const loadOptions = async () => {
  definitions.value = (await FlowDefinitionApi.simpleList()) || []
}

const loadDefinition = async () => {
  definition.value = undefined
  const id = definitionId.value
  if (!id) {
    resetGraph()
    return
  }
  const detail = await FlowDefinitionApi.get(id)
  definition.value = detail
  buildGraph(detail.nodes || [])
}

const resetGraph = () => {
  graph.nodes = []
  graph.edges = []
  graph.containers = []
  graph.width = 0
  graph.height = 0
}

/**
 * 折叠布局：loop 的 body 节点折叠进虚线容器，外部入口边重定向到容器。
 * body 区间由 loop 的 typeConfig.bodyEntryNodeId/bodyExitNodeId 按 sortNo 闭区间定位。
 */
function buildGraph(nodes: FlowNodeVO[]) {
  const byId = new Map(nodes.map((n) => [n.nodeId, n]))

  // 1. 识别 loop body
  const bodyLoopOf = new Map<string, string>()
  const bodyIdsOf = new Map<string, string[]>()
  nodes
    .filter((n) => n.stepType === 'loop')
    .forEach((loop) => {
      const cfg = safeParse(loop.typeConfig)
      const entrySort = sortNoOf(byId, cfg.bodyEntryNodeId)
      const exitSort = sortNoOf(byId, cfg.bodyExitNodeId)
      if (entrySort == null || exitSort == null) return
      const body = nodes.filter(
        (n) => n.nodeId !== loop.nodeId && (n.sortNo ?? 0) >= entrySort && (n.sortNo ?? 0) <= exitSort
      )
      bodyIdsOf.set(loop.nodeId, body.map((n) => n.nodeId))
      body.forEach((n) => bodyLoopOf.set(n.nodeId, loop.nodeId))
    })

  // 2. 有效依赖：body 节点被外部依赖时，把源重定向到 loop 容器
  const effectiveDepends = new Map<string, string[]>()
  nodes.forEach((n) => {
    effectiveDepends.set(
      n.nodeId,
      (n.dependsOn ?? []).map((dep) => {
        const loop = bodyLoopOf.get(dep)
        if (loop && bodyLoopOf.get(n.nodeId) !== loop) return loop
        return dep
      })
    )
  })

  // 3. 顶层节点与顶层边
  const topNodes = nodes.filter((n) => !bodyLoopOf.has(n.nodeId))
  const topEdges: PlacedEdge[] = []
  topNodes.forEach((n) => {
    effectiveDepends.get(n.nodeId)!.forEach((dep) => {
      pushEdge(topEdges, dep, n.nodeId, branchLabel(nodes, dep, n.nodeId))
    })
  })

  // 4. 顶层布局
  const topPos = layeredLayout(topNodes, (id) => effectiveDepends.get(id) ?? [])

  // 5. 容器展开：body 纵向排列（循环体从上到下），容器窄而高，不侵占相邻层
  const containers: PlacedContainer[] = []
  const allNodes: PlacedNode[] = []
  const bodyEdges: PlacedEdge[] = []
  topNodes.forEach((n) => {
    const pos = topPos.get(n.nodeId)!
    if (n.stepType === 'loop' && bodyIdsOf.has(n.nodeId)) {
      const bodyIds = bodyIdsOf.get(n.nodeId)!
      const sortedBody = bodyIds.map((id) => byId.get(id)!).sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
      // body 内部边
      sortedBody.forEach((bn) => {
        ;(bn.dependsOn ?? [])
          .filter((d) => bodyIds.includes(d))
          .forEach((d) => pushEdge(bodyEdges, d, bn.nodeId, undefined))
      })
      const INNER_V_GAP = 24
      let yOffset = 0
      sortedBody.forEach((bn) => {
        allNodes.push({
          id: bn.nodeId,
          node: bn,
          x: pos.x + CONTAINER_PAD,
          y: pos.y + CONTAINER_HEADER_H + CONTAINER_PAD + yOffset,
          w: NODE_W,
          h: NODE_H
        })
        yOffset += NODE_H + INNER_V_GAP
      })
      const cw = NODE_W + CONTAINER_PAD * 2
      const ch = CONTAINER_HEADER_H + Math.max(0, yOffset - INNER_V_GAP) + CONTAINER_PAD * 2
      containers.push({ id: n.nodeId, node: n, x: pos.x, y: pos.y, w: cw, h: ch })
    } else {
      allNodes.push({ id: n.nodeId, node: n, x: pos.x, y: pos.y, w: NODE_W, h: NODE_H })
    }
  })

  // 6. 全局尺寸
  let width = 0
  let height = 0
  allNodes.forEach((n) => {
    width = Math.max(width, n.x + n.w)
    height = Math.max(height, n.y + n.h)
  })
  containers.forEach((c) => {
    width = Math.max(width, c.x + c.w)
    height = Math.max(height, c.y + c.h)
  })
  width += 80
  height += 80

  graph.nodes = allNodes
  graph.edges = [...topEdges, ...bodyEdges]
  graph.containers = containers
  graph.width = width
  graph.height = height
  resetView()
}

/** 简单分层布局（longest-path layering），dependsOf 提供有效依赖（用于 body 内部过滤 / 重定向） */
function layeredLayout(nodes: FlowNodeVO[], dependsOf: (id: string) => string[]): Map<string, { x: number; y: number }> {
  const layer = new Map<string, number>()
  const visit = (id: string, stack: Set<string>): number => {
    if (layer.has(id)) return layer.get(id)!
    if (stack.has(id)) return 0
    stack.add(id)
    const deps = dependsOf(id)
    const l = deps.length ? Math.max(...deps.map((d) => visit(d, stack))) + 1 : 0
    stack.delete(id)
    layer.set(id, l)
    return l
  }
  nodes.forEach((n) => visit(n.nodeId, new Set()))

  const byLayer = new Map<number, FlowNodeVO[]>()
  nodes.forEach((n) => {
    const l = layer.get(n.nodeId)!
    if (!byLayer.has(l)) byLayer.set(l, [])
    byLayer.get(l)!.push(n)
  })

  const pos = new Map<string, { x: number; y: number }>()
  ;[...byLayer.keys()]
    .sort((a, b) => a - b)
    .forEach((l) => {
      const list = byLayer.get(l)!.sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
      list.forEach((n, i) => pos.set(n.nodeId, { x: l * H_GAP, y: i * V_GAP }))
    })
  return pos
}

// ==================== 工具 ====================

function safeParse(json?: string): any {
  if (!json) return {}
  try {
    return JSON.parse(json)
  } catch {
    return {}
  }
}

function sortNoOf(byId: Map<string, FlowNodeVO>, id?: string): number | undefined {
  if (!id) return undefined
  return byId.get(id)?.sortNo
}

function pushEdge(edges: PlacedEdge[], source: string, target: string, label?: string) {
  if (!source || !target || source === target) return
  if (edges.some((e) => e.source === source && e.target === target)) return
  edges.push({ source, target, label })
}

/** route 分支出口名作为边标签 */
function branchLabel(nodes: FlowNodeVO[], source: string, target: string): string | undefined {
  const route = nodes.find((n) => n.nodeId === source && n.stepType === 'route')
  if (!route?.branches) return undefined
  for (const [key, targets] of Object.entries(route.branches)) {
    if (targets.includes(target)) return key
  }
  return undefined
}

// ==================== 边渲染 ====================

interface Box {
  x: number
  y: number
  w: number
  h: number
  cx: number
  cy: number
}

/** 节点或容器的包围盒（容器只取 header 行，边连到 header 中线） */
function boxOf(id: string): Box {
  const c = graph.containers.find((x) => x.id === id)
  if (c) {
    return { x: c.x, y: c.y, w: c.w, h: CONTAINER_HEADER_H, cx: c.x + c.w / 2, cy: c.y + CONTAINER_HEADER_H / 2 }
  }
  const n = graph.nodes.find((x) => x.id === id)
  if (!n) return { x: 0, y: 0, w: 0, h: 0, cx: 0, cy: 0 }
  return { x: n.x, y: n.y, w: n.w, h: n.h, cx: n.x + n.w / 2, cy: n.y + n.h / 2 }
}

/** 边：target 在右 → 右出左入（横向）；target 在下 → 底出顶入（纵向，循环体内部） */
function edgePath(edge: PlacedEdge): string {
  const s = boxOf(edge.source)
  const t = boxOf(edge.target)
  if (t.x > s.x + 1) {
    const sx = s.x + s.w
    const sy = s.cy
    const tx = t.x
    const ty = t.cy
    const dx = Math.max(50, (tx - sx) / 2)
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

// ==================== 缩放平移 ====================

const viewport = reactive({ x: 40, y: 40, scale: 1 })
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
  viewport.x = 40
  viewport.y = 40
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

onMounted(async () => {
  await loadOptions()
})
</script>

<style lang="scss" scoped>
.flow-empty {
  padding: 60px 0;
}

.flow-viewport {
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

.flow-canvas {
  position: relative;
  width: 0;
  height: 0;
}

.flow-edges {
  position: absolute;
  left: 0;
  top: 0;
  overflow: visible;
  z-index: 2;
}

.flow-edge {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 1.5;
}

.flow-edge-label-bg {
  fill: #fff;
  stroke: #e2e8f0;
}

.flow-edge-label {
  font-size: 11px;
  fill: #475569;
}

.loop-container {
  position: absolute;
  border: 1.5px dashed #22c55e;
  border-radius: 10px;
  background: rgba(220, 252, 231, 0.35);
  z-index: 1;
}

.loop-header {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  font-size: 13px;
  font-weight: 600;
  color: #15803d;
  border-bottom: 1px dashed rgba(34, 197, 94, 0.4);
}

.loop-header-id {
  margin-left: auto;
  font-size: 11px;
  font-weight: 400;
  color: #86efac;
}

.flow-node {
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

  &.is-control {
    border-style: dashed;
  }
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

.node-type {
  margin-left: 8px;
  font-size: 11px;
  white-space: nowrap;
  flex-shrink: 0;
}
</style>
