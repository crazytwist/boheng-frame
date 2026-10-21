import { computed } from 'vue'
import { DICT_TYPE, getDictLabel, getStrDictOptions } from '@/utils/dict'

/** 样本类型下拉：system 字典 wms_sample_type */
export const useSampleTypeDictOptions = () => computed(() => getStrDictOptions(DICT_TYPE.WMS_SAMPLE_TYPE))

export const sampleTypeLabel = (v?: string) => getDictLabel(DICT_TYPE.WMS_SAMPLE_TYPE, v) || v || '—'

const SAMPLE_STATUS: Record<string, string> = {
  REGISTERED: '已登记',
  LOADED: '已装载',
  IN_PROCESS: '处理中',
  SPLIT: '已拆分',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
  REJECTED: '不合格',
  LOST: '丢失'
}

export const sampleStatusLabel = (v?: string) => SAMPLE_STATUS[v || ''] || v || '—'
