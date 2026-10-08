import request from '@/config/axios'

// WMS 容器类型 VO
export interface ContainerTypeVO {
  id?: number
  typeCode?: string
  typeName?: string
  imageUrl?: string
  category?: string
  hierarchyRole?: string
  maxVolUl?: number
  wellCount?: number
  wellRows?: number
  wellCols?: number
  positionNaming?: string
  childTypeCode?: string
  specJson?: string
  nestable?: boolean
  usageType?: string
  lifeCycles?: number
  status?: number
  description?: string
  createTime?: Date
}

// WMS 容器类型 API
export const ContainerTypeApi = {
  // 查询容器类型分页
  getContainerTypePage: async (params: any) => {
    return await request.get({ url: '/wms/container-type/page', params })
  },

  // 查询容器类型列表
  getContainerTypeList: async (params?: any) => {
    return await request.get({ url: '/wms/container-type/list', params })
  },

  // 查询容器类型详情
  getContainerType: async (id: number) => {
    return await request.get({ url: '/wms/container-type/get?id=' + id })
  },

  // 新增容器类型
  createContainerType: async (data: ContainerTypeVO) => {
    return await request.post({ url: '/wms/container-type/create', data })
  },

  // 修改容器类型
  updateContainerType: async (data: ContainerTypeVO) => {
    return await request.put({ url: '/wms/container-type/update', data })
  },

  // 删除容器类型
  deleteContainerType: async (id: number) => {
    return await request.delete({ url: '/wms/container-type/delete?id=' + id })
  }
}
