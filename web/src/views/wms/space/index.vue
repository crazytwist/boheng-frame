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
            <!-- 第一层：槽位（实际槽位映射进网格，颜色反映状态） -->
            <el-tab-pane label="槽位" name="slot">
              <div class="legend-bar">
                <span class="legend-item"><span class="legend-dot is-free" />空闲</span>
                <span class="legend-item"><span class="legend-dot is-occupied" />占用</span>
                <span class="legend-item"><span class="legend-dot is-locked" />锁定</span>
                <span class="legend-item"><span class="legend-dot is-checking" />盘点</span>
                <span class="legend-item"><span class="legend-dot is-disabled" />停用</span>
              </div>
              <div class="batch-bar">
                <el-button
                  v-hasPermi="['wms:material-instance:put-in']"
                  type="primary"
                  :disabled="selectedSlotIds.length === 0"
                  @click="openBatchPutIn"
                >
                  <Icon class="mr-5px" icon="ep:upload" />
                  批量上架{{ selectedSlotIds.length ? `（${selectedSlotIds.length}）` : '' }}
                </el-button>
                <el-button
                  v-hasPermi="['wms:material-instance:take-out']"
                  type="warning"
                  :disabled="selectedSlotIds.length === 0"
                  @click="openBatchTakeOut"
                >
                  <Icon class="mr-5px" icon="ep:download" />
                  批量下架{{ selectedSlotIds.length ? `（${selectedSlotIds.length}）` : '' }}
                </el-button>
                <el-button
                  v-hasPermi="['wms:slot:update']"
                  :disabled="selectedSlotIds.length === 0"
                  @click="openBatchMaterialEdit"
                >
                  <Icon class="mr-5px" icon="ep:goods" />
                  设置适用物料
                </el-button>
                <el-button :disabled="selectableSlots.length === 0" @click="selectAllSlots">全选</el-button>
                <el-button :disabled="selectedSlotIds.length === 0" @click="clearSlotSelection">全不选</el-button>
                <span class="batch-hint">勾选槽位后可批量操作：上架铺货（仅空闲）/ 下架删除实例 / 设置该位置适合放什么物料</span>
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
                  <div
                    v-if="cell.slot && isSlotSelectable(cell.slot)"
                    class="slot-check"
                    :title="selectedSlotIds.includes(cell.slot.id!) ? '取消选择' : '选择'"
                    @click.stop="toggleSelectSlot(cell.slot.id!)"
                  >
                    <el-checkbox :model-value="selectedSlotIds.includes(cell.slot.id!)" />
                  </div>
                  <span class="slot-code">{{ cell.coord }}</span>
                </div>
              </div>
              <div class="slot-stat">
                已建槽位 {{ slotCells.filter((c) => c.slot).length }} / {{ layoutCells.length }}，
                空闲 {{ freeCount }}，占用 {{ occupiedCount }}
              </div>
            </el-tab-pane>

            <!-- 第二层：布局（空网格骨架，回答「货架长什么样」） -->
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
            <div class="batch-bar">
              <el-button
                v-hasPermi="['wms:material-instance:put-in']"
                type="primary"
                :disabled="selectedSlotIds.length === 0"
                @click="openBatchPutIn"
              >
                <Icon class="mr-5px" icon="ep:upload" />
                批量上架{{ selectedSlotIds.length ? `（${selectedSlotIds.length}）` : '' }}
              </el-button>
              <el-button
                v-hasPermi="['wms:material-instance:take-out']"
                type="warning"
                :disabled="selectedSlotIds.length === 0"
                @click="openBatchTakeOut"
              >
                <Icon class="mr-5px" icon="ep:download" />
                批量下架{{ selectedSlotIds.length ? `（${selectedSlotIds.length}）` : '' }}
              </el-button>
              <el-button
                v-hasPermi="['wms:slot:update']"
                :disabled="selectedSlotIds.length === 0"
                @click="openBatchMaterialEdit"
              >
                <Icon class="mr-5px" icon="ep:goods" />
                设置适用物料
              </el-button>
              <el-button :disabled="selectableSlots.length === 0" @click="selectAllSlots">全选</el-button>
              <el-button :disabled="selectedSlotIds.length === 0" @click="clearSlotSelection">全不选</el-button>
              <span class="batch-hint">勾选槽位后可批量操作：上架铺货（仅空闲）/ 下架删除实例 / 设置该位置适合放什么物料</span>
            </div>
            <div v-loading="slotLoading" class="slot-list">
              <el-empty v-if="!slots.length" description="该区域下暂无槽位" />
              <div v-for="slot in slots" :key="slot.id" class="slot-row" :class="slotStatusClass(slot)" @click="handleSlotClick(slot)">
                <div
                  v-if="isSlotSelectable(slot)"
                  class="slot-check slot-row-check"
                  @click.stop="toggleSelectSlot(slot.id!)"
                >
                  <el-checkbox :model-value="selectedSlotIds.includes(slot.id!)" />
                </div>
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
        <el-descriptions-item label="适用物料">
          <div class="material-tags">
            <el-tag v-for="m in currentSlotMaterials" :key="m.kind + m.materialId" size="small" :type="m.kind === 'CONTAINER' ? 'primary' : 'success'">
              {{ m.materialName }}
            </el-tag>
            <span v-if="!currentSlotMaterials.length" class="material-empty">未设置</span>
          </div>
          <el-button v-hasPermi="['wms:slot:update']" link type="primary" size="small" @click="openMaterialEdit()">
            编辑
          </el-button>
        </el-descriptions-item>
        <el-descriptions-item label="备注">{{ currentSlot.description || '—' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 槽位上的物料：真相源是 wms_material_instance.root_slot_id，按它反查 -->
      <div class="drawer-section">
        <div class="drawer-section-title">槽位上的物料</div>
        <div v-if="slotOccupant" class="occupant-card">
          <div class="occupant-head">
            <span class="occupant-code">{{ slotOccupant.instanceCode }}</span>
            <el-tag size="small" :type="instanceStatusTag(slotOccupant.instanceStatus)">
              {{ instanceStatusLabel(slotOccupant.instanceStatus) }}
            </el-tag>
          </div>
          <dl class="occupant-facts">
            <dt>名称</dt>
            <dd>{{ slotOccupant.instanceName || '—' }}</dd>
            <dt>条码</dt>
            <dd>{{ slotOccupant.barcode || '—' }}</dd>
            <dt>容器类型</dt>
            <dd>{{ slotOccupant.containerTypeCode || '—' }}</dd>
            <dt>内容物</dt>
            <dd>{{ slotOccupant.contentDefCode || '—' }}</dd>
            <dt>内容物类型</dt>
            <dd>{{ slotOccupant.contentType || '—' }}</dd>
            <dt>关联样本</dt>
            <dd>{{ slotOccupant.sampleId || '—' }}</dd>
            <dt>当前数量</dt>
            <dd>{{ slotOccupant.currentCount ?? '—' }}</dd>
            <dt>当前体积</dt>
            <dd>{{ slotOccupant.currentVolUl != null ? `${slotOccupant.currentVolUl} μL` : '—' }}</dd>
            <dt>创建时间</dt>
            <dd>{{ formatDateTime(slotOccupant.createTime) }}</dd>
            <dt>备注</dt>
            <dd>{{ slotOccupant.description || '—' }}</dd>
          </dl>
        </div>
        <el-empty v-else :image-size="56" description="该槽位空闲" />
        <!-- 顶层实例内的子实例明细 -->
        <template v-if="slotChildInstances.length">
          <div class="child-instances-title">内含子实例（{{ slotChildInstances.length }}）</div>
          <div v-for="c in slotChildInstances" :key="c.id" class="child-instance-row">
            <span class="child-instance-pos">{{ c.parentPositionCode || '—' }}</span>
            <span class="child-instance-code">{{ c.instanceCode }}</span>
            <el-tag size="small" :type="instanceStatusTag(c.instanceStatus)">
              {{ instanceStatusLabel(c.instanceStatus) }}
            </el-tag>
          </div>
        </template>
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

  <!-- 编辑适用物料对话框（单槽位 / 批量） -->
  <el-dialog v-model="materialEditVisible" :title="materialEditTitle" width="560px">
    <el-form label-width="100px">
      <el-form-item label="适用容器">
        <el-select v-model="materialEditForm.containerIds" multiple filterable placeholder="可绑定多种容器（可留空）" class="w-full">
          <el-option v-for="c in containerTypes" :key="c.id" :label="`${c.typeName}（${c.typeCode}）`" :value="c.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="适用试剂">
        <el-select v-model="materialEditForm.contentIds" multiple filterable placeholder="可绑定多种试剂/内容物（可留空）" class="w-full">
          <el-option v-for="c in contentDefs" :key="c.id" :label="`${c.contentName}（${c.contentCode}）`" :value="c.id!" />
        </el-select>
      </el-form-item>
    </el-form>
    <div class="batch-hint">
      适用物料用于提示该槽位适合放什么；批量铺货时会据此智能带出默认物料。{{
        materialEditTargetIds.length > 1 ? `保存后将覆盖所选 ${materialEditTargetIds.length} 个槽位的适用物料（已预填它们的公共配置）。` : ''
      }}
    </div>
    <template #footer>
      <el-button @click="materialEditVisible = false">取消</el-button>
      <el-button type="primary" :loading="opLoading" @click="submitMaterialEdit">保存</el-button>
    </template>
  </el-dialog>

  <!-- 批量铺货上架对话框 -->
  <el-dialog v-model="batchPutInVisible" title="批量铺货上架" width="560px">
    <el-form label-width="100px">
      <el-form-item label="已选槽位">
        <el-tag type="primary">{{ selectedSlotIds.length }}</el-tag>
        <span class="option-sub">个空闲槽位，每个槽位将生成 1 个实例并上架</span>
      </el-form-item>
      <el-form-item label="物料类型">
        <el-radio-group v-model="batchPutInForm.materialKind" @change="handleMaterialKindChange">
          <el-radio-button value="CONTAINER">容器</el-radio-button>
          <el-radio-button value="CONTENT">试剂/内容物</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="batchPutInForm.materialKind === 'CONTAINER'" label="选择容器">
        <el-select
          v-model="batchPutInForm.materialId"
          filterable
          placeholder="请选择容器类型"
          class="w-full"
          @change="handleBatchContainerChange"
        >
          <el-option v-for="c in containerTypes" :key="c.id" :label="`${c.typeName}（${c.typeCode}）`" :value="c.id!" />
        </el-select>
      </el-form-item>
      <template v-else>
        <el-form-item label="选择试剂">
          <el-select v-model="batchPutInForm.materialId" filterable placeholder="请选择试剂/内容物" class="w-full">
            <el-option v-for="c in contentDefs" :key="c.id" :label="`${c.contentName}（${c.contentCode}）`" :value="c.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="装载容器">
          <el-select
            v-model="batchPutInForm.containerTypeId"
            filterable
            placeholder="请选择容器类型"
            class="w-full"
            @change="handleBatchContainerChange"
          >
            <el-option v-for="c in containerTypes" :key="c.id" :label="`${c.typeName}（${c.typeCode}）`" :value="c.id!" />
          </el-select>
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="batchPutInVisible = false">取消</el-button>
      <el-button type="primary" :loading="opLoading" :disabled="!canSubmitBatch" @click="submitBatchPutIn">
        确定铺货
      </el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import { ZoneApi, ZoneVO } from '@/api/wms/space/zone'
import { SlotApi, SlotVO, SlotSuitableMaterial } from '@/api/wms/space/slot'
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import { MaterialMovementApi, MaterialMovementVO, MOVEMENT_TYPE_TAG } from '@/api/wms/movement'
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
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
const activeTab = ref('slot') // 货架区域默认停在「槽位」层

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
  activeTab.value = 'slot' // 切换区域时回到「槽位」层
  selectedSlotIds.value = [] // 切换区域清空批量选择
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
/** 当前槽位上的全部实例（顶层 + 子实例），真相源：wms_material_instance.root_slot_id */
const slotInstances = ref<MaterialInstanceVO[]>([])
/** 当前槽位的顶层占用者（一槽一实例） */
const slotOccupant = computed(() => slotInstances.value.find((i) => i.parentInstanceId == null))
/** 顶层实例内的子实例明细（如 96 孔板的孔位） */
const slotChildInstances = computed(() => slotInstances.value.filter((i) => i.parentInstanceId != null))
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

/** 拉取该槽位上的实例（顶层 + 子实例）与最近流水 */
const loadSlotDetail = async (slot: SlotVO) => {
  if (!slot.id) return
  slotInstances.value = []
  slotMovements.value = []
  const [page, movements] = await Promise.all([
    MaterialInstanceApi.getMaterialInstancePage({
      pageNo: 1,
      pageSize: 100,
      rootSlotId: slot.id
    }),
    MaterialMovementApi.getListBySlot(slot.id, 10)
  ])
  slotInstances.value = page?.list || []
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

// ========== 适用物料 / 批量铺货上架 ==========
const contentDefs = ref<ContentDefVO[]>([])
const containerTypes = ref<ContainerTypeVO[]>([])
const materialOptionsLoaded = ref(false)

/** 解析槽位的适用物料（JSON 字符串 → 数组） */
const parseMaterials = (s?: SlotVO): SlotSuitableMaterial[] => {
  try {
    return s?.suitableMaterials ? JSON.parse(s.suitableMaterials) : []
  } catch {
    return []
  }
}

/** 当前抽屉槽位的适用物料 */
const currentSlotMaterials = computed(() => parseMaterials(currentSlot.value))

/** 懒加载试剂/容器下拉数据 */
const loadMaterialOptions = async () => {
  if (materialOptionsLoaded.value) return
  const [cts, cds] = await Promise.all([
    ContainerTypeApi.getContainerTypeList(),
    ContentDefApi.getContentDefList()
  ])
  containerTypes.value = cts || []
  contentDefs.value = cds || []
  materialOptionsLoaded.value = true
}

// —— 编辑适用物料（单槽位 / 批量） ——
const materialEditVisible = ref(false)
const materialEditForm = reactive<{ contentIds: number[]; containerIds: number[] }>({
  contentIds: [],
  containerIds: []
})
/** 目标槽位（单槽位编辑传 [id]，批量传选中的 ids） */
const materialEditTargetIds = ref<number[]>([])

const materialEditTitle = computed(() =>
  materialEditTargetIds.value.length > 1
    ? `批量设置适用物料（${materialEditTargetIds.value.length} 个槽位）`
    : '编辑适用物料'
)

/** 打开适用物料编辑：targetIds 为空时取当前抽屉槽位 */
const openMaterialEdit = async (targetIds?: number[]) => {
  const ids = targetIds && targetIds.length ? targetIds : currentSlot.value?.id ? [currentSlot.value.id] : []
  if (!ids.length) return
  materialEditTargetIds.value = ids
  if (ids.length === 1) {
    // 单槽位：预填该槽位现有物料
    const mats = parseMaterials(slots.value.find((s) => s.id === ids[0]) || currentSlot.value)
    materialEditForm.contentIds = mats.filter((m) => m.kind === 'CONTENT').map((m) => m.materialId)
    materialEditForm.containerIds = mats.filter((m) => m.kind === 'CONTAINER').map((m) => m.materialId)
  } else {
    // 批量：预填所有目标槽位的公共物料（交集），避免误覆盖
    let common: SlotSuitableMaterial[] | null = null
    for (const id of ids) {
      const mats = parseMaterials(slots.value.find((s) => s.id === id))
      if (common === null) common = mats
      else common = common.filter((c) => mats.some((m) => m.kind === c.kind && m.materialId === c.materialId))
    }
    const list = common || []
    materialEditForm.contentIds = list.filter((m) => m.kind === 'CONTENT').map((m) => m.materialId)
    materialEditForm.containerIds = list.filter((m) => m.kind === 'CONTAINER').map((m) => m.materialId)
  }
  materialEditVisible.value = true
  await loadMaterialOptions()
}

/** 批量设置适用物料入口 */
const openBatchMaterialEdit = () => {
  if (!selectedSlotIds.value.length) return
  openMaterialEdit(selectedSlotIds.value)
}

const submitMaterialEdit = async () => {
  const mats: SlotSuitableMaterial[] = []
  containerTypes.value
    .filter((c) => materialEditForm.containerIds.includes(c.id!))
    .forEach((c) => {
      mats.push({ kind: 'CONTAINER', materialId: c.id!, materialCode: c.typeCode!, materialName: c.typeName! })
    })
  contentDefs.value
    .filter((c) => materialEditForm.contentIds.includes(c.id!))
    .forEach((c) => {
      mats.push({ kind: 'CONTENT', materialId: c.id!, materialCode: c.contentCode!, materialName: c.contentName! })
    })
  opLoading.value = true
  try {
    await SlotApi.updateSuitableMaterials({
      ids: materialEditTargetIds.value,
      suitableMaterials: JSON.stringify(mats)
    })
    const n = materialEditTargetIds.value.length
    message.success(n > 1 ? `已批量更新 ${n} 个槽位的适用物料` : '适用物料已更新')
    materialEditVisible.value = false
    selectedSlotIds.value = []
    if (currentZone.value?.zoneCode) {
      await loadSlots(currentZone.value.zoneCode)
      const fresh = slots.value.find((s) => s.id === currentSlot.value?.id)
      if (fresh) currentSlot.value = fresh
    }
  } finally {
    opLoading.value = false
  }
}

// —— 批量铺货上架 ——
const selectedSlotIds = ref<number[]>([])

/** 槽位是否可被勾选（未停用即可；上架/下架/设置物料各自再校验） */
const isSlotSelectable = (slot: SlotVO) => slot.status !== 1

const toggleSelectSlot = (slotId: number) => {
  const arr = selectedSlotIds.value
  const idx = arr.indexOf(slotId)
  if (idx >= 0) arr.splice(idx, 1)
  else arr.push(slotId)
}

/** 当前区域所有可被选中的槽位（未停用且空闲） */
const selectableSlots = computed(() => slots.value.filter(isSlotSelectable))

/** 全选：选中当前区域所有空闲槽位 */
const selectAllSlots = () => {
  selectedSlotIds.value = selectableSlots.value.map((s) => s.id!)
}

/** 全不选：清空选择 */
const clearSlotSelection = () => {
  selectedSlotIds.value = []
}

const batchPutInVisible = ref(false)
const batchPutInForm = reactive<{
  materialKind: 'CONTENT' | 'CONTAINER'
  materialId?: number
  containerTypeId?: number
  containerTypeCode?: string
}>({
  materialKind: 'CONTAINER',
  materialId: undefined,
  containerTypeId: undefined,
  containerTypeCode: undefined
})

const canSubmitBatch = computed(
  () =>
    selectedSlotIds.value.length > 0 &&
    batchPutInForm.materialId != null &&
    (batchPutInForm.materialKind === 'CONTAINER' || batchPutInForm.containerTypeId != null)
)

/** 打开批量铺货：据选中槽位的适用物料智能带出默认物料 */
const openBatchPutIn = async () => {
  batchPutInForm.materialKind = 'CONTAINER'
  batchPutInForm.materialId = undefined
  batchPutInForm.containerTypeId = undefined
  batchPutInForm.containerTypeCode = undefined
  batchPutInVisible.value = true
  await loadMaterialOptions()
  // 选中槽位都绑定同一个物料时，自动带出（单选无需再选）
  const selectedSlots = slots.value.filter((s) => selectedSlotIds.value.includes(s.id!))
  let common: SlotSuitableMaterial[] | null = null
  for (const s of selectedSlots) {
    const mats = parseMaterials(s)
    if (common === null) common = mats
    else common = common.filter((c) => mats.some((m) => m.kind === c.kind && m.materialId === c.materialId))
  }
  const list = common || []
  if (list.length === 1) {
    batchPutInForm.materialKind = list[0].kind
    batchPutInForm.materialId = list[0].materialId
    if (list[0].kind === 'CONTAINER') {
      batchPutInForm.containerTypeId = list[0].materialId
      batchPutInForm.containerTypeCode = list[0].materialCode
    }
  }
}

const handleMaterialKindChange = () => {
  batchPutInForm.materialId = undefined
  batchPutInForm.containerTypeId = undefined
  batchPutInForm.containerTypeCode = undefined
}

const handleBatchContainerChange = (id?: number) => {
  const ct = containerTypes.value.find((c) => c.id === id)
  batchPutInForm.containerTypeCode = ct?.typeCode
}

const submitBatchPutIn = async () => {
  if (!selectedSlotIds.value.length) return
  if (batchPutInForm.materialId == null) {
    message.warning('请选择物料')
    return
  }
  if (batchPutInForm.materialKind === 'CONTENT' && batchPutInForm.containerTypeId == null) {
    message.warning('请选择装载容器')
    return
  }
  // 只对空闲槽位上架，非空闲自动跳过
  const freeSlotIds = selectedSlotIds.value.filter((id) => {
    const s = slots.value.find((x) => x.id === id)
    return s && s.status !== 1 && s.slotStatus === 'FREE'
  })
  if (!freeSlotIds.length) {
    message.warning('选中的槽位中没有空闲槽位，无法上架')
    return
  }
  opLoading.value = true
  try {
    const ids = await MaterialInstanceApi.batchPutIn({
      slotIds: freeSlotIds,
      materialKind: batchPutInForm.materialKind,
      materialId: batchPutInForm.materialId,
      containerTypeId: batchPutInForm.containerTypeId,
      containerTypeCode: batchPutInForm.containerTypeCode
    })
    const n = Array.isArray(ids) ? ids.length : freeSlotIds.length
    const skipped = selectedSlotIds.value.length - freeSlotIds.length
    message.success(skipped > 0 ? `已批量铺货 ${n} 个实例（自动跳过 ${skipped} 个非空闲槽位）` : `已批量铺货 ${n} 个实例`)
    selectedSlotIds.value = []
    batchPutInVisible.value = false
    await refreshAfterOperation()
  } finally {
    opLoading.value = false
  }
}

/** 批量下架：删除选中槽位上的实例并解绑槽位 */
const openBatchTakeOut = async () => {
  if (!selectedSlotIds.value.length) return
  try {
    await message.delConfirm(
      `确定对选中的 ${selectedSlotIds.value.length} 个槽位执行批量下架吗？槽位上的实例将被删除并解绑（空槽位自动跳过）。`
    )
  } catch {
    return
  }
  opLoading.value = true
  try {
    const n = await MaterialInstanceApi.batchTakeOut(selectedSlotIds.value)
    message.success(n > 0 ? `已下架并删除 ${n} 个实例` : '选中的槽位上没有实例')
    selectedSlotIds.value = []
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

.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.batch-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.material-tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-right: 8px;
}

.material-empty {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
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
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
}

/* 勾选热区：外层 div 命中点击，checkbox 纯视觉 */
.slot-check {
  position: absolute;
  top: 0;
  left: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 2;
}

/* 列表行内的勾选区：不用绝对定位 */
.slot-row-check {
  position: static;
  width: auto;
  height: auto;
  flex-shrink: 0;
}

.slot-cell:hover {
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

.occupant-facts {
  display: grid;
  grid-template-columns: 68px 1fr;
  gap: 4px 10px;
  margin: 8px 0 0;
  font-size: 12px;

  dt {
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 0;
    color: var(--el-text-color-primary);
    word-break: break-all;
  }
}

.occupant-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.child-instances-title {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.child-instance-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px dashed var(--el-border-color-extra-light);
  font-size: 12px;

  &:last-child {
    border-bottom: none;
  }
}

.child-instance-pos {
  min-width: 36px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
}

.child-instance-code {
  flex: 1;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
