<template>
  <ContentWrap title="空间管理">
    <!-- 区域树 + 槽位网格 双栏布局 -->
    <div class="space-layout">
      <!-- 左侧：区域树 -->
      <div class="zone-panel">
        <div class="panel-header">
          <span class="panel-title">区域树</span>
          <el-button
            v-hasPermi="['wms:zone:create']"
            type="primary"
            link
            @click="handleAddRootZone"
          >
            <Icon class="mr-5px" icon="ep:plus" />
            新增区域
          </el-button>
        </div>
        <el-tree
          v-loading="treeLoading"
          class="zone-tree"
          :data="zoneTree"
          :props="{ label: 'zoneName', children: 'children' }"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleZoneClick"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <Icon :icon="zoneTypeIcon(data.zoneType)" class="tree-node-icon" />
              <span class="tree-node-label">{{ data.zoneName }}</span>
              <el-tag v-if="data.tempMin != null || data.tempMax != null" size="small" type="info" class="tree-node-tag">
                {{ formatTemp(data) }}
              </el-tag>
              <span class="tree-node-actions" @click.stop>
                <el-button v-hasPermi="['wms:zone:create']" link type="primary" @click="handleAddChildZone(data)">
                  <Icon icon="ep:plus" />
                </el-button>
                <el-button v-hasPermi="['wms:zone:update']" link type="primary" @click="handleEditZone(data)">
                  <Icon icon="ep:edit" />
                </el-button>
                <el-button v-hasPermi="['wms:zone:delete']" link type="danger" @click="handleDeleteZone(data)">
                  <Icon icon="ep:delete" />
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 右侧：布局 / 槽位 分层视图 -->
      <div class="slot-panel">
        <template v-if="currentZone">
          <div class="panel-header">
            <span class="panel-title">{{ currentZone.zoneName }}</span>
            <el-tag v-if="currentZone.zoneType" size="small" type="info">{{ zoneTypeLabel(currentZone.zoneType) }}</el-tag>
          </div>

          <!-- 区域概要 -->
          <div class="zone-meta">
            <span v-if="isRackZone">货架布局 {{ rackRows }} 行 × {{ rackCols }} 列</span>
            <span v-else>非货架区域</span>
            <span v-if="formatTemp(currentZone)" class="zone-meta-item">{{ formatTemp(currentZone) }}</span>
            <span v-if="currentZone.biosafetyLevel" class="zone-meta-item">BSL-{{ currentZone.biosafetyLevel }}</span>
          </div>

          <!-- 货架区域：布局 / 槽位 双层切换 -->
          <el-tabs v-if="isRackZone" v-model="activeTab" class="rack-tabs">
            <!-- 第一层：布局（空网格骨架，回答「货架长什么样」） -->
            <el-tab-pane label="布局" name="layout">
              <div class="layout-grid" :style="gridStyle">
                <div v-for="cell in layoutCells" :key="cell.key" class="layout-cell">
                  <span class="layout-cell-coord">{{ cell.coord }}</span>
                </div>
              </div>
              <div class="layout-hint">
                <p>上方为货架布局骨架（{{ rackRows }}×{{ rackCols }} 共 {{ layoutCells.length }} 个标准位）。</p>
                <div class="layout-actions">
                  <el-button
                    v-hasPermi="['wms:slot:create']"
                    type="primary"
                    :loading="generating"
                    @click="handleGenerateSlots"
                  >
                    <Icon class="mr-5px" icon="ep:magic-stick" />
                    按布局生成槽位
                  </el-button>
                  <span class="layout-action-hint">一键把骨架落成 {{ layoutCells.length }} 个真实槽位</span>
                </div>
              </div>
            </el-tab-pane>

            <!-- 第二层：槽位（实际槽位映射进网格，颜色反映状态） -->
            <el-tab-pane label="槽位" name="slot">
              <div class="legend-bar">
                <span class="legend-item"><span class="legend-dot is-free" />空闲</span>
                <span class="legend-item"><span class="legend-dot is-occupied" />占用</span>
                <span class="legend-item"><span class="legend-dot is-locked" />锁定</span>
                <span class="legend-item"><span class="legend-dot is-checking" />盘点</span>
                <span class="legend-item"><span class="legend-dot is-disabled" />停用</span>
              </div>
              <div v-loading="slotLoading" class="slot-grid" :style="gridStyle">
                <div
                  v-for="cell in slotCells"
                  :key="cell.key"
                  class="slot-cell"
                  :class="cell.slot ? slotStatusClass(cell.slot) : 'is-empty'"
                  :title="cell.slot ? `${cell.slot.slotCode} · ${slotStatusText(cell.slot)}` : `${cell.coord} · 未建槽位`"
                  @click="cell.slot && handleSlotClick(cell.slot)"
                >
                  <span class="slot-code">{{ cell.coord }}</span>
                </div>
              </div>
              <div class="slot-stat">
                已建槽位 {{ slotCells.filter((c) => c.slot).length }} / {{ layoutCells.length }}，
                空闲 {{ freeCount }}，占用 {{ occupiedCount }}
              </div>
            </el-tab-pane>
          </el-tabs>

          <!-- 非货架区域：槽位列表 -->
          <template v-else>
            <div class="legend-bar">
              <span class="legend-item"><span class="legend-dot is-free" />空闲</span>
              <span class="legend-item"><span class="legend-dot is-occupied" />占用</span>
              <span class="legend-item"><span class="legend-dot is-locked" />锁定</span>
              <span class="legend-item"><span class="legend-dot is-checking" />盘点</span>
              <span class="legend-item"><span class="legend-dot is-disabled" />停用</span>
            </div>
            <div v-loading="slotLoading" class="slot-list">
              <el-empty v-if="!slots.length" description="该区域下暂无槽位" />
              <div v-for="slot in slots" :key="slot.id" class="slot-row" :class="slotStatusClass(slot)" @click="handleSlotClick(slot)">
                <span class="slot-row-code">{{ slot.slotCode }}</span>
                <el-tag size="small" :type="slotStatusTagType(slot.slotStatus)">{{ slotStatusText(slot) }}</el-tag>
              </div>
            </div>
          </template>
        </template>

        <!-- 未选中区域 -->
        <el-empty v-else description="请在左侧选择区域查看布局与槽位" />
      </div>
    </div>
  </ContentWrap>

  <!-- 区域新增/编辑表单 -->
  <ZoneForm ref="zoneFormRef" @success="loadZoneTree" />

  <!-- 槽位详情抽屉 -->
  <el-drawer v-model="slotDrawerVisible" :title="currentSlot?.slotCode || '槽位详情'" size="460px">
    <template v-if="currentSlot">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="槽位编码">{{ currentSlot.slotCode }}</el-descriptions-item>
        <el-descriptions-item label="槽位类型">{{ currentSlot.slotType }}</el-descriptions-item>
        <el-descriptions-item label="使用状态">
          <el-tag :type="slotStatusTagType(currentSlot.slotStatus)">{{ slotStatusText(currentSlot) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="容量">{{ currentSlot.capacity }} 标准位</el-descriptions-item>
        <el-descriptions-item label="已占用量">{{ currentSlot.occupiedQty ?? 0 }}</el-descriptions-item>
        <el-descriptions-item label="占用时间">{{ formatDateTime(currentSlot.occupiedTime) }}</el-descriptions-item>
        <el-descriptions-item label="设备编码">{{ currentSlot.deviceCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="设备位置">{{ currentSlot.devicePositionNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ currentSlot.description || '—' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 当前占用者：真相源是 wms_material_instance.root_slot_id，按它反查 -->
      <div class="drawer-section">
        <div class="drawer-section-title">当前占用</div>
        <div v-if="slotOccupant" class="occupant-card">
          <div class="occupant-head">
            <span class="occupant-code">{{ slotOccupant.instanceCode }}</span>
            <el-tag size="small" :type="instanceStatusTag(slotOccupant.instanceStatus)">
              {{ instanceStatusLabel(slotOccupant.instanceStatus) }}
            </el-tag>
          </div>
          <div class="occupant-sub">
            {{ slotOccupant.instanceName || '—' }} · {{ slotOccupant.containerTypeCode }}
          </div>
          <div v-if="slotOccupant.contentDefCode" class="occupant-sub">
            内容物 {{ slotOccupant.contentDefCode }}
          </div>
        </div>
        <el-empty v-else :image-size="56" description="该槽位空闲" />
      </div>

      <!-- 落位操作 -->
      <div class="drawer-section">
        <div class="drawer-section-title">操作</div>
        <div class="op-buttons">
          <el-button
            v-if="!slotOccupant"
            v-hasPermi="['wms:material-instance:put-in']"
            type="primary"
            :disabled="currentSlot.status === 1"
            :loading="opLoading"
            @click="openPutIn"
          >
            <Icon class="mr-5px" icon="ep:upload" />
            上架
          </el-button>
          <template v-if="slotOccupant">
            <el-button
              v-hasPermi="['wms:material-instance:take-out']"
              type="warning"
              :loading="opLoading"
              @click="handleTakeOut"
            >
              <Icon class="mr-5px" icon="ep:download" />
              下架
            </el-button>
            <el-button
              v-hasPermi="['wms:material-instance:transfer']"
              :loading="opLoading"
              @click="openTransfer"
            >
              <Icon class="mr-5px" icon="ep:rank" />
              转移
            </el-button>
          </template>
        </div>
        <div v-if="currentSlot.status === 1" class="op-hint">槽位已停用，不可上架</div>
      </div>

      <!-- 本槽位最近流水 -->
      <div class="drawer-section">
        <div class="drawer-section-title">本槽位最近流水</div>
        <el-timeline v-if="slotMovements.length" class="movement-timeline">
          <el-timeline-item
            v-for="m in slotMovements"
            :key="m.id"
            :timestamp="formatDateTime(m.operateTime)"
            :type="movementTag(m.movementType)"
            size="normal"
          >
            <div class="movement-row">
              <el-tag :type="movementTag(m.movementType)" size="small">
                {{ m.movementTypeName || m.movementType }}
              </el-tag>
              <span class="movement-loc">
                {{ m.fromSlotCode || '—' }} → {{ m.toSlotCode || '—' }}
              </span>
            </div>
            <div class="movement-sub">{{ m.instanceCode }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else :image-size="56" description="暂无流水" />
      </div>
    </template>
  </el-drawer>

  <!-- 上架对话框：从「未落位的顶层实例」里挑一个放到当前槽位 -->
  <el-dialog v-model="putInVisible" title="上架" width="520px">
    <el-form label-width="90px">
      <el-form-item label="目标槽位">
        <el-input :model-value="currentSlot?.slotCode" disabled />
      </el-form-item>
      <el-form-item label="选择实例">
        <el-select
          v-model="putInForm.instanceId"
          filterable
          placeholder="请选择未落位的顶层实例"
          class="w-full"
          :loading="putInLoading"
        >
          <el-option
            v-for="i in unplacedList"
            :key="i.id"
            :label="`${i.instanceCode}  ${i.instanceName || ''}`"
            :value="i.id!"
          >
            <span>{{ i.instanceCode }}</span>
            <span class="option-sub">{{ i.containerTypeCode }} · {{ i.instanceName || '未命名' }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="putInForm.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <el-empty v-if="!putInLoading && !unplacedList.length" :image-size="60" description="没有未落位的顶层实例" />
    <template #footer>
      <el-button @click="putInVisible = false">取消</el-button>
      <el-button type="primary" :loading="opLoading" :disabled="!putInForm.instanceId" @click="submitPutIn">
        确定上架
      </el-button>
    </template>
  </el-dialog>

  <!-- 转移对话框：选一个空闲槽位作为目标 -->
  <el-dialog v-model="transferVisible" title="转移" width="520px">
    <el-form label-width="90px">
      <el-form-item label="当前槽位">
        <el-input :model-value="currentSlot?.slotCode" disabled />
      </el-form-item>
      <el-form-item label="实例">
        <el-input :model-value="slotOccupant?.instanceCode" disabled />
      </el-form-item>
      <el-form-item label="目标槽位">
        <el-select
          v-model="transferForm.targetSlotId"
          filterable
          placeholder="请选择空闲槽位"
          class="w-full"
          :loading="allSlotLoading"
        >
          <el-option
            v-for="s in transferTargets"
            :key="s.id"
            :label="s.slotCode!"
            :value="s.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="transferForm.remark" placeholder="选填" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="transferVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="opLoading"
        :disabled="!transferForm.targetSlotId"
        @click="submitTransfer"
      >
        确定转移
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ZoneApi, ZoneVO } from '@/api/wms/space/zone'
import { SlotApi, SlotVO } from '@/api/wms/space/slot'
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { MaterialMovementApi, MaterialMovementVO, MOVEMENT_TYPE_TAG } from '@/api/wms/movement'
import { formatDate } from '@/utils/formatTime'
import ZoneForm from './ZoneForm.vue'

defineOptions({ name: 'WmsSpace' })

const message = useMessage() // 消息弹窗
const { t } = useI18n() // 国际化

// ========== 区域树 ==========
const treeLoading = ref(false)
const zoneTree = ref<ZoneVO[]>([])

/** 区域类型 → 图标映射 */
const zoneTypeIcon = (type?: string) => {
  const map: Record<string, string> = {
    LAB: 'ep:office-building',
    ROOM: 'ep:house',
    FUNCTION: 'ep:grid',
    TEMP: 'ep:cold-drink',
    SAFETY: 'ep:warning',
    RACK: 'ep:menu',
    BENCH: 'ep:platform',
    DEVICE: 'ep:cpu',
    CUSTOM: 'ep:star'
  }
  return map[type || ''] || 'ep:folder'
}

/** 区域类型 → 中文标签 */
const zoneTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    LAB: '实验室',
    ROOM: '房间',
    FUNCTION: '功能区',
    TEMP: '温控区',
    SAFETY: '安全区',
    RACK: '料架',
    BENCH: '台面',
    DEVICE: '设备工位',
    CUSTOM: '自定义'
  }
  return map[type || ''] || type || ''
}

const formatTemp = (data: ZoneVO) => {
  if (data.tempMin != null && data.tempMax != null) return `${data.tempMin}~${data.tempMax}℃`
  if (data.tempMin != null) return `≥${data.tempMin}℃`
  if (data.tempMax != null) return `≤${data.tempMax}℃`
  return ''
}

/** 加载区域树 */
const loadZoneTree = async () => {
  treeLoading.value = true
  try {
    const list = await ZoneApi.getZoneList()
    zoneTree.value = buildTree(list || [])
  } finally {
    treeLoading.value = false
  }
}

/** 平铺列表 → 树（后端返回平铺 list，children 需前端组装） */
const buildTree = (list: ZoneVO[]): ZoneVO[] => {
  const map = new Map<string, ZoneVO>()
  list.forEach((node) => map.set(node.zoneCode!, { ...node, children: [] }))
  const roots: ZoneVO[] = []
  map.forEach((node) => {
    if (node.parentZoneCode && map.has(node.parentZoneCode)) {
      map.get(node.parentZoneCode)!.children!.push(node)
    } else {
      roots.push(node)
    }
  })
  return roots
}

// ========== 槽位网格 ==========
const currentZone = ref<ZoneVO>()
const slotLoading = ref(false)
const slots = ref<SlotVO[]>([])
const activeTab = ref('layout') // 货架区域默认停在「布局」层

/** 解析区域 ext_data 的货架尺寸 */
const rackExtData = computed(() => {
  try {
    return currentZone.value?.extData ? JSON.parse(currentZone.value.extData) : {}
  } catch {
    return {}
  }
})
const rackRows = computed(() => Number(rackExtData.value.rackRows || 0))
const rackCols = computed(() => Number(rackExtData.value.rackCols || 0))
const isRackZone = computed(() => currentZone.value?.zoneType === 'RACK' && rackRows.value > 0 && rackCols.value > 0)

/** 网格样式 */
const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${rackCols.value}, 1fr)`,
  gridTemplateRows: `repeat(${rackRows.value}, 1fr)`
}))

/** 布局单元格（空骨架：按 rackRows×rackCols 生成，坐标 R{row}C{col}） */
const layoutCells = computed(() => {
  const cells: { key: string; row: number; col: number; coord: string }[] = []
  for (let r = 1; r <= rackRows.value; r++) {
    for (let c = 1; c <= rackCols.value; c++) {
      cells.push({ key: `${r}-${c}`, row: r, col: c, coord: `R${r}C${c}` })
    }
  }
  return cells
})

/** 解析槽位坐标（row/col 从 ext_data 读） */
const slotCoord = (slot: SlotVO): { row: number; col: number } | null => {
  try {
    const ext = slot.extData ? JSON.parse(slot.extData) : {}
    if (ext.rowNo != null && ext.colNo != null) {
      return { row: Number(ext.rowNo), col: Number(ext.colNo) }
    }
  } catch {
    // ignore
  }
  return null
}

/** 槽位映射进布局网格（有 slot 则填状态，无则标记未建） */
const slotCells = computed(() => {
  // 按坐标建索引
  const coordMap = new Map<string, SlotVO>()
  slots.value.forEach((slot) => {
    const coord = slotCoord(slot)
    if (coord) coordMap.set(`${coord.row}-${coord.col}`, slot)
  })
  return layoutCells.value.map((cell) => ({
    ...cell,
    slot: coordMap.get(cell.key) || null
  }))
})

/** 统计：空闲 / 占用 */
const freeCount = computed(() => slots.value.filter((s) => s.status !== 1 && s.slotStatus === 'FREE').length)
const occupiedCount = computed(() => slots.value.filter((s) => s.slotStatus === 'OCCUPIED').length)

/** 槽位状态类名 */
const slotStatusClass = (slot: SlotVO) => {
  if (slot.status === 1) return 'is-disabled'
  const map: Record<string, string> = {
    FREE: 'is-free',
    OCCUPIED: 'is-occupied',
    LOCKED: 'is-locked',
    CHECKING: 'is-checking'
  }
  return map[slot.slotStatus || 'FREE'] || 'is-free'
}

/** 槽位状态文案 */
const slotStatusText = (slot: SlotVO) => {
  if (slot.status === 1) return '停用'
  const map: Record<string, string> = {
    FREE: '空闲',
    OCCUPIED: '占用',
    LOCKED: '锁定',
    CHECKING: '盘点'
  }
  return map[slot.slotStatus || 'FREE'] || '空闲'
}

/** 槽位状态 tag 类型 */
const slotStatusTagType = (status?: string) => {
  const map: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    FREE: 'info',
    OCCUPIED: 'success',
    LOCKED: 'warning',
    CHECKING: 'danger'
  }
  return map[status || 'FREE'] || 'info'
}

/** 加载槽位列表 */
const loadSlots = async (zoneCode: string) => {
  slotLoading.value = true
  try {
    slots.value = await SlotApi.getSlotList({ zoneCode })
  } finally {
    slotLoading.value = false
  }
}

// ========== 交互 ==========
const handleZoneClick = (data: ZoneVO) => {
  currentZone.value = data
  activeTab.value = 'layout' // 切换区域时回到「布局」层
  if (data.zoneCode) loadSlots(data.zoneCode)
}

const zoneFormRef = ref()

const handleAddRootZone = () => {
  zoneFormRef.value.open('create')
}

const handleAddChildZone = (data: ZoneVO) => {
  zoneFormRef.value.open('create', undefined, data.zoneCode)
}

const handleEditZone = (data: ZoneVO) => {
  zoneFormRef.value.open('update', data.id)
}

const handleDeleteZone = async (data: ZoneVO) => {
  try {
    await message.delConfirm(`确定删除区域「${data.zoneName}」吗？`)
  } catch {
    return
  }
  try {
    await ZoneApi.deleteZone(data.id!)
    message.success(t('common.delSuccess'))
    loadZoneTree()
  } catch {
    // 错误已由拦截器提示
  }
}

const slotDrawerVisible = ref(false)
const currentSlot = ref<SlotVO>()
/** 当前槽位的占用者（真相源：wms_material_instance.root_slot_id） */
const slotOccupant = ref<MaterialInstanceVO>()
/** 当前槽位的最近流水 */
const slotMovements = ref<MaterialMovementVO[]>([])
const opLoading = ref(false)

const formatDateTime = (v?: Date | string) =>
  v ? formatDate(v as any, 'YYYY-MM-DD HH:mm:ss') || '—' : '—'

const movementTag = (v?: string) => MOVEMENT_TYPE_TAG[v || ''] || 'info'

const instanceStatusLabel = (v?: string) => {
  const map: Record<string, string> = {
    AVAILABLE: '可用',
    RESERVED: '已预留',
    IN_USE: '使用中',
    USED: '已用完',
    EXPIRED: '已过期',
    DISCARDED: '已废弃'
  }
  return v ? map[v] || v : '—'
}
const instanceStatusTag = (
  v?: string
): 'success' | 'primary' | 'warning' | 'danger' | 'info' => {
  const map: Record<string, 'success' | 'primary' | 'warning' | 'danger' | 'info'> = {
    AVAILABLE: 'success',
    RESERVED: 'warning',
    IN_USE: 'primary',
    USED: 'info',
    EXPIRED: 'danger',
    DISCARDED: 'info'
  }
  return map[v || ''] || 'info'
}

/** 拉取该槽位的占用者与最近流水 */
const loadSlotDetail = async (slot: SlotVO) => {
  if (!slot.id) return
  slotOccupant.value = undefined
  slotMovements.value = []
  const [page, movements] = await Promise.all([
    MaterialInstanceApi.getMaterialInstancePage({
      pageNo: 1,
      pageSize: 1,
      rootSlotId: slot.id
    }),
    MaterialMovementApi.getListBySlot(slot.id, 10)
  ])
  slotOccupant.value = page?.list?.[0] || undefined
  slotMovements.value = movements || []
}

/** 操作完成后：刷新槽位网格 + 抽屉内容 */
const refreshAfterOperation = async () => {
  if (currentZone.value?.zoneCode) {
    await loadSlots(currentZone.value.zoneCode)
    // 用最新数据回填抽屉里的槽位对象，保持状态标签同步
    const fresh = slots.value.find((s) => s.id === currentSlot.value?.id)
    if (fresh) currentSlot.value = fresh
    await loadSlotDetail(currentSlot.value!)
  }
}

const handleSlotClick = async (slot: SlotVO) => {
  currentSlot.value = slot
  slotDrawerVisible.value = true
  await loadSlotDetail(slot)
}

// ========== 上架 ==========
const putInVisible = ref(false)
const putInLoading = ref(false)
const unplacedList = ref<MaterialInstanceVO[]>([])
const putInForm = reactive<{ instanceId?: number; remark?: string }>({
  instanceId: undefined,
  remark: undefined
})

const openPutIn = async () => {
  putInForm.instanceId = undefined
  putInForm.remark = undefined
  putInVisible.value = true
  putInLoading.value = true
  try {
    unplacedList.value = (await MaterialInstanceApi.getUnplacedList()) || []
  } finally {
    putInLoading.value = false
  }
}

const submitPutIn = async () => {
  if (!putInForm.instanceId || !currentSlot.value?.id) return
  opLoading.value = true
  try {
    await MaterialInstanceApi.putIn({
      instanceId: putInForm.instanceId,
      slotId: currentSlot.value.id,
      remark: putInForm.remark
    })
    message.success('上架成功')
    putInVisible.value = false
    await refreshAfterOperation()
  } finally {
    opLoading.value = false
  }
}

// ========== 下架 ==========
const handleTakeOut = async () => {
  const occupant = slotOccupant.value
  if (!occupant?.id) return
  try {
    await message.delConfirm(`确定把「${occupant.instanceCode}」从该槽位下架吗？`)
  } catch {
    return
  }
  opLoading.value = true
  try {
    await MaterialInstanceApi.takeOut({
      instanceId: occupant.id,
      remark: '从槽位详情下架'
    })
    message.success('下架成功')
    await refreshAfterOperation()
  } finally {
    opLoading.value = false
  }
}

// ========== 转移 ==========
const transferVisible = ref(false)
const allSlotLoading = ref(false)
const allSlots = ref<SlotVO[]>([])
const transferForm = reactive<{ targetSlotId?: number; remark?: string }>({
  targetSlotId: undefined,
  remark: undefined
})

/** 可转移到的目标：未停用且空闲，且不是当前槽位 */
const transferTargets = computed(() =>
  allSlots.value.filter(
    (s) =>
      s.id !== currentSlot.value?.id &&
      s.status !== 1 &&
      s.slotStatus === 'FREE' &&
      s.slotType !== 'DEVICE'
  )
)

const openTransfer = async () => {
  transferForm.targetSlotId = undefined
  transferForm.remark = undefined
  transferVisible.value = true
  allSlotLoading.value = true
  try {
    allSlots.value = (await SlotApi.getSlotList()) || []
  } finally {
    allSlotLoading.value = false
  }
}

const submitTransfer = async () => {
  const occupant = slotOccupant.value
  if (!occupant?.id || !transferForm.targetSlotId) return
  opLoading.value = true
  try {
    await MaterialInstanceApi.transfer({
      instanceId: occupant.id,
      targetSlotId: transferForm.targetSlotId,
      remark: transferForm.remark
    })
    message.success('转移成功')
    transferVisible.value = false
    await refreshAfterOperation()
  } finally {
    opLoading.value = false
  }
}

// ========== 按布局批量生成槽位 ==========
const generating = ref(false)

const handleGenerateSlots = async () => {
  if (!currentZone.value?.zoneCode) return
  try {
    await message.delConfirm(
      `将按当前布局生成 ${layoutCells.value.length} 个槽位，确定继续吗？`
    )
  } catch {
    return
  }
  generating.value = true
  try {
    const count = await SlotApi.generateByZone(currentZone.value.zoneCode)
    message.success(`已生成 ${count} 个槽位`)
    // 切到「槽位」层并刷新
    activeTab.value = 'slot'
    await loadSlots(currentZone.value.zoneCode)
  } finally {
    generating.value = false
  }
}

onMounted(() => {
  loadZoneTree()
})
</script>

<style lang="scss" scoped>
.space-layout {
  display: flex;
  gap: 16px;
  min-height: 560px;
}

.zone-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
}

.slot-panel {
  flex: 1;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 12px;
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

.zone-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.zone-meta-item {
  color: var(--el-text-color-regular);
}

.rack-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.zone-tree {
  .tree-node {
    display: flex;
    align-items: center;
    flex: 1;
    gap: 4px;
    overflow: hidden;
  }
  .tree-node-icon {
    margin-right: 4px;
    color: var(--el-color-primary);
  }
  .tree-node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .tree-node-tag {
    flex-shrink: 0;
  }
  .tree-node-actions {
    flex-shrink: 0;
  }
}

.legend-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  padding: 8px 12px;
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

.slot-grid {
  display: grid;
  gap: 8px;
}

/* 布局层：空骨架单元格 */
.layout-grid {
  display: grid;
  gap: 8px;
}

.layout-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  border-radius: 6px;
  border: 1px dashed var(--el-border-color);
  background: var(--el-fill-color-lighter);
}

.layout-cell-coord {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.layout-hint {
  margin-top: 12px;
  padding: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.layout-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.layout-action-hint {
  color: var(--el-text-color-secondary);
}

.slot-stat {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.slot-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}

.slot-cell:hover {
  transform: scale(1.04);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

/* 未建槽位的格子 */
.is-empty {
  background: var(--el-fill-color-lighter);
  border-color: var(--el-border-color-lighter);
  color: var(--el-text-color-placeholder);
  cursor: default;
}

.slot-code {
  font-size: 12px;
}

.slot-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.slot-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 6px;
  cursor: pointer;
}

.slot-row-code {
  font-size: 13px;
}

/* 状态配色：空闲白、占用绿、锁定琥珀、盘点红、停用灰 */
.is-free {
  background: #fff;
  border-color: var(--el-border-color);
  color: var(--el-text-color-regular);
}
.is-occupied {
  background: #e1f5ee;
  border-color: #0f6e56;
  color: #085041;
}
.is-locked {
  background: #faeeda;
  border-color: #854f0b;
  color: #633806;
}
.is-checking {
  background: #fcebeb;
  border-color: #a32d2d;
  color: #791f1f;
}
.is-disabled {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
  color: var(--el-text-color-placeholder);
}

/* ===== 槽位详情抽屉：占用者 / 操作 / 流水 ===== */
.drawer-section {
  margin-top: 16px;
}

.drawer-section-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.occupant-card {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.occupant-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.occupant-code {
  font-size: 13px;
  font-weight: 500;
}

.occupant-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.op-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.op-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.movement-timeline {
  padding-left: 2px;
}

.movement-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.movement-loc {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.movement-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.option-sub {
  float: right;
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
