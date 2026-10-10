import request from '@/config/axios'

// 仿真 HTTP 配置 VO
export interface DeviceSimulateProfileVO {
  id?: number
  deviceId?: number
  deviceCode?: string
  actionCode?: string
  returnMode?: 'SYNC' | 'CALLBACK' | 'POLL' | 'ASYNC'
  asyncWaitMs?: number
  pollIntervalMs?: number
  pollMaxTimes?: number
  requestTemplate?: string
  responseBody?: string
  ackBody?: string
  pendingBody?: string
  status?: number
  createTime?: Date
}

// 仿真 HTTP 配置 API
export const DeviceSimulateApi = {
  // 查询仿真配置分页
  getProfilePage: async (params: any) => {
    return await request.get({ url: '/device/simulate/profile/page', params })
  },

  // 按设备和动作取配置，没有时返回 undefined
  findProfile: async (deviceId: number, actionCode: string): Promise<DeviceSimulateProfileVO | undefined> => {
    const data = await request.get({
      url: '/device/simulate/profile/page',
      params: { deviceId, actionCode, pageNo: 1, pageSize: 1 }
    })
    return data?.list?.[0]
  },

  // 新增仿真配置
  createProfile: async (data: DeviceSimulateProfileVO) => {
    return await request.post({ url: '/device/simulate/profile/create', data })
  },

  // 修改仿真配置
  updateProfile: async (data: DeviceSimulateProfileVO) => {
    return await request.put({ url: '/device/simulate/profile/update', data })
  },

  // 删除仿真配置
  deleteProfile: async (id: number) => {
    return await request.delete({ url: '/device/simulate/profile/delete?id=' + id })
  }
}
