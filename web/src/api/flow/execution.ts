import request from '@/config/axios'

export interface FlowExecutionVO {
  id?: number
  executionNo?: string
  pipelineKey?: string
  version?: number
  definitionId?: number
  checksum?: string
  status?: string
  triggerType?: string
  triggeredBy?: string
  businessKey?: string
  parentExecutionId?: number
  parentNodeId?: string
  parentAttempt?: number
  parentGroupIndex?: number
  inputParams?: string
  outputData?: string
  startedAt?: Date
  finishedAt?: Date
  errorCode?: string
  errorMessage?: string
  createTime?: Date
  steps?: FlowStepExecutionVO[]
  branchDecisions?: FlowBranchDecisionVO[]
}

export interface FlowStepExecutionVO {
  id?: number
  executionId?: number
  nodeId?: string
  stepType?: string
  iterationNo?: number
  attempt?: number
  status?: string
  skipReason?: string
  waitingFor?: string
  waitRef?: string
  commandId?: number
  commandNo?: string
  selectedBranch?: string
  inputSnapshot?: string
  outputData?: string
  errorCode?: string
  errorMessage?: string
  queuedAt?: Date
  startedAt?: Date
  finishedAt?: Date
  deadlineAt?: Date
}

export interface FlowBranchDecisionVO {
  id?: number
  executionId?: number
  routeNodeId?: string
  attempt?: number
  iterationNo?: number
  selectedBranch?: string
  matchMode?: string
  activatedTargets?: string
  decisionData?: string
}

export const FlowExecutionApi = {
  start: async (data: { definitionId: number; inputParams?: string; businessKey?: string }) => {
    return await request.post({ url: '/flow/execution/start', data })
  },
  cancel: async (id: number) => {
    return await request.post({ url: '/flow/execution/cancel', params: { id } })
  },
  get: async (id: number) => {
    return await request.get({ url: '/flow/execution/get', params: { id } })
  },
  getPage: async (params: any) => {
    return await request.get({ url: '/flow/execution/page', params })
  },
  getStepPage: async (params: any) => {
    return await request.get({ url: '/flow/execution/step-page', params })
  },
  /** 人工确认 / 拒绝等待人工的节点 */
  manualConfirm: async (data: { executionId: number; nodeId: string; approve: boolean; comment?: string }) => {
    return await request.post({ url: '/flow/signal/manual', data })
  }
}
