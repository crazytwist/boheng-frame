<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="可观测属性" :title="property?.propertyName" :summary="lead">
    <template #tags>
      <el-tag v-if="property" :type="property.status === 0 ? 'success' : 'info'" size="small">
        {{ property.status === 0 ? '启用' : '停用' }}
      </el-tag>
    </template>

    <template v-if="property">
      <section class="cat-block">
        <div class="cat-kicker">观测对象</div>
        <h3 class="cat-title">读的是什么</h3>
        <p class="cat-lead">
          {{ deviceTypeLabel(property.deviceTypeCode) }} 的 {{ property.propertyCode }}。
          值的类型是 {{ property.dataType || '未声明' }}<template v-if="property.unit">，单位 {{ property.unit }}</template>。
          当前值不存在这张定义表里，快照挂在设备台账上。
        </p>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">取值</div>
        <h3 class="cat-title">数从哪来</h3>
        <p class="cat-lead">{{ acquireLead }}</p>
        <dl class="cat-facts">
          <dt>来源</dt>
          <dd>{{ sourceLabel(property.source) }}</dd>
        </dl>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DevicePropertyApi, DevicePropertyVO } from '@/api/device/property'
import CategoryDrawer from '../components/CategoryDrawer.vue'
import { deviceTypeLabel, sourceLabel } from '../labels'

const visible = ref(false)
const loading = ref(false)
const property = ref<DevicePropertyVO>()

const lead = computed(() => (property.value ? deviceTypeLabel(property.value.deviceTypeCode) : ''))

const acquireLead = computed(() => {
  const item = property.value
  if (!item) return ''
  const parts: string[] = []
  if (item.readable) parts.push('需要时可以主动读一次')
  if (item.subscribable) parts.push('仪器状态变化时可以推送')
  if (item.pollIntervalSec) parts.push(`不推送时每 ${item.pollIntervalSec} 秒轮询`)
  if (!parts.length) return '这个属性目前既不能读，也不会被刷新。'
  return parts.join('，') + '。'
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  property.value = undefined
  try {
    property.value = await DevicePropertyApi.getProperty(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
