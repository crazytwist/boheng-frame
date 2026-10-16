<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="样本" :title="item?.sampleName || item?.sampleId">
    <template #tags>
      <el-tag v-if="item" :type="statusTagType(item.status)" size="small">{{ statusLabel(item.status) }}</el-tag>
      <el-tag v-if="item" size="small" type="info">{{ `${item.generation ?? 0} 代 · ${deriveTypeLabel(item.deriveType)}` }}</el-tag>
    </template>

    <template v-if="item">
      <section class="cat-block">
        <div class="cat-kicker">基本信息</div>
        <dl class="cat-facts">
          <dt>样本编号</dt>
          <dd>{{ item.sampleId || '—' }}</dd>
          <dt>样本名称</dt>
          <dd>{{ item.sampleName || '—' }}</dd>
          <dt>样本类型</dt>
          <dd>{{ sampleTypeLabel(item.sampleType) }}</dd>
          <dt>优先级</dt>
          <dd>{{ priorityLabel(item.priority) }}</dd>
          <dt>来源</dt>
          <dd>{{ sourceLabel(item.source) }}</dd>
          <dt>初始体积</dt>
          <dd>{{ item.initialVolUl != null ? `${item.initialVolUl} μL` : '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">物质与容器</div>
        <dl class="cat-facts">
          <dt>内容物编码</dt>
          <dd>{{ item.contentDefCode || '—' }}</dd>
          <dt>期望容器类型</dt>
          <dd>{{ item.containerTypeCode || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">谱系</div>
        <dl class="cat-facts">
          <dt>父样本编号</dt>
          <dd>{{ item.parentSampleId || '—' }}</dd>
          <dt>根样本编号</dt>
          <dd>{{ item.rootSampleId || '—' }}</dd>
          <dt>代数</dt>
          <dd>{{ item.generation ?? 0 }}</dd>
          <dt>衍生方式</dt>
          <dd>{{ deriveTypeLabel(item.deriveType) }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">流转</div>
        <dl class="cat-facts">
          <dt>当前流程</dt>
          <dd>{{ item.currentExecutionId || '—' }}</dd>
          <dt>当前节点</dt>
          <dd>{{ item.currentNodeId || '—' }}</dd>
          <dt>批次号</dt>
          <dd>{{ item.batchNo || '—' }}</dd>
          <dt>检测单号</dt>
          <dd>{{ item.orderNo || '—' }}</dd>
          <dt>外部系统 ID</dt>
          <dd>{{ item.externalId || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">已生成实例（{{ instances.length }}）</div>
        <div v-if="instances.length" class="instance-list">
          <div v-for="inst in instances" :key="inst.id" class="instance-item">
            <span class="instance-code">{{ inst.instanceCode }}</span>
            <span class="instance-meta">{{ inst.containerTypeCode || '—' }}</span>
            <el-tag size="small" :type="instanceStatusTag(inst.instanceStatus)">{{ instanceStatusLabel(inst.instanceStatus) }}</el-tag>
          </div>
        </div>
        <p v-else class="cat-lead">尚未生成实例，可在样本列表点「生成实例」物化</p>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">时间</div>
        <dl class="cat-facts">
          <dt>采样时间</dt>
          <dd>{{ fmtDate(item.collectedAt) }}</dd>
          <dt>收样时间</dt>
          <dd>{{ fmtDate(item.receivedAt) }}</dd>
          <dt>有效期</dt>
          <dd>{{ fmtDate(item.expireTime) }}</dd>
          <dt>创建时间</dt>
          <dd>{{ fmtDate(item.createTime) }}</dd>
        </dl>
      </section>

      <section v-if="paramsRows.length" class="cat-block">
        <div class="cat-kicker">实验参数</div>
        <div v-for="row in paramsRows" :key="row.key" class="param-card">
          <div class="param-name">{{ row.key }}</div>
          <div class="param-meta">{{ row.value }}</div>
        </div>
      </section>

      <section v-if="item.description" class="cat-block">
        <div class="cat-kicker">备注</div>
        <p class="cat-lead">{{ item.description }}</p>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { SampleApi, SampleVO } from '@/api/wms/sample'
import { MaterialInstanceApi, MaterialInstanceVO } from '@/api/wms/instance'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'WmsSampleDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const item = ref<SampleVO>()
const instances = ref<MaterialInstanceVO[]>([])

const fmtDate = (v?: Date | string) => (v ? formatDate(v) : '—')

const SAMPLE_TYPE: Record<string, string> = {
  WATER: '水样',
  BLOOD: '血样',
  URINE: '尿样',
  TISSUE: '组织',
  SOIL: '土样',
  AIR: '气样',
  FOOD: '食品',
  MIXTURE: '混合物',
  OTHER: '其他'
}
const sampleTypeLabel = (v?: string) => SAMPLE_TYPE[v || ''] || v || '—'

const STATUS_META: Record<string, { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }> = {
  REGISTERED: { label: '已登记', type: 'info' },
  LOADED: { label: '已装载', type: 'primary' },
  IN_PROCESS: { label: '处理中', type: 'warning' },
  SPLIT: { label: '已拆分', type: 'primary' },
  COMPLETED: { label: '已完成', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
  REJECTED: { label: '不合格', type: 'danger' },
  LOST: { label: '丢失', type: 'danger' }
}
const statusLabel = (v?: string) => STATUS_META[v || '']?.label || v || '—'
const statusTagType = (v?: string) => STATUS_META[v || '']?.type || 'info'

const DERIVE: Record<string, string> = {
  ORIGINAL: '原始',
  SPLIT: '拆分',
  MERGE: '合并',
  ALIQUOT: '分装',
  ADD_REAGENT: '加试剂'
}
const deriveTypeLabel = (v?: string) => DERIVE[v || ''] || v || '—'

const SOURCE: Record<string, string> = {
  MANUAL: '手动',
  IMPORT: '导入',
  LIS: 'LIS',
  DEVICE: '设备'
}
const sourceLabel = (v?: string) => SOURCE[v || ''] || v || '—'

const priorityLabel = (v?: number) => ({ 0: '普通', 1: '加急', 2: '特急' }[v ?? 0] || '—')

const INSTANCE_STATUS: Record<string, { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }> = {
  AVAILABLE: { label: '可用', type: 'success' },
  RESERVED: { label: '已预留', type: 'warning' },
  IN_USE: { label: '使用中', type: 'primary' },
  USED: { label: '已用完', type: 'info' },
  EXPIRED: { label: '已过期', type: 'warning' },
  DISCARDED: { label: '已废弃', type: 'danger' }
}
const instanceStatusLabel = (v?: string) => INSTANCE_STATUS[v || '']?.label || v || '—'
const instanceStatusTag = (v?: string) => INSTANCE_STATUS[v || '']?.type || 'info'

const paramsRows = computed(() => {
  if (!item.value?.experimentParams) return []
  try {
    const obj = JSON.parse(item.value.experimentParams) as Record<string, unknown>
    return Object.entries(obj).map(([key, value]) => ({
      key,
      value: value === null || value === undefined ? '—' : String(value)
    }))
  } catch {
    return [{ key: '原始值', value: item.value.experimentParams }]
  }
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  item.value = undefined
  instances.value = []
  try {
    item.value = await SampleApi.getSample(id)
    if (item.value?.sampleId) {
      instances.value = (await MaterialInstanceApi.getMaterialInstanceListBySample(item.value.sampleId)) || []
    }
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style lang="scss" scoped>
.instance-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.instance-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
}

.instance-code {
  font-weight: 600;
  color: var(--el-text-color-primary);
  font-size: 13px;
}

.instance-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
