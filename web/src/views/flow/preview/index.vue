<template>
  <ContentWrap title="流程预览">
    <!-- 选择流程定义 -->
    <el-form class="-mb-15px" :inline="true" label-width="80px">
      <el-form-item label="流程">
        <el-select
          v-model="definitionId"
          placeholder="请选择要预览的流程定义"
          clearable
          filterable
          class="!w-380px"
          @change="loadDefinition"
        >
          <el-option
            v-for="item in definitions"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-if="definition">
        <el-tag :type="statusTag(definition.status)" size="small">{{ statusLabel(definition.status) }}</el-tag>
        <span class="ml-10px text-13px color-#909399">
          共 {{ definition.nodes?.length || 0 }} 个节点 · 点击节点可看参数传递
        </span>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div v-if="!definitionId" class="flow-empty">
      <el-empty description="从上方选择一个流程定义，即可看到 Dify 风格的编排图" />
    </div>
    <FlowDag v-else :nodes="definition?.nodes || []" />
  </ContentWrap>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import FlowDag from './FlowDag.vue'

defineOptions({ name: 'FlowPreview' })

const definitions = ref<FlowDefinitionVO[]>([])
const definitionId = ref<number>()
const definition = ref<FlowDefinitionVO>()

const statusLabel = (status?: string) =>
  ({ DRAFT: '草稿', PUBLISHED: '已发布', RETIRED: '已停用' }[status || ''] || status || '')
const statusTag = (status?: string): 'success' | 'info' | 'warning' => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'RETIRED') return 'info'
  return 'warning'
}

const loadDefinition = async () => {
  definition.value = undefined
  const id = definitionId.value
  if (!id) return
  definition.value = await FlowDefinitionApi.get(id)
}

onMounted(async () => {
  definitions.value = (await FlowDefinitionApi.simpleList()) || []
})
</script>

<style lang="scss" scoped>
.flow-empty {
  padding: 60px 0;
}
</style>
