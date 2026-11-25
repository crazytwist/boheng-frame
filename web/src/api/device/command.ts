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
  waitingFor?: string
  sentAt?: Date
  timeoutAt?: Date
  paramsJson?: string
  requestJson?: string
  responseJson?: string
  errorMsg?: string
  codecCode?: string
  rawBody?: string
  rawFormat?: string
  operator?: string
  remark?: string
  createTime?: Date
  finishedAt?: Date
}

/** 人工结束：只给卡住的命令定结论，不能补投回调 */
export interface DeviceCommandCloseReqVO {
  id: number
  /** SUCCEEDED / FAILED */
  status: string
  /** 必填：留空会被流程当成可重试 */
  retryable: boolean
  /** 失败分类，失败时默认 BUSINESS */
  errorType?: string
  /** 原因，会写进命令备注 */
  remark: string
}

export const DeviceCommandApi = {
  getCommandPage: async (params: any) => {
    return await request.get({ url: '/device/command/page', params })
  },
  getCommand: async (id: number) => {
    return await request.get({ url: '/device/command/get?id=' + id })
  },
  closeCommand: async (data: DeviceCommandCloseReqVO) => {
    return await request.post({ url: '/device/command/close', data })
  }
}
