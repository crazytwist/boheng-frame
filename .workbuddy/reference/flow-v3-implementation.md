# Flow 第三版 · 实施清单（施工级）

上游设计：`flow-v3-design.md`。现状基线：`flow-v2.md` + 本模块 36 个 Java 文件 / 2808 行。
本清单**只描述要做什么，不含代码**；用户确认后按 §9 顺序开工。

---

## 0. 已定决策

| # | 决策 | 影响 |
|---|---|---|
| D1 | 本轮**不动代码**，先出施工级清单 | 本文件 |
| D2 | `flow_step.device_code` / `action_code` **彻底收敛进 `type_config`** | 需迁移脚本搬存量数据；前端步骤表单要改 |
| D3 | **循环 / 子流程与并行 / 合并一起做** | 编排四类一次成型；`flow_step_execution` 从第一天就带「循环轮次」维度；不分期收缩 |

D3 的连带后果（必须提前定，否则后补要改唯一键）：
- `flow_step_execution` 唯一键 = `(execution_id, node_id, iteration_no, attempt)`，多一个 `iteration_no`。
- `flow_execution_context` 与 `flow_branch_decision` 同样要带 `iteration_no`。
- `flow_node.step_id` 必须**可空**（控制类节点不对应能力目录）。

---

## 1. 目标态总览

### 1.1 表的增改

| 表 | 动作 | 说明 |
|---|---|---|
| `flow_step` | **改** | 能力目录。加 `step_type` / `type_config` / `default_params`，删 `device_code` / `action_code` |
| `flow_definition` | 保留 | 不动（版本 + checksum） |
| `flow_node` | **改** | 编排节点。`step_id` 改可空，加 8 个列 |
| `flow_execution` | 新增 | 一次运行 |
| `flow_step_execution` | 新增 | 一次步骤尝试（含循环轮次） |
| `flow_step_signal` | 新增 | 统一信号收件箱 |
| `flow_execution_context` | 新增 | 分项上下文 |
| `flow_branch_decision` | 新增 | 分支决策 |

一次性建的**不搬**表：`artifact` / `reconciliation` / `compensation_plan` / `event_log` / `outbox`。

### 1.2 两个维度落点

| 维度 | 载体 |
|---|---|
| 能力（怎么执行） | `flow_step.step_type` + `type_config`；节点级可被 `flow_node.step_type` + `type_config` 覆盖 |
| 编排（怎么串） | 串行/并行/合并 = `flow_node.depends_on` + `join_policy`；循环/子流程 = `role=CONTROL/SUBFLOW` 的容器节点 |

---

## 2. 表结构

### 2.1 `flow_step`（改）

```sql
ALTER TABLE `flow_step`
  ADD COLUMN `step_type`     varchar(64) NOT NULL DEFAULT 'device.command' COMMENT '类型 key，见 StepTypeRegistry' AFTER `name`,
  ADD COLUMN `type_config`   json        NOT NULL COMMENT '类型专属配置，由 Descriptor 反序列化' AFTER `step_type`,
  ADD COLUMN `default_params` json       NULL COMMENT '通用默认输入，节点可覆盖' AFTER `type_config`,
  DROP COLUMN `device_code`,
  DROP COLUMN `action_code`;
```

`device.command` 的 `type_config` 形态：`{"deviceCode":"centrifuge_01","actionCode":"START"}`。

⚠️ 存量数据搬迁：`type_config = JSON_OBJECT('deviceCode', device_code, 'actionCode', action_code)`，
必须在 `DROP COLUMN` **之前**执行（写进迁移脚本，见 §2.8）。

### 2.2 `flow_node`（改）

