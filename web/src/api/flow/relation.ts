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
  stepId?: number
  stepKey?: string
  stepName?: string
  deviceCode?: string
  actionCode?: string
  timeoutSec?: number
  maxAttempts?: number
}

export const FlowRelationApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/flow/relation/page', params })
  },
  create: async (data: { definitionId: number; stepId: number; nodeId?: string }) => {
    return await request.post({ url: '/flow/relation/create', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/flow/relation/delete', params: { id } })
  },
  move: async (id: number, offset: number) => {
    return await request.post({ url: '/flow/relation/move', params: { id, offset } })
  }
}
