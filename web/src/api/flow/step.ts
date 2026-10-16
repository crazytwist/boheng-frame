import request from '@/config/axios'

/**
 * 步骤类型 key。与后端 StepTypeRegistry 注册表一致，
 * 控制类类型（route/loop/subflow/timer.wait）只能在流程节点上配置，不进能力目录
 */
export const STEP_TYPES = [
  { value: 'device.command', label: '设备动作' },
  { value: 'device.task', label: '设备任务' },
  { value: 'bean.invoke', label: 'JavaBean 调用' },
  { value: 'script.run', label: '脚本表达式' },
  { value: 'manual.task', label: '人工确认' },
  { value: 'notification.send', label: '发送通知' }
] as const

export interface FlowStepVO {
  id?: number
  stepKey?: string
  name?: string
  stepType?: string
  typeConfig?: string
  defaultParams?: string
  outputSchema?: string
  timeoutSec?: number
  maxAttempts?: number
  remark?: string
  status?: number
  createTime?: Date
}

export const FlowStepApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/flow/step/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/flow/step/get', params: { id } })
  },
  getList: async () => {
    return await request.get({ url: '/flow/step/list' })
  },
  create: async (data: FlowStepVO) => {
    return await request.post({ url: '/flow/step/create', data })
  },
  update: async (data: FlowStepVO) => {
    return await request.put({ url: '/flow/step/update', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/flow/step/delete', params: { id } })
  }
}
