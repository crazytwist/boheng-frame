<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="流程定义" :title="definition?.name" size="720px">
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
        <div class="cat-kicker">节点</div>
        <el-table :data="definition.nodes || []" border>
          <el-table-column label="序" width="50" align="center">
            <template #default="scope">{{ scope.$index + 1 }}</template>
          </el-table-column>
          <el-table-column label="节点编码" prop="nodeId" min-width="100" />
          <el-table-column label="名称" prop="name" min-width="100" />
          <el-table-column label="类型" min-width="100" align="center">
            <template #default="scope">
              <el-tag :type="isControl(scope.row.stepType) ? 'warning' : 'primary'" size="small">
                {{ typeLabel(scope.row.stepType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="前置" min-width="110" :show-overflow-tooltip="true">
            <template #default="scope">
              {{ (scope.row.dependsOn || []).length ? (scope.row.dependsOn || []).join('、') : '起点' }}
            </template>
          </el-table-column>
          <el-table-column label="汇合" width="90" align="center">
            <template #default="scope">{{ joinLabel(scope.row.joinPolicy) }}</template>
          </el-table-column>
          <el-table-column label="等待(秒)" prop="timeoutSec" width="85" align="center">
            <template #default="scope">{{ scope.row.timeoutSec < 0 ? '永不超时' : scope.row.timeoutSec }}</template>
          </el-table-column>
        </el-table>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import { STEP_TYPES } from '@/api/flow/step'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'FlowDefinitionDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const definition = ref<FlowDefinitionVO>()

const CONTROL_TYPES = ['route', 'loop', 'subflow', 'timer.wait']
const CONTROL_LABELS: Record<string, string> = {
  route: '出口路由',
  loop: '循环容器',
  subflow: '子流程',
  'timer.wait': '定时等待'
}

const isControl = (type?: string) => CONTROL_TYPES.includes(type ?? '')
const typeLabel = (type?: string) =>
  CONTROL_LABELS[type ?? ''] ?? STEP_TYPES.find((item) => item.value === type)?.label ?? type ?? '—'
const joinLabel = (policy?: string) =>
  policy === 'ANY_SUCCESS' ? '任一成功' : policy === 'ALL_DONE' ? '全部终态' : '全部成功'

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
