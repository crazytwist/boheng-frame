<template>
  <ContentWrap title="物料实例">
    <el-form
      ref="queryFormRef"
      :inline="true"
      :model="queryParams"
      class="-mb-15px"
      label-width="72px"
    >
      <el-form-item label="实例" prop="instanceCode">
        <el-input
          v-model="queryParams.instanceCode"
          class="!w-220px"
          clearable
          placeholder="实例编码"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="名称" prop="instanceName">
        <el-input
          v-model="queryParams.instanceName"
          class="!w-200px"
          clearable
          placeholder="实例名称"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="条码" prop="barcode">
        <el-input
          v-model="queryParams.barcode"
          class="!w-180px"
          clearable
          placeholder="条码"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="instanceStatus">
        <el-select
          v-model="queryParams.instanceStatus"
          class="!w-150px"
          clearable
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
      <el-form-item label="槽位" prop="rootSlotCode">
        <el-input
          v-model="queryParams.rootSlotCode"
          class="!w-180px"
          clearable
          placeholder="槽位编码"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon class="mr-5px" icon="ep:search" />搜索</el-button>
        <el-button @click="resetQuery"><Icon class="mr-5px" icon="ep:refresh" />重置</el-button>
        <el-button
          v-hasPermi="['wms:material-instance:create']"
          plain
          type="primary"
          @click="handleAddInstance"
        >
          <Icon class="mr-5px" icon="ep:plus" />新增实例
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="list-toolbar">
      <div
        ><span class="list-title">实例列表</span
        ><span class="muted">共 {{ total }} 条，点击一行查看详情</span></div
      >
      <el-button :loading="loading" link @click="getList"
        ><Icon class="mr-5px" icon="ep:refresh" />刷新</el-button
      >
    </div>
    <el-table
      v-loading="loading"
      :data="list"
      class="instance-table"
      highlight-current-row
      row-key="id"
      @current-change="handleInstanceClick"
      @row-dblclick="handleEditInstance"
    >
      <el-table-column label="实例" min-width="210" fixed="left">
        <template #default="scope">
          <div class="instance-cell">
            <Icon :icon="instanceIcon(scope.row)" class="instance-icon" />
            <div class="instance-main">
              <span class="instance-name">{{
                scope.row.instanceName || scope.row.instanceCode
              }}</span>
              <span class="instance-code">{{ scope.row.instanceCode }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" width="100">
        <template #default="scope"
          ><el-tag :type="statusType(scope.row.instanceStatus)" size="small">{{
            statusText(scope.row.instanceStatus)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="容器类型" min-width="145" show-overflow-tooltip>
        <template #default="scope">{{ containerTypeText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="内容物" min-width="145" show-overflow-tooltip>
        <template #default="scope">{{ contentText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="样本编号" min-width="145" prop="sampleId" show-overflow-tooltip />
      <el-table-column label="体积 / 数量" align="right" width="120">
        <template #default="scope">{{ amountText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="所在区域 → 库位" min-width="230" show-overflow-tooltip>
        <template #default="scope">{{ locationText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="条码" min-width="145" prop="barcode" show-overflow-tooltip />
      <el-table-column
        label="创建时间"
        align="center"
        width="170"
        prop="createTime"
        :formatter="dateFormatter"
      />
      <el-table-column label="操作" align="center" fixed="right" width="210">
        <template #default="scope">
          <el-button
            v-hasPermi="['wms:material-instance:create']"
            link
            type="primary"
            @click.stop="handleAddChild(scope.row)"
            >层级详情</el-button
          >
          <el-button
            v-hasPermi="['wms:material-instance:update']"
            link
            type="primary"
            @click.stop="handleEditInstance(scope.row)"
            >编辑</el-button
          >
          <el-button
            v-hasPermi="['wms:material-instance:delete']"
            link
            type="danger"
            @click.stop="handleDeleteInstance(scope.row)"
            >删除</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <ContentWrap v-if="currentInstance">
    <div class="detail-header">
      <div class="detail-heading">
        <Icon :icon="instanceIcon(currentInstance)" class="detail-icon" />
        <div>
          <div class="detail-title">{{
            currentInstance.instanceName || currentInstance.instanceCode
          }}</div>
          <div class="detail-sub"
            >{{ currentInstance.instanceCode }} ·
            {{ currentInstance.containerTypeCode || '未绑定容器' }}</div
          >
        </div>
      </div>
      <div class="detail-actions">
        <el-tag :type="statusType(currentInstance.instanceStatus)">{{
          statusText(currentInstance.instanceStatus)
        }}</el-tag>
        <el-button link @click="currentInstance = undefined"><Icon icon="ep:close" /></el-button>
      </div>
    </div>
    <el-descriptions :column="3" border class="detail-desc">
      <el-descriptions-item label="条码">{{ currentInstance.barcode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="内容物类型">{{
        contentTypeText(currentInstance.contentType)
      }}</el-descriptions-item>
      <el-descriptions-item label="样本编号">{{
        currentInstance.sampleId || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="当前体积">{{
        currentInstance.currentVolUl != null ? currentInstance.currentVolUl + ' μL' : '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="落位槽位">{{
        currentInstance.rootSlotCode || '未落位'
      }}</el-descriptions-item>
      <el-descriptions-item label="父位置">{{
        currentInstance.parentPositionCode || '顶层'
      }}</el-descriptions-item>
    </el-descriptions>
    <template v-if="isCarrierWithWells">
      <div class="plate-title"
        ><span class="list-title">孔位平面图</span
        ><span class="muted">{{ wellRows }} × {{ wellCols }}</span></div
      >
      <div class="plate-legend">
        <span class="legend-item"><span class="legend-dot well-filled"></span>有内容物</span>
        <span class="legend-item"><span class="legend-dot well-alert"></span>告警</span>
        <span class="legend-item"><span class="legend-dot well-empty"></span>空孔</span>
      </div>
      <div v-loading="detailLoading" class="plate-grid" :style="plateGridStyle">
        <div
          v-for="cell in wellCells"
          :key="cell.code"
          class="well-cell"
          :class="wellClass(cell)"
          :title="wellTitle(cell)"
          @click="handleWellClick(cell)"
        >
          <span>{{ cell.code }}</span>
        </div>
      </div>
    </template>
  </ContentWrap>

  <InstanceForm ref="instanceFormRef" @success="handleFormSuccess" />
</template>

<script lang="ts" setup>
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import { SlotApi, SlotVO } from '@/api/wms/space/slot'
import { ZoneApi, ZoneVO } from '@/api/wms/space/zone'
import { formatDate } from '@/utils/formatTime'
import InstanceForm from './InstanceForm.vue'

defineOptions({ name: 'WmsInstance' })
const message = useMessage()
const { t } = useI18n()
const router = useRouter()
const loading = ref(false)
const detailLoading = ref(false)
const list = ref<MaterialInstanceVO[]>([])
const total = ref(0)
const containerTypes = ref<ContainerTypeVO[]>([])
const contentDefs = ref<ContentDefVO[]>([])
const slots = ref<SlotVO[]>([])
const zones = ref<ZoneVO[]>([])
const currentInstance = ref<MaterialInstanceVO>()
const currentChildren = ref<MaterialInstanceVO[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  instanceCode: undefined,
  instanceName: undefined,
  barcode: undefined,
  instanceStatus: undefined,
  rootSlotCode: undefined
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
const CONTENT_META: Record<string, string> = {
  EMPTY: '空容器',
  REAGENT: '试剂',
  STANDARD: '标准品',
  BUFFER: '缓冲液',
  SAMPLE: '样本',
  WASTE: '废液',
  MEDIA: '培养基',
  SOLVENT: '溶剂',
  OTHER: '其他'
}
const contentTypeText = (value?: string) => CONTENT_META[value || ''] || value || '—'
const containerTypeText = (row: MaterialInstanceVO) => {
  const type = containerTypes.value.find(
    (item) => item.id === row.containerTypeId || item.typeCode === row.containerTypeCode
  )
  return type?.typeName || row.containerTypeCode || '—'
}
const contentText = (row: MaterialInstanceVO) => {
  const content = contentDefs.value.find(
    (item) => item.id === row.contentDefId || item.contentCode === row.contentDefCode
  )
  return (
    content?.contentName ||
    (row.contentType === 'EMPTY' ? '空容器' : row.contentDefCode) ||
    contentTypeText(row.contentType)
  )
}
const zonePathMap = computed(() => {
  const byCode = new Map(zones.value.map((item) => [item.zoneCode, item]))
  const result = new Map<string, string>()
  const resolve = (zone?: ZoneVO): string => {
    if (!zone) return ''
    const parent = zone.parentZoneCode ? byCode.get(zone.parentZoneCode) : undefined
    return [parent ? resolve(parent) : '', zone.zoneName || zone.zoneCode]
      .filter(Boolean)
      .join(' → ')
  }
  zones.value.forEach((zone) => zone.zoneCode && result.set(zone.zoneCode, resolve(zone)))
  return result
})
const locationText = (row: MaterialInstanceVO) => {
  if (row.parentPositionCode) return `容器内 → ${row.parentPositionCode}`
  const slot = slots.value.find(
    (item) => item.id === row.rootSlotId || item.slotCode === row.rootSlotCode
  )
  if (!slot) return row.rootSlotCode || '未落位'
  return [zonePathMap.value.get(slot.zoneCode || ''), slot.slotCode].filter(Boolean).join(' → ')
}
const instanceIcon = (row: MaterialInstanceVO) => {
  const type = containerTypes.value.find((item) => item.id === row.containerTypeId)
  return type?.hierarchyRole === 'CARRIER'
    ? 'ep:grid'
    : type?.hierarchyRole === 'WELL'
      ? 'ep:circle-check'
      : 'ep:takeaway-box'
}
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
  currentInstance.value = undefined
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const currentContainerType = computed(() =>
  containerTypes.value.find((item) => item.id === currentInstance.value?.containerTypeId)
)
const wellRows = computed(() => currentContainerType.value?.wellRows || 0)
const wellCols = computed(() => currentContainerType.value?.wellCols || 0)
const isCarrierWithWells = computed(
  () =>
    currentContainerType.value?.hierarchyRole === 'CARRIER' &&
    wellRows.value > 0 &&
    wellCols.value > 0
)
const wellMap = computed(
  () =>
    new Map(
      currentChildren.value
        .filter((item) => item.parentPositionCode)
        .map((item) => [item.parentPositionCode!, item])
    )
)
const wellCells = computed(() => {
  const cells: { code: string; instance?: MaterialInstanceVO }[] = []
  for (let row = 0; row < wellRows.value; row++)
    for (let col = 1; col <= wellCols.value; col++) {
      const code = `${String.fromCharCode(65 + row)}${col}`
      cells.push({ code, instance: wellMap.value.get(code) })
    }
  return cells
})
const plateGridStyle = computed(() => ({ gridTemplateColumns: `repeat(${wellCols.value}, 1fr)` }))
const loadChildren = async (row: MaterialInstanceVO) => {
  currentChildren.value = []
  if (!row.id || !isCarrierWithWells.value) return
  detailLoading.value = true
  try {
    const data = await MaterialInstanceApi.getMaterialInstancePage({
      pageNo: 1,
      pageSize: Math.max(wellRows.value * wellCols.value, 20),
      parentInstanceId: row.id
    })
    currentChildren.value = data.list || []
  } finally {
    detailLoading.value = false
  }
}
const handleInstanceClick = async (row?: MaterialInstanceVO) => {
  if (!row) return
  currentInstance.value = row
  await nextTick()
  await loadChildren(row)
}
const wellClass = (cell: { instance?: MaterialInstanceVO }) =>
  !cell.instance || !cell.instance.contentType || cell.instance.contentType === 'EMPTY'
    ? 'well-empty'
    : ['EXPIRED', 'DISCARDED'].includes(cell.instance.instanceStatus || '')
      ? 'well-alert'
      : 'well-filled'
const wellTitle = (cell: { code: string; instance?: MaterialInstanceVO }) =>
  cell.instance
    ? `${cell.code} · ${cell.instance.instanceName || cell.instance.instanceCode} · ${statusText(cell.instance.instanceStatus)}`
    : `${cell.code} · 空孔`

const instanceFormRef = ref()
const handleAddInstance = () => instanceFormRef.value.open('create')
const handleAddChild = (row: MaterialInstanceVO) =>
  router.push({ name: 'WmsInstanceChildren', params: { parentId: row.id } })
const handleEditInstance = (row: MaterialInstanceVO) => instanceFormRef.value.open('update', row.id)
const handleDeleteInstance = async (row: MaterialInstanceVO) => {
  try {
    await message.delConfirm(`确定删除实例「${row.instanceName || row.instanceCode}」吗？`)
  } catch {
    return
  }
  await MaterialInstanceApi.deleteMaterialInstance(row.id!)
  message.success(t('common.delSuccess'))
  if (currentInstance.value?.id === row.id) currentInstance.value = undefined
  await getList()
}
const handleFormSuccess = async () => {
  await getList()
  if (currentInstance.value) await handleInstanceClick(currentInstance.value)
}
const handleWellClick = (cell: { code: string; instance?: MaterialInstanceVO }) =>
  cell.instance ? handleInstanceClick(cell.instance) : message.info(`${cell.code} 为空孔`)

onMounted(async () => {
  const [, types, contents, slotList, zoneList] = await Promise.all([
    getList(),
    ContainerTypeApi.getContainerTypeList(),
    ContentDefApi.getContentDefList(),
    SlotApi.getSlotList(),
    ZoneApi.getZoneList()
  ])
  containerTypes.value = types || []
  contentDefs.value = contents || []
  slots.value = slotList || []
  zones.value = zoneList || []
})
</script>

<style lang="scss" scoped>
.list-toolbar,
.detail-header,
.detail-heading,
.detail-actions,
.instance-cell,
.plate-title,
.plate-legend,
.legend-item {
  display: flex;
  align-items: center;
}

.list-toolbar,
.detail-header {
  justify-content: space-between;
}

.list-toolbar {
  margin-bottom: 14px;
}

.list-title {
  font-size: 15px;
  font-weight: 600;
}

.muted,
.detail-sub {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.instance-table :deep(.el-table__row) {
  cursor: pointer;
}

.instance-cell,
.detail-heading {
  gap: 10px;
}

.instance-icon,
.detail-icon {
  flex-shrink: 0;
  color: var(--el-color-primary);
}

.detail-icon {
  font-size: 24px;
}

.instance-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.instance-name,
.instance-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.instance-name {
  font-weight: 500;
}

.instance-code {
  margin-top: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.detail-header {
  margin-bottom: 16px;
}

.detail-title {
  font-size: 17px;
  font-weight: 600;
}

.detail-sub {
  margin-top: 3px;
  margin-left: 0;
}

.detail-actions,
.plate-title,
.legend-item {
  gap: 8px;
}

.detail-desc {
  margin-bottom: 20px;
}

.plate-title {
  margin-bottom: 8px;
}

.plate-legend {
  padding: 6px 12px;
  margin-bottom: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  gap: 16px;
}

.legend-item {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.legend-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.plate-grid {
  display: grid;
  max-width: 620px;
  gap: 6px;
}

.well-cell {
  display: flex;
  height: 28px;
  font-size: 11px;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 4px;
  transition: all 0.12s;
  align-items: center;
  justify-content: center;
}

.well-cell:hover {
  transform: scale(1.06);
}

.well-filled {
  color: #085041;
  background: #e1f5ee;
  border-color: #0f6e56;
}

.well-alert {
  color: #791f1f;
  background: #fcebeb;
  border-color: #a32d2d;
}

.well-empty {
  color: var(--el-text-color-placeholder);
  background: #fff;
  border-color: var(--el-border-color);
}
</style>
