import request from '@/config/axios'

export interface FlowRelationVO {
  id?: number
  definitionId?: number
  pipelineKey?: string
  definitionName?: string
  version?: number
  definitionStatus?: string
  sortNo?: number
  nodeId?: string
  name?: string
  stepId?: number
  stepKey?: string
  stepName?: string
  stepType?: string
  typeConfig?: string
  dependsOn?: string[]
  joinPolicy?: string
  joinFailurePolicy?: string
  branches?: Record<string, string[]>
  mappings?: FlowMappingVO[]
  outputMapping?: string
  outputSchema?: string
  timeoutSec?: number
  maxAttempts?: number
  /** 节点级运行覆盖原始 JSON。为空 = 全部继承步骤表 */
  runConfig?: string
  /** 节点级超时覆盖值（秒）。null = 继承步骤表 */
  overrideTimeoutSec?: number
  /** 节点级尝试次数覆盖值。null = 继承步骤表 */
  overrideMaxAttempts?: number
  /** 失败策略生效值：FAIL_FAST / FAIL_SKIP */
  failureStrategy?: string
  /** 完成断言生效值。空 = 执行器报成功即成功 */
  completeWhen?: string
  x?: number
  y?: number
}

export interface FlowRelationSaveVO {
  definitionId: number
  stepId?: number
  nodeId?: string
  name?: string
  stepType?: string
  typeConfig?: string
  dependsOn?: string[]
  x?: number
  y?: number
}

export interface FlowRelationUpdateVO {
  id: number
  name?: string
  typeConfig?: string
  dependsOn?: string[]
  joinPolicy?: string
  joinFailurePolicy?: string
  branches?: Record<string, string[]>
  mappings?: FlowMappingVO[]
  outputMapping?: Record<string, string>
  // 运行配置：null = 不改这个键；0 / 空串 = 清掉这层覆盖、回落步骤表
  timeoutSec?: number | null
  maxAttempts?: number | null
  failureStrategy?: string | null
  completeWhen?: string | null
  x?: number
  y?: number
}

export interface FlowRelationLayoutItemVO {
  id: number
  x: number
  y: number
}

export interface FlowMappingVO {
  param: string
  source: 'INPUT' | 'NODE' | 'LITERAL' | string
  field?: string
  nodeId?: string
  value?: string
}

export const FlowRelationApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/flow/relation/page', params })
  },
  create: async (data: FlowRelationSaveVO) => {
    return await request.post({ url: '/flow/relation/create', data })
  },
  update: async (data: FlowRelationUpdateVO) => {
    return await request.put({ url: '/flow/relation/update', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/flow/relation/delete', params: { id } })
  },
  move: async (id: number, offset: number) => {
    return await request.post({ url: '/flow/relation/move', params: { id, offset } })
  },
  saveLayout: async (items: FlowRelationLayoutItemVO[]) => {
    return await request.post({ url: '/flow/relation/layout', data: { items } })
  }
}
