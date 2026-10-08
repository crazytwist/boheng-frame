import request from '@/config/axios'

export interface DeviceCodecVO {
  id?: number
  codecCode?: string
  codecName?: string
  parseType?: string
  fieldMapping?: string
  regexPattern?: string
  sampleRaw?: string
  status?: number
  remark?: string
  createTime?: Date
}

export const DeviceCodecApi = {
  getCodecPage: async (params: any) => {
    return await request.get({ url: '/device/codec/page', params })
  },
  getCodecList: async () => {
    return await request.get({ url: '/device/codec/list' })
  },
  getCodec: async (id: number) => {
    return await request.get({ url: '/device/codec/get?id=' + id })
  },
  createCodec: async (data: DeviceCodecVO) => {
    return await request.post({ url: '/device/codec/create', data })
  },
  updateCodec: async (data: DeviceCodecVO) => {
    return await request.put({ url: '/device/codec/update', data })
  },
  deleteCodec: async (id: number) => {
    return await request.delete({ url: '/device/codec/delete?id=' + id })
  },
  preview: async (data: Pick<DeviceCodecVO, 'parseType' | 'fieldMapping' | 'regexPattern' | 'sampleRaw'>) => {
    return await request.post({ url: '/device/codec/preview', data })
  }
}
