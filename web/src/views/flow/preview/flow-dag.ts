import type { FlowNodeVO } from '@/api/flow/definition'
import type { FlowStepExecutionVO } from '@/api/flow/execution'

/**
 * 流程 DAG 只读画布的纯逻辑层：类型元数据 + 折叠布局。
 * 定义态（只传 nodes）和运行态（nodes + steps 执行行）共用同一套布局。
 */

// ==================== 节点类型元数据 ====================

export interface TypeMeta {
  label: string
  color: string
  bg: string
  icon: string
}

export const TYPE_META: Record<string, TypeMeta> = {
  'device.command': { label: '设备动作', color: '#2563eb', bg: '#dbeafe', icon: 'ep:cpu' },
  'device.task': { label: '设备任务', color: '#0284c7', bg: '#e0f2fe', icon: 'ep:list' },
  'bean.invoke': { label: 'JavaBean', color: '#7c3aed', bg: '#ede9fe', icon: 'ep:grid' },
  'script.run': { label: '脚本', color: '#0891b2', bg: '#cffafe', icon: 'ep:document' },
  'manual.task': { label: '人工确认', color: '#d97706', bg: '#fef3c7', icon: 'ep:user' },
  'notification.send': { label: '通知', color: '#db2777', bg: '#fce7f3', icon: 'ep:bell' },
  route: { label: '出口路由', color: '#ca8a04', bg: '#fef9c3', icon: 'ep:share' },
  loop: { label: '循环容器', color: '#16a34a', bg: '#dcfce7', icon: 'ep:refresh' },
  subflow: { label: '子流程', color: '#4f46e5', bg: '#e0e7ff', icon: 'ep:connection' },
  'timer.wait': { label: '定时等待', color: '#64748b', bg: '#f1f5f9', icon: 'ep:clock' }
}

export const CONTROL_TYPES = ['route', 'loop', 'subflow', 'timer.wait']

export const isControl = (type?: string) => !!type && CONTROL_TYPES.includes(type)

export const typeMeta = (type?: string): TypeMeta =>
  TYPE_META[type ?? ''] ?? { label: type ?? '未知', color: '#64748b', bg: '#f1f5f9', icon: 'ep:question-filled' }

// ==================== 布局常量 ====================

export const NODE_W = 200
export const NODE_H = 60
const H_GAP = 110
const V_GAP = 150
export const CONTAINER_HEADER_H = 36
export const CONTAINER_PAD = 16
const INNER_V_GAP = 24

// ==================== 布局结果 ====================

export interface PlacedNode {
  id: string
  node: FlowNodeVO
  x: number
  y: number
  w: number
  h: number
  /** 运行态：该节点的全部执行行（含循环各轮） */
  runs: FlowStepExecutionVO[]
  /** 运行态：最新一轮的执行行 */
  latest?: FlowStepExecutionVO
  /** 运行态生效状态（取最新轮） */
  status?: string
  /** 运行态：device.task 节点的任务状态（PENDING/CLAIMED/DONE/FAILED/CANCELLED） */
  deviceTaskStatus?: string
  /** 运行态：执行过的轮数（>1 显示徽标） */
  iterationCount: number
}

export interface PlacedEdge {
  source: string
  target: string
  label?: string
  /** 运行态：目标节点正在执行 → 流动动画 */
  animated?: boolean
}

export interface PlacedContainer {
  id: string
  node: FlowNodeVO
  x: number
  y: number
  w: number
  h: number
  status?: string
  iterationCount: number
}

export interface DagResult {
  nodes: PlacedNode[]
  edges: PlacedEdge[]
  containers: PlacedContainer[]
  width: number
  height: number
}

// ==================== 运行态合并 ====================

const latestOf = (runs: FlowStepExecutionVO[]): FlowStepExecutionVO | undefined =>
  runs.length
    ? runs.slice().sort((a, b) => (b.iterationNo ?? 0) - (a.iterationNo ?? 0) || (b.attempt ?? 0) - (a.attempt ?? 0))[0]
    : undefined

const ACTIVE_STATUSES = ['RUNNING', 'WAITING']

// ==================== 折叠布局 ====================

/**
 * 折叠布局：loop 的 body 节点折叠进虚线容器（纵向排列），body 区间由
 * typeConfig.bodyEntryNodeId/bodyExitNodeId 按 sortNo 闭区间定位（与引擎 loopBody 一致）。
 * body 节点被外部依赖时，边源重定向到 loop 容器；层间 x 按实际宽度累计，
 * 容器比普通节点宽也不会侵占下一层。
 */
