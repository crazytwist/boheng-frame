<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="容器类型" :title="item?.typeName" :summary="summary">
    <template #tags>
      <el-tag v-if="item" :type="item.status === 0 ? 'success' : 'info'" size="small">
        {{ item.status === 0 ? '开启' : '停用' }}
      </el-tag>
    </template>

    <template v-if="item">
      <section class="cat-block">
        <div class="cat-kicker">角色</div>
        <h3 class="cat-title">它在实例树里站哪一层</h3>
        <p class="cat-lead">{{ roleLead }}</p>
        <dl class="cat-facts">
          <dt>编码</dt>
          <dd>{{ item.typeCode }}</dd>
          <dt>形态</dt>
          <dd>{{ categoryLabel(item.category) }}</dd>
          <dt>能否入架</dt>
          <dd>{{ item.nestable ? '可以装进别的容器' : '不允许被装入其他容器' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">规格</div>
        <h3 class="cat-title">能装多少、位置怎么编号</h3>
        <p class="cat-lead">{{ specLead }}</p>
        <dl class="cat-facts">
          <dt>位置命名</dt>
          <dd>{{ namingLabel(item.positionNaming) }}</dd>
          <dt>子单元</dt>
          <dd>{{ item.childTypeCode || '不承载固定的子类型' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">使用</div>
        <h3 class="cat-title">用完是丢掉还是再洗</h3>
        <p class="cat-lead">{{ usageLead }}</p>
      </section>

      <section v-if="specs.length" class="cat-block">
        <div class="cat-kicker">补充规格</div>
        <h3 class="cat-title">类型上额外记下的参数</h3>
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
import { ContainerTypeApi, ContainerTypeVO } from '@/api/wms/container'
import CategoryDrawer from '@/views/device/components/CategoryDrawer.vue'

const visible = ref(false)
const loading = ref(false)
const item = ref<ContainerTypeVO>()

const categoryLabel = (v?: string) =>
  ({
    PLATE: '孔板',
    TUBE: '试管/离心管',
    BOTTLE: '瓶',
    RACK: '托盘架',
    VIAL: '小瓶',
    TIP: '吸头',
    CHIP: '芯片',
    FILTER: '滤膜',
    BOX: '盒',
    BAG: '袋',
    OTHER: '其他'
  }[v || ''] || v || '未分类')

const namingLabel = (v?: string) =>
  ({
    ROW_COL: '行列，如 A1、B12',
    SEQ: '顺序号，如 1 到 96',
    ROW_COL_LAYER: '行列加层，如 A1-L1',
    NONE: '没有位置概念'
  }[v || ''] || v || '没有位置概念')

const summary = computed(() => {
  if (!item.value) return ''
  return `${categoryLabel(item.value.category)} · ${item.value.typeCode}`
})

const roleLead = computed(() => {
  const role = item.value?.hierarchyRole
  if (role === 'CARRIER') return '这是载体。它自己不装物质，用来托住孔板、试管或孔位。'
  if (role === 'CONTAINER') return '这是直接容器。内容物装在它里面，不再往下拆成孔。'
  if (role === 'WELL') return '这是孔位，是孔板或架子里的最小一格，一块板会展开成很多个这样的实例。'
  return '还没有标明它在实例树里的角色。'
})

const specLead = computed(() => {
  const row = item.value
  if (!row) return ''
  if (row.hierarchyRole === 'CARRIER' && row.wellCount) {
    const grid = row.wellRows && row.wellCols ? `，排成 ${row.wellRows} 行 ${row.wellCols} 列` : ''
    return `一共 ${row.wellCount} 个位${grid}。`
  }
  if (row.maxVolUl != null) return `最多装 ${row.maxVolUl} μL。`
  return '没有填写容积或位数。'
})

const usageLead = computed(() => {
  const row = item.value
  if (!row) return ''
  if (row.usageType === 'DISPOSABLE') return '一次性耗材，用完即弃，不记复用次数。'
  const life = row.lifeCycles ? `最多复用 ${row.lifeCycles} 次。` : '复用次数不限。'
  return `周转容器，洗完可以再用。${life}`
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
    item.value = await ContainerTypeApi.getContainerType(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
