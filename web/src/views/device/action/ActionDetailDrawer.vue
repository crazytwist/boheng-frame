<template>
  <CategoryDrawer v-model="visible" :loading="loading" kicker="设备动作" :title="action?.actionName">
    <template #tags>
      <el-tag v-if="action" :type="action.status === 0 ? 'success' : 'info'" size="small">
        {{ action.status === 0 ? '启用' : '停用' }}
      </el-tag>
    </template>

    <template v-if="action">
      <section class="cat-block">
        <div class="cat-kicker">基本信息</div>
        <dl class="cat-facts">
          <dt>设备类型</dt>
          <dd>{{ deviceTypeLabel(action.deviceTypeCode) }}</dd>
          <dt>动作编码</dt>
          <dd>{{ action.actionCode }}</dd>
          <dt>能力来源</dt>
          <dd>{{ sourceLabel(action.source) }}</dd>
          <dt>预估耗时</dt>
          <dd>{{ durationText(action.estimateDurationMs) }}</dd>
          <dt>SiLA 标准特性</dt>
          <dd>{{ action.standardFeature || '—' }}</dd>
          <dt>厂商映射</dt>
          <dd>{{ action.vendorRef || '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">请求配置</div>
        <dl class="cat-facts">
          <dt>请求方法</dt>
          <dd>{{ action.httpMethod || '—' }}</dd>
          <dt>请求路径</dt>
          <dd>{{ action.requestPath || '—' }}</dd>
          <dt>报文格式</dt>
          <dd>{{ formatLabel(action.bodyFormat) }}</dd>
        </dl>
        <p v-if="action.requestTemplate" class="mono">{{ action.requestTemplate }}</p>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">结果处理</div>
        <dl class="cat-facts">
          <dt>结果获取方式</dt>
          <dd>{{ resultModeLabel(action.resultMode || 'SYNC_RETURN') }}</dd>
          <template v-if="action.resultMode === 'CALLBACK'">
            <dt>回调超时</dt>
            <dd>{{ action.callbackTimeoutSec ? `${action.callbackTimeoutSec} 秒` : '取设备配置' }}</dd>
          </template>
          <dt>解析规则</dt>
          <dd>{{ action.codecCode || '—' }}</dd>
          <dt>完成判定表达式</dt>
          <dd>{{ action.pollDoneExpr || '—' }}</dd>
          <dt>最大轮询次数</dt>
          <dd>{{ action.pollMaxTimes ?? '—' }}</dd>
        </dl>
      </section>

      <section class="cat-block">
        <div class="cat-kicker">下发前检查</div>
        <dl class="cat-facts">
          <dt>空闲检查</dt>
          <dd>{{ action.needBusyCheck ? '需要' : '不需要' }}</dd>
          <template v-if="action.needBusyCheck">
            <dt>状态查询动作</dt>
            <dd>{{ action.statusCommandCode || '—' }}</dd>
          </template>
        </dl>
      </section>

      <section v-if="fields.length" class="cat-block">
        <div class="cat-kicker">参数定义</div>
        <div v-for="field in fields" :key="field.key" class="param-card">
          <div class="param-name">{{ field.label }}</div>
          <div class="param-meta">
            {{ field.type || '未声明类型' }}
            <template v-if="field.unit"> · {{ field.unit }}</template>
            · {{ field.required ? '必填' : '选填' }}
            <template v-if="field.defaultValue !== undefined && field.defaultValue !== null">
              · 默认 {{ field.defaultValue }}
            </template>
            <template v-if="constraintText(field.constraints)"> · {{ constraintText(field.constraints) }}</template>
          </div>
        </div>
      </section>
      <section class="cat-block">
        <div class="cat-kicker">绑定参数集</div>
        <div v-if="paramSets.length">
          <div v-for="item in paramSets" :key="item.id" class="param-card">
            <div class="param-name">{{ item.paramSetName }} <el-tag size="small" :type="item.validated ? 'success' : 'info'">{{ item.validated ? '已验证' : '未验证' }}</el-tag></div>
            <div class="param-meta">{{ item.paramSetCode }} · {{ item.status === 0 ? '启用' : '停用' }}</div>
          </div>
        </div>
        <div v-else class="empty-text">暂未绑定参数集</div>
      </section>
    </template>
  </CategoryDrawer>
</template>

<script lang="ts" setup>
import { DeviceActionApi, DeviceActionVO } from '@/api/device/action'
import { DeviceParamSetApi, DeviceParamSetVO } from '@/api/device/paramSet'
import CategoryDrawer from '../components/CategoryDrawer.vue'
import { constraintText, deviceTypeLabel, durationText, parseSchema, resultModeLabel, sourceLabel } from '../labels'

const visible = ref(false)
const loading = ref(false)
const action = ref<DeviceActionVO>()
const paramSets = ref<DeviceParamSetVO[]>([])

const fields = computed(() => parseSchema(action.value?.paramSchema))

const formatLabel = (value?: string) =>
  ({ JSON: 'JSON', FORM: '表单', TEXT: '纯文本', XML: 'XML' }[value || ''] || value || 'JSON')

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  action.value = undefined
  paramSets.value = []
  try {
    action.value = await DeviceActionApi.getAction(id)
    if (action.value?.deviceTypeCode && action.value?.actionCode) {
      const list = await DeviceParamSetApi.getParamSetList({ deviceTypeCode: action.value.deviceTypeCode, actionCode: action.value.actionCode })
      paramSets.value = list || []
    }
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>
