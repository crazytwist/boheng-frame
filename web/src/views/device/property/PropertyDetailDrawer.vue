<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="可观测属性" :title="property?.propertyName">
    <template #tags>
      <el-tag v-if="property" :type="property.status === 0 ? 'success' : 'info'" size="small">
        {{ property.status === 0 ? '启用' : '停用' }}
      </el-tag>
    </template>

    <template v-if="property">
      <section class="cat-block">
        <div class="cat-kicker">观测对象</div>
        <dl class="cat-facts">
          <dt>设备类型</dt>
          <dd>{{ deviceTypeLabel(property.deviceTypeCode) }}</dd>
          <dt>编码</dt>
          <dd>{{ property.propertyCode }}</dd>
          <dt>类型</dt>
          <dd>{{ property.dataType || '—' }}</dd>
          <dt>单位</dt>
          <dd>{{ property.unit || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">取值</div>
        <dl class="cat-facts">
          <dt>来源</dt>
          <dd>{{ sourceLabel(property.source) }}</dd>
          <dt>主动读取</dt>
          <dd>{{ property.readable ? '是' : '否' }}</dd>
          <dt>推送</dt>
          <dd>{{ property.subscribable ? '是' : '否' }}</dd>
          <dt>轮询</dt>
          <dd>{{ property.pollIntervalSec ? `${property.pollIntervalSec} 秒` : '—' }}</dd>
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
