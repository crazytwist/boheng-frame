<template>
  <CategoryDrawer
    v-model="visible"
    :loading="loading"
    kicker="设备台账"
    :title="device?.deviceName || device?.deviceCode"
    :summary="portrait?.dispatchSummary"
  >
    <template #tags>
      <el-tag v-if="device" :type="statusTagType(device.status)" size="small">{{ statusLabel(device.status) }}</el-tag>
      <el-tag v-if="device?.simulationMode" type="warning" size="small">仿真</el-tag>
    </template>

    <template v-if="device">
      <section class="cat-block">
        <div class="cat-kicker">身份</div>
        <h3 class="cat-title">这是哪一台</h3>
        <p class="cat-lead">
          {{ deviceTypeLabel(device.deviceTypeCode) }}
          <template v-if="device.vendor || device.model">
            ，{{ [device.vendor, device.model].filter(Boolean).join(' ') }}
          </template>
          。跨模块只认编码 {{ device.deviceCode }}。
        </p>
        <dl class="cat-facts">
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
        <h3 class="cat-title">结果从哪进来</h3>
        <p class="cat-lead">{{ portrait?.connectionSummary }}</p>
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
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">占用</div>
        <h3 class="cat-title">现在能不能用</h3>
        <p class="cat-lead">{{ portrait?.occupancySummary }}</p>
        <dl v-if="device.lockHolder || device.lockReason" class="cat-facts">
          <dt>锁类型</dt>
          <dd>{{ lockTypeLabel(device.lockType) || '未标明' }}</dd>
          <dt>原因</dt>
          <dd>{{ device.lockReason || '未填写' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">动作</div>
        <h3 class="cat-title">这类设备能被怎么启动</h3>
        <p v-if="!portrait?.actions?.length" class="cat-lead">还没有为 {{ deviceTypeLabel(device.deviceTypeCode) }} 配置动作。</p>
        <div v-for="item in portrait?.actions" :key="item.code" class="facet" :class="{ 'is-off': item.active === false }">
          <div>
            <span class="facet-title">{{ item.title }}</span>
            <span class="facet-code">{{ item.code }}</span>
          </div>
          <span class="facet-detail">{{ item.detail }}</span>
        </div>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">遥测</div>
        <h3 class="cat-title">能观测到什么</h3>
        <p v-if="!portrait?.properties?.length" class="cat-lead">还没有可观测属性。</p>
        <div v-for="item in portrait?.properties" :key="item.code" class="facet" :class="{ 'is-off': item.active === false }">
          <div>
            <span class="facet-title">{{ item.title }}</span>
            <span class="facet-code">{{ item.code }}</span>
          </div>
          <span class="facet-detail">{{ item.detail }}</span>
        </div>
        <template v-if="telemetry.length">
          <p class="cat-lead" style="margin-top: 12px">最近一次快照</p>
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
        <h3 class="cat-title">可以直接拿去下发的预设</h3>
        <div v-for="item in portrait.paramSets" :key="item.title" class="facet" :class="{ 'is-off': item.active === false }">
          <div>
            <span class="facet-title">{{ item.title }}</span>
            <span class="facet-code">{{ item.code }}</span>
          </div>
          <span class="facet-detail">{{ item.detail }}</span>
        </div>
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
