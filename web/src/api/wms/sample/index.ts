import request from '@/config/axios'

// WMS 样本 VO
export interface SampleVO {
  id?: number
  sampleId?: string
  parentSampleId?: string
  rootSampleId?: string
  generation?: number
  deriveType?: string
  sampleType?: string
  sampleName?: string
  contentDefId?: number
  contentDefCode?: string
  containerTypeCode?: string
  initialVolUl?: number
  experimentParams?: string
  extData?: string
  status?: string
  currentExecutionId?: string
  currentNodeId?: string
  batchNo?: string
  orderNo?: string
  priority?: number
  source?: string
  externalId?: string
  collectedAt?: Date
  receivedAt?: Date
  expireTime?: Date
  description?: string
  createTime?: Date
}

// WMS 样本 API
export const SampleApi = {
  // 样本分页
  getSamplePage: async (params: any) => {
    return await request.get({ url: '/wms/sample/page', params })
  },

  // 样本列表
  getSampleList: async (params?: any) => {
    return await request.get({ url: '/wms/sample/list', params })
  },

  // 样本详情
  getSample: async (id: number) => {
    return await request.get({ url: '/wms/sample/get?id=' + id })
  },

  // 登记样本
  createSample: async (data: SampleVO) => {
    return await request.post({ url: '/wms/sample/create', data })
  },

  // 更新样本
  updateSample: async (data: SampleVO) => {
    return await request.put({ url: '/wms/sample/update', data })
  },

  // 删除样本
  deleteSample: async (id: number) => {
    return await request.delete({ url: '/wms/sample/delete?id=' + id })
  }
}
