import request from '@/config/axios'

// WMS 内容物定义 VO
export interface ContentDefVO {
  id?: number
  contentCode?: string
  contentName?: string
  imageUrl?: string
  contentType?: string
  unit?: string
  supplier?: string
  catalogNo?: string
  casNo?: string
  concentration?: string
  storageCond?: string
  shelfLifeDays?: number
  openLifeDays?: number
  hazardLevel?: string
  fefo?: boolean
  specJson?: string
  status?: number
  description?: string
  createTime?: Date
}

// WMS 内容物定义 API
export const ContentDefApi = {
  // 查询内容物定义分页
  getContentDefPage: async (params: any) => {
    return await request.get({ url: '/wms/content-def/page', params })
  },

  // 查询内容物定义列表
  getContentDefList: async (params?: any) => {
    return await request.get({ url: '/wms/content-def/list', params })
  },

  // 查询内容物定义详情
  getContentDef: async (id: number) => {
    return await request.get({ url: '/wms/content-def/get?id=' + id })
  },

  // 新增内容物定义
  createContentDef: async (data: ContentDefVO) => {
    return await request.post({ url: '/wms/content-def/create', data })
  },

  // 修改内容物定义
  updateContentDef: async (data: ContentDefVO) => {
    return await request.put({ url: '/wms/content-def/update', data })
  },

  // 删除内容物定义
  deleteContentDef: async (id: number) => {
    return await request.delete({ url: '/wms/content-def/delete?id=' + id })
  }
}
