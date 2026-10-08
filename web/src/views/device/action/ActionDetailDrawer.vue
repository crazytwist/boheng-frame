<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="设备动作" :title="action?.actionName" :summary="lead">
    <template #tags>
      <el-tag v-if="action" :type="action.status === 0 ? 'success' : 'info'" size="small">
        {{ action.status === 0 ? '启用' : '停用' }}
      </el-tag>
    </template>

    <template v-if="action">
      <section class="cat-block">
        <div class="cat-kicker">动作</div>
        <h3 class="cat-title">启动的是什么</h3>
        <p class="cat-lead">
          {{ deviceTypeLabel(action.deviceTypeCode) }} 上的 {{ action.actionCode }}。
          {{ sourceLabel(action.source) }}，预计 {{ durationText(action.estimateDurationMs) }}。
          {{ syncHint }}
        </p>
        <dl class="cat-facts">
          <dt>标准特性</dt>
          <dd>{{ action.standardFeature || '私有动作，没有对应的标准名' }}</dd>
          <dt>厂商映射</dt>
          <dd>{{ action.vendorRef || '还没有映射到厂商命令' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">下发前</div>
        <h3 class="cat-title">要不要先问空闲</h3>
        <p class="cat-lead">{{ busyLead }}</p>
        <dl v-if="action.needBusyCheck" class="cat-facts">
          <dt>查询动作</dt>
          <dd>{{ action.statusCommandCode || '没指定，真机不报空闲时会降级为直接下发' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">回收</div>
        <h3 class="cat-title">怎么知道做完了</h3>
        <p class="cat-lead">{{ pollLead }}</p>
        <p v-if="action.requestTemplate" class="mono">{{ action.requestTemplate }}</p>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">参数</div>
        <h3 class="cat-title">调用时要填什么</h3>
        <p v-if="!fields.length" class="cat-lead">这个动作没有参数，直接启动。</p>
        <div v-for="field in fields" :key="field.key" class="param-card">
          <div class="param-name">{{ field.label }}</div>
          <div class="param-meta">
            {{ field.type || '未声明类型' }}
            <template v-if="field.unit"> · {{ field.unit }}</template>
            · {{ field.required ? '必填' : '可选' }}
            <template v-if="field.defaultValue !== undefined && field.defaultValue !== null">
              · 默认 {{ field.defaultValue }}
            </template>
            <template v-if="constraintText(field.constraints)"> · {{ constraintText(field.constraints) }}</template>
          </div>
        </div>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import CategoryDrawer from '../components/CategoryDrawer.vue'
import { constraintText, deviceTypeLabel, durationText, parseSchema, sourceLabel } from '../labels'

const visible = ref(false)
const loading = ref(false)
const action = ref<DeviceActionVO>()

const fields = computed(() => parseSchema(action.value?.paramSchema))

const lead = computed(() => {
  if (!action.value) return ''
  return `${deviceTypeLabel(action.value.deviceTypeCode)} · ${action.value.actionCode}`
})

const syncHint = computed(() => {
  const ms = action.value?.estimateDurationMs
  if (ms === undefined || ms === null) return '没有耗时估计，不能判断能不能同步等待。'
  return ms < 3000 ? '耗时很短，可以同步等结果。' : '耗时较长，应异步下发，避免调用方一直等。'
})

const busyLead = computed(() => {
  if (!action.value?.needBusyCheck) {
    return '这个动作自己不要求先查空闲。设备台账若设成「强制预检」，下发时仍会问。'
  }
  return '这个动作要求先确认仪器空闲。设备台账若设成「从不预检」，会跳过这一步。'
})

const pollLead = computed(() => {
  if (!action.value) return ''
  if (action.value.pollDoneExpr) {
    const times = action.value.pollMaxTimes ? `最多问 ${action.value.pollMaxTimes} 次。` : '次数走全局默认。'
    return `轮询时用「${action.value.pollDoneExpr}」判断完成。${times}`
  }
  return '没有配置轮询完成条件。若仪器会回调，可以不填；若只能轮询，完成状态将无法自动判断。'
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  action.value = undefined
  try {
    action.value = await DeviceActionApi.getAction(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
