<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="参数集" :title="paramSet?.paramSetName">
    <template #tags>
      <el-tag v-if="paramSet" :type="paramSet.validated ? 'success' : 'warning'" size="small">
        {{ paramSet.validated ? '已验证' : '未验证' }}
      </el-tag>
    </template>

    <template v-if="paramSet">
      <section class="cat-block">
        <div class="cat-kicker">用途</div>
        <dl class="cat-facts">
          <dt>设备类型</dt>
          <dd>{{ deviceTypeLabel(paramSet.deviceTypeCode) }}</dd>
          <dt>动作</dt>
          <dd>{{ action?.actionName || paramSet.actionCode || '—' }}</dd>
          <dt>状态</dt>
          <dd>{{ paramSet.status === 1 ? '停用' : '启用' }}</dd>
        </dl>
      </section>

      <section v-if="rows.length" class="cat-block">
        <div class="cat-kicker">取值</div>
        <div v-for="row in rows" :key="row.key" class="param-card">
          <div class="param-name">{{ row.label }}</div>
          <div class="param-meta">
            {{ row.value }}<template v-if="row.unit"> {{ row.unit }}</template>
          </div>
        </div>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">验证</div>
        <dl class="cat-facts">
          <dt>状态</dt>
          <dd>{{ paramSet.validated ? '已验证' : '未验证' }}</dd>
          <template v-if="paramSet.validated">
            <dt>验证人</dt>
            <dd>{{ paramSet.validatedBy || '—' }}</dd>
            <dt>时间</dt>
            <dd>{{ paramSet.validatedTime ? formatDate(paramSet.validatedTime) : '—' }}</dd>
          </template>
        </dl>
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

const rows = computed(() => {
  const values = parseParams(paramSet.value?.paramsJson)
  const schema = parseSchema(action.value?.paramSchema)
  return values.map((item) => {
    const field = schema.find((f) => f.key === item.key)
    return {
      key: item.key,
      label: field?.label || item.key,
      value: item.value,
      unit: field?.unit
    }
  })
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
