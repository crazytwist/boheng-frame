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
  endpointUrl?: string
  mqttTopicPrefix?: string
  callbackEnabled?: boolean
  pollIntervalSec?: number
  busyCheckPolicy?: string
  concurrencyPolicy?: string
  maxInflight?: number
  inflightCount?: number
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
  }
}
