<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="实验步骤" :title="step?.name" size="480px">
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
        <dt>设备</dt>
        <dd>{{ step.deviceCode }}</dd>
        <dt>动作</dt>
        <dd>{{ step.actionCode }}</dd>
        <dt>等待</dt>
        <dd>{{ step.timeoutSec }} 秒</dd>
        <dt>尝试次数</dt>
        <dd>{{ step.maxAttempts }}</dd>
        <dt>说明</dt>
        <dd>{{ step.remark || '—' }}</dd>
      </dl>
    </section>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { FlowStepApi, FlowStepVO } from '@/api/flow/step'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'

defineOptions({ name: 'FlowStepDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const step = ref<FlowStepVO>()

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