```sql
ALTER TABLE `flow_node`
  MODIFY COLUMN `step_id` bigint NULL COMMENT '引用能力目录。控制类内置节点为 NULL',
  ADD COLUMN `step_type`       varchar(64) NULL COMMENT '节点级类型覆盖。NULL 取 flow_step.step_type' AFTER `step_id`,
  ADD COLUMN `role`            varchar(32) NULL COMMENT 'ACTION/CONTROL/WAIT/SUBFLOW。NULL 取 Descriptor' AFTER `step_type`,
  ADD COLUMN `completion_mode` varchar(32) NULL COMMENT 'SYNC/CALLBACK/SIGNAL/TIMER/CHILD' AFTER `role`,
  ADD COLUMN `type_config`     json        NULL COMMENT '节点级类型配置。控制类节点全放这里（含 loop 循环体范围）' AFTER `input_mapping`,
  ADD COLUMN `output_mapping`  json        NULL COMMENT '节点输出归一化到上下文的映射' AFTER `type_config`,
  ADD COLUMN `run_config`      json        NULL COMMENT '节点级运行覆盖：timeoutMs/maxAttempts/backoffMs/failureStrategy' AFTER `output_mapping`,
  ADD COLUMN `join_policy`     varchar(32) NOT NULL DEFAULT 'ALL_SUCCESS' COMMENT 'ALL_SUCCESS/ANY_SUCCESS/ALL_DONE' AFTER `run_config`,
  ADD COLUMN `join_failure_policy` varchar(32) NOT NULL DEFAULT 'FAIL' COMMENT 'FAIL/SKIP' AFTER `join_policy`,
  ADD COLUMN `branches`        json        NULL COMMENT '控制节点出口映射，如 {"pass":["report"]}' AFTER `join_failure_policy`;
```

**用两个 JSON 列而不是 8 个独立列**（labillion 是独立列）：`type_config` = 类型专属，`run_config` = 运行通用。
理由：boheng 的 `timeout_ms` / `max_attempts` 默认值已经在 `flow_step` 上，节点级只需少数覆盖项，JSON 更省且加类型零改表。

`sort_no` 保留（画布展示序），`depends_on` 保留但**放开为可编辑**（不再由服务端按顺序写死）。

### 2.3 `flow_execution`（新增）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 雪花主键 |
| `execution_no` | varchar(64) | 运行业务标识，如 `exe_...`。唯一键用生成列兼容逻辑删除 |
| `pipeline_key` / `pipeline_version` | varchar(64) / int | 绑定的**不可变**版本 |
| `definition_id` | bigint | 指向 `flow_definition.id` |
| `checksum` | varchar(64) | 启动时冻结的定义摘要，用于检测漂移 |
| `status` | varchar(32) | `PENDING/RUNNING/SUCCESS/FAILED/CANCELLED` |
| `trigger_type` / `triggered_by` | varchar(32) / varchar(64) | `MANUAL/SCHEDULE/EVENT/RETRY/CHILD` + 主体 |
| `business_key` | varchar(128) | 业务关联键，如实验单号 |
| `parent_execution_id` | bigint NULL | 子流程指父流程 |
| `parent_node_id` | varchar(32) NULL | 父流程里启动本子流程的 node_id |
| `parent_attempt` | int NULL | 父节点的 attempt。**CHILD 信号必须回传，禁止用旧 attempt 推进新 attempt** |
| `parent_group_index` | int NULL | **FANOUT 场景**：同一父节点扇出的第几个子执行（计划里的组序号）。SINGLE 恒 NULL |
| `input_params` / `output_data` | json | 入参 / 出参 |
| `started_at` / `finished_at` | datetime | |
| `error_code` / `error_message` | varchar(128) / text | |
| `row_version` | int | CAS 乐观锁 |

索引：`(tenant_id, status, update_time)`、`(tenant_id, business_key)`、`(tenant_id, parent_execution_id, parent_node_id)`。

### 2.4 `flow_step_execution`（新增）

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 雪花主键 |
| `execution_id` | bigint | 所属运行 |
| `node_id` | varchar(32) | 节点编码 |
| `step_type` | varchar(64) | **启动时冻结**的类型 key |
| `iteration_no` | int NOT NULL DEFAULT 0 | **循环轮次**。非循环节点恒为 0 |
| `attempt` | int NOT NULL | 本轮的尝试序号，从 1 开始。重试**新行**不覆盖 |
| `status` | varchar(32) | `PENDING/RUNNING/WAITING/SUCCESS/FAILED/DEAD/SKIPPED/CANCELLED` |
| `skip_reason` | varchar(32) NULL | **仅 `SKIPPED` 时有值**：`BRANCH`（出口未选中被裁剪）/ `FAILURE`（被前置失败带倒）/ `UPSTREAM`。`JoinResolver` 只把 `BRANCH` 视为满足 |
| `waiting_for` | varchar(32) NULL | `DEVICE_CALLBACK/MANUAL/TIMER/CHILD/EXTERNAL` |
| `wait_ref` | varchar(256) NULL | 非密引用，如设备命令号 |
| `command_id` / `command_no` | bigint / varchar(64) NULL | 设备命令关联（`device.command` 专用） |
| `input_snapshot` / `output_data` | json | 输入快照 / 已结算输出 |
| `error_code` / `error_message` | varchar(128) / text | |
| `queued_at` / `started_at` / `finished_at` / `deadline_at` / `next_retry_at` | datetime | |
| `row_version` | int | CAS 乐观锁 |

