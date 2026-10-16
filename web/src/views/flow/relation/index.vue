<template>
  <ContentWrap title="流程节点编排">
    <!-- 搜索 -->
    <el-form class="-mb-15px" :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
      <el-form-item label="流程" prop="definitionId">
        <el-select v-model="queryParams.definitionId" placeholder="请选择流程" clearable filterable class="!w-280px">
          <el-option
            v-for="item in definitions"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain @click="openCreate" v-hasPermi="['flow:relation:create']" :disabled="!canEdit">
          <Icon icon="ep:plus" class="mr-5px" /> 添加节点
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <!-- 流程上下文条：进入节点编排页面时，让用户一眼知道在为哪个流程的哪一版工作 -->
  <ContentWrap v-if="currentDefinition" class="relation-context">
    <div class="flex items-center gap-15px flex-wrap">
      <div class="text-13px color-#909399">正在为</div>
      <div class="text-15px font-bold">{{ currentDefinition.name }}</div>
      <el-tag size="small" :type="statusTag(currentDefinition.status)">{{ statusLabel(currentDefinition.status) }}</el-tag>
      <div class="text-13px color-#909399">v{{ currentDefinition.version }} · {{ list.length }} 个节点</div>
      <div class="ml-auto flex items-center gap-10px">
        <el-button link type="primary" @click="goValidate" :loading="validating">校验</el-button>
        <el-button v-hasPermi="['flow:definition:query']" link type="primary" @click="goBackToList">返回定义</el-button>
      </div>
    </div>
  </ContentWrap>

  <!-- 列表 -->
  <ContentWrap>
    <el-table v-loading="loading" :data="list" :row-class-name="rowClassName">
      <template #empty>
        <el-empty :description="queryParams.definitionId ? '该流程还没有节点' : '先在上方选择流程'">
          <el-button v-if="queryParams.definitionId" type="primary" @click="openCreate" v-hasPermi="['flow:relation:create']" :disabled="!canEdit">添加节点</el-button>
        </el-empty>
      </template>      <el-table-column label="流程编码" align="center" prop="pipelineKey" min-width="110" :show-overflow-tooltip="true" />
      <el-table-column label="版本" align="center" width="70">
        <template #default="scope">v{{ scope.row.version }}</template>
      </el-table-column>
      <el-table-column label="序" align="center" width="50">
        <template #default="scope">{{ (scope.row.sortNo ?? 0) + 1 }}</template>
      </el-table-column>
      <el-table-column label="节点编码" align="center" prop="nodeId" min-width="110" />
      <el-table-column label="名称" align="center" prop="name" min-width="120" :show-overflow-tooltip="true" />
      <el-table-column label="类型" align="center" min-width="110">
        <template #default="scope">
          <el-tag :type="isControl(scope.row.stepType) ? 'warning' : 'primary'">
            {{ typeLabel(scope.row.stepType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="前置" align="center" min-width="150" :show-overflow-tooltip="true">
        <template #default="scope">{{ dependsLabel(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="汇合" align="center" min-width="110">
        <template #default="scope">{{ joinLabel(scope.row.joinPolicy) }}</template>
      </el-table-column>
      <el-table-column label="等待(秒)" align="center" prop="timeoutSec" width="90">
        <template #default="scope">{{ scope.row.timeoutSec < 0 ? '永不超时' : scope.row.timeoutSec }}</template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="220" fixed="right">
        <template #default="scope">
          <el-button v-hasPermi="['flow:relation:update']" link type="primary" :disabled="scope.row.definitionStatus !== 'DRAFT'" @click="openEdit(scope.row)">
            编排
          </el-button>
          <el-button
            v-if="scope.row.definitionStatus === 'DRAFT'"
            v-hasPermi="['flow:relation:update']"
            link
            type="primary"
            :disabled="scope.row.sortNo === 0"
            @click="move(scope.row.id, -1)"
          >
            上移
          </el-button>
          <el-button
            v-if="scope.row.definitionStatus === 'DRAFT'"
            v-hasPermi="['flow:relation:delete']"
            link
            type="danger"
            @click="handleDelete(scope.row.id)"
          >
            移除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" @pagination="getList" />
  </ContentWrap>

  <!-- 添加节点：挂能力 或 控制类节点 -->
  <Dialog v-model="createVisible" title="添加节点" width="560px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
      <el-form-item label="流程" prop="definitionId">
        <el-select v-model="createForm.definitionId" filterable placeholder="请选择草稿" class="!w-1/1">
          <el-option
            v-for="item in drafts"
            :key="item.id"
            :label="`${item.name}（${item.pipelineKey} v${item.version}）`"
            :value="item.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="节点形式">
        <el-radio-group v-model="createForm.mode">
          <el-radio value="step">挂能力（步骤）</el-radio>
          <el-radio value="control">控制类节点</el-radio>
        </el-radio-group>
      </el-form-item>
      <template v-if="createForm.mode === 'step'">
        <el-form-item label="步骤" prop="stepId">
          <el-select v-model="createForm.stepId" filterable placeholder="请选择已有步骤" class="!w-1/1">
            <el-option
              v-for="item in steps"
              :key="item.id"
              :label="`${item.name}（${item.stepKey}·${typeLabel(item.stepType)}）`"
              :value="item.id!"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="节点编码">
          <el-input :model-value="autoNodeId" disabled placeholder="选完步骤后自动生成" />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item label="节点编码" prop="nodeId">
          <el-input v-model="createForm.nodeId" maxlength="32" placeholder="小写字母开头，如 route_od" />
        </el-form-item>
        <el-form-item label="节点名称" prop="name">
          <el-input v-model="createForm.name" maxlength="128" placeholder="如 按 OD 值路由" />
        </el-form-item>
        <el-form-item label="节点类型" prop="stepType">
          <el-select v-model="createForm.stepType" placeholder="请选择控制类型" class="!w-1/1">
            <el-option v-for="item in CONTROL_TYPES" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <template v-if="createTypeFields.length">
          <SchemaForm :fields="createTypeFields" v-model="createForm.typeConfig" />
        </template>
        <el-form-item v-else label="类型配置" prop="typeConfig">
          <el-input
            v-model="createForm.typeConfig"
            type="textarea"
            :rows="4"
            maxlength="4096"
            :placeholder="controlPlaceholder"
          />
        </el-form-item>
      </template>
      <el-form-item label="前置节点">
        <el-select v-model="createForm.dependsOn" multiple filterable placeholder="空 = 起点" class="!w-1/1">
          <el-option
            v-for="opt in nodeOptions(createForm.definitionId)"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取 消</el-button>
      <el-button type="primary" :loading="creating" @click="submitCreate">确 定</el-button>
    </template>
  </Dialog>

  <!-- 编排编辑：前置 / 汇合 / 分支 / 配置 / 参数映射 -->
  <Dialog v-model="editVisible" title="节点编排" width="720px">
    <el-form :model="editForm" label-width="100px" v-loading="editLoading">
      <el-form-item label="节点">
        <span>{{ editForm.nodeId }}（{{ typeLabel(editForm.stepType) }}）</span>
      </el-form-item>
      <el-form-item v-if="editForm.isControl" label="节点名称" required>
        <el-input v-model="editForm.name" maxlength="128" />
      </el-form-item>
      <el-form-item>
        <template #label>
          <Tooltip
            title="前置节点"
            message="上游节点的节点编码。空 = 起点（流程入口）。多选 = 多路汇合到本节点。决定执行顺序与参数映射可引用的范围。"
          />
        </template>
        <el-select v-model="editForm.dependsOn" multiple filterable placeholder="空 = 起点" class="!w-1/1">
          <el-option
            v-for="opt in editNodeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label>
          <Tooltip
            title="汇合策略"
            message="多路前置汇合到本节点时，何时视为「汇合完成、可以执行本节点」。ALL_SUCCESS=全部成功才进；ANY_SUCCESS=任一成功即进；ALL_DONE=全部进入终态才进（成功/失败都算）。单前置不能选 ANY_SUCCESS。"
          />
        </template>
        <el-select v-model="editForm.joinPolicy" clearable placeholder="默认 ALL_SUCCESS" class="!w-1/1">
          <el-option label="全部成功（ALL_SUCCESS）" value="ALL_SUCCESS" />
          <el-option label="任一成功（ANY_SUCCESS）" value="ANY_SUCCESS" />
          <el-option label="全部终态（ALL_DONE）" value="ALL_DONE" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <template #label>
          <Tooltip
            title="失败策略"
            message="前置里有节点失败了，本节点要不要继续被触发。FAIL=本节点一起失败；SKIP=把本节点裁掉，下游看本节点按 SKIP 处理。"
          />
        </template>
        <el-select v-model="editForm.joinFailurePolicy" clearable placeholder="默认 FAIL" class="!w-1/1">
          <el-option label="跟着失败（FAIL）" value="FAIL" />
          <el-option label="裁剪本节点（SKIP）" value="SKIP" />
        </el-select>
      </el-form-item>
      <template v-if="editTypeFields.length">
        <SchemaForm :fields="editTypeFields" v-model="editForm.typeConfig" />
      </template>
      <el-form-item v-else :label="editForm.isControl ? '类型配置' : '配置覆盖'">
        <el-input
          v-model="editForm.typeConfig"
          type="textarea"
          :rows="4"
          maxlength="4096"
          :placeholder="editForm.isControl ? '控制类节点必填' : '选填。节点级覆盖步骤上的配置 JSON'"
        />
      </el-form-item>
      <!-- route 分支出口 -->
      <el-form-item v-if="editForm.stepType === 'route'" label="分支出口">
        <div class="w-1/1">
          <div v-for="(branch, index) in editForm.branches" :key="index" class="flex items-center gap-5px mb-5px">
            <el-input v-model="branch.key" maxlength="32" placeholder="出口名" class="!w-120px" />
            <el-select v-model="branch.targets" multiple filterable placeholder="目标节点" class="!flex-1">
              <el-option
                v-for="opt in editNodeOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-button link type="danger" @click="editForm.branches.splice(index, 1)">删</el-button>
          </div>
          <el-button link type="primary" @click="editForm.branches.push({ key: '', targets: [] })">加出口</el-button>
        </div>
      </el-form-item>
      <!-- 步骤声明的参数：表单直改（生成固定值覆盖，标橙）或 JSON 预览 -->
      <el-form-item v-if="declaredParams.length" label="步骤入参">
        <div class="w-1/1 declared-params">
          <div class="declared-toolbar">
            <span class="declared-hint">表单视图下直接改值即生成固定值覆盖，改动标橙；JSON 为生效预览</span>
            <el-radio-group v-model="paramView" size="small">
              <el-radio-button value="form">表单</el-radio-button>
              <el-radio-button value="json">JSON</el-radio-button>
            </el-radio-group>
          </div>
          <!-- 表单视图：改值即替换 -->
          <template v-if="paramView === 'form'">
            <div v-for="item in declaredParams" :key="item.key" class="declared-row">
              <el-tooltip :content="item.comment" :disabled="!item.comment" placement="top">
                <span class="declared-key" :class="{ 'has-comment': item.comment }">{{ item.key }}</span>
              </el-tooltip>
              <!-- 已被 INPUT/NODE 动态映射的参数：行内替换会打架，只读展示 -->
              <template v-if="isDynamicMapped(item.key)">
                <span class="declared-value">{{ item.text }}</span>
                <el-tag type="success" size="small" class="!mr-0">动态映射</el-tag>
              </template>
              <template v-else>
                <el-input
                  :model-value="effectiveValue(item)"
                  maxlength="512"
                  class="declared-input"
                  :class="{ 'is-overridden': isReplaced(item.key) }"
                  @update:model-value="(v: string) => replaceParam(item, v)"
                />
                <el-tag :type="isReplaced(item.key) ? 'warning' : 'info'" size="small" class="!mr-0">
                  {{ isReplaced(item.key) ? '已替换' : '默认值' }}
                </el-tag>
                <el-button v-if="isReplaced(item.key)" link type="primary" @click="revertParam(item)">还原</el-button>
              </template>
            </div>
          </template>
          <!-- JSON 视图：默认入参 + 固定值覆盖合并后的生效预览 -->
          <pre v-else class="declared-json">{{ effectiveParamsJson }}</pre>
        </div>
      </el-form-item>
      <!-- 参数映射 -->
      <el-form-item label="参数映射">
        <div class="w-1/1">
          <div v-for="(mapping, index) in editForm.mappings" :key="index" class="flex items-center gap-5px mb-5px">
            <el-select
              v-if="declaredParams.length"
              v-model="mapping.param"
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="参数名"
              class="!w-140px"
            >
              <el-option
                v-for="item in declaredParams"
                :key="item.key"
                :label="`${item.key} = ${item.text}`"
                :value="item.key"
              />
            </el-select>
            <el-input v-else v-model="mapping.param" maxlength="64" placeholder="参数名" class="!w-100px" />
            <el-select v-model="mapping.source" class="!w-90px">
              <el-option label="入参" value="INPUT" />
              <el-option label="节点" value="NODE" />
              <el-option label="固定" value="LITERAL" />
            </el-select>
            <!-- NODE 源先选前置节点、再选字段：字段下拉依赖节点选择才有候选 -->
            <el-select
              v-if="mapping.source === 'NODE'"
              v-model="mapping.nodeId"
              filterable
              placeholder="前置节点"
              class="!w-180px"
              @change="mapping.field = undefined"
            >
              <el-option
                v-for="opt in editForm.dependsOn.map(toNodeOption)"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-select
              v-if="mapping.source !== 'LITERAL'"
              v-model="mapping.field"
              filterable
              allow-create
              default-first-option
              :placeholder="mapping.source === 'NODE' && !mapping.nodeId ? '先选前置节点' : '字段/路径'"
              class="!w-150px"
            >
              <el-option
                v-for="f in knownFields(mapping.source, mapping.source === 'NODE' ? mapping.nodeId : undefined)"
                :key="f.value"
                :label="f.label"
                :value="f.value"
              />
            </el-select>
            <el-input v-if="mapping.source === 'LITERAL'" v-model="mapping.value" maxlength="512" placeholder="固定值" class="!w-120px" />
            <el-button link type="danger" @click="editForm.mappings.splice(index, 1)">删</el-button>
          </div>
          <el-button link type="primary" @click="editForm.mappings.push({ param: '', source: 'INPUT' })">加映射</el-button>
        </div>
      </el-form-item>
      <!-- 输出映射：把节点输出字段归一化到上下文键 -->
      <el-form-item label="输出映射">
        <div class="w-1/1">
          <div v-for="(row, index) in editForm.outputMapping" :key="index" class="flex items-center gap-5px mb-5px">
            <el-input v-model="row.key" maxlength="64" placeholder="输出字段" class="!w-160px" />
            <el-input v-model="row.value" maxlength="64" placeholder="上下文键（默认同字段名）" class="!flex-1" />
            <el-button link type="danger" @click="editForm.outputMapping.splice(index, 1)">删</el-button>
          </div>
          <el-button link type="primary" @click="editForm.outputMapping.push({ key: '', value: '' })">加输出</el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取 消</el-button>
      <el-button type="primary" :loading="editing" @click="submitEdit">确 定</el-button>
    </template>
  </Dialog>
  <FlowIssuePanel ref="issueRef" />
</template>

<script lang="ts" setup>
import { FlowDefinitionApi, FlowDefinitionVO } from '@/api/flow/definition'
import { FlowRelationApi, FlowRelationVO } from '@/api/flow/relation'
import { FlowStepApi, FlowStepVO, STEP_TYPES } from '@/api/flow/step'
import { STEP_TYPE_FIELDS } from '@/api/flow/stepSchema'
import SchemaForm from '@/components/flow/SchemaForm.vue'
import FlowIssuePanel from '@/components/flow/FlowIssuePanel.vue'

defineOptions({ name: 'FlowRelation' })

/** 控制类节点类型：只能在流程节点上配置，不进能力目录 */
const CONTROL_TYPES = [
  { value: 'route', label: '出口路由' },
  { value: 'loop', label: '循环容器' },
  { value: 'subflow', label: '子流程' },
  { value: 'timer.wait', label: '定时等待' }
] as const

const CONTROL_PLACEHOLDERS: Record<string, string> = {
  route: '{"router":{"kind":"expression","expression":"input.od > 1.0 ? \'high\' : \'default\'"},"branches":{"high":["alarm"],"default":["read"]}}',
  loop: '{"bodyEntryNodeId":"read","bodyExitNodeId":"wash","maxIterations":5,"whileExpression":"sys.needMore == true"}',
  subflow: '{"dispatchMode":"SINGLE","pipelineKey":"elisa-assay","inputJson":"{}"}',
  'timer.wait': '{"waitSeconds":60}'
}

interface NodeOption {
  value: string
  label: string
}

/** 顶栏校验后定位的节点行 */
const selectedRow = ref<FlowRelationVO>()

const message = useMessage()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const list = ref<FlowRelationVO[]>([])
const total = ref(0)
const definitions = ref<FlowDefinitionVO[]>([])
const steps = ref<FlowStepVO[]>([])
const queryParams = reactive({
  pageNo: 1,
  pageSize: 50,
  definitionId: undefined as number | undefined
})
const queryFormRef = ref()

const selectedDefinition = computed(() => definitions.value.find((item) => item.id === queryParams.definitionId))
const drafts = computed(() => definitions.value.filter((item) => item.status === 'DRAFT'))
const canEdit = computed(() => !queryParams.definitionId || selectedDefinition.value?.status === 'DRAFT')

const isControl = (type?: string) => !!CONTROL_TYPES.find((item) => item.value === type)
const typeLabel = (type?: string) =>
  STEP_TYPES.find((item) => item.value === type)?.label ??
  CONTROL_TYPES.find((item) => item.value === type)?.label ??
  type ??
  '—'
const joinLabel = (policy?: string) =>
  policy === 'ANY_SUCCESS' ? '任一成功' : policy === 'ALL_DONE' ? '全部终态' : '全部成功'

/**
 * 已知结构化字段：
 * - INPUT → 流程入参声明顶层键
 * - NODE → 优先按下列顺序聚合候选（避免用户手填字段名漂移）：
 *     1) 上游节点 outputMapping 归一化出的上下文键（用户已声明的「我会写这些键」）
 *     2) 上游节点能力的 outputSchema 声明字段（写为 output.<字段>，引擎未做映射时的兜底路径）
 *     3) loop / subflow 引擎固定键（iterations、children、detail[0].*）
 *   全无匹配时给「output」兜底，提示用户可下钻 output.xxx。
 */
const knownFields = (source?: string, nodeId?: string): { label: string; value: string }[] => {
  // INPUT：流程入参声明里声明的字段
  if (source === 'INPUT') {
    return inputDeclaredKeys.value.map((key) => ({ label: `input.${key}`, value: key }))
  }
  if (!nodeId) return []
  const row = list.value.find((item) => item.nodeId === nodeId)
  const fields: { label: string; value: string }[] = []
  // 1) 上游节点输出映射：运行时按 {输出字段: 上下文键} 写入 node.<nodeId>.<键>
  for (const entry of parseOutputMapping(row?.outputMapping)) {
    if (!entry.key?.trim()) continue
    const contextKey = entry.value?.trim() || entry.key.trim()
    fields.push({
      label: contextKey === entry.key.trim() ? contextKey : `${contextKey} · ← ${entry.key.trim()}`,
      value: contextKey
    })
  }
  // 2) 上游能力的输出声明：未配映射时整体在 output 键下，下钻路径 output.<字段>
  for (const declared of declaredOutputSchema(row?.outputSchema)) {
    fields.push({
      label: `output.${declared.key} · ${declared.desc || '上游能力声明'}`,
      value: `output.${declared.key}`
    })
  }
  // 3) 引擎固定键（loop / subflow）
  const type = row?.stepType
  if (type === 'loop') {
    fields.push({ label: 'iterations · 循环轮数', value: 'iterations' })
  }
  if (type === 'subflow') {
    fields.push(
      { label: 'children · 子流程数', value: 'children' },
      { label: 'detail[0].executionId · 首个子运行ID', value: 'detail[0].executionId' },
      { label: 'detail[0].executionNo · 首个子运行业务号', value: 'detail[0].executionNo' },
      { label: 'detail[0].status · 首个子运行状态', value: 'detail[0].status' },
      { label: 'detail[0].groupIndex · 首个子运行组号', value: 'detail[0].groupIndex' }
    )
  }
  // 全无匹配给 output 兜底（区分字段候选缺失 vs 节点本身无输出）
  if (!fields.length) {
    fields.push({ label: 'output · 完整输出对象（可下钻 output.xxx）', value: 'output' })
  }
  return fields
}

/** 读能力的输出声明为 {key, desc} 列表。空 / 坏数据返回空数组 */
const declaredOutputSchema = (raw?: string): { key: string; desc: string }[] => {
  if (!raw?.trim()) return []
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
    return Object.entries(obj)
      .filter(([key]) => key && !key.startsWith('__'))
      .map(([key, value]) => ({ key, desc: value == null ? '' : String(value) }))
  } catch {
    return []
  }
}

const nodeNameOf = (nodeId: string) => list.value.find((item) => item.nodeId === nodeId)?.name

/** 顶栏校验后定位的节点：高亮显示 */
const rowClassName = ({ row }: { row: FlowRelationVO }) =>
  selectedRow.value?.id === row.id ? 'row-focus' : ''

/** 某流程版本下可选的前置节点。展示用中文名，值仍是节点编码 */
const nodeOptions = (definitionId?: number): NodeOption[] =>
  list.value
    .filter((item) => item.definitionId === definitionId)
    .map((item) => ({
      value: item.nodeId!,
      label: `${item.name || item.nodeId}（${item.nodeId}）`
    }))

/** 单个节点编码 → 展示项。参数映射里从 dependsOn 反查时用 */
const toNodeOption = (nodeId: string): NodeOption => ({
  value: nodeId,
  label: `${nodeNameOf(nodeId) || nodeId}（${nodeId}）`
})

/** 列表「前置」列：中文名连缀，空表示起点 */
const dependsLabel = (row: FlowRelationVO) =>
  (row.dependsOn || []).length ? (row.dependsOn || []).map((dep) => nodeNameOf(dep) || dep).join('、') : '起点'

/**
 * 挂能力时系统自动分配的节点编码。与后端 FlowRelationServiceImpl#nextNodeId 同一套规则：
 * 默认取步骤编码，同版本内被占用时依次试 _2 / _3。
 */
const autoNodeId = computed(() => {
  const stepKey = steps.value.find((item) => item.id === createForm.stepId)?.stepKey
  if (!stepKey) return ''
  const taken = new Set(nodeOptions(createForm.definitionId).map((item) => item.value))
  if (!taken.has(stepKey)) return stepKey
  for (let i = 2; i <= 61; i++) {
    const suffix = `_${i}`
    const candidate = stepKey.length + suffix.length > 32 ? stepKey.slice(0, 32 - suffix.length) + suffix : stepKey + suffix
    if (!taken.has(candidate)) return candidate
  }
  return ''
})

const getList = async () => {
  loading.value = true
  try {
    const data = await FlowRelationApi.getPage({ ...queryParams, pageSize: 100 })
    list.value = data.list || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const loadOptions = async () => {
  definitions.value = (await FlowDefinitionApi.simpleList()) || []
  steps.value = (await FlowStepApi.getList()) || []
}

const applyRoute = () => {
  const raw = route.query.definitionId
  if (raw) queryParams.definitionId = Number(raw)
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.definitionId = undefined
  queryParams.pageNo = 1
  getList()
}

// ==================== 添加节点 ====================

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const createForm = reactive({
  mode: 'step' as 'step' | 'control',
  definitionId: undefined as number | undefined,
  stepId: undefined as number | undefined,
  nodeId: '',
  name: '',
  stepType: undefined as string | undefined,
  typeConfig: '',
  dependsOn: [] as string[]
})
const createRules = {
  definitionId: [{ required: true, message: '请选择流程', trigger: 'change' }],
  stepId: [{ required: true, message: '请选择步骤', trigger: 'change' }],
  name: [{ required: true, message: '请填写节点名称', trigger: 'blur' }],
  stepType: [{ required: true, message: '请选择节点类型', trigger: 'change' }],
  // nodeId 只有控制类节点要手填，挂能力时由系统分配
  nodeId: [
    { pattern: /^[a-z][a-z0-9_]{0,31}$/, message: '小写字母开头，只含小写、数字和下划线', trigger: 'blur' }
  ]
}
const controlPlaceholder = computed(() => {
  const sample = CONTROL_PLACEHOLDERS[createForm.stepType ?? '']
  return sample ? `配置 JSON，如 ${sample}` : '类型专属配置 JSON'
})

const openCreate = () => {
  createForm.mode = 'step'
  createForm.definitionId = selectedDefinition.value?.status === 'DRAFT' ? queryParams.definitionId : undefined
  createForm.stepId = undefined
  createForm.nodeId = ''
  createForm.name = ''
  createForm.stepType = undefined
  createForm.typeConfig = ''
  createForm.dependsOn = []
  createVisible.value = true
}

const submitCreate = async () => {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (createForm.mode === 'control') {
    if (!createForm.nodeId?.trim()) {
      message.error('控制类节点要填写节点编码')
      return
    }
    if (!createForm.typeConfig?.trim()) {
      message.error('控制类节点要填写类型配置')
      return
    }
  }
  creating.value = true
  try {
    await FlowRelationApi.create(
      createForm.mode === 'step'
        ? {
            definitionId: createForm.definitionId!,
            stepId: createForm.stepId!,
            // 编码不传：完全交给后端分配，前端只负责把算出来的结果展示给用户看，避免两边算出两个值
            dependsOn: createForm.dependsOn
          }
        : {
            definitionId: createForm.definitionId!,
            nodeId: createForm.nodeId!.trim(),
            name: createForm.name!.trim(),
            stepType: createForm.stepType!,
            typeConfig: createForm.typeConfig!.trim(),
            dependsOn: createForm.dependsOn
          }
    )
    message.success('已添加')
    createVisible.value = false
    queryParams.definitionId = createForm.definitionId
    await getList()
  } finally {
    creating.value = false
  }
}

// ==================== 编排编辑 ====================

interface BranchRow {
  key: string
  targets: string[]
}
interface MappingRow {
  param: string
  source: string
  field?: string
  nodeId?: string
  value?: string
}
interface OutputRow {
  key: string
  value: string
}

const editVisible = ref(false)
const editing = ref(false)
const editLoading = ref(false)
const editId = ref(0)
const editDefinitionId = ref<number>()
const editForm = reactive({
  nodeId: '',
  stepType: '',
  isControl: false,
  name: '',
  stepId: undefined as number | undefined,
  typeConfig: '',
  dependsOn: [] as string[],
  joinPolicy: undefined as string | undefined,
  joinFailurePolicy: undefined as string | undefined,
  branches: [] as BranchRow[],
  mappings: [] as MappingRow[],
  outputMapping: [] as OutputRow[]
})
const editNodeOptions = computed(() => nodeOptions(editDefinitionId.value))

/** 当前编排节点挂的能力步骤。控制类节点没有 step，声明参数为空 */
const editStep = computed(() => steps.value.find((item) => item.id === editForm.stepId))

/** 当前编排节点所属的流程定义（拿入参声明） */
const editDefinition = computed(() => definitions.value.find((item) => item.id === editDefinitionId.value))

/** 流程入参声明里的顶层键，给参数映射 INPUT 来源当字段候选 */
const inputDeclaredKeys = computed<string[]>(() => {
  const raw = editDefinition.value?.inputParams
  if (!raw?.trim()) return []
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
    return Object.keys(obj).filter((key) => key !== '__comments__')
  } catch {
    return []
  }
})

/**
 * 兜底取步骤详情：/flow/step/list 只返回启用步骤，
 * 步骤被停用后仍挂在节点上时，列表里查不到会误判成「没声明参数」。
 */
const loadStepDetail = async (stepId?: number) => {
  if (!stepId || steps.value.some((item) => item.id === stepId)) return
  try {
    const detail = await FlowStepApi.get(stepId)
    if (detail) steps.value.push(detail)
  } catch {
    // 步骤已删除：保持候选为空，退化为自由输入
  }
}

/** 步骤入参视图：表单直改 / JSON 预览 */
const paramView = ref<'form' | 'json'>('form')

/** 注释保留键，与后端 FlowConstants.PARAM_COMMENTS_KEY 对应 */
const COMMENTS_KEY = '__comments__'

interface DeclaredParam {
  key: string
  /** 原始类型的默认值，生成 JSON 预览用 */
  value: unknown
  /** 默认值的字符串形态，行内展示与「是否改过」比较用 */
  text: string
  /** 参数注释（defaultParams 的 __comments__） */
  comment: string
}

/**
 * 步骤上声明的默认入参（default_params 的顶层键，__comments__ 拆成注释）。
 * 运行期 resolveParams 拿它当基底、节点映射同名覆盖。
 */
const declaredParams = computed<DeclaredParam[]>(() => {
  const raw = editStep.value?.defaultParams
  if (!raw?.trim()) return []
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
    const comments =
      typeof obj[COMMENTS_KEY] === 'object' && obj[COMMENTS_KEY] !== null
        ? (obj[COMMENTS_KEY] as Record<string, string>)
        : {}
    return Object.entries(obj)
      .filter(([key]) => key !== COMMENTS_KEY)
      .map(([key, value]) => ({
        key,
        value,
        text: value === null || typeof value === 'object' ? JSON.stringify(value) : String(value),
        comment: comments[key] ?? ''
      }))
  } catch {
    return []
  }
})

/** 该参数的固定值（LITERAL）覆盖映射行 */
const literalMappingOf = (key: string) =>
  editForm.mappings.find((item) => item.param === key && item.source === 'LITERAL')

/** 是否被固定值替换过 */
const isReplaced = (key: string) => !!literalMappingOf(key)

/** 是否被 INPUT/NODE 动态映射。此时行内替换会打架，只读展示 */
const isDynamicMapped = (key: string) =>
  editForm.mappings.some((item) => item.param === key && item.source !== 'LITERAL')

/** 行内输入框的生效值：覆盖值优先，否则默认值 */
const effectiveValue = (item: DeclaredParam) => literalMappingOf(item.key)?.value ?? item.text

/**
 * 行内改值：与默认值相同（或清空还原）就删掉覆盖映射，
 * 否则 upsert 一条固定值映射。与后端 plainValue 的类型推断一致，
 * 数字/布尔串运行期会还原成对应类型。
 */
const replaceParam = (item: DeclaredParam, value: string) => {
  const existing = literalMappingOf(item.key)
  if (value === item.text) {
    if (existing) editForm.mappings.splice(editForm.mappings.indexOf(existing), 1)
    return
  }
  if (existing) {
    existing.value = value
  } else {
    editForm.mappings.push({ param: item.key, source: 'LITERAL', value })
  }
}

/** 还原成默认值 */
const revertParam = (item: DeclaredParam) => {
  const existing = literalMappingOf(item.key)
  if (existing) editForm.mappings.splice(editForm.mappings.indexOf(existing), 1)
}

/** 生效参数：默认入参 + 固定值覆盖合并（剥掉注释保留键），给 JSON 视图 */
const effectiveParams = computed<Record<string, unknown>>(() => {
  const base: Record<string, unknown> = {}
  for (const item of declaredParams.value) {
    base[item.key] = item.value
  }
  for (const mapping of editForm.mappings) {
    if (!mapping.param?.trim() || mapping.source !== 'LITERAL') continue
    const raw = mapping.value?.trim()
    if (raw === 'true') base[mapping.param] = true
    else if (raw === 'false') base[mapping.param] = false
    else if (raw !== '' && raw !== undefined && !Number.isNaN(Number(raw))) base[mapping.param] = Number(raw)
    else base[mapping.param] = mapping.value ?? ''
  }
  return base
})

const effectiveParamsJson = computed(() =>
  Object.keys(effectiveParams.value).length ? JSON.stringify(effectiveParams.value, null, 2) : '{}'
)

/** 新增控制节点的类型配置字段表 */
const createTypeFields = computed(() => STEP_TYPE_FIELDS[createForm.stepType ?? ''] ?? [])
/** 编辑节点的类型配置字段表 */
const editTypeFields = computed(() => STEP_TYPE_FIELDS[editForm.stepType ?? ''] ?? [])

/** 输出映射 JSON 字符串 → 行。{输出字段: 上下文键} */
const parseOutputMapping = (raw?: string): OutputRow[] => {
  if (!raw?.trim()) return []
  try {
    const obj = JSON.parse(raw)
    if (!obj || typeof obj !== 'object' || Array.isArray(obj)) return []
    return Object.entries(obj).map(([key, value]) => ({ key, value: String(value ?? '') }))
  } catch {
    return []
  }
}

const openEdit = async (row: FlowRelationVO) => {
  editId.value = row.id!
  editDefinitionId.value = row.definitionId
  editVisible.value = true
  editLoading.value = true
  try {
    const detail = list.value.find((item) => item.id === row.id) ?? row
    editForm.nodeId = detail.nodeId ?? ''
    editForm.stepType = detail.stepType ?? ''
    editForm.isControl = isControl(detail.stepType)
    editForm.name = detail.name ?? ''
    editForm.stepId = detail.stepId
    editForm.typeConfig = detail.typeConfig ?? ''
    editForm.dependsOn = [...(detail.dependsOn ?? [])]
    editForm.joinPolicy = detail.joinPolicy || undefined
    editForm.joinFailurePolicy = detail.joinFailurePolicy || undefined
    // 分支出口：route 节点的 branches 直接来自列表行
    editForm.branches = Object.entries(detail.branches ?? {}).map(([key, targets]) => ({
      key,
      targets: [...targets]
    }))
    editForm.mappings = (detail.mappings ?? []).map((item) => ({ ...item }))
    editForm.outputMapping = parseOutputMapping(detail.outputMapping)
    await loadStepDetail(detail.stepId)
  } finally {
    editLoading.value = false
  }
}

const submitEdit = async () => {
  if (editForm.isControl && !editForm.typeConfig?.trim()) {
    message.error('控制类节点要填写类型配置')
    return
  }
  if (editForm.isControl && !editForm.name?.trim()) {
    message.error('控制类节点要填写名称')
    return
  }
  const branches: Record<string, string[]> = {}
  for (const branch of editForm.branches) {
    if (branch.key?.trim()) {
      branches[branch.key.trim()] = branch.targets
    }
  }
  const outputMapping: Record<string, string> = {}
  for (const row of editForm.outputMapping) {
    if (row.key?.trim()) {
      outputMapping[row.key.trim()] = row.value?.trim() || ''
    }
  }
  editing.value = true
  try {
    await FlowRelationApi.update({
      id: editId.value,
      name: editForm.isControl ? editForm.name?.trim() : undefined,
      typeConfig: editForm.typeConfig?.trim() || undefined,
      dependsOn: editForm.dependsOn,
      joinPolicy: editForm.joinPolicy || '',
      joinFailurePolicy: editForm.joinFailurePolicy || '',
      branches,
      outputMapping,
      mappings: editForm.mappings.filter((item) => item.param?.trim())
    })
    message.success('已保存')
    editVisible.value = false
    await getList()
  } finally {
    editing.value = false
  }
}

const move = async (id: number, offset: number) => {
  await FlowRelationApi.move(id, offset)
  await getList()
}

const handleDelete = async (id: number) => {
  try {
    await message.delConfirm('移除后，还要依赖它的节点会校验失败，请先解除那些依赖。')
  } catch {
    return
  }
  await FlowRelationApi.delete(id)
  message.success('已移除')
  await getList()
}

watch(
  () => route.query.definitionId,
  () => {
    applyRoute()
    queryParams.pageNo = 1
    getList()
  }
)

// ==================== 顶栏上下文条 ====================

/** 当前选中流程（= queryParams.definitionId 对应的草稿/发布/停用） */
const currentDefinition = computed(() => {
  if (!queryParams.definitionId) return undefined
  return definitions.value.find((item) => item.id === queryParams.definitionId)
})

const statusLabel = (status?: string) => {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'RETIRED') return '已停用'
  return '草稿'
}
const statusTag = (status?: string): 'success' | 'info' | 'warning' =>
  status === 'PUBLISHED' ? 'success' : status === 'RETIRED' ? 'info' : 'warning'

/** 顶栏「校验」：调后端 /validate 把问题清单弹出；可点击条目跳到该节点（当前实现仅高亮选中行） */
const validating = ref(false)
const issueRef = ref()
const goValidate = async () => {
  if (!currentDefinition.value) {
    message.warning('请先在上方选择流程')
    return
  }
  validating.value = true
  try {
    const issues = await FlowDefinitionApi.validate(currentDefinition.value.id!)
    issueRef.value?.open(issues || [], {
      title: `校验：${currentDefinition.value.name} v${currentDefinition.value.version}`,
      emptyText: '所有检查通过，可以发布。',
      onPick: (issue: { nodeId?: string }) => {
        if (issue.nodeId) focusNode(issue.nodeId)
      }
    })
  } finally {
    validating.value = false
  }
}

const goBackToList = () => {
  router.push({ path: '/flow/definition' })
}

/** 跳到指定节点的行：当前版本选中行 + 滚到可见，后续可在列表上做高亮。 */
const focusNode = (nodeId: string) => {
  const target = list.value.find((item) => item.nodeId === nodeId)
  if (!target) {
    message.warning(`节点【${nodeId}】不在当前流程，跳转失败`)
    return
  }
  // 简单的视觉提示：触发一次表格选中状态
  selectedRow.value = target
  message.info(`已定位到节点【${target.name || target.nodeId}】，点击「编排」可继续编辑`)
}

onMounted(async () => {
  applyRoute()
  await loadOptions()
  await getList()
})
</script>

<style lang="scss" scoped>
.declared-params {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color-lighter);
}