export function buildDag(nodes: FlowNodeVO[], runtime?: FlowStepExecutionVO[]): DagResult {
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

  // 2. 有效依赖：body 节点被外部依赖时，源重定向到 loop 容器
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

  // 3. 顶层节点与边
  const topNodes = nodes.filter((n) => !bodyLoopOf.has(n.nodeId))
  const topEdges: PlacedEdge[] = []
  topNodes.forEach((n) => {
    effectiveDepends.get(n.nodeId)!.forEach((dep) => {
      pushEdge(topEdges, dep, n.nodeId, branchLabel(nodes, dep, n.nodeId))
    })
  })

  // 4. 分层（longest-path layering）
  const layerOf = new Map<string, number>()
  const visit = (id: string, stack: Set<string>): number => {
    if (layerOf.has(id)) return layerOf.get(id)!
    if (stack.has(id)) return 0
    stack.add(id)
    const deps = effectiveDepends.get(id) ?? []
    const l = deps.length ? Math.max(...deps.map((d) => visit(d, stack))) + 1 : 0
    stack.delete(id)
    layerOf.set(id, l)
    return l
  }
  topNodes.forEach((n) => visit(n.nodeId, new Set()))

  // 5. 逐层累计 x：层宽按「容器实际宽度 / 节点宽度」取最大，避免容器侵占下一层
  const byLayer = new Map<number, FlowNodeVO[]>()
  topNodes.forEach((n) => {
    const l = layerOf.get(n.nodeId)!
    if (!byLayer.has(l)) byLayer.set(l, [])
    byLayer.get(l)!.push(n)
  })
  const pos = new Map<string, { x: number; y: number }>()
  let cursorX = 0
  ;[...byLayer.keys()]
    .sort((a, b) => a - b)
    .forEach((l) => {
      const list = byLayer.get(l)!.sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
      let maxW = 0
      list.forEach((n, i) => {
        pos.set(n.nodeId, { x: cursorX, y: i * V_GAP })
        maxW = Math.max(maxW, isLoopContainer(n, bodyIdsOf) ? NODE_W + CONTAINER_PAD * 2 : NODE_W)
      })
      cursorX += maxW + H_GAP
    })

  // 6. 运行态合并
  const runsByNode = new Map<string, FlowStepExecutionVO[]>()
  runtime?.forEach((r) => {
    if (!r.nodeId) return
    if (!runsByNode.has(r.nodeId)) runsByNode.set(r.nodeId, [])
    runsByNode.get(r.nodeId)!.push(r)
  })
  const statusOf = (id: string) => {
    const runs = runsByNode.get(id)
    return runs ? latestOf(runs)?.status : undefined
  }

  // 7. 组装：容器展开（body 纵向排列）
  const containers: PlacedContainer[] = []
  const allNodes: PlacedNode[] = []
  const bodyEdges: PlacedEdge[] = []
  topNodes.forEach((n) => {
    const p = pos.get(n.nodeId)!
    const attach = (id: string): { runs: FlowStepExecutionVO[]; status?: string; deviceTaskStatus?: string; iterationCount: number } => {
      const runs = runsByNode.get(id) ?? []
      const latest = latestOf(runs)
      const iterationCount = new Set(runs.map((r) => r.iterationNo ?? 0)).size
      return { runs, status: latest?.status, deviceTaskStatus: latest?.deviceTaskStatus, iterationCount }
    }
    if (isLoopContainer(n, bodyIdsOf)) {
      const bodyIds = bodyIdsOf.get(n.nodeId)!
      const sortedBody = bodyIds.map((id) => byId.get(id)!).sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
      sortedBody.forEach((bn) => {
        ;(bn.dependsOn ?? [])
          .filter((d) => bodyIds.includes(d))
          .forEach((d) => pushEdge(bodyEdges, d, bn.nodeId, undefined))
      })
      let yOffset = 0
      sortedBody.forEach((bn) => {
        allNodes.push({
          id: bn.nodeId,
          node: bn,
          x: p.x + CONTAINER_PAD,
          y: p.y + CONTAINER_HEADER_H + CONTAINER_PAD + yOffset,
          w: NODE_W,
          h: NODE_H,
          ...attach(bn.nodeId)
        })
        yOffset += NODE_H + INNER_V_GAP
      })
      containers.push({
        id: n.nodeId,
        node: n,
        x: p.x,
        y: p.y,
        w: NODE_W + CONTAINER_PAD * 2,
        h: CONTAINER_HEADER_H + Math.max(0, yOffset - INNER_V_GAP) + CONTAINER_PAD * 2,
        status: statusOf(n.nodeId),
        iterationCount: attach(n.nodeId).iterationCount
      })
    } else {
      allNodes.push({ id: n.nodeId, node: n, x: p.x, y: p.y, w: NODE_W, h: NODE_H, ...attach(n.nodeId) })
    }
  })

  // 8. 全局尺寸 + 边动画
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

  const allEdges = [...topEdges, ...bodyEdges].map((e) => {
    const targetStatus = statusOf(e.target)
    return { ...e, animated: !!targetStatus && ACTIVE_STATUSES.includes(targetStatus) }
  })

  return {
    nodes: allNodes,
    edges: allEdges,
    containers,
    width: width + 80,
    height: height + 80
  }
}

// ==================== 工具 ====================

function isLoopContainer(n: FlowNodeVO, bodyIdsOf: Map<string, string[]>): boolean {
  return n.stepType === 'loop' && bodyIdsOf.has(n.nodeId)
}

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

/** JSON 字符串格式化展示（非法 JSON 原样返回） */
export function prettyJson(json?: string): string {
  if (!json) return ''
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

/** 参数映射的取值展示 */
export function mappingValue(m: { source?: string; field?: string; nodeId?: string; value?: string }): string {
  if (m.source === 'INPUT') return `input.${m.field ?? ''}`
  if (m.source === 'NODE') return `${m.nodeId ?? ''}.${m.field ?? ''}`
  if (m.source === 'LITERAL') return `${m.value ?? ''}`
  return m.source ?? ''
}