唯一键：`(tenant_id, execution_id, node_id, iteration_no, attempt)`。
索引：`(tenant_id, execution_id, status)`、`(tenant_id, status, deadline_at)`、`(tenant_id, status, next_retry_at)`、`(tenant_id, wait_ref)`。

### 2.5 `flow_step_signal`（新增）

`signal_id`（varchar 128，来源方幂等 ID）、`execution_id`、`node_id`、`attempt`、`iteration_no`、
`signal_type`（`DEVICE_CALLBACK/MANUAL/TIMER/CHILD/EXTERNAL`）、`payload` json、`success` bit(1)、
`error_code` / `error_message`、`process_status`（`RECEIVED/APPLIED/DUPLICATE/IGNORED/REJECTED`）、
`received_at` / `processed_at`。唯一键 `(tenant_id, signal_id)`。

### 2.6 `flow_execution_context`（新增）

`execution_id`、`namespace`（`INPUT/NODE/SYSTEM`）、`node_id`、`iteration_no`、`context_key`、`value_data` json。
唯一键 `(tenant_id, execution_id, namespace, node_id, iteration_no, context_key)`。

### 2.7 `flow_branch_decision`（新增）

`execution_id`、`route_node_id`、`attempt`、`iteration_no`、`selected_branch`、`match_mode`、`activated_targets` json、`decision_data` json（**表达式文本 + 求值时的变量快照**，用于事后回答「为什么走了这条」）。
唯一键 `(tenant_id, execution_id, route_node_id, attempt, iteration_no)`。

### 2.8 迁移脚本

新增 `sql/mysql/flow-v3-migrate.sql`（**幂等**，参照 `flow-step.sql` 的写法：`CREATE TABLE IF NOT EXISTS` + 存储过程 + `information_schema` 探测列是否存在，列表存在则跳过）：

1. 探测 `flow_step.device_code` 是否存在 → 存在则先加 `type_config`，`UPDATE ... SET type_config = JSON_OBJECT('deviceCode', device_code, 'actionCode', action_code)`，再 `DROP COLUMN`。
2. 探测 `flow_step.step_type` 不存在 → `ADD COLUMN`。
3. 探测 `flow_node.step_id` 是否 `NOT NULL` → 是则 `MODIFY` 成可空。
4. 逐列补 `flow_node` 的新列。
5. 建运行层 5 张表。

新库只跑 `flow-definition.sql`（更新版）+ `flow-execution.sql`；旧库跑 `flow-v3-migrate.sql`。
**沿用 v2 的约定**：唯一键 = 生成列 + 唯一索引（兼容逻辑删除）；所有表带 `tenant_id`，DO 继承 `TenantBaseDO`（不加 `@TenantIgnore`）。

---

## 3. 定义层类清单

包根 `cn.boheng.frame.module.flow`。

### 3.1 新增

**枚举** `enums/`
- `StepRole`（ACTION / CONTROL / WAIT / SUBFLOW）
- `CompletionMode`（SYNC / CALLBACK / SIGNAL / TIMER / CHILD）
- `JoinPolicy`（ALL_SUCCESS / ANY_SUCCESS / ALL_DONE）
- `JoinFailurePolicy`（FAIL / SKIP）
- `SkippedReason`（**BRANCH** / **FAILURE** / UPSTREAM）—— 见 §2.2 的 `skip_reason` 列，`JoinResolver` 靠它区分「被裁剪」与「被失败带倒」
- `RouteMatchMode`（EXCLUSIVE / INCLUSIVE）

