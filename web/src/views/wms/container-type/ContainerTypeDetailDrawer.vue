<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="容器类型" :title="item?.typeName">
    <template #tags>
      <el-tag v-if="item" :type="item.status === 0 ? 'success' : 'info'" size="small">
        {{ item.status === 0 ? '开启' : '停用' }}
      </el-tag>
    </template>

    <template v-if="item">
      <section class="cat-block">
        <div class="cat-kicker">角色</div>
        <dl class="cat-facts">
          <dt>编码</dt>
          <dd>{{ item.typeCode }}</dd>
          <dt>形态</dt>
          <dd>{{ categoryLabel(item.category) }}</dd>
          <dt>角色</dt>
          <dd>{{ roleLabel(item.hierarchyRole) }}</dd>
          <dt>能否入架</dt>
          <dd>{{ item.nestable ? '可以' : '不可以' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">规格</div>
        <dl class="cat-facts">
          <dt>位数</dt>
          <dd>{{ item.wellCount ?? '—' }}</dd>
          <dt>行列</dt>
          <dd>{{ item.wellRows && item.wellCols ? `${item.wellRows} × ${item.wellCols}` : '—' }}</dd>
          <dt>容积</dt>
          <dd>{{ item.maxVolUl != null ? `${item.maxVolUl} μL` : '—' }}</dd>
          <dt>位置命名</dt>
          <dd>{{ namingLabel(item.positionNaming) }}</dd>
          <dt>子单元</dt>
          <dd>{{ item.childTypeCode || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">使用</div>
        <dl class="cat-facts">
          <dt>方式</dt>
          <dd>{{ item.usageType === 'DISPOSABLE' ? '一次性' : '周转' }}</dd>
          <dt>复用次数</dt>
          <dd>{{ item.usageType === 'DISPOSABLE' ? '—' : item.lifeCycles ?? '不限' }}</dd>
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
  }[v || ''] || v || '—')

const roleLabel = (v?: string) =>
  ({
    CARRIER: '载体',
    CONTAINER: '直接容器',
    WELL: '孔位'
  }[v || ''] || v || '—')

const namingLabel = (v?: string) =>
  ({
    ROW_COL: '行列',
    SEQ: '顺序号',
    ROW_COL_LAYER: '行列加层',
    NONE: '无'
  }[v || ''] || v || '无')

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