.declared-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.declared-hint {
  font-size: 12px;
  color: #909399;
}

.declared-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  line-height: 22px;

  & + .declared-row {
    border-top: 1px dashed var(--el-border-color-lighter);
  }
}

.declared-key {
  flex-shrink: 0;
  width: 200px;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.has-comment {
    cursor: help;
    text-decoration: underline dotted var(--el-text-color-placeholder);
  }
}

.declared-value {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-regular);
  word-break: break-all;
}

/* 行内替换值：橙字 + 淡橙底，一眼看出哪些参数被本节点改过 */
.declared-input {
  flex: 1;
  min-width: 0;

  &.is-overridden {
    :deep(.el-input__inner) {
      color: var(--el-color-warning);
      font-weight: 600;
    }

    :deep(.el-input__wrapper) {
      background: var(--el-color-warning-light-9);
      box-shadow: 0 0 0 1px var(--el-color-warning-light-5) inset;
    }
  }
}

.declared-json {
  margin: 0;
  max-height: 280px;
  overflow: auto;
  font-family: 'JetBrains Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--el-text-color-regular);
}

/* 顶栏校验后定位到的节点：浅色高亮，不破坏表格观感 */
:deep(.el-table .row-focus) {
  background-color: var(--el-color-warning-light-9) !important;
}

.relation-context {
  margin-bottom: -10px;
}
</style>