**类型骨架** `engine/type/`
- `StepTypeDescriptor<C>`（`type` / `role` / `completionMode` / `configType` / `retryable` / `validate`）
- `StepExecutor<C>`（`type` / `execute(ctx)`）
- `StepExecutionContext<C>`
- `StepOutcome`（成功 / 失败 / 等待 + 输出）
- `StepTypeRegistry`（`@Component`，收集所有 Descriptor + Executor，启动校验：type 不重复、成对、config 可反序列化）
- `StepTypeRegistryValidator`（`@PostConstruct` 触发）

**各类型的配置 POJO** `engine/type/config/`
`DeviceCommandConfig` / `BeanInvokeConfig` / `ScriptConfig` / `ManualTaskConfig` / `NotificationConfig` / `RouteConfig` / `LoopConfig` / `SubflowConfig`（`dispatchMode` SINGLE/FANOUT + FANOUT 的 `dispatch` / `completionPolicy`）/ `DispatchPlanEntry`（`pipelineKey` / `version` / `input`）/ `TimerWaitConfig`

`RouteConfig` = `{ router:{ kind, expression|beanName }, branches:{ key:[nodeId] }, matchMode, missingKeyPolicy }`

**表达式求值器** `engine/expression/`
- `ExpressionEvaluator`（统一入口：`evalBool` / `evalString` / `eval`）
- `ExpressionContext`（求值时的上下文快照，同时供审计写 `decision_data`）
- 实现走 `aviator`（`boheng-dependencies` 已管理 5.4.4）或 SpEL（`boheng-common` 已有）；**路由 / `loop.whileExpression` / `input_mapping` / `output_mapping` 四处必须共用同一个求值器**

**路由决策 SPI** `engine/route/`
- `FlowRouteAction`（`decide(RouteContext) → String key`，**纯函数**：不 import 调度类、不写运行表）
- `FlowRouteRegistry`（收集 + **白名单校验**，与 `bean.invoke` 共用同一张名单；**禁止按任意类名反射调用**）
- `RouteContext`（上下文读取 + 上游节点输出取值）

**扇出分发 SPI** `engine/dispatch/`
- `FlowDispatchAction`（`dispatch(DispatchContext) → List<DispatchPlanEntry>`，**纯函数**：只产出拆分计划，不启动执行、不写库）
- `FlowDispatchRegistry`（白名单校验，与 `bean.invoke` / `FlowRouteAction` 共用同一张名单）
- `DispatchContext`（上下文读取：上游节点输出 / 入参）

**各类型的 Descriptor + Executor** `engine/type/builtin/`（一个类实现两个接口）
- `RouteStepType`（CONTROL / SYNC）— 按 `kind` 调表达式或 `FlowRouteAction` 得到 key，**只返回 key**；激活与裁剪交给 `BranchResolver`
- `LoopStepType`（CONTROL / SYNC）— 容器，驱动循环体
- `SubflowStepType`（SUBFLOW / CHILD）— 容器，创建子执行
- `TimerWaitStepType`（WAIT / TIMER）— 走 infra job
- `ManualTaskStepType`（WAIT / SIGNAL）— 等人确认
- `NotificationStepType`（ACTION / SYNC）— 通知出口
- `BeanInvokeStepType`（ACTION / SYNC）— 调 Spring Bean
- `ScriptStepType`（ACTION / SYNC）— 脚本 / 表达式

**设备类型** `engine/type/device/`
- `DeviceCommandStepType`（ACTION / CALLBACK）— 只依赖 `DeviceCommandApi`

**图模型** `engine/definition/`
- `PipelineGraph`（从 `flow_definition` + `flow_node` + `flow_step` 冻结出的不可变图）
- `PipelineNode`（节点 + 解析后的依赖 + 容器归属）

### 3.2 修改

| 文件 | 改什么 |
|---|---|
| `enums/FlowConstants.java` | 删 `STEP_DEVICE_COMMAND` 写死；加 `DEFAULT_JOIN_POLICY` / `DEFAULT_JOIN_FAILURE_POLICY` / 各 role 常量；`MAX_NODES` 语义放宽（画布 >30 步） |
| `dal/dataobject/step/FlowStepDO.java` | 加 `stepType` / `typeConfig` / `defaultParams`；删 `deviceCode` / `actionCode` |
| `dal/dataobject/definition/FlowNodeDO.java` | `stepId` 可空 + §2.2 的 8 个新字段 |
| `service/definition/model/FlowNodeDraft.java` | 同步新字段 |
| `service/definition/FlowGraphChecker.java` | 改为**按注册表 + 图**校验（见 §8） |
| `service/definition/FlowDefinitionServiceImpl.java` | 去掉硬编码 `device.command` / `FAIL_FAST`；`toDraft()` 从 `flow_step` 取 `step_type` / `type_config` |
| `service/step/FlowStepServiceImpl.java` | 保存时用 `type_config` 走 registry 的 `validate` |
| `controller/admin/**/vo/*`（10 个 VO） | 加新字段、换 `deviceCode`/`actionCode` 为 `stepType`/`typeConfig` |
| `enums/ErrorCodeConstants.java` | 加 `1_005_003_xxx`（类型/图校验）、`1_005_004_xxx`（运行层） |

