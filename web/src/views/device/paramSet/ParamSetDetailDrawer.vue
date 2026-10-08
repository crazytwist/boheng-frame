<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="参数集" :title="paramSet?.paramSetName" :summary="lead">
    <template #tags>
      <el-tag v-if="paramSet" :type="paramSet.validated ? 'success' : 'warning'" size="small">
        {{ paramSet.validated ? '已验证' : '未验证' }}
      </el-tag>
    </template>

    <template v-if="paramSet">
      <section class="cat-block">
        <div class="cat-kicker">用途</div>
        <h3 class="cat-title">准备下发给哪一个动作</h3>
        <p class="cat-lead">{{ usageLead }}</p>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">取值</div>
        <h3 class="cat-title">这次具体填了什么</h3>
        <p v-if="!rows.length" class="cat-lead">还没有参数取值。</p>
        <div v-for="row in rows" :key="row.key" class="param-card">
          <div class="param-name">{{ row.label }}</div>
          <div class="param-meta">
            {{ row.value }}<template v-if="row.unit"> {{ row.unit }}</template>
            <template v-if="row.hint"> · {{ row.hint }}</template>
          </div>
        </div>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">验证</div>
        <h3 class="cat-title">能不能拿去下发</h3>
        <p class="cat-lead">{{ validateLead }}</p>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceParamSetApi, DeviceParamSetVO } from '@/api/device/paramSet'
import CategoryDrawer from '../components/CategoryDrawer.vue'
import { deviceTypeLabel, parseParams, parseSchema } from '../labels'
import { formatDate } from '@/utils/formatTime'

const visible = ref(false)
const loading = ref(false)
const paramSet = ref<DeviceParamSetVO>()
const action = ref<DeviceActionVO>()

const lead = computed(() => {
  if (!paramSet.value) return ''
  return `${deviceTypeLabel(paramSet.value.deviceTypeCode)} · ${paramSet.value.actionCode || '未关联动作'}`
})

const usageLead = computed(() => {
  if (!paramSet.value) return ''
  const name = action.value?.actionName || paramSet.value.actionCode || '未关联动作'
  if (paramSet.value.status === 1) return `预设绑定 ${name}，但参数集已停用。`
  return `调用 ${name} 时可以直接套用这组参数，不用每次手填。`
})

const rows = computed(() => {
  const values = parseParams(paramSet.value?.paramsJson)
  const schema = parseSchema(action.value?.paramSchema)
  return values.map((item) => {
    const field = schema.find((f) => f.key === item.key)
    return {
      key: item.key,
      label: field?.label || item.key,
      value: item.value,
      unit: field?.unit,
      hint: field?.type
    }
  })
})

const validateLead = computed(() => {
  const item = paramSet.value
  if (!item) return ''
  if (!item.validated) return '还没验证。未验证的参数集不能用于发起命令。'
  const who = item.validatedBy || '未记录验证人'
  const when = item.validatedTime ? formatDate(item.validatedTime) : ''
  return when ? `${who} 于 ${when} 确认过，可以下发。` : `${who} 已确认，可以下发。`
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  paramSet.value = undefined
  action.value = undefined
  try {
    const data = await DeviceParamSetApi.getParamSet(id)
    paramSet.value = data
    if (data?.deviceTypeCode) {
      const actions = (await DeviceActionApi.getActionList({ deviceTypeCode: data.deviceTypeCode })) || []
      action.value = actions.find((item: DeviceActionVO) => item.actionCode === data.actionCode)
    }
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
