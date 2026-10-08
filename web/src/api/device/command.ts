import request from '@/config/axios'

export interface DeviceCommandVO {
  id?: number
  commandNo?: string
  sourceType?: string
  deviceCode?: string
  deviceTypeCode?: string
  actionCode?: string
  dispatchMode?: string
  resultMode?: string
  status?: string
  paramsJson?: string
  requestJson?: string
  responseJson?: string
  errorMsg?: string
  codecCode?: string
  rawBody?: string
  rawFormat?: string
  operator?: string
  createTime?: Date
  finishedAt?: Date
}

export const DeviceCommandApi = {
  getCommandPage: async (params: any) => {
    return await request.get({ url: '/device/command/page', params })
  },
  getCommand: async (id: number) => {
    return await request.get({ url: '/device/command/get?id=' + id })
  }
}