### 3.3 删除

- `FlowConstants.STEP_DEVICE_COMMAND` 的**唯一类型地位**（保留常量本身作为默认值）。
- 无文件删除。

---

## 4. 运行层类清单

包根 `cn.boheng.frame.module.flow`。

**DO / Mapper** `dal/dataobject/execution/` + `dal/mysql/execution/`
`FlowExecutionDO` / `FlowStepExecutionDO` / `FlowStepSignalDO` / `FlowExecutionContextDO` / `FlowBranchDecisionDO` + 5 个 Mapper（继承 `BaseMapperX`）

**状态机** `engine/execution/`
- `ExecutionStateMachine`（纯 CAS，零外部依赖 —— 从 labillion 整体照搬的一块）
- `StepStateMachine`（同上）

**调度** `engine/execution/scheduler/`
- `DagScheduler`（就绪判定：前置按 `join_policy` 满足 → READY）
- `JoinResolver`（`ALL_SUCCESS` / `ANY_SUCCESS` / `ALL_DONE` + `join_failure_policy`）
- `BranchResolver`（**只做激活与裁剪**：拿 `RouteStepType` 已经算好的 key → 查 `branches` 映射 → 激活目标子图 → 其余出口下游逐节点标 `SKIPPED/BRANCH` → 写 `flow_branch_decision`。**它不负责求值**，避免「决策」和「执行」耦合在一处）
- `LoopDriver`（**容器**：驱动循环体多轮，写 `iteration_no`，判 `maxIterations` / `whileExpression`）
- `SubflowDriver`（**容器**，两形态：SINGLE 创建单个子执行等 CHILD 信号；FANOUT ①调 `FlowDispatchAction` 算计划 → ②CAS 落计划 → ③按 `business_key={execution_id}:{node_id}:{attempt}:{group_index}` 幂等逐条建子执行 → ④收齐 N 个 CHILD 信号后按 `completionPolicy` 结算。**扇出点即汇合点**）

**执行** `engine/execution/`
- `ExecutorDispatcher`（按 `step_type` 取 Executor 执行；同步类型直接返回，异步类型转 WAITING）
- `ParamResolver`（`INPUT` / `NODE` / `LITERAL` 三来源解析）
- `FlowExecutionEngine`（启动 / 推进 / 取消的门面）
- `FlowExecutionWorker`（**独立线程池** + `TenantUtils.execute(tenantId, ...)` —— 照 `DeviceCommandSweeper` 的模式：daemon `ScheduledExecutorService` + `@EventListener(ApplicationReadyEvent)` + `@PreDestroy`）

**信号** `engine/signal/`
- `SignalRouter`（收信号 → 幂等去重 → 定位 `flow_step_execution` → CAS 结算 → 推进 DAG；**FANOUT 场景**：按 `signal_id` 匹配计划里的某个子执行，全部到齐才触发节点结算，不是「一个 CHILD 就推进」）
- `SignalInbox`（写 `flow_step_signal`）

**服务** `service/execution/`
- `FlowExecutionService` / `FlowExecutionServiceImpl`（start / cancel / get / page / stepPage）

---

## 5. 设备域改动（`boheng-module-device-api`）

