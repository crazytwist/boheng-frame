import request from '@/config/axios'

export interface FlowStepVO {
  id?: number
  stepKey?: string
  name?: string
  deviceCode?: string
  actionCode?: string
  timeoutSec?: number
  maxAttempts?: number
  remark?: string
  status?: number
  createTime?: Date
}

export const FlowStepApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/flow/step/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/flow/step/get', params: { id } })
  },
  getList: async () => {
    return await request.get({ url: '/flow/step/list' })
  },
  create: async (data: FlowStepVO) => {
    return await request.post({ url: '/flow/step/create', data })
  },
  update: async (data: FlowStepVO) => {
    return await request.put({ url: '/flow/step/update', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/flow/step/delete', params: { id } })
  }
}
