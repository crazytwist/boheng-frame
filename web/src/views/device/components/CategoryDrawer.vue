<template>
  <el-drawer
    :model-value="modelValue"
    :size="size"
    append-to-body
    destroy-on-close
    class="device-category-drawer"
    @close="emit('update:modelValue', false)"
  >
    <template #header>
      <div class="drawer-head">
        <div class="drawer-kicker">{{ kicker }}</div>
        <div class="drawer-title-row">
          <div class="drawer-title">{{ title || '加载中' }}</div>
          <slot name="tags"></slot>
        </div>
      </div>
    </template>
    <div v-loading="loading" class="drawer-body">
      <p v-if="summary" class="drawer-summary">{{ summary }}</p>
      <slot></slot>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  kicker: { type: String, default: '' },
  title: { type: String, default: '' },
  summary: { type: String, default: '' },
  size: { type: String, default: '480px' }
})

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
</script>

<style lang="scss">
.device-category-drawer.el-drawer {
  .el-drawer__header {
    align-items: flex-start;
    margin-bottom: 0;
    padding: 16px 20px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .el-drawer__close-btn {
    margin-top: 2px;
  }

  .el-drawer__body {
    padding: 16px 20px 24px;
    color: var(--el-text-color-primary);
    font-size: 14px;
    line-height: 22px;
  }

  .drawer-kicker {
    margin-bottom: 4px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    line-height: 18px;
  }

  .drawer-title-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    padding-right: 28px;
  }

  .drawer-title {
    color: var(--el-text-color-primary);
    font-size: 16px;
    font-weight: 600;
    line-height: 24px;
  }

  .drawer-summary {
    margin: 0 0 16px;
    padding: 10px 12px;
    border-radius: 6px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-regular);
    font-size: 13px;
    line-height: 22px;
  }

  .cat-block + .cat-block {
    margin-top: 20px;
  }

  .cat-kicker {
    margin-bottom: 8px;
    color: var(--el-color-primary);
    font-size: 12px;
    font-weight: 600;
    line-height: 18px;
  }

  h3.cat-title {
    margin: 0 0 8px;
    color: var(--el-text-color-primary);
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
  }

  p.cat-lead {
    margin: 0 0 10px;
    color: var(--el-text-color-regular);
    font-size: 13px;
    line-height: 22px;
  }

  dl.cat-facts {
    display: grid;
    grid-template-columns: 112px minmax(0, 1fr);
    margin: 0;
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
  }

  .cat-facts dt,
  .cat-facts dd {
    margin: 0;
    padding: 8px 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    font-size: 13px;
    font-weight: 400;
    line-height: 20px;
  }

  .cat-facts > :nth-last-child(-n + 2) {
    border-bottom: none;
  }

  .cat-facts dt {
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
  }

  .cat-facts dd {
    border-left: 1px solid var(--el-border-color-lighter);
    color: var(--el-text-color-primary);
    word-break: break-word;
  }

  .cat-facts .is-off {
    opacity: 0.55;
  }

  .facet,
  .param-card {
    margin-top: 8px;
    padding: 10px 12px;
    border-radius: 6px;
    background: var(--el-fill-color-light);
  }

  .facet.is-off {
    opacity: 0.55;
  }

  .facet-title,
  .param-name {
    color: var(--el-text-color-primary);
    font-size: 14px;
    font-weight: 600;
    line-height: 22px;
  }

  .facet-code {
    margin-left: 6px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
    font-weight: 400;
  }

  .facet-detail,
  .param-meta {
    display: block;
    margin-top: 2px;
    color: var(--el-text-color-secondary);
    font-size: 13px;
    line-height: 20px;
    text-align: left;
  }

  .mono {
    margin: 0;
    padding: 10px 12px;
    border-radius: 6px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-regular);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 12px;
    line-height: 20px;
    white-space: pre-wrap;
  }
}
</style>
