<!--
  节点级运行配置。落 flow_node.run_config，是「这一步怎么算跑完」的唯一定义处。

  设计取舍：
  · 四个字段都允许「继承」——留空即继承步骤表上的值，节点只在需要时偏离。
  · 提交时留空统一送 0 / 空串（表示清除覆盖），不送 undefined，
    否则后端分不清「没打算改」和「想恢复继承」。
  · 完成断言在这里只做轻量提示，真正的语法校验在后端保存时做，报错更权威。
-->
<template>
  <div class="node-run-config">
    <div class="nrc-tip">
      留空 = 继承步骤上的配置。这里只覆盖需要偏离的项，不影响其它节点。
    </div>

    <div class="nrc-grid">
      <div class="nrc-item">
        <label class="nrc-label">
          超时
          <el-tooltip placement="top">
            <template #content>
              这一步最多等多久。<br />
              设备类节点建议不小于设备动作本身的最长耗时，<br />
              否则流程侧会先判超时，而设备其实还在跑。
            </template>
            <span class="nrc-help">?</span>
          </el-tooltip>
        </label>
        <el-input-number
          :model-value="timeoutSec ?? undefined"
          :min="0"
          :max="86400"
          :step="30"
          controls-position="right"
          placeholder="继承"
          class="!w-1/1"
          @update:model-value="(v: number | undefined) => emit('update:timeoutSec', v ?? null)"
        />
        <span class="nrc-hint">{{ inheritedTime }}</span>
      </div>

      <div class="nrc-item">
        <label class="nrc-label">
          最大尝试
          <el-tooltip placement="top">
            <template #content>
              含首次。失败后是否重试、重试几次。<br />
              非幂等的物理动作（开盖、移液）建议保持 1，<br />
              否则重试会把动作叠加执行。
            </template>
            <span class="nrc-help">?</span>
          </el-tooltip>
        </label>
        <el-input-number
          :model-value="maxAttempts ?? undefined"
          :min="0"
          :max="10"
          :step="1"
          controls-position="right"
          placeholder="继承"
          class="!w-1/1"
          @update:model-value="(v: number | undefined) => emit('update:maxAttempts', v ?? null)"
        />
        <span class="nrc-hint">{{ inheritedAttempts }}</span>
      </div>
    </div>

    <div class="nrc-item">
      <label class="nrc-label">
        失败策略
        <el-tooltip placement="top">
          <template #content>
            FAIL_FAST：本节点失败即把整条流程判失败。<br />
            FAIL_SKIP：本节点失败时裁掉它的下游，其余分支照常推进。
          </template>
          <span class="nrc-help">?</span>
        </el-tooltip>
      </label>
      <el-select
        :model-value="failureStrategy ?? undefined"
        placeholder="继承（默认 FAIL_FAST）"
        clearable
        class="!w-1/1"
        @update:model-value="(v: string | undefined) => emit('update:failureStrategy', v ?? null)"
      >
        <el-option label="快速失败（FAIL_FAST）" value="FAIL_FAST" />
        <el-option label="跳过下游（FAIL_SKIP）" value="FAIL_SKIP" />
      </el-select>
    </div>

    <div class="nrc-item">
      <label class="nrc-label">
        完成断言
        <el-tooltip placement="top">
          <template #content>
            执行器报「成功」之后，再过这一道判断。<br />
            不成立按失败结算，并且复用上面的重试次数。<br />
            写法：output.weight &gt;= 0.95、params.mode == 'STRICT'<br />
            可用作用域：output.* / params.* / node.&lt;节点编码&gt;.* / input.*
          </template>
          <span class="nrc-help">?</span>
        </el-tooltip>
        <el-tag v-if="completeWhen" type="warning" size="small" class="!mr-0">已设断言</el-tag>
      </label>
      <el-input
        :model-value="completeWhen ?? ''"
        type="textarea"
        :rows="2"
        maxlength="1024"
        show-word-limit
        placeholder="留空 = 执行器报成功即成功。例：output.weight >= 0.95"
        @update:model-value="(v: string) => emit('update:completeWhen', v)"
      />
      <span class="nrc-hint">
        留空时，设备回报的任何成功都会被当作完成——这是设备「假成功」最常见的漏点。
      </span>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue'

defineOptions({ name: 'NodeRunConfigForm' })

const props = defineProps<{
  /** 节点级超时秒数（覆盖值）。null = 继承 */
  timeoutSec?: number | null
  /** 节点级尝试次数（覆盖值）。null = 继承 */
  maxAttempts?: number | null
  /** 失败策略覆盖值。null = 继承 */
  failureStrategy?: string | null
  /** 完成断言。空 = 不校验 */
  completeWhen?: string | null
  /** 步骤表上的超时秒数，用于占位提示 */
  effectiveTimeoutSec?: number | null
  /** 步骤表上的尝试次数，用于占位提示 */
  effectiveMaxAttempts?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:timeoutSec', v: number | null): void
  (e: 'update:maxAttempts', v: number | null): void
  (e: 'update:failureStrategy', v: string | null): void
  (e: 'update:completeWhen', v: string | null): void
}>()

const inheritedTime = computed(() =>
  props.timeoutSec
    ? '已覆盖步骤上的设置'
    : props.effectiveTimeoutSec
      ? `继承：${props.effectiveTimeoutSec} 秒`
      : '继承：未设置（由执行器决定）'
)

const inheritedAttempts = computed(() =>
  props.maxAttempts ? '已覆盖步骤上的设置' : `继承：${props.effectiveMaxAttempts ?? 1} 次`
)
</script>

<style lang="scss" scoped>
.node-run-config {
  width: 100%;
}

.nrc-tip {
  margin-bottom: 10px;
  padding: 6px 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.nrc-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.nrc-item {
  margin-bottom: 12px;
}

.nrc-label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.nrc-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 50%;
  color: var(--el-text-color-placeholder);
  font-size: 10px;
  cursor: help;
}

.nrc-hint {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 11px;
  line-height: 1.5;
}
</style>
