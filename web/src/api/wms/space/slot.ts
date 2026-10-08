import request from '@/config/axios'

// WMS 槽位 VO
export interface SlotVO {
  id?: number
  slotCode?: string
  zoneCode?: string
  slotType?: string
  status?: number
  slotStatus?: string
  capacity?: number
  uniqueBatch?: boolean
  extData?: string
  deviceCode?: string
  devicePositionNo?: string
  occupiedQty?: number
  occupiedTime?: Date
  version?: number
  description?: string
  createTime?: Date
}

// WMS 槽位 API
export const SlotApi = {
  // 查询槽位分页
  getSlotPage: async (params: any) => {
    return await request.get({ url: '/wms/slot/page', params })
  },

  // 查询槽位列表（按区域过滤，用于槽位网格）
  getSlotList: async (params?: any) => {
    return await request.get({ url: '/wms/slot/list', params })
  },

  // 查询槽位详情
  getSlot: async (id: number) => {
    return await request.get({ url: '/wms/slot/get?id=' + id })
  },

  // 新增槽位
  createSlot: async (data: SlotVO) => {
    return await request.post({ url: '/wms/slot/create', data })
  },

  // 修改槽位
  updateSlot: async (data: SlotVO) => {
    return await request.put({ url: '/wms/slot/update', data })
  },

  // 删除槽位
  deleteSlot: async (id: number) => {
    return await request.delete({ url: '/wms/slot/delete?id=' + id })
  },

  // 按区域布局批量生成槽位
  generateByZone: async (zoneCode: string) => {
    return await request.post({ url: '/wms/slot/generate-by-zone', params: { zoneCode } })
  }
}
