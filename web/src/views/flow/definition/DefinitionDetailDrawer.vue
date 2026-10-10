<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="流程定义" :title="definition?.name" size="640px">
    <template #tags>
      <el-tag v-if="definition" :type="statusTag(definition.status)" size="small">{{ statusLabel(definition.status) }}</el-tag>
    </template>
    <template v-if="definition">
      <section class="cat-block">
        <div class="cat-kicker">基本信息</div>
        <dl class="cat-facts">
          <dt>流程编码</dt>
          <dd>{{ definition.pipelineKey }}</dd>
          <dt>版本</dt>
          <dd>v{{ definition.version }}</dd>
          <dt>说明</dt>
          <dd>{{ definition.remark || '—' }}</dd>
          <dt>发布时间</dt>
          <dd>{{ publishedText }}</dd>
        </dl>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">步骤</div>
        <el-table :data="definition.nodes || []" border>
          <el-table-column label="顺序" width="60" align="center">
            <template #default="scope">{{ scope.$index + 1 }}</template>
          </el-table-column>
          <el-table-column label="节点编码" prop="nodeId" min-width="100" />
          <el-table-column label="步骤编码" prop="stepKey" min-width="100" />
          <el-table-column label="步骤名称" prop="name" min-width="100" />
          <el-table-column label="设备" prop="deviceCode" min-width="110" />
          <el-table-column label="动作" prop="actionCode" min-width="110" />
          <el-table-column label="等待(秒)" prop="timeoutSec" width="90" align="center" />
          <el-table-column label="尝试次数" prop="maxAttempts" width="90" align="center" />
        </el-table>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'FlowDefinitionDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const definition = ref<FlowDefinitionVO>()

const statusLabel = (status?: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' }[status || ''] || status || '')
const statusTag = (status?: string): 'success' | 'info' | 'warning' => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'RETIRED') return 'info'
  return 'warning'
}
const publishedText = computed(() => (definition.value?.publishedAt ? formatDate(definition.value.publishedAt) : '—'))

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  definition.value = undefined
  try {
    definition.value = await FlowDefinitionApi.get(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
