<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="640px"
    :close-on-click-modal="false"
    align-center
  >
    <template v-if="issues.length">
      <el-alert :title="summary" type="warning" :closable="false" show-icon class="mb-10px" />
      <ul class="issue-list">
        <li v-for="(issue, index) in issues" :key="index" class="issue-item" @click="onPickLocal(issue)">
          <el-tag v-if="issue.nodeId" type="warning" effect="plain" size="small" class="issue-tag">
            {{ issue.nodeId }}
          </el-tag>
          <span v-else class="issue-tag issue-tag--global">全局</span>
          <span class="issue-msg">{{ issue.message }}</span>
        </li>
      </ul>
    </template>
    <el-empty v-else :description="emptyText || '没有需要修复的问题，可以继续下一步'" />
    <template #footer>
      <el-button type="primary" @click="dialogVisible = false">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
/**
 * 流程校验结果的结构化展示。后端 /flow/definition/validate 返回的 FlowIssueVO[]
 * 一直是拼接成一条 warning，没有按节点定位。校验是发布前唯一的拦截面，
 * 把它做厚一层，是发布链路能否被信任的基础。
 *
 * 使用方式：
 *   const ref = ref<InstanceType<typeof FlowIssuePanel>>()
 *   ref.value?.open(issues, { title: '流程校验：6 个问题', onPick: (i) => ... })
 *
 * 缺省主题：仅展示，不联动（弹层即收口）。
 */
export interface FlowIssueVO {
  nodeId?: string
  message: string
}

defineOptions({ name: 'FlowIssuePanel' })

interface OpenOptions {
  title?: string
  emptyText?: string
  /** 点击某条 issue：父页面用 nodeId 跳到该节点/弹窗 */
  onPick?: (issue: FlowIssueVO) => void
}

const dialogVisible = ref(false)
const issues = ref<FlowIssueVO[]>([])
const title = ref('流程校验结果')
const emptyText = ref<string>()
let onPick: ((issue: FlowIssueVO) => void) | undefined

const summary = computed(() => {
  const n = issues.value.length
  if (!n) return ''
  const withNode = issues.value.filter((i) => i.nodeId).length
  return `共 ${n} 个问题（${withNode} 个可定位到节点，${n - withNode} 个全局问题）`
})

const open = (payload: FlowIssueVO[], options: OpenOptions = {}) => {
  issues.value = Array.isArray(payload) ? payload : []
  title.value = options.title ?? '流程校验结果'
  emptyText.value = options.emptyText
  onPick = options.onPick
  dialogVisible.value = true
}

const onPickLocal = (issue: FlowIssueVO) => {
  onPick?.(issue)
}

defineExpose({ open })
</script>

<style lang="scss" scoped>
.issue-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  overflow: hidden;
  max-height: 420px;
  overflow-y: auto;
}

.issue-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  line-height: 1.5;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background-color 0.12s ease;
}

.issue-item:last-child {
  border-bottom: none;
}

.issue-item:hover {
  background-color: var(--el-color-warning-light-9);
}

.issue-tag {
  flex-shrink: 0;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 12px;
}

.issue-tag--global {
  display: inline-block;
  padding: 0 6px;
  height: 20px;
  line-height: 18px;
  background-color: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
  border-radius: 3px;
  font-size: 12px;
}

.issue-msg {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-regular);
}
</style>
