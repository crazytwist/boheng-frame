<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="命令" :title="command?.commandNo" size="560px">
    <template #tags>
      <el-tag v-if="command" :type="commandStatusType(command.status)" size="small">
        {{ commandStatusLabel(command.status) }}
      </el-tag>
    </template>
    <template v-if="command">
      <section class="cat-block">
        <div class="cat-kicker">调用</div>
        <dl class="cat-facts">
          <dt>设备</dt>
          <dd>{{ command.deviceCode || '—' }}</dd>
          <dt>动作</dt>
          <dd>{{ command.actionCode || '—' }}</dd>
          <dt>来源</dt>
          <dd>{{ command.sourceType || '—' }}</dd>
          <dt>解析规则</dt>
          <dd>{{ command.codecCode || '—' }}</dd>
          <dt>操作人</dt>
          <dd>{{ command.operator || '—' }}</dd>
        </dl>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">参数快照</div>
        <pre class="raw">{{ pretty(command.paramsJson) }}</pre>
      </section>
      <section v-if="command.errorMsg" class="cat-block">
        <div class="cat-kicker">说明</div>
        <pre class="raw">{{ command.errorMsg }}</pre>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">请求</div>
        <pre class="raw">{{ pretty(command.requestJson) }}</pre>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">响应 {{ command.rawFormat || '' }}</div>
        <pre class="raw">{{ pretty(command.rawBody || command.responseJson) }}</pre>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceCommandApi, DeviceCommandVO } from '@/api/device/command'
import CategoryDrawer from '../components/CategoryDrawer.vue'
import { commandStatusLabel, commandStatusType } from '../labels'

const visible = ref(false)
const loading = ref(false)
const command = ref<DeviceCommandVO>()

const pretty = (text?: string) => {
  if (!text) return '—'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  command.value = undefined
  try {
    command.value = await DeviceCommandApi.getCommand(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.raw {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}
</style>
