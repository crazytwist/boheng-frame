/** 与后端 FlowConstants.PARAM_COMMENTS_KEY 一致；仅存说明，不参与执行入参 */
export const FLOW_PARAM_COMMENTS_KEY = '__comments__'

export interface FlowParamComment {
  key: string
  comment: string
}

/** 从入参 JSON 对象中拆出默认值与参数注释 */
export function splitFlowParamComments(obj: Record<string, unknown>): {
  values: Record<string, unknown>
  comments: FlowParamComment[]
} {
  const commentsRaw = obj[FLOW_PARAM_COMMENTS_KEY]
  const values = { ...obj }
  delete values[FLOW_PARAM_COMMENTS_KEY]

  const comments: FlowParamComment[] = []
  if (commentsRaw && typeof commentsRaw === 'object' && !Array.isArray(commentsRaw)) {
    for (const [key, comment] of Object.entries(commentsRaw as Record<string, unknown>)) {
      comments.push({ key, comment: String(comment ?? '') })
    }
  }
  return { values, comments }
}

/** 列表/详情展示：默认值 JSON（不含 __comments__）+ 注释列表 */
export function formatFlowInputParamsForView(raw?: string): {
  valuesText: string
  comments: FlowParamComment[]
} {
  if (!raw?.trim()) {
    return { valuesText: '{}', comments: [] }
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
      const { values, comments } = splitFlowParamComments(parsed as Record<string, unknown>)
      return { valuesText: JSON.stringify(values, null, 2), comments }
    }
    return { valuesText: JSON.stringify(parsed, null, 2), comments: [] }
  } catch {
    return { valuesText: raw, comments: [] }
  }
}
