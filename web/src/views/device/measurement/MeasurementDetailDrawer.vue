<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="测量" :title="row?.deviceCode" size="640px">
    <template v-if="row">
      <section class="cat-block">
        <div class="cat-kicker">测量</div>
        <dl class="cat-facts">
          <dt>设备</dt>
          <dd>{{ row.deviceCode || '—' }}</dd>
          <dt>命令</dt>
          <dd>{{ row.commandId || '—' }}</dd>
          <dt>模式</dt>
          <dd>{{ row.measureMode || '—' }}</dd>
          <dt>波长</dt>
          <dd>{{ row.wavelengthNm ? `${row.wavelengthNm} nm` : '—' }}</dd>
          <dt>方法</dt>
          <dd>{{ row.scriptName || '—' }}</dd>
        </dl>
      </section>

      <section v-if="row.analyses?.length" class="cat-block">
        <div class="cat-kicker">结论</div>
        <dl class="cat-facts">
          <template v-for="item in row.analyses" :key="item.resultType">
            <dt>{{ item.resultType }}</dt>
            <dd>{{ item.resultValue || '—' }}</dd>
          </template>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">孔位</div>
        <el-table v-if="row.wells?.length" :data="row.wells" size="small">
          <el-table-column label="孔位" prop="wellPosition" width="80" />
          <el-table-column label="波长" prop="wavelengthNm" width="80" />
          <el-table-column label="读数" prop="readValue" />
          <el-table-column label="单位" prop="unit" width="80" />
          <el-table-column label="质量" prop="qualityFlag" />
        </el-table>
        <div v-else class="empty">这次响应里没有孔位读数</div>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceMeasurementApi, DeviceMeasurementVO } from '@/api/device/measurement'
import CategoryDrawer from '../components/CategoryDrawer.vue'

const visible = ref(false)
const loading = ref(false)
const row = ref<DeviceMeasurementVO>()

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  row.value = undefined
  try {
    row.value = await DeviceMeasurementApi.getMeasurement(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
