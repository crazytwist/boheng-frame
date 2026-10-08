<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="内容物定义" :title="item?.contentName" :summary="summary">
    <template #tags>
      <el-tag v-if="item" :type="item.status === 0 ? 'success' : 'info'" size="small">
        {{ item.status === 0 ? '开启' : '停用' }}
      </el-tag>
      <el-tag v-if="hazardTag" :type="hazardTag" size="small">{{ hazardLabel(item?.hazardLevel) }}</el-tag>
    </template>

    <template v-if="item">
      <section class="cat-block">
        <div class="cat-kicker">物质</div>
        <h3 class="cat-title">装进去的是什么</h3>
        <p class="cat-lead">{{ matterLead }}</p>
        <dl class="cat-facts">
          <dt>编码</dt>
          <dd>{{ item.contentCode }}</dd>
          <dt>计量单位</dt>
          <dd>{{ item.unit || '未填' }}</dd>
          <dt>标准浓度</dt>
          <dd>{{ item.concentration || '未填' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">存放</div>
        <h3 class="cat-title">放在哪、能放多久</h3>
        <p class="cat-lead">{{ storageLead }}</p>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">来源</div>
        <h3 class="cat-title">谁供的、危不危险</h3>
        <p class="cat-lead">{{ sourceLead }}</p>
        <dl class="cat-facts">
          <dt>供应商</dt>
          <dd>{{ item.supplier || '未填' }}</dd>
          <dt>货号</dt>
          <dd>{{ item.catalogNo || '未填' }}</dd>
          <dt>CAS</dt>
          <dd>{{ item.casNo || '无' }}</dd>
        </dl>
      </section>

      <section v-if="specs.length" class="cat-block">
        <div class="cat-kicker">补充规格</div>
        <h3 class="cat-title">定义上额外记下的参数</h3>
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
  }[v || ''] || v || '未分类')

const storageLabel = (v?: string) =>
  ({
    RT: '常温',
    C2_8: '2~8℃',
    F20: '-20℃',
    Ultra80: '-80℃',
    FROZTHAW: '需要控制冻融'
  }[v || ''] || '未规定存储条件')

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

const summary = computed(() => (item.value ? `${typeLabel(item.value.contentType)} · ${item.value.contentCode}` : ''))

const matterLead = computed(() => {
  const row = item.value
  if (!row) return ''
  const conc = row.concentration ? `标准浓度是 ${row.concentration}。` : '没有填写标准浓度，实例上可以单独覆盖。'
  return `这是一种${typeLabel(row.contentType)}。${conc}`
})

const storageLead = computed(() => {
  const row = item.value
  if (!row) return ''
  const shelf = row.shelfLifeDays ? `未开封保质 ${row.shelfLifeDays} 天` : '未开封保质期不限'
  const opened = row.openLifeDays ? `开封后再放 ${row.openLifeDays} 天` : '开封后有效期不限'
  const fefo = row.fefo === false ? '出库不要求先到期先出。' : '出库按先到期先出。'
  return `存放条件是${storageLabel(row.storageCond)}。${shelf}，${opened}。${fefo}`
})

const sourceLead = computed(() => {
  const row = item.value
  if (!row) return ''
  if (!row.hazardLevel || row.hazardLevel === 'NONE') return '按普通物质管理，没有危险等级。'
  return `危险等级是${hazardLabel(row.hazardLevel)}，使用和存放时要按这个等级处理。`
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