| 文件 | 动作 |
|---|---|
| `api/DeviceCommandApi.java` | **新增**。`send(DeviceCommandSendReqDTO)` / `getResult(commandId)` |
| `api/dto/DeviceCommandSendReqDTO.java` | **新增**。`deviceCode` / `actionCode` / `paramsJson` / `timeoutMs` / `sourceType` / `sourceRef` / `attempt` |
| `api/dto/DeviceCommandResultDTO.java` | **新增**。`commandId` / `commandNo` / `success` / `outputJson` / `errorCode` / `errorMessage`。**异常收成失败 DTO，不跨模块抛业务异常** |
| `device/api/DeviceCommandApiImpl.java` | **新增**（device 模块内）。复用 `DeviceHttpService.invoke` 全流程 |
| `device/service/command/DeviceCommandService` | **重载** `open(...)` 带来源参数（旧入口保持 MANUAL 行为不变） |
| `device/service/http/DeviceHttpService` | **重载** `invoke(...)` 带来源 |

要点：`DeviceCommandDO` 已有 `sourceType` / `attempt` / `sourceRef` 语义位，只是 `open()` 写死 `MANUAL` / `1`，属**接线**不是造引擎。

---

## 6. 接口与权限

**新增** `controller/admin/execution/FlowExecutionController`：

| 方法 | 路径 | 权限 |
|---|---|---|
| 启动 | `POST /flow/execution/start` | `flow:execution:start` |
| 取消 | `POST /flow/execution/cancel` | `flow:execution:cancel` |
| 详情 | `GET /flow/execution/get` | `flow:execution:query` |
| 分页 | `GET /flow/execution/page` | `flow:execution:query` |
| 步骤列表 | `GET /flow/execution/step-page` | `flow:execution:query` |

**新增** `controller/admin/signal/FlowSignalController`：人工确认 `POST /flow/signal/manual` → `flow:signal:manual`。

菜单脚本 `sql/mysql/flow-execution-menu.sql`（沿用 `flow-menu.sql` 写法）。
现有 3 菜单 / 13 权限不动。

---

## 7. 前端影响（本轮不做，但要排期）

| 文件 | 影响 |
|---|---|
| `web/src/api/flow/step.ts` | DTO 变：`deviceCode`/`actionCode` → `stepType`/`typeConfig` |
| `web/src/views/flow/step/StepForm.vue` | **按 `stepType` 切换表单**（设备选设备、脚本填脚本…） |
| `web/src/views/flow/relation/index.vue` | `depends_on` 从「只读顺序」变为可编辑；一期可先只展示 |
| 新增 `web/src/views/flow/execution/index.vue` + 详情 | 运行列表 / 步骤时间线 / 人工确认入口 |
| 新增 `web/src/api/flow/execution.ts` | 运行接口 |

⚠️ 菜单 `component` 不能写 `flow/index` —— 前端按子串匹配，会命中 `views/ai/workflow/index.vue`（v2 已踩过）。

---

## 8. 发布校验矩阵（fail-fast）

| 层 | 校验项 | 错误码 |
|---|---|---|
| 保存 | 编码 / 名称 / 超时与次数范围 | 沿用 `1_005_00x` |
| 保存 | `step_type` 已注册；`type_config` 能反序列化且过 `validate` | `1_005_003_000` |
| 图 | `depends_on` **无环**（拓扑排序） | `1_005_003_001` |
| 图 | `depends_on` 引用的 node_id 存在且在本版本内 | `1_005_003_002` |
| 图 | `join_policy` 与前置数量一致（单前置不许 `ANY_SUCCESS`） | `1_005_003_003` |
| 图 | 参数映射引用的前置必须是**直接前驱**（v2 的「不能跳步」要升级为「必须是 depends_on 之一」） | `1_005_003_004` |
| 容器 | `loop` 的 `bodyEntry/bodyExit` 存在、区间连续、**不含自身** | `1_005_003_005` |
| 容器 | `loop.maxIterations` 必填且 ≤ 上限 | `1_005_003_006` |
| 容器 | `subflow` 目标版本必须 `PUBLISHED` | `1_005_003_007` |
| 容器 | `subflow` 引用链**无环**（可达性检查） | `1_005_003_008` |
| 分支 | `branches` 出口名与 Descriptor 声明一致；目标节点存在 | `1_005_003_009` |
| 分支 | 必有 `default` 出口（否则运行时可能无路可走） | `1_005_003_010` |
| 分支 | `router.kind=bean` 时，`beanName` **必须在白名单**且实现 `FlowRouteAction` | `1_005_003_011` |
| 分支 | `matchMode=EXCLUSIVE` 时，不同 key 的 `activated_targets` **不得交叠**；`INCLUSIVE` 时才允许 | `1_005_003_012` |
| 分支 | `router.kind=expression` 时表达式可解析（编译期校验，不等到运行） | `1_005_003_013` |
| 控制 | `route` / `loop` / `subflow` / `timer.wait` 的 `step_id` 必须为 **NULL**（Flow 内建，不指向能力目录） | `1_005_003_014` |
| 容器 | FANOUT 的 `dispatch.kind=bean` 时，`beanName` 在白名单且实现 `FlowDispatchAction` | `1_005_003_015` |
| 容器 | FANOUT 的 `completionPolicy` ∈ {`ALL_SUCCESS`,`ANY_SUCCESS`}；缺省 `ALL_SUCCESS` | `1_005_003_016` |
| 发布 | 至少一步；设备类节点的设备在台账且动作启用 | 沿用 v2 |

