<template>
  <ContentWrap>
    <div class="parent-header">
      <div class="parent-title">
        <el-button link type="primary" @click="router.back()">
          <Icon icon="ep:arrow-left" class="mr-5px" />返回实例列表
        </el-button>
        <el-divider direction="vertical" />
        <div>
          <div class="title">{{
            parent?.instanceName || parent?.instanceCode || '子实例管理'
          }}</div>
          <div class="subtitle">父实例：{{ parent?.instanceCode || '加载中' }}</div>
        </div>
      </div>
      <el-button
        v-if="parent"
        v-hasPermi="['wms:material-instance:create']"
        type="primary"
        @click="openCreate"
      >
        <Icon icon="ep:plus" class="mr-5px" />新增子实例
      </el-button>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="72px"
    >
      <el-form-item label="实例编码" prop="instanceCode">
        <el-input
          v-model="queryParams.instanceCode"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="实例名称" prop="instanceName">
        <el-input
          v-model="queryParams.instanceName"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="instanceStatus">
        <el-select
          v-model="queryParams.instanceStatus"
          clearable
          class="!w-160px"
          placeholder="全部状态"
        >
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" row-key="id">
      <el-table-column label="位置" prop="parentPositionCode" width="100" align="center" />
      <el-table-column label="实例编码" prop="instanceCode" min-width="180" show-overflow-tooltip />
      <el-table-column label="实例名称" prop="instanceName" min-width="160" show-overflow-tooltip />
      <el-table-column label="内容物" min-width="160" show-overflow-tooltip>
        <template #default="scope">{{ contentText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="样本编号" prop="sampleId" min-width="150" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope"
          ><el-tag :type="statusType(scope.row.instanceStatus)" size="small">{{
            statusText(scope.row.instanceStatus)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="体积 / 数量" width="120" align="right">
        <template #default="scope">{{ amountText(scope.row) }}</template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        prop="createTime"
        width="170"
        align="center"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" width="210" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openChildren(scope.row)">下级详情</el-button>
          <el-button
            v-hasPermi="['wms:material-instance:update']"
            link
            type="primary"
            @click="openEdit(scope.row)"
            >编辑</el-button
          >
          <el-button
            v-hasPermi="['wms:material-instance:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <InstanceBatchForm ref="batchFormRef" @success="getList" />
  <InstanceForm ref="instanceFormRef" @success="getList" />
</template>

<script setup lang="ts">
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import { formatDate } from '@/utils/formatTime'
import InstanceBatchForm from '../InstanceBatchForm.vue'
import InstanceForm from '../InstanceForm.vue'

defineOptions({ name: 'WmsInstanceChildren' })

const route = useRoute()
const router = useRouter()
const message = useMessage()
const { t } = useI18n()
// Long/Snowflake ID 必须保持字符串，转成 Number 会超过 JS 安全整数并导致查询不到子集。
const parentId = computed(() => String(route.params.parentId || ''))
const parent = ref<MaterialInstanceVO>()
const list = ref<MaterialInstanceVO[]>([])
const contentDefs = ref<ContentDefVO[]>([])
const total = ref(0)
const loading = ref(false)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  parentInstanceId: parentId.value,
  instanceCode: undefined,
  instanceName: undefined,
  instanceStatus: undefined
})

const STATUS_META: Record<
  string,
  { label: string; type: 'success' | 'primary' | 'warning' | 'danger' | 'info' }
> = {
  AVAILABLE: { label: '可用', type: 'success' },
  RESERVED: { label: '已预留', type: 'primary' },
  IN_USE: { label: '使用中', type: 'primary' },
  USED: { label: '已用完', type: 'info' },
  EXPIRED: { label: '已过期', type: 'warning' },
  DISCARDED: { label: '已废弃', type: 'danger' }
}
const statusOptions = Object.entries(STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.label
}))
const statusText = (value?: string) => STATUS_META[value || '']?.label || value || '—'
const statusType = (value?: string) => STATUS_META[value || '']?.type || 'info'
const contentText = (row: MaterialInstanceVO) =>
  contentDefs.value.find(
    (item) => item.id === row.contentDefId || item.contentCode === row.contentDefCode
  )?.contentName ||
  (row.contentType === 'EMPTY' ? '空容器' : row.contentDefCode) ||
  '—'
const amountText = (row: MaterialInstanceVO) =>
  row.currentVolUl != null
    ? `${row.currentVolUl} μL`
    : row.currentCount != null
      ? `${row.currentCount} 个`
      : '—'
const dateFormatter = (_row: unknown, _column: unknown, value: unknown) =>
  value ? formatDate(value as string | number | Date) : '—'

const getList = async () => {
  loading.value = true
  try {
    const data = await MaterialInstanceApi.getMaterialInstancePage(queryParams)
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
  handleQuery()
}
const batchFormRef = ref()
const instanceFormRef = ref()
const openCreate = () => parent.value && batchFormRef.value.open(parent.value)
const openEdit = (row: MaterialInstanceVO) => instanceFormRef.value.open('update', row.id)
const openChildren = (row: MaterialInstanceVO) =>
  router.push({ name: 'WmsInstanceChildren', params: { parentId: row.id } })
const handleDelete = async (row: MaterialInstanceVO) => {
  try {
    await message.delConfirm(`确定删除子实例「${row.instanceName || row.instanceCode}」吗？`)
  } catch {
    return
  }
  await MaterialInstanceApi.deleteMaterialInstance(row.id!)
  message.success(t('common.delSuccess'))
  await getList()
}

const loadPage = async () => {
  if (!parentId.value) return router.back()
  queryParams.parentInstanceId = parentId.value
  queryParams.pageNo = 1
  const [parentData, contents] = await Promise.all([
    MaterialInstanceApi.getMaterialInstance(parentId.value),
    ContentDefApi.getContentDefList()
  ])
  parent.value = parentData
  contentDefs.value = contents || []
  await getList()
}

watch(parentId, loadPage)
onMounted(loadPage)
</script>

<style scoped lang="scss">
.parent-header,
.parent-title {
  display: flex;
  align-items: center;
}

.parent-header {
  justify-content: space-between;
}

.parent-title {
  gap: 8px;
}

.title {
  font-size: 17px;
  font-weight: 600;
}

.subtitle {
  margin-top: 3px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
