<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="内容物定义" :title="item?.contentName">
    <template #tags>
      <el-tag v-if="item" :type="item.status === 0 ? 'success' : 'info'" size="small">
        {{ item.status === 0 ? '开启' : '停用' }}
      </el-tag>
      <el-tag v-if="hazardTag" :type="hazardTag" size="small">{{ hazardLabel(item?.hazardLevel) }}</el-tag>
    </template>

    <template v-if="item">
      <section class="cat-block">
        <div class="cat-kicker">物质</div>
        <dl class="cat-facts">
          <dt>编码</dt>
          <dd>{{ item.contentCode }}</dd>
          <dt>类型</dt>
          <dd>{{ typeLabel(item.contentType) }}</dd>
          <dt>计量单位</dt>
          <dd>{{ item.unit || '—' }}</dd>
          <dt>标准浓度</dt>
          <dd>{{ item.concentration || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">存放</div>
        <dl class="cat-facts">
          <dt>条件</dt>
          <dd>{{ storageLabel(item.storageCond) }}</dd>
          <dt>保质期</dt>
          <dd>{{ item.shelfLifeDays != null ? `${item.shelfLifeDays} 天` : '—' }}</dd>
          <dt>开封后</dt>
          <dd>{{ item.openLifeDays != null ? `${item.openLifeDays} 天` : '—' }}</dd>
          <dt>先到期先出</dt>
          <dd>{{ item.fefo === false ? '否' : '是' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">来源</div>
        <dl class="cat-facts">
          <dt>供应商</dt>
          <dd>{{ item.supplier || '—' }}</dd>
          <dt>货号</dt>
          <dd>{{ item.catalogNo || '—' }}</dd>
          <dt>CAS</dt>
          <dd>{{ item.casNo || '—' }}</dd>
        </dl>
      </section>

      <section v-if="specs.length" class="cat-block">
        <div class="cat-kicker">补充规格</div>
        <div v-for="row in specs" :key="row.key" class="param-card">
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
import { ContentDefApi, ContentDefVO } from '@/api/wms/content'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'

const visible = ref(false)
const loading = ref(false)
const item = ref<ContentDefVO>()

const typeLabel = (v?: string) =>
  ({
    REAGENT: '试剂',
    STANDARD: '标准品',
    BUFFER: '缓冲液',
    SAMPLE: '样本',
    WASTE: '废液',
    MEDIA: '培养基',
    SOLVENT: '溶剂',
    OTHER: '其他'
  }[v || ''] || v || '—')

const storageLabel = (v?: string) =>
  ({
    RT: '常温',
    C2_8: '2~8℃',
    F20: '-20℃',
    Ultra80: '-80℃',
    FROZTHAW: '控制冻融'
  }[v || ''] || '—')

const hazardLabel = (v?: string) =>
  ({
    NONE: '无危险',
    LOW: '低危',
    MEDIUM: '中危',
    HIGH: '高危',
    FLAMMABLE: '易燃',
    TOXIC: '有毒'
  }[v || ''] || '')

const hazardTag = computed(() => {
  const v = item.value?.hazardLevel
  if (!v || v === 'NONE') return ''
  if (v === 'HIGH' || v === 'TOXIC' || v === 'FLAMMABLE') return 'danger'
  if (v === 'MEDIUM') return 'warning'
  return 'info'
})

const specs = computed(() => {
  if (!item.value?.specJson) return []
  try {
    const obj = JSON.parse(item.value.specJson) as Record<string, unknown>
    return Object.entries(obj).map(([key, value]) => ({
      key,
      value: value === null || value === undefined ? '—' : String(value)
    }))
  } catch {
    return []
  }
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  item.value = undefined
  try {
    item.value = await ContentDefApi.getContentDef(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
