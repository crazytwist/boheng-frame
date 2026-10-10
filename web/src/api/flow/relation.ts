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
  timeoutSec?: number
  maxAttempts?: number
}

export interface FlowRelationSaveVO {
  definitionId: number
  stepId?: number
  nodeId?: string
  name?: string
  stepType?: string
  typeConfig?: string
  dependsOn?: string[]
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
  }
}
