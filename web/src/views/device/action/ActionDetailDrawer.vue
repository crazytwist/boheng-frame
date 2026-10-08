<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="设备动作" :title="action?.actionName">
    <template #tags>
      <el-tag v-if="action" :type="action.status === 0 ? 'success' : 'info'" size="small">
        {{ action.status === 0 ? '启用' : '停用' }}
      </el-tag>
    </template>

    <template v-if="action">
      <section class="cat-block">
        <div class="cat-kicker">动作</div>
        <dl class="cat-facts">
          <dt>设备类型</dt>
          <dd>{{ deviceTypeLabel(action.deviceTypeCode) }}</dd>
          <dt>编码</dt>
          <dd>{{ action.actionCode }}</dd>
          <dt>来源</dt>
          <dd>{{ sourceLabel(action.source) }}</dd>
          <dt>预计耗时</dt>
          <dd>{{ durationText(action.estimateDurationMs) }}</dd>
          <dt>标准特性</dt>
          <dd>{{ action.standardFeature || '—' }}</dd>
          <dt>厂商映射</dt>
          <dd>{{ action.vendorRef || '—' }}</dd>
          <dt>请求方法</dt>
          <dd>{{ action.httpMethod || '—' }}</dd>
          <dt>报文格式</dt>
          <dd>{{ formatLabel(action.bodyFormat) }}</dd>
          <dt>接口路径</dt>
          <dd>{{ action.requestPath || '—' }}</dd>
          <dt>解析规则</dt>
          <dd>{{ action.codecCode || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">下发前</div>
        <dl class="cat-facts">
          <dt>空闲预检</dt>
          <dd>{{ action.needBusyCheck ? '需要' : '不需要' }}</dd>
          <template v-if="action.needBusyCheck">
            <dt>查询动作</dt>
            <dd>{{ action.statusCommandCode || '—' }}</dd>
          </template>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">回收</div>
        <dl class="cat-facts">
          <dt>完成条件</dt>
          <dd>{{ action.pollDoneExpr || '—' }}</dd>
          <dt>轮询次数</dt>
          <dd>{{ action.pollMaxTimes ?? '—' }}</dd>
        </dl>
        <p v-if="action.requestTemplate" class="mono">{{ action.requestTemplate }}</p>
      </section>

      <section v-if="fields.length" class="cat-block">
        <div class="cat-kicker">参数</div>
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

const formatLabel = (value?: string) =>
  ({ JSON: 'JSON', FORM: '表单', TEXT: '纯文本', XML: 'XML' }[value || ''] || value || 'JSON')

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
