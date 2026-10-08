import request from '@/config/axios'

// 设备动作定义 VO
export interface DeviceActionVO {
  id?: number
  deviceTypeCode?: string
  actionCode?: string
  actionName?: string
  standardFeature?: string
  paramSchema?: string
  vendorRef?: string
  source?: string
  estimateDurationMs?: number
  status?: number
  createTime?: Date
}

// 设备动作定义 API
export const DeviceActionApi = {
  // 查询设备动作分页
  getActionPage: async (params: any) => {
    return await request.get({ url: '/device/action/page', params })
  },

  // 查询设备动作列表（可按设备类型过滤）
  getActionList: async (params?: any) => {
    return await request.get({ url: '/device/action/list', params })
  },

  // 查询设备动作详情
  getAction: async (id: number) => {
    return await request.get({ url: '/device/action/get?id=' + id })
  },

  // 新增设备动作
  createAction: async (data: DeviceActionVO) => {
    return await request.post({ url: '/device/action/create', data })
  },

  // 修改设备动作
  updateAction: async (data: DeviceActionVO) => {
    return await request.put({ url: '/device/action/update', data })
  },

  // 删除设备动作
  deleteAction: async (id: number) => {
    return await request.delete({ url: '/device/action/delete?id=' + id })
  }
}
