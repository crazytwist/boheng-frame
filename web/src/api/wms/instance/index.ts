import request from '@/config/axios'

// WMS 物料实例 VO
export interface MaterialInstanceVO {
  id?: number
  instanceCode?: string
  instanceName?: string
  barcode?: string
  containerTypeId?: number
  containerTypeCode?: string
  parentInstanceId?: number
  parentPositionCode?: string
  instancePath?: string
  rootInstanceId?: number
  rootSlotId?: number
  rootSlotCode?: string
  contentDefId?: number
  contentDefCode?: string
  contentType?: string
  currentVolUl?: number
  currentCount?: number
  extData?: string
  instanceStatus?: string
  description?: string
  children?: MaterialInstanceVO[]
  createTime?: Date
}

// WMS 物料实例 API
export const MaterialInstanceApi = {
  // 查询实例分页
  getMaterialInstancePage: async (params: any) => {
    return await request.get({ url: '/wms/material-instance/page', params })
  },

  // 查询实例列表（全部，前端组树）
  getMaterialInstanceList: async (params?: any) => {
    return await request.get({ url: '/wms/material-instance/list', params })
  },

  // 查询实例详情
  getMaterialInstance: async (id: number) => {
    return await request.get({ url: '/wms/material-instance/get?id=' + id })
  },

  // 新增实例
  createMaterialInstance: async (data: MaterialInstanceVO) => {
    return await request.post({ url: '/wms/material-instance/create', data })
  },

  // 批量新增实例（同一父实例下批量生成子实例）
  createMaterialInstanceBatch: async (data: MaterialInstanceVO[]) => {
    return await request.post({ url: '/wms/material-instance/create-batch', data })
  },

  // 修改实例
  // ⚠️ 注意：本接口不接受改动落位槽位，改槽位请用 putIn / takeOut / transfer
  updateMaterialInstance: async (data: MaterialInstanceVO) => {
    return await request.put({ url: '/wms/material-instance/update', data })
  },

  // 删除实例
  deleteMaterialInstance: async (id: number) => {
    return await request.delete({ url: '/wms/material-instance/delete?id=' + id })
  },

  // 未落位的顶层实例（上架弹窗选源）
  getUnplacedList: async () => {
    return await request.get({ url: '/wms/material-instance/list-unplaced' })
  },

  // 上架
  putIn: async (data: { instanceId: number; slotId: number; remark?: string }) => {
    return await request.post({ url: '/wms/material-instance/put-in', data })
  },

  // 下架
  takeOut: async (data: { instanceId: number; remark?: string; afterStatus?: string }) => {
    return await request.post({ url: '/wms/material-instance/take-out', data })
  },

  // 转移
  transfer: async (data: { instanceId: number; targetSlotId: number; remark?: string }) => {
    return await request.post({ url: '/wms/material-instance/transfer', data })
  },

  // 消耗（按实例精确消耗，供其他模块调用）
  consume: async (data: any) => {
    return await request.post({ url: '/wms/material-instance/consume', data })
  },

  // 消耗（按容器领取 N 个子实例）
  consumeByContainer: async (data: {
    rootInstanceId: number
    count: number
    strategy?: string
    afterStatus?: string
    remark?: string
  }) => {
    return await request.post({ url: '/wms/material-instance/consume-by-container', data })
  }
}
