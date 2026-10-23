<template>
  <ContentWrap title="流程定义">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="流程编码" prop="pipelineKey">
        <el-input v-model="queryParams.pipelineKey" placeholder="请输入流程编码" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="流程名称" prop="name">
        <el-input v-model="queryParams.name" placeholder="请输入流程名称" clearable @keyup.enter="handleQuery" class="!w-200px" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openForm('create')" v-hasPermi="['flow:definition:create']">
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <template #empty>
        <el-empty description="还没有流程定义">
          <el-button type="primary" @click="openForm('create')" v-hasPermi="['flow:definition:create']">新建流程</el-button>
        </el-empty>
      </template>
      <el-table-column label="流程编码" align="center" prop="pipelineKey" min-width="140" :show-overflow-tooltip="true" />
      <el-table-column label="流程名称" align="center" prop="name" min-width="160" :show-overflow-tooltip="true" />
      <el-table-column label="版本" align="center" width="80">
        <template #default="scope">v{{ scope.row.version }}</template>
      </el-table-column>
      <el-table-column label="步骤数" align="center" prop="nodeCount" width="80" />
      <el-table-column label="默认出参" align="center" width="100">
        <template #default="scope">
          <el-button link type="primary" @click="openOutput(scope.row)">查看 JSON</el-button>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusTag(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="发布时间" align="center" prop="publishedAt" width="180" :formatter="dateFormatter" />
      <el-table-column label="操作" align="center" width="320" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['flow:definition:query']" link type="primary" @click="handleValidate(scope.row)">校验</el-button>
          <el-button v-hasPermi="['flow:definition:query']" link type="primary" @click="openDetail(scope.row.id)">详情</el-button>
          <el-button v-hasPermi="['flow:relation:query']" link type="primary" @click="openRelation(scope.row.id)">步骤</el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['flow:definition:update']"
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
          >
            编辑
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['flow:definition:publish']"
            link
            type="primary"
            @click="handlePublish(scope.row)"
          >
            发布
          </el-button>
          <el-button
            v-if="scope.row.status !== 'DRAFT'"
            v-hasPermi="['flow:definition:create']"
            link
            type="primary"
            @click="handleRevise(scope.row)"
          >
            修订
          </el-button>
          <el-button
            v-if="scope.row.status !== 'DRAFT'"
            v-hasPermi="['flow:definition:update']"
            link
            type="warning"
            @click="handleRollbackDraft(scope.row)"
          >
            退回草稿
          </el-button>
          <el-button
            v-if="scope.row.status === 'PUBLISHED'"
            v-hasPermi="['flow:definition:publish']"
            link
            type="primary"
            @click="handleRetire(scope.row)"
          >
            停用
          </el-button>
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            v-hasPermi="['flow:definition:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <DefinitionForm ref="formRef" @success="getList" />
  <DefinitionDetailDrawer ref="detailRef" />
  <FlowIssuePanel ref="issueRef" />
  <el-dialog v-model="outputVisible" title="默认出参" width="680px" append-to-body>
    <pre class="output-json">{{ outputJson || '{}' }}</pre>
  </el-dialog>
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import { dateFormatter } from '@/utils/formatTime'
import DefinitionForm from './DefinitionForm.vue'
import DefinitionDetailDrawer from './DefinitionDetailDrawer.vue'
import FlowIssuePanel from '@/components/flow/FlowIssuePanel.vue'

defineOptions({ name: 'FlowDefinition' })

const message = useMessage()
const router = useRouter()

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'RETIRED' }
]
const statusLabel = (status?: string) => statusOptions.find((item) => item.value === status)?.label || status || ''
const statusTag = (status?: string): 'success' | 'info' | 'warning' => {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'RETIRED') return 'info'
  return 'warning'
}

const loading = ref(false)
const list = ref<FlowDefinitionVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  pipelineKey: undefined,
  name: undefined,
  status: undefined
})
const queryFormRef = ref()

const getList = async () => {
  loading.value = true
  try {
    const data = await FlowDefinitionApi.getPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.pageNo = 1
  getList()
}

const formRef = ref()
const detailRef = ref()
const issueRef = ref()
const outputVisible = ref(false)
const outputJson = ref('')
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}
const openDetail = (id: number) => {
  detailRef.value.open(id)
}
const openRelation = (id: number) => {
  router.push({ path: '/flow/relation', query: { definitionId: id } })
}

/** 不发布只校验。打开结构化问题清单，发现问题可一键去编排 */
const handleValidate = async (row: FlowDefinitionVO) => {
  const issues = await FlowDefinitionApi.validate(row.id!)
  await getList()
  issueRef.value?.open(issues || [], {
    title: `校验：${row.name} v${row.version}`,
    emptyText: '所有检查通过，可以发布。',
    onPick: (issue) => {
      if (issue.nodeId) {
        router.push({ path: '/flow/relation', query: { definitionId: row.id, focusNode: issue.nodeId } })
      }
    }
  })
}

/** 从节点输出声明汇总默认出参，保持列表接口轻量，点击时再读取详情。 */
const openOutput = async (row: FlowDefinitionVO) => {
  const detail = await FlowDefinitionApi.get(row.id!)
  const result: Record<string, unknown> = {}
  for (const node of detail.nodes || []) {
    if (!node.outputSchema?.trim()) continue
    try {
      result[node.name || node.nodeId] = JSON.parse(node.outputSchema)
    } catch {
      result[node.name || node.nodeId] = node.outputSchema
    }
  }
  outputJson.value = JSON.stringify(result, null, 2)
  outputVisible.value = true
}

const handleRollbackDraft = async (row: FlowDefinitionVO) => {
  try {
    await message.confirm(`确认将「${row.name}」v${row.version} 退回草稿？发布状态和发布指纹将被清除。`)
  } catch {
    return
  }
  await FlowDefinitionApi.rollbackDraft(row.id!)
  message.success('已退回草稿')
  await getList()
}

const handlePublish = async (row: FlowDefinitionVO) => {
  try {
    await message.confirm(`发布「${row.name}」v${row.version} 后不能再改。确定发布？`)
  } catch {
    return
  }
  const issues = await FlowDefinitionApi.validate(row.id!)
  if (issues?.length) {
    // 发布前的最后一次校验：问题清单 + 跳到编排定位
    issueRef.value?.open(issues, {
      title: `「${row.name}」v${row.version} 还有 ${issues.length} 个问题未处理`,
      onPick: (issue) => {
        if (issue.nodeId) {
          router.push({ path: '/flow/relation', query: { definitionId: row.id, focusNode: issue.nodeId } })
        }
      }
    })
    return
  }
  await FlowDefinitionApi.publish(row.id!)
  message.success('已发布')
  getList()
}

const handleRevise = async (row: FlowDefinitionVO) => {
  try {
    await message.confirm(`从「${row.name}」v${row.version} 复制出下一版草稿？`)
  } catch {
    return
  }
  await FlowDefinitionApi.revise(row.id!)
  message.success('已生成草稿')
  getList()
}

const handleRetire = async (row: FlowDefinitionVO) => {
  try {
    await message.confirm(`停用「${row.name}」v${row.version}？`)
  } catch {
    return
  }
  await FlowDefinitionApi.retire(row.id!)
  message.success('已停用')
  getList()
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm()
  } catch {
    return
  }
  await FlowDefinitionApi.delete(id)
  message.success('删除成功')
  getList()
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.output-json {
  max-height: 520px;
  margin: 0;
  padding: 14px;
  overflow: auto;
  font-family: Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
}
</style>