> ⚠️ **本版最大风险**：v2「依赖不由人填」是刻意的防错设计，从根上杜绝「画得出跑不了」。放开 `depends_on` 后，这条防线**只能靠上表补回来**。图校验不全 = 运行期才炸。

---

## 9. 施工顺序与验收点

D3 决定「编排四类一次成型」，所以阶段按**层**切，不按拓扑切。

| 阶段 | 内容 | 验收点 |
|---|---|---|
| **S0 前置** | ① flow 模块**纳入 git 并提交**（当前完全 untracked）；② 确认并发写入者已停手 | `git ls-files boheng-module-flow` 非空 |
| **S1 定义层** | 类型注册表（§3.1）+ `flow_step` / `flow_node` 字段收敛与扩充（§2.1–2.2）+ 迁移脚本（§2.8）+ 校验矩阵（§8） | `rm -rf target` 后编译通过；`FlowGraphCheckerTest` 扩展全绿（无环 / join / 容器区间 / 子流程成环 / 分支出口）；旧库跑迁移脚本后数据不丢 |
| **S2 运行内核** | 5 张表（§2.3–2.7）+ 状态机 + 上下文 + 信号 + 直线调度 + `DeviceCommandApi`（§5） | 端到端：启动一次 `device.command` 直线流程 → 下发 → 回调 → 推进 → SUCCESS；调设备走独立线程池且租户上下文正确 |
| **S3 编排四类** | `DagScheduler` + `JoinResolver`（并行/合并）+ `RouteStepType` + `BranchResolver`（路由）+ `LoopDriver`（循环）+ `SubflowDriver`（子流程 SINGLE + FANOUT） | ① 两条并行分支同时 RUNNING；② `ALL_SUCCESS` / `ANY_SUCCESS` 差异可验；③ 路由选路正确、写 `flow_branch_decision`、未选中出口的节点落 `SKIPPED/BRANCH` **且汇合节点仍能 READY**（这是 labillion 的债，必须验）；④ `FlowRouteAction` 实现类**不持有任何调度器引用**（架构约束，可用 ArchUnit 或单测断言）；⑤ 循环体跑 N 轮且 `iteration_no` 正确；⑥ 子流程终态唤醒父节点（CHILD 信号回传 `attempt`）；⑦ **FANOUT：拆分 Bean 返回 2 组计划 → 两个子执行并行 RUNNING、`parent_group_index` 0/1 → 全部终态后父节点按 `completionPolicy` 结算；杀掉其中一个子执行重启引擎，恢复逻辑按计划幂等补建、不重复** |
| **S4 其余执行器 + 接口** | `bean.invoke` / `script.run` / `manual.task` / `notification.send` / `timer.wait`；Controller + 权限 + 菜单 | 各类型在页面上可建、可发、可跑通 |

