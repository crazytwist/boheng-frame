<template>
  <ContentWrap title="物料流水">
    <!-- 搜索 -->
    <el-form
      class="-mb-15px"
      :model="queryParams"
      ref="queryFormRef"
      :inline="true"
      label-width="80px"
    >
      <el-form-item label="流水类型" prop="movementType">
        <el-select v-model="queryParams.movementType" placeholder="全部" clearable class="!w-160px">
          <el-option
            v-for="item in movementTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="实例编码" prop="instanceCode">
        <el-input
          v-model="queryParams.instanceCode"
          placeholder="请输入实例编码"
          clearable
          @keyup.enter="handleQuery"
          class="!w-220px"
        />
      </el-form-item>
      <el-form-item label="内容物" prop="contentDefCode">
        <el-input
          v-model="queryParams.contentDefCode"
          placeholder="请输入内容物编码"
          clearable
          @keyup.enter="handleQuery"
          class="!w-200px"
        />
      </el-form-item>
      <el-form-item label="区域" prop="zoneCode">
        <el-input
          v-model="queryParams.zoneCode"
          placeholder="区域编码"
          clearable
          @keyup.enter="handleQuery"
          class="!w-200px"
        />
      </el-form-item>
      <el-form-item label="来源" prop="bizSource">
        <el-select v-model="queryParams.bizSource" placeholder="全部" clearable class="!w-140px">
          <el-option
            v-for="item in bizSourceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="操作时间" prop="operateTime">
        <el-date-picker
          v-model="queryParams.operateTime"
          value-format="YYYY-MM-DD HH:mm:ss"
          type="daterange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          :default-time="[new Date('1 00:00:00'), new Date('1 23:59:59')]"
          class="!w-260px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="mb-12px"
      title="流水只增不改：每次上架 / 下架 / 转移 / 消耗都会在这里留一条记录。整盒转移只记顶层实例一条。"
    />
    <el-table v-loading="loading" :data="list">
      <el-table-column label="操作时间" align="center" width="165">
        <template #default="scope">{{ fmtDateTime(scope.row.operateTime) }}</template>
      </el-table-column>
      <el-table-column label="类型" align="center" width="90">
        <template #default="scope">
          <el-tag :type="movementTag(scope.row.movementType)" size="small">
            {{ scope.row.movementTypeName || movementLabel(scope.row.movementType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="实例编码" align="center" min-width="170" :show-overflow-tooltip="true">
        <template #default="scope">
          <el-link type="primary" @click="handleViewInstanceTrace(scope.row)">
            {{ scope.row.instanceCode }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column label="内容物" align="center" width="130" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.contentDefCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="位置变化" align="center" min-width="240" :show-overflow-tooltip="true">
        <template #default="scope">
          <span class="loc">{{ scope.row.fromSlotCode || '—' }}</span>
          <Icon icon="ep:right" class="loc-arrow" />
          <span class="loc">{{ scope.row.toSlotCode || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="数量/体积变化" align="center" width="150">
        <template #default="scope">{{ changeText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="状态变化" align="center" width="150">
        <template #default="scope">
          <template v-if="scope.row.beforeStatus || scope.row.afterStatus">
            <span class="loc">{{ statusLabel(scope.row.beforeStatus) }}</span>
            <Icon icon="ep:right" class="loc-arrow" />
            <span class="loc">{{ statusLabel(scope.row.afterStatus) }}</span>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="来源" align="center" width="90">
        <template #default="scope">{{ bizSourceLabel(scope.row.bizSource) }}</template>
      </el-table-column>
      <el-table-column label="操作人" align="center" width="90" :show-overflow-tooltip="true">
        <template #default="scope">{{ scope.row.operator || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="80" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="handleDetail(scope.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <!-- 详情抽屉 -->
  <el-drawer v-model="detailVisible" :title="detail?.movementTypeName || '流水详情'" size="440px">
    <template v-if="detail">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="流水编号">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="movementTag(detail.movementType)" size="small">
            {{ detail.movementTypeName || movementLabel(detail.movementType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作时间">
          {{ fmtDateTime(detail.operateTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="来源">
          {{ bizSourceLabel(detail.bizSource) }} / {{ operatorTypeLabel(detail.operatorType) }}
        </el-descriptions-item>
        <el-descriptions-item label="操作人">{{ detail.operator || '—' }}</el-descriptions-item>
        <el-descriptions-item label="实例编码">{{ detail.instanceCode }}</el-descriptions-item>
        <el-descriptions-item label="容器类型">
          {{ detail.containerTypeCode || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="内容物">{{ detail.contentDefCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="源槽位">{{ detail.fromSlotCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="目标槽位">{{ detail.toSlotCode || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态变化">
          {{ statusLabel(detail.beforeStatus) }} → {{ statusLabel(detail.afterStatus) }}
        </el-descriptions-item>
        <el-descriptions-item label="数量变化">{{ qtyText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="体积变化">{{ volText(detail) }}</el-descriptions-item>
        <el-descriptions-item label="操作批次号">
          {{ detail.operationId || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="关联单据">
          {{ detail.refNo || detail.refId || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="幂等键">{{ detail.idempotentKey || '—' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
      </el-descriptions>
    </template>
  </el-drawer>

  <!-- 实例轨迹抽屉 -->
  <el-drawer v-model="traceVisible" title="实例轨迹" size="520px">
    <el-timeline v-if="traceList.length">
      <el-timeline-item
        v-for="item in traceList"
        :key="item.id"
        :timestamp="fmtDateTime(item.operateTime)"
        :type="movementTag(item.movementType)"
        placement="top"
      >
        <div class="trace-title">
          <el-tag :type="movementTag(item.movementType)" size="small">
            {{ item.movementTypeName || movementLabel(item.movementType) }}
          </el-tag>
          <span class="trace-loc">
            {{ item.fromSlotCode || '—' }}
            <Icon icon="ep:right" class="loc-arrow" />
            {{ item.toSlotCode || '—' }}
          </span>
        </div>
        <div class="trace-sub">
          {{ item.instanceCode }}
          <span v-if="changeText(item) !== '—'"> · {{ changeText(item) }}</span>
          <span v-if="item.remark"> · {{ item.remark }}</span>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="该实例暂无流水" />
  </el-drawer>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import {
  MaterialMovementApi,
  MaterialMovementVO,
  MOVEMENT_TYPE_LABEL,
  MOVEMENT_TYPE_TAG,
  BIZ_SOURCE_LABEL,
  OPERATOR_TYPE_LABEL
} from '@/api/wms/movement'

defineOptions({ name: 'WmsMovement' })

const loading = ref(false)
const list = ref<MaterialMovementVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  movementType: undefined,
  instanceCode: undefined,
  contentDefCode: undefined,
  zoneCode: undefined,
  bizSource: undefined,
  operateTime: undefined as string[] | undefined
})
const queryFormRef = ref()

const movementTypeOptions = Object.entries(MOVEMENT_TYPE_LABEL).map(([value, label]) => ({
  value,
  label
}))
const bizSourceOptions = Object.entries(BIZ_SOURCE_LABEL).map(([value, label]) => ({ value, label }))

const movementLabel = (v?: string) => (v ? MOVEMENT_TYPE_LABEL[v] || v : '—')
const movementTag = (v?: string) => MOVEMENT_TYPE_TAG[v || ''] || 'info'
const bizSourceLabel = (v?: string) => (v ? BIZ_SOURCE_LABEL[v] || v : '—')
const operatorTypeLabel = (v?: string) => (v ? OPERATOR_TYPE_LABEL[v] || v : '—')

const statusLabel = (v?: string) => {
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

const fmtDateTime = (v?: Date | string) =>
  v ? formatDate(v as any, 'YYYY-MM-DD HH:mm:ss') || '—' : '—'

/** 数量变化：优先看 qty 组，没有则看 vol 组 */
const changeText = (row: MaterialMovementVO) => {
  if (row.changeQty != null) {
    const sign = row.changeQty > 0 ? '+' : ''
    return `${sign}${row.changeQty} 个（${row.beforeQty ?? '—'} → ${row.afterQty ?? '—'}）`
  }
  if (row.changeVolUl != null) {
    const sign = Number(row.changeVolUl) > 0 ? '+' : ''
    return `${sign}${row.changeVolUl} μL（${row.beforeVolUl ?? '—'} → ${row.afterVolUl ?? '—'}）`
  }
  return '—'
}
const qtyText = (r: MaterialMovementVO) =>
  r.changeQty == null ? '—' : `${r.beforeQty ?? '—'} → ${r.afterQty ?? '—'}（${r.changeQty}）`
const volText = (r: MaterialMovementVO) =>
  r.changeVolUl == null
    ? '—'
    : `${r.beforeVolUl ?? '—'} → ${r.afterVolUl ?? '—'}（${r.changeVolUl} μL）`

const getList = async () => {
  loading.value = true
  try {
    const data = await MaterialMovementApi.getMovementPage(queryParams)
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
  queryParams.operateTime = undefined
  queryParams.pageNo = 1
  getList()
}

const detailVisible = ref(false)
const detail = ref<MaterialMovementVO>()

const handleDetail = (row: MaterialMovementVO) => {
  detail.value = row
  detailVisible.value = true
}

const traceVisible = ref(false)
const traceList = ref<MaterialMovementVO[]>([])

const handleViewInstanceTrace = async (row: MaterialMovementVO) => {
  if (!row.instanceId) return
  traceList.value = await MaterialMovementApi.getListByInstance(row.instanceId)
  traceVisible.value = true
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.loc {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
}

.loc-arrow {
  margin: 0 6px;
  color: var(--el-text-color-placeholder);
}

.trace-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-loc {
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.trace-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
