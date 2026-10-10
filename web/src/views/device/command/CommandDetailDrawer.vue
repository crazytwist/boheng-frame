<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="命令记录" :title="command?.commandNo" size="560px">
    <template #tags>
      <el-tag v-if="command" :type="commandStatusType(command.status)" size="small">
        {{ commandStatusLabel(command.status) }}
      </el-tag>
    </template>
    <template v-if="command">
      <section class="cat-block">
        <div class="cat-kicker">基本信息</div>
        <dl class="cat-facts">
          <dt>设备编码</dt>
          <dd>{{ command.deviceCode || '—' }}</dd>
          <dt>动作编码</dt>
          <dd>{{ command.actionCode || '—' }}</dd>
          <dt>命令来源</dt>
          <dd>{{ command.sourceType || '—' }}</dd>
          <dt>结果获取方式</dt>
          <dd>{{ resultModeLabel(command.resultMode) }}</dd>
          <template v-if="command.resultMode === 'CALLBACK'">
            <dt>下发时间</dt>
            <dd>{{ formatDate(command.sentAt) || '—' }}</dd>
            <dt>回调截止时间</dt>
            <dd>{{ formatDate(command.timeoutAt) || '—' }}</dd>
            <dt>回调时间</dt>
            <dd>{{ command.status === 'SUCCEEDED' || command.status === 'FAILED' ? formatDate(command.finishedAt) : '—' }}</dd>
          </template>
          <dt>解析规则</dt>
          <dd>{{ command.codecCode || '—' }}</dd>
          <dt>操作人</dt>
          <dd>{{ command.operator || '—' }}</dd>
        </dl>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">调用参数</div>
        <pre class="raw">{{ pretty(command.paramsJson) }}</pre>
      </section>
      <section v-if="command.errorMsg" class="cat-block">
        <div class="cat-kicker">错误信息</div>
        <pre class="raw">{{ command.errorMsg }}</pre>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">{{ command.resultMode === 'CALLBACK' ? '请求报文与受理应答' : '请求报文' }}</div>
        <pre class="raw">{{ pretty(command.requestJson) }}</pre>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">{{ command.resultMode === 'CALLBACK' ? '回调报文' : '响应报文' }} {{ command.rawFormat || '' }}</div>
        <pre class="raw">{{ pretty(command.rawBody || command.responseJson) }}</pre>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceCommandApi, DeviceCommandVO } from '@/api/device/command'
import CategoryDrawer from '../components/CategoryDrawer.vue'
import { formatDate } from '@/utils/formatTime'
import { commandStatusLabel, commandStatusType, resultModeLabel } from '../labels'

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
