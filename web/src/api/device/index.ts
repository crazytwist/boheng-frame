import request from '@/config/axios'

// 设备台账 VO
export interface DeviceVO {
  id?: number
  deviceCode?: string
  deviceName?: string
  imageUrl?: string
  deviceTypeCode?: string
  model?: string
  vendor?: string
  serialNo?: string
  driverType?: string
  status?: string
  connectionType?: string
  taskMode?: 'PULL' | 'PUSH' | string
  endpointUrl?: string
  loginPath?: string
  loginMethod?: string
  loginUsername?: string
  loginPassword?: string
  loginConfigured?: boolean
  loginUsernameKey?: string
  loginPasswordKey?: string
  tokenPath?: string
  tokenHeader?: string
  tokenPrefix?: string
  tokenTtlSec?: number
  mqttTopicPrefix?: string
  callbackEnabled?: boolean
  pollEnabled?: boolean
  statusQueryEnabled?: boolean
  callbackUrl?: string
  callbackTimeoutSec?: number
  pollIntervalSec?: number
  busyCheckPolicy?: string
  concurrencyPolicy?: string
  maxInflight?: number
  capabilitySource?: string
  telemetryJson?: string
  simulationMode?: boolean
  discoveryType?: string
  currentCommandId?: number
  lockHolder?: string
  lockType?: string
  lockAcquiredTime?: Date
  lockExpireTime?: Date
  lockReason?: string
  remark?: string
  createTime?: Date
}

export interface DeviceFacet {
  title?: string
  code?: string
  detail?: string
  active?: boolean
}

export interface DevicePortrait {
  device?: DeviceVO
  connectionSummary?: string
  dispatchSummary?: string
  occupancySummary?: string
  actions?: DeviceFacet[]
  properties?: DeviceFacet[]
  paramSets?: DeviceFacet[]
}

export interface DeviceInvokeResult {
  status?: number
  body?: string
  contentType?: string
  retriedLogin?: boolean
  commandId?: number
  commandNo?: string
  commandStatus?: 'WAITING' | 'SUCCEEDED' | 'FAILED'
}

// 设备台账 API
export const DeviceInfoApi = {
  // 查询设备台账分页
  getDevicePage: async (params: any) => {
    return await request.get({ url: '/device/info/page', params })
  },

  // 查询设备台账列表
  getDeviceList: async (params?: any) => {
    return await request.get({ url: '/device/info/list', params })
  },

  // 查询设备台账详情
  getDevice: async (id: number) => {
    return await request.get({ url: '/device/info/get?id=' + id })
  },

  // 设备画像：接入、下发、占用，以及同类型动作/属性/参数集
  getDevicePortrait: async (id: number) => {
    return await request.get({ url: '/device/info/portrait?id=' + id })
  },

  // 新增设备台账
  createDevice: async (data: DeviceVO) => {
    return await request.post({ url: '/device/info/create', data })
  },

  // 修改设备台账
  updateDevice: async (data: DeviceVO) => {
    return await request.put({ url: '/device/info/update', data })
  },

  // 删除设备台账
  deleteDevice: async (id: number) => {
    return await request.delete({ url: '/device/info/delete?id=' + id })
  },

  // 调用设备动作。回调动作被设备受理后立即返回 WAITING，结果到命令记录中查看
  invoke: async (data: {
    deviceId: number
    actionCode: string
    paramsJson?: string
  }): Promise<DeviceInvokeResult> => {
    return await request.post({ url: '/device/info/invoke', data })
  }
}
