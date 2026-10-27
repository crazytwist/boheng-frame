<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="实验步骤" :title="step?.name" size="560px">
    <template #tags>
      <el-tag v-if="step" :type="step.status === 0 ? 'success' : 'info'" size="small">
        {{ step.status === 0 ? '启用' : '停用' }}
      </el-tag>
    </template>
    <section v-if="step" class="cat-block">
      <div class="cat-kicker">基本信息</div>
      <dl class="cat-facts">
        <dt>步骤编码</dt>
        <dd>{{ step.stepKey }}</dd>
        <dt>步骤类型</dt>
        <dd>{{ stepTypeLabel(step.stepType) }}</dd>
        <dt>类型配置</dt>
        <dd>
          <pre v-if="typeConfigText" class="param-json">{{ typeConfigText }}</pre>
          <span v-else>—</span>
        </dd>
        <dt>默认输入</dt>
        <dd>
          <pre v-if="defaultParamsText" class="param-json">{{ defaultParamsText }}</pre>
          <span v-else>—</span>
          <div v-for="c in defaultComments" :key="c.key" class="param-comment"># {{ c.key }}：{{ c.comment }}</div>
        </dd>
        <dt>输出声明</dt>
        <dd>
          <pre v-if="outputSchemaText" class="param-json">{{ outputSchemaText }}</pre>
          <span v-else class="param-hint">未声明（输出动态，不做契约校验）</span>
        </dd>
        <dt>等待</dt>
        <dd>{{ (step.timeoutSec ?? 0) < 0 ? '永不超时' : `${step.timeoutSec} 秒` }}</dd>
        <dt>尝试次数</dt>
        <dd>{{ step.maxAttempts }}</dd>
        <dt>说明</dt>
        <dd>{{ step.remark || '—' }}</dd>
      </dl>
    </section>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { FlowStepApi, FlowStepVO, STEP_TYPES } from '@/api/flow/step'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'
import { formatFlowInputParamsForView } from '@/utils/flowInputParams'

defineOptions({ name: 'FlowStepDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const step = ref<FlowStepVO>()

const stepTypeLabel = (type?: string) =>
  STEP_TYPES.find((item) => item.value === type)?.label ?? type ?? '—'

const formatJson = (raw?: string): string => {
  if (!raw?.trim()) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

const typeConfigText = computed(() => formatJson(step.value?.typeConfig))
const defaultParamsView = computed(() => formatFlowInputParamsForView(step.value?.defaultParams))
const defaultParamsText = computed(() => defaultParamsView.value.valuesText)
const defaultComments = computed(() => defaultParamsView.value.comments)
const outputSchemaText = computed(() => formatJson(step.value?.outputSchema))

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  step.value = undefined
  try {
    step.value = await FlowStepApi.get(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style lang="scss" scoped>
.param-json {
  margin: 0;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
}

.param-comment {
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
}

.param-hint {
  font-size: 12px;
  color: #909399;
}
</style>
