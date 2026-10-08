export const deviceTypeOptions = [
  { label: '酶标仪/读板机', value: 'PLATE_READER' },
  { label: '液体处理工作站', value: 'LIQUID_HANDLER' },
  { label: '机械臂', value: 'ROBOTIC_ARM' },
  { label: '培养箱', value: 'INCUBATOR' },
  { label: '离心机', value: 'CENTRIFUGE' },
  { label: '其他', value: 'OTHER' }
]

export const deviceTypeLabel = (v?: string) => deviceTypeOptions.find((i) => i.value === v)?.label || v || '未分类'

export const statusOptions = [
  { label: '在线', value: 'ONLINE' },
  { label: '离线', value: 'OFFLINE' },
  { label: '维护中', value: 'MAINTENANCE' },
  { label: '故障', value: 'FAULT' }
]

export const statusLabel = (v?: string) => statusOptions.find((i) => i.value === v)?.label || v || '未知'

export const statusTagType = (v?: string): 'success' | 'info' | 'warning' | 'danger' => {
  const map: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
    ONLINE: 'success',
    OFFLINE: 'info',
    MAINTENANCE: 'warning',
    FAULT: 'danger'
  }
  return map[v || ''] || 'info'
}

export const connectionLabel = (v?: string) =>
  ({ HTTP: 'HTTP 直连', MQTT: 'MQTT', NODE_RED: '经 Node-RED', SIMULATED: '仿真' }[v || ''] || v || '未设置')

export const commandStatusOptions = [
  { label: '已创建', value: 'CREATED' },
  { label: '已占用', value: 'ACQUIRED' },
  { label: '已下发', value: 'SENT' },
  { label: '等待结果', value: 'WAITING' },
  { label: '成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' },
  { label: '超时', value: 'TIMED_OUT' },
  { label: '已取消', value: 'CANCELLED' }
]

export const commandStatusLabel = (v?: string) => commandStatusOptions.find((i) => i.value === v)?.label || v || '—'

export const commandStatusType = (v?: string): 'success' | 'info' | 'warning' | 'danger' => {
  if (v === 'SUCCEEDED') return 'success'
  if (v === 'FAILED' || v === 'TIMED_OUT') return 'danger'
  if (v === 'WAITING' || v === 'ACQUIRED') return 'warning'
  return 'info'
}

export const sourceLabel = (v?: string) =>
  ({ MANUAL: '手工录入', DEVICE_DECLARED: '驱动上报' }[v || ''] || v || '未标明')

export const lockTypeLabel = (v?: string) =>
  ({ COMMAND: '命令占用', MANUAL: '人工锁定', MAINTENANCE: '维护锁定' }[v || ''] || v || '')

export interface SchemaField {
  key: string
  label: string
  type?: string
  unit?: string
  required?: boolean
  defaultValue?: unknown
  constraints?: unknown
}

export const parseSchema = (raw?: string): SchemaField[] => {
  if (!raw) return []
  try {
    const obj = JSON.parse(raw) as Record<string, Record<string, unknown>>
    return Object.entries(obj).map(([key, val]) => ({
      key,
      label: String(val?.label || key),
      type: val?.type ? String(val.type) : undefined,
      unit: val?.unit ? String(val.unit) : undefined,
      required: Boolean(val?.required),
      defaultValue: val?.default,
      constraints: val?.constraints
    }))
  } catch {
    return []
  }
}

export const parseParams = (raw?: string): Array<{ key: string; value: string }> => {
  if (!raw) return []
  try {
    const obj = JSON.parse(raw) as Record<string, unknown>
    return Object.entries(obj).map(([key, value]) => ({
      key,
      value: value === null || value === undefined ? '—' : String(value)
    }))
  } catch {
    return []
  }
}

export const constraintText = (value: unknown) => {
  if (value === undefined || value === null || value === '') return ''
  if (Array.isArray(value)) return value.join(' / ')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export const durationText = (v?: number) => {
  if (v === undefined || v === null) return '未估计'
  if (v < 1000) return `${v} 毫秒`
  return `${(v / 1000).toFixed(v % 1000 === 0 ? 0 : 1)} 秒`
}
