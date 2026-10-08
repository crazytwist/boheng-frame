import request from '@/config/axios'

export interface MeasurementWell {
  wellPosition?: string
  wavelengthNm?: number
  readValue?: number
  unit?: string
  rawText?: string
  qualityFlag?: string
}

export interface MeasurementAnalysis {
  resultType?: string
  resultValue?: string
  sourceSystem?: string
  summaryJson?: string
  remark?: string
}

export interface DeviceMeasurementVO {
  id?: number
  commandId?: number
  deviceCode?: string
  measureMode?: string
  wavelengthNm?: number
  scriptName?: string
  operator?: string
  readTime?: Date
  createTime?: Date
  wells?: MeasurementWell[]
  analyses?: MeasurementAnalysis[]
}

export const DeviceMeasurementApi = {
  getMeasurementPage: async (params: any) => {
    return await request.get({ url: '/device/measurement/page', params })
  },
  getMeasurement: async (id: number) => {
    return await request.get({ url: '/device/measurement/get?id=' + id })
  }
}
