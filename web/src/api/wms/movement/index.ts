import request from '@/config/axios'

// WMS 物料流水 VO
export interface MaterialMovementVO {
  id?: number
  movementType?: string
  movementTypeName?: string
  bizSource?: string
  operationId?: string

  instanceId?: number
  instanceCode?: string
  rootInstanceId?: number
  containerTypeCode?: string
  contentDefCode?: string
  contentType?: string

  fromSlotId?: number
  fromSlotCode?: string
  fromZoneCode?: string
  toSlotId?: number
  toSlotCode?: string
  toZoneCode?: string

  beforeStatus?: string
  afterStatus?: string

  beforeQty?: number
  changeQty?: number
  afterQty?: number
  beforeVolUl?: number
  changeVolUl?: number
  afterVolUl?: number

  refType?: string
  refId?: string
  refNo?: string
  idempotentKey?: string

  operator?: string
  operatorType?: string
  operateTime?: Date
  remark?: string
  createTime?: Date
}

// 流水类型 → 中文名（后端已下发 movementTypeName，此处仅作兜底）
export const MOVEMENT_TYPE_LABEL: Record<string, string> = {
  CREATE: '建账',
  PUT_IN: '上架',
  TAKE_OUT: '下架',
  MOVE: '转移',
  CONSUME: '消耗',
  STATUS_CHANGE: '状态变更',
  RESERVE: '预留',
  RELEASE: '释放预留',
  ADJUST: '冲正'
}

// 流水类型 → element-plus tag 类型
export const MOVEMENT_TYPE_TAG: Record<
  string,
  'success' | 'primary' | 'warning' | 'danger' | 'info'
> = {
  CREATE: 'info',
  PUT_IN: 'success',
  TAKE_OUT: 'warning',
  MOVE: 'primary',
  CONSUME: 'danger',
  STATUS_CHANGE: 'info',
  RESERVE: 'warning',
  RELEASE: 'info',
  ADJUST: 'danger'
}

// 业务来源 → 中文名
export const BIZ_SOURCE_LABEL: Record<string, string> = {
  MANUAL: '手工',
  TASK: '调度任务',
  DEVICE: '设备',
  IMPORT: '导入'
}

// 操作者类型 → 中文名
export const OPERATOR_TYPE_LABEL: Record<string, string> = {
  USER: '人工',
  DEVICE: '设备',
  AUTO: '自动'
}

// WMS 物料流水 API
export const MaterialMovementApi = {
  // 流水分页
  getMovementPage: async (params: any) => {
    return await request.get({ url: '/wms/material-movement/page', params })
  },

  // 某实例的完整轨迹（正序时间线）
  getListByInstance: async (instanceId: number) => {
    return await request.get({
      url: '/wms/material-movement/list-by-instance',
      params: { instanceId }
    })
  },

  // 某顶层容器整树的流水
  getListByRootInstance: async (rootInstanceId: number) => {
    return await request.get({
      url: '/wms/material-movement/list-by-root-instance',
      params: { rootInstanceId }
    })
  },

  // 某槽位的最近流水（源或目标任一命中）
  getListBySlot: async (slotId: number, limit = 10) => {
    return await request.get({
      url: '/wms/material-movement/list-by-slot',
      params: { slotId, limit }
    })
  }
}