**测试清单**（S2/S3 必须补）：
- `StepTypeRegistryTest`（重复 type / 缺 Executor / config 反序列化失败）
- `FlowGraphCheckerTest` 扩展（§8 全项）
- `ParamResolverTest`（INPUT / NODE / LITERAL）
- `DagSchedulerTest` + `JoinResolverTest`（直线 / 扇出 / 扇入 / 三种 join / `SKIPPED/BRANCH` 视为满足 / `SKIPPED/FAILURE` 按策略）
- `ExpressionEvaluatorTest`（布尔 / 字符串 / 取值 / 异常兜底）
- `RouteStepTypeTest` + `BranchResolverTest`（表达式选路 / Bean 选路 / 无命中走 `default` / 出口子图裁剪 / `EXCLUSIVE` 交叠拒绝）
- `FlowRouteRegistryTest`（未注册 Bean 拒绝 / 白名单外 Bean 拒绝）
- `SubflowDriverTest`（SINGLE 语义 + FANOUT：计划落库先于建子执行 / 空计划直接 SUCCESS / `business_key` 幂等防重 / 收齐 N 个 CHILD 才结算 / `ANY_SUCCESS` 部分失败可过 / 兄弟不被取消）
- `FlowDispatchRegistryTest`（同 RouteRegistry）
- `LoopDriverTest` / `SubflowDriverTest`
- 全部用**假的 `DeviceCommandApi`** 替代真实设备

---

## 10. 未决 / 需你拍板

| # | 问题 | 我的倾向 |
|---|---|---|
| Q1 | `script.run` 用哪个引擎？`aviator` 只能算表达式，不是脚本 | 一期只支持 `aviator` 表达式；要真脚本再引 Groovy |
| Q2 | `notification.send` 的出口？本精简基座**没有 notify 模块** | 先做 `NotificationSender` SPI + 日志/WebSocket 实现（infra 有 `WebSocketSenderApi`），真实渠道后续接 |
| Q3 | `timer.wait` 走 infra 的 quartz job，还是自建延迟队列？ | 走 infra job（已有，不重复造） |
| Q4 | `bean.invoke` 的白名单？任意 Bean 名 = 任意代码执行 | **必须白名单**：只允许实现某个 `FlowBeanAction` 接口且显式注册的 Bean |
| Q5 | 循环内节点的 `attempt` 与 `iteration_no` 命名是否接受？ | 接受，见 §2.4 |
| Q6 | v2 遗留的 4 件小事是否本轮顺手修？（`flow_node.step_id` 可空已并入本版；`toDraft()` 硬编码已在 S1；`FLOW_RELATION_LIMIT` 与 `MAX_NODES` 命名在 S1） | 顺手修 |
| **Q10** | FANOUT 的某个子执行失败时，要不要提供**主动取消兄弟子执行**的开关？ | 默认**不取消**（物理命令可能已在执行，取消不安全），等全部终态后按 `completionPolicy` 结算；「取消整条父执行」时才级联取消未终态的子执行 |

### 已拍板（本节记录结论，不再待决）

| # | 结论 |
|---|---|
| **D4** | **「拆分」= 控制流路由**（一次运行只走一条出口）。**数据流拆分**（一份产出裂成 N 份并行）一期不做 —— 见 design §3.5 |
| **D5** | 决策载体：`expression` **做全**；`bean` **只留 SPI + 白名单注册表**（与 Q4 的 `FlowBeanAction` 共用同一张名单），不给 UI 与注册治理；`script` 不做 |
| **D6** | 匹配语义：UI **只开放 `exclusive`**；`inclusive` 仅在模型与校验层支持（枚举保留、画布无入口） |
| **D7** | 「96 样本拆机」类需求 = **子流程 FANOUT 扇出**（design §3.4.2），**不是** §3.5 的同图动态实例。拆分计算归 `FlowDispatchAction` Bean（纯函数出计划），启动子执行归引擎（计划先落库、`business_key` 幂等）；有了 FANOUT，`instance_key` 彻底放弃 |

---

## 11. 不做的（明确排除）

- `pe_execution_artifact`（产物引用 + 对象存储）、`pe_step_reconciliation`（对账）、`pe_compensation_plan`（补偿）、`pe_execution_event_log`（事件日志）、`pe_event_outbox`（发件箱）—— 一期无需求。
- `ProjectContextHolder`（项目隔离，boheng 用 `tenant_id`）、LiteFlow、Task 队列分发、AGV/Lab 业务类型、WebSocket 推送、监控/运维页。
- **不继承 labillion 自认的债务**，尤其两条：
  - `BranchSkipper` 把 `SKIPPED` **无条件**当成功 → 汇合节点会误判 READY。boheng 必须区分「裁剪的 SKIP」与「失败的 SKIP」。
  - `RecoveryScanner` 对仪器步骤做 `RUNNING→PENDING` 重置 → 物理指令可能已发出。恢复必须先对账。
