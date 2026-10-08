import request from '@/config/axios'

// 设备可观测属性定义 VO
export interface DevicePropertyVO {
  id?: number
  deviceTypeCode?: string
  propertyCode?: string
  propertyName?: string
  dataType?: string
  unit?: string
  readable?: boolean
  subscribable?: boolean
  pollIntervalSec?: number
  source?: string
  status?: number
  createTime?: Date
}

// 设备可观测属性定义 API
export const DevicePropertyApi = {
  // 查询设备属性分页
  getPropertyPage: async (params: any) => {
    return await request.get({ url: '/device/property/page', params })
  },

  // 查询设备属性列表（可按设备类型过滤）
  getPropertyList: async (params?: any) => {
    return await request.get({ url: '/device/property/list', params })
  },

  // 查询设备属性详情
  getProperty: async (id: number) => {
    return await request.get({ url: '/device/property/get?id=' + id })
  },

  // 新增设备属性
  createProperty: async (data: DevicePropertyVO) => {
    return await request.post({ url: '/device/property/create', data })
  },

  // 修改设备属性
  updateProperty: async (data: DevicePropertyVO) => {
    return await request.put({ url: '/device/property/update', data })
  },

  // 删除设备属性
  deleteProperty: async (id: number) => {
    return await request.delete({ url: '/device/property/delete?id=' + id })
  }
}
