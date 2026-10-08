<template>
  <CategoryDrawer
    v-model="visible"
    :loading="loading"
    kicker="设备台账"
    :title="device?.deviceName || device?.deviceCode"
  >
    <template #tags>
      <el-tag v-if="device" :type="statusTagType(device.status)" size="small">{{ statusLabel(device.status) }}</el-tag>
      <el-tag v-if="device?.simulationMode" type="warning" size="small">仿真</el-tag>
    </template>

    <template v-if="device">
      <section class="cat-block">
        <div class="cat-kicker">身份</div>
        <dl class="cat-facts">
          <dt>编码</dt>
          <dd>{{ device.deviceCode }}</dd>
          <dt>类型</dt>
          <dd>{{ deviceTypeLabel(device.deviceTypeCode) }}</dd>
          <template v-if="device.vendor">
            <dt>厂商</dt>
            <dd>{{ device.vendor }}</dd>
          </template>
          <template v-if="device.model">
            <dt>型号</dt>
            <dd>{{ device.model }}</dd>
          </template>
          <dt>序列号</dt>
          <dd>{{ device.serialNo || '未登记' }}</dd>
          <dt>驱动</dt>
          <dd>{{ device.driverType || '未指定' }}</dd>
          <dt>能力来源</dt>
          <dd>{{ sourceLabel(device.capabilitySource) }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">接入</div>
        <dl class="cat-facts">
          <dt>方式</dt>
          <dd>{{ connectionLabel(device.connectionType) }}</dd>
          <template v-if="device.connectionType === 'HTTP' || device.connectionType === 'NODE_RED'">
            <dt>地址</dt>
            <dd>{{ device.endpointUrl || '未填写' }}</dd>
          </template>
          <template v-if="device.connectionType === 'MQTT'">
            <dt>主题前缀</dt>
            <dd>{{ device.mqttTopicPrefix || '未填写' }}</dd>
          </template>
          <template v-if="device.loginPath">
            <dt>登录</dt>
            <dd>{{ device.loginMethod || 'POST' }} {{ device.loginPath }}</dd>
            <dt>账号</dt>
            <dd>{{ device.loginUsername || '未填写' }}</dd>
          </template>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">占用</div>
        <dl class="cat-facts">
          <dt>状态</dt>
          <dd>{{ occupancyStatus }}</dd>
          <template v-if="occupied">
            <dt>持有方</dt>
            <dd>{{ device.lockHolder || '未记录' }}</dd>
            <dt>锁类型</dt>
            <dd>{{ lockTypeLabel(device.lockType) || '未标明' }}</dd>
            <dt>原因</dt>
            <dd>{{ device.lockReason || '未填写' }}</dd>
          </template>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">动作</div>
        <dl v-if="portrait?.actions?.length" class="cat-facts">
          <template v-for="item in portrait.actions" :key="item.code">
            <dt :class="{ 'is-off': item.active === false }">{{ item.title }}</dt>
            <dd :class="{ 'is-off': item.active === false }">{{ item.code }}</dd>
          </template>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">遥测</div>
        <dl v-if="portrait?.properties?.length" class="cat-facts">
          <template v-for="item in portrait.properties" :key="item.code">
            <dt :class="{ 'is-off': item.active === false }">{{ item.title }}</dt>
            <dd :class="{ 'is-off': item.active === false }">{{ item.code }}</dd>
          </template>
        </dl>
        <template v-if="telemetry.length">
          <div class="cat-kicker snapshot-kicker">快照</div>
          <dl class="cat-facts">
            <template v-for="item in telemetry" :key="item.key">
              <dt>{{ item.key }}</dt>
              <dd>{{ item.value }}</dd>
            </template>
          </dl>
        </template>
      </section>

      <section v-if="portrait?.paramSets?.length" class="cat-block">
        <div class="cat-kicker">参数集</div>
        <dl class="cat-facts">
          <template v-for="item in portrait.paramSets" :key="item.title">
            <dt :class="{ 'is-off': item.active === false }">{{ item.code || '—' }}</dt>
            <dd :class="{ 'is-off': item.active === false }">{{ item.title }}</dd>
          </template>
        </dl>
      </section>

      <section v-if="device.remark" class="cat-block">
        <div class="cat-kicker">备注</div>
        <p class="cat-lead">{{ device.remark }}</p>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceInfoApi, DevicePortrait, DeviceVO } from '@/api/device'
import CategoryDrawer from './components/CategoryDrawer.vue'
import { connectionLabel, deviceTypeLabel, lockTypeLabel, parseParams, sourceLabel, statusLabel, statusTagType } from './labels'

const visible = ref(false)
const loading = ref(false)
const portrait = ref<DevicePortrait>()
const device = computed(() => portrait.value?.device as DeviceVO | undefined)
const telemetry = computed(() => parseParams(device.value?.telemetryJson))
const occupied = computed(() => !!(device.value?.lockHolder || device.value?.currentCommandId))
const occupancyStatus = computed(() => {
  const row = device.value
  if (!row || !occupied.value) return '空闲'
  const expire = row.lockExpireTime ? new Date(row.lockExpireTime).getTime() : 0
  if (expire && expire < Date.now()) return '已过期'
  return '占用中'
})

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  portrait.value = undefined
  try {
    portrait.value = await DeviceInfoApi.getDevicePortrait(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.snapshot-kicker {
  margin-top: 12px;
}
</style>
