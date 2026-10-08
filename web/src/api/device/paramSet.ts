import request from '@/config/axios'

// 参数集预设 VO
export interface DeviceParamSetVO {
  id?: number
  paramSetCode?: string
  paramSetName?: string
  deviceTypeCode?: string
  actionCode?: string
  paramsJson?: string
  validated?: boolean
  validatedBy?: string
  validatedTime?: Date
  status?: number
  createTime?: Date
}

// 参数集预设 API
export const DeviceParamSetApi = {
  // 查询参数集分页
  getParamSetPage: async (params: any) => {
    return await request.get({ url: '/device/param-set/page', params })
  },

  // 查询参数集列表（可按设备类型 + 动作过滤）
  getParamSetList: async (params?: any) => {
    return await request.get({ url: '/device/param-set/list', params })
  },

  // 查询参数集详情
  getParamSet: async (id: number) => {
    return await request.get({ url: '/device/param-set/get?id=' + id })
  },

  // 新增参数集
  createParamSet: async (data: DeviceParamSetVO) => {
    return await request.post({ url: '/device/param-set/create', data })
  },

  // 修改参数集
  updateParamSet: async (data: DeviceParamSetVO) => {
    return await request.put({ url: '/device/param-set/update', data })
  },

  // 标记验证状态
  updateValidated: async (id: number, validated: boolean) => {
    return await request.put({ url: `/device/param-set/update-validated?id=${id}&validated=${validated}` })
  },

  // 删除参数集
  deleteParamSet: async (id: number) => {
    return await request.delete({ url: '/device/param-set/delete?id=' + id })
  }
}
