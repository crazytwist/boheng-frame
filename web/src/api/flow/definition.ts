import request from '@/config/axios'

export interface FlowMappingVO {
  param: string
  source: 'INPUT' | 'NODE' | 'LITERAL' | string
  field?: string
  nodeId?: string
  value?: string
}

export interface FlowNodeVO {
  id?: number
  stepId?: number
  stepKey?: string
  nodeId: string
  name: string
  sortNo?: number
  stepType?: string
  typeConfig?: string
  dependsOn?: string[]
  joinPolicy?: string
  joinFailurePolicy?: string
  branches?: Record<string, string[]>
  timeoutSec?: number
  maxAttempts?: number
  failStrategy?: string
  mappings?: FlowMappingVO[]
}

export interface FlowDefinitionVO {
  id?: number
  pipelineKey?: string
  version?: number
  name?: string
  remark?: string
  status?: string
  checksum?: string
  publishedAt?: Date
  createTime?: Date
  nodeCount?: number
  nodes?: FlowNodeVO[]
}

export interface FlowIssueVO {
  nodeId?: string
  message: string
}

export const FlowDefinitionApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/flow/definition/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/flow/definition/get', params: { id } })
  },
  create: async (data: { pipelineKey: string; name: string; remark?: string }) => {
    return await request.post({ url: '/flow/definition/create', data })
  },
  save: async (data: { id: number; name: string; remark?: string }) => {
    return await request.put({ url: '/flow/definition/save', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/flow/definition/delete', params: { id } })
  },
  simpleList: async () => {
    return await request.get({ url: '/flow/definition/simple-list' })
  },
  validate: async (id: number) => {
    return await request.post({ url: '/flow/definition/validate', params: { id } })
  },
  publish: async (id: number) => {
    return await request.post({ url: '/flow/definition/publish', params: { id } })
  },
  retire: async (id: number) => {
    return await request.post({ url: '/flow/definition/retire', params: { id } })
  },
  revise: async (id: number) => {
    return await request.post({ url: '/flow/definition/revise', params: { id } })
  }
}
