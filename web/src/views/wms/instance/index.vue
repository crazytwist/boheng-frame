<template>
  <ContentWrap title="物料实例">
    <div class="instance-layout">
      <!-- 左侧：实例树 -->
      <div class="tree-panel">
        <div class="panel-header">
          <span class="panel-title">实例树</span>
          <el-button v-hasPermi="['wms:material-instance:create']" type="primary" link @click="handleAddInstance">
            <Icon class="mr-5px" icon="ep:plus" />
            新增实例
          </el-button>
        </div>
        <el-tree
          v-loading="treeLoading"
          class="instance-tree"
          :data="instanceTree"
          :props="{ label: 'instanceName', children: 'children' }"
          node-key="id"
          highlight-current
          :expand-on-click-node="false"
          @node-click="handleInstanceClick"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <Icon :icon="instanceIcon(data)" class="tree-node-icon" />
              <span class="tree-node-label">{{ data.instanceName || data.instanceCode }}</span>
              <span class="tree-node-status" :class="statusClass(data.instanceStatus)" />
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 右侧：实例详情 + 孔板可视化 -->
      <div class="detail-panel">
        <template v-if="currentInstance">
          <!-- 详情头 -->
          <div class="detail-header">
            <div>
              <div class="detail-title">{{ currentInstance.instanceName || currentInstance.instanceCode }}</div>
              <div class="detail-sub">
                {{ currentInstance.containerTypeCode || '未绑定容器' }} ·
                <el-tag size="small" :type="statusTagType(currentInstance.instanceStatus)">
                  {{ statusText(currentInstance.instanceStatus) }}
                </el-tag>
              </div>
            </div>
            <div class="detail-actions">
              <el-button v-hasPermi="['wms:material-instance:create']" link type="primary" @click="handleAddChild(currentInstance)">
                <Icon icon="ep:plus" class="mr-5px" />批量新增子实例
              </el-button>
              <el-button v-hasPermi="['wms:material-instance:update']" link type="primary" @click="handleEditInstance(currentInstance)">
                <Icon icon="ep:edit" class="mr-5px" />编辑
              </el-button>
              <el-button v-hasPermi="['wms:material-instance:delete']" link type="danger" @click="handleDeleteInstance(currentInstance)">
                <Icon icon="ep:delete" class="mr-5px" />删除
              </el-button>
            </div>
          </div>

          <!-- 基础信息 -->
          <el-descriptions :column="2" border class="detail-desc">
            <el-descriptions-item label="实例编码">{{ currentInstance.instanceCode }}</el-descriptions-item>
            <el-descriptions-item label="条码">{{ currentInstance.barcode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="内容物类型">{{ contentTypeText(currentInstance.contentType) }}</el-descriptions-item>
            <el-descriptions-item label="当前体积">{{ currentInstance.currentVolUl != null ? currentInstance.currentVolUl + ' μL' : '—' }}</el-descriptions-item>
            <el-descriptions-item label="落位槽位">{{ currentInstance.rootSlotCode || '未落位' }}</el-descriptions-item>
            <el-descriptions-item label="父位置">{{ currentInstance.parentPositionCode || '顶层' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 孔板平面图（CARRIER 且带子实例时展示） -->
          <template v-if="isCarrierWithWells">
            <div class="plate-title">
              <span class="panel-title">孔位平面图</span>
              <span class="panel-sub">{{ wellRows }} × {{ wellCols }}</span>
            </div>
            <div class="plate-legend">
              <span class="legend-item"><span class="legend-dot well-filled" />有内容物</span>
              <span class="legend-item"><span class="legend-dot well-alert" />告警</span>
              <span class="legend-item"><span class="legend-dot well-empty" />空孔</span>
            </div>
            <div class="plate-grid" :style="plateGridStyle">
              <div
                v-for="cell in wellCells"
                :key="cell.code"
                class="well-cell"
                :class="wellClass(cell)"
                :title="wellTitle(cell)"
                @click="cell && handleWellClick(cell)"
              >
                <span class="well-code">{{ cell.code }}</span>
              </div>
            </div>
          </template>

          <!-- 非载体或无可视化 -->
          <el-empty v-else description="该实例无孔位结构" :image-size="80" />
        </template>

        <el-empty v-else description="请在左侧选择实例查看详情" />
      </div>
    </div>
  </ContentWrap>

  <!-- 实例新增/编辑表单 -->
  <InstanceForm ref="instanceFormRef" @success="handleFormSuccess" />
  <!-- 子实例批量新增表单 -->
  <InstanceBatchForm ref="batchFormRef" @success="handleFormSuccess" />
</template>

<script lang="ts" setup>
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import InstanceForm from './InstanceForm.vue'
import InstanceBatchForm from './InstanceBatchForm.vue'

defineOptions({ name: 'WmsInstance' })

const message = useMessage()
const { t } = useI18n()

// ========== 实例树 ==========
const treeLoading = ref(false)
const instanceTree = ref<MaterialInstanceVO[]>([])
const containerTypes = ref<ContainerTypeVO[]>([])

const instanceIcon = (data: MaterialInstanceVO) => {
  const ct = containerTypes.value.find((c) => c.id === data.containerTypeId)
  if (ct?.hierarchyRole === 'CARRIER') return 'ep:grid'
  if (ct?.hierarchyRole === 'WELL') return 'ep:circle-check'
  return 'ep:takeaway-box'
}

const statusText = (s?: string) => {
  const map: Record<string, string> = {
    AVAILABLE: '可用',
    RESERVED: '已预留',
    IN_USE: '使用中',
    USED: '已用完',
    EXPIRED: '已过期',
    DISCARDED: '已废弃'
  }
  return map[s || ''] || s || '—'
}

const statusTagType = (s?: string) => {
  const map: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    AVAILABLE: 'success',
    RESERVED: 'primary',
    IN_USE: 'primary',
    USED: 'info',
    EXPIRED: 'warning',
    DISCARDED: 'danger'
  }
  return map[s || ''] || 'info'
}

const statusClass = (s?: string) => {
  const map: Record<string, string> = {
    AVAILABLE: 'st-available',
    RESERVED: 'st-reserved',
    IN_USE: 'st-inuse',
    USED: 'st-used',
    EXPIRED: 'st-expired',
    DISCARDED: 'st-discarded'
  }
  return map[s || ''] || 'st-available'
}

const contentTypeText = (t?: string) => {
  if (!t) return '—'
  if (t === 'EMPTY') return '空'
  const map: Record<string, string> = {
    REAGENT: '试剂',
    STANDARD: '标准品',
    BUFFER: '缓冲液',
    SAMPLE: '样本',
    WASTE: '废液',
    MEDIA: '培养基',
    SOLVENT: '溶剂',
    OTHER: '其他'
  }
  return map[t] || t
}

/** 前端组树：按 parentInstanceId */
const buildTree = (list: MaterialInstanceVO[]): MaterialInstanceVO[] => {
  const map = new Map<number, MaterialInstanceVO>()
  list.forEach((item) => map.set(item.id!, { ...item, children: [] }))
  const roots: MaterialInstanceVO[] = []
  list.forEach((item) => {
    const node = map.get(item.id!)!
    if (item.parentInstanceId != null && map.has(item.parentInstanceId)) {
      map.get(item.parentInstanceId)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

const loadInstances = async () => {
  treeLoading.value = true
  try {
    const [instances, cts] = await Promise.all([
      MaterialInstanceApi.getMaterialInstanceList(),
      ContainerTypeApi.getContainerTypeList()
    ])
    containerTypes.value = cts || []
    instanceTree.value = buildTree(instances || [])
  } finally {
    treeLoading.value = false
  }
}

// ========== 详情 + 孔板 ==========
const currentInstance = ref<MaterialInstanceVO>()

const currentContainerType = computed(() =>
  containerTypes.value.find((c) => c.id === currentInstance.value?.containerTypeId)
)

const wellRows = computed(() => currentContainerType.value?.wellRows || 0)
const wellCols = computed(() => currentContainerType.value?.wellCols || 0)

const isCarrierWithWells = computed(() =>
  currentContainerType.value?.hierarchyRole === 'CARRIER' && wellRows.value > 0 && wellCols.value > 0
)

/** 孔的实例（当前实例的直接子节点） */
const wellChildren = computed(() => currentInstance.value?.children || [])

/** 位置码 → 子实例映射 */
const wellMap = computed(() => {
  const map = new Map<string, MaterialInstanceVO>()
  wellChildren.value.forEach((w) => {
    if (w.parentPositionCode) map.set(w.parentPositionCode, w)
  })
  return map
})

/** 生成孔位单元格（按 rows×cols，行字母 A/B/C… × 列号 1/2/3…） */
const wellCells = computed(() => {
  const cells: { code: string; instance?: MaterialInstanceVO }[] = []
  const rows = wellRows.value
  const cols = wellCols.value
  if (rows === 0 || cols === 0) return cells
  for (let r = 0; r < rows; r++) {
    const rowName = String.fromCharCode(65 + r)
    for (let c = 0; c < cols; c++) {
      const code = `${rowName}${c + 1}`
      cells.push({ code, instance: wellMap.value.get(code) })
    }
  }
  return cells
})

const plateGridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${wellCols.value}, 1fr)`
}))

const wellClass = (cell: { instance?: MaterialInstanceVO }) => {
  const inst = cell.instance
  if (!inst) return 'well-empty'
  if (inst.instanceStatus === 'EXPIRED' || inst.instanceStatus === 'DISCARDED') return 'well-alert'
  if (inst.contentType === 'EMPTY' || inst.contentType == null) return 'well-empty'
  return 'well-filled'
}

const wellTitle = (cell: { code: string; instance?: MaterialInstanceVO }) => {
  const inst = cell.instance
  if (!inst) return `${cell.code} · 空孔`
  return `${cell.code} · ${inst.instanceName || inst.instanceCode} · ${statusText(inst.instanceStatus)}`
}

// ========== 交互 ==========
const instanceFormRef = ref()
const batchFormRef = ref()

const handleInstanceClick = (data: MaterialInstanceVO) => {
  currentInstance.value = data
}

const handleAddInstance = () => {
  instanceFormRef.value.open('create')
}

const handleAddChild = (data: MaterialInstanceVO) => {
  batchFormRef.value.open(data)
}

const handleEditInstance = (data: MaterialInstanceVO) => {
  instanceFormRef.value.open('update', data.id)
}

const handleDeleteInstance = async (data: MaterialInstanceVO) => {
  try {
    await message.delConfirm(`确定删除实例「${data.instanceName || data.instanceCode}」吗？`)
  } catch {
    return
  }
  await MaterialInstanceApi.deleteMaterialInstance(data.id!)
  message.success(t('common.delSuccess'))
  currentInstance.value = undefined
  loadInstances()
}

const handleFormSuccess = () => {
  loadInstances()
}

const handleWellClick = (cell: { code: string; instance?: MaterialInstanceVO }) => {
  if (!cell.instance) {
    message.info(`${cell.code} 为空孔`)
    return
  }
  currentInstance.value = cell.instance
}

onMounted(() => {
  loadInstances()
})
</script>

<style lang="scss" scoped>
.instance-layout {
  display: flex;
  gap: 16px;
  min-height: 560px;
}

.tree-panel {
  width: 300px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}

.detail-panel {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title {
  font-size: 14px;
  font-weight: 500;
}

.panel-sub {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.instance-tree {
  .tree-node {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    overflow: hidden;
  }
  .tree-node-icon {
    color: var(--el-color-primary);
  }
  .tree-node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .tree-node-status {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }
}

/* 实例状态圆点配色 */
.st-available { background: #1d9e75; }
.st-reserved { background: #378add; }
.st-inuse { background: #185fa5; }
.st-used { background: #888780; }
.st-expired { background: #ba7517; }
.st-discarded { background: #a32d2d; }

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-actions {
  flex-shrink: 0;
}

.detail-title {
  font-size: 16px;
  font-weight: 500;
}

.detail-sub {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.detail-desc {
  margin-bottom: 20px;
}

.plate-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.plate-legend {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  padding: 6px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.legend-dot {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;
}

.plate-grid {
  display: grid;
  gap: 6px;
  max-width: 480px;
}

.well-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 26px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.12s;
}

.well-cell:hover {
  transform: scale(1.08);
}

.well-code {
  font-size: 11px;
}

.well-filled {
  background: #e1f5ee;
  border-color: #0f6e56;
  color: #085041;
}

.well-alert {
  background: #fcebeb;
  border-color: #a32d2d;
  color: #791f1f;
}

.well-empty {
  background: #fff;
  border-color: var(--el-border-color);
  color: var(--el-text-color-placeholder);
}
</style>
