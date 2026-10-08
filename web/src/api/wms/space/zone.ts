import request from '@/config/axios'

// WMS 区域树 VO
export interface ZoneVO {
  id?: number
  zoneCode?: string
  parentZoneCode?: string
  zoneName?: string
  zoneType?: string
  zoneLevel?: number
  tempMin?: number
  tempMax?: number
  biosafetyLevel?: number
  extData?: string
  sortNo?: number
  status?: number
  description?: string
  children?: ZoneVO[]
  createTime?: Date
}

// WMS 区域树 API
export const ZoneApi = {
  // 查询区域树列表（含 children 树形结构）
  getZoneList: async () => {
    return await request.get({ url: '/wms/zone/list' })
  },

  // 查询区域分页
  getZonePage: async (params: any) => {
    return await request.get({ url: '/wms/zone/page', params })
  },

  // 查询区域详情
  getZone: async (id: number) => {
    return await request.get({ url: '/wms/zone/get?id=' + id })
  },

  // 新增区域
  createZone: async (data: ZoneVO) => {
    return await request.post({ url: '/wms/zone/create', data })
  },

  // 修改区域
  updateZone: async (data: ZoneVO) => {
    return await request.put({ url: '/wms/zone/update', data })
  },

  // 删除区域
  deleteZone: async (id: number) => {
    return await request.delete({ url: '/wms/zone/delete?id=' + id })
  }
}
