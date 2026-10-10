# Flow 模块差异清单：boheng vs labillion

> **口径**
> - 左：当前项目 `boheng-frame/boheng-module-flow`，状态为 2026-10-10 定稿版（36 文件 / 2808 行，未纳入 git）
> - 右：参照项目 `labillion-frame/labillion-module-flow`，`dev` 分支（202 文件 / 17265 行）
> - 本文只做差异盘点和分级，**不含代码改动**

---

## 0. 结论先行

1. **定义层已经对齐，不需要"改造"，只需要补字段。**
   boheng 的 `flow_step` / `flow_definition` / `flow_node` 三层，与 labillion 的
   `pd_step_definition` / `pd_pipeline_definition` / `pd_pipeline_step` **一一对应**。

2. **差距 100% 落在运行层。**
   labillion 有 10 张 `pe_*` 表 + 71 个引擎类 + 40 个契约类；boheng 是 0。

3. **最大阻塞项不是运行层本身，而是设备命令契约。**
   boheng 的 `DeviceApi` 只暴露「选设备 / 查设备 / 查动作」，接口注释自己写明
   「发命令 / 查命令状态 / 收结果的契约随命令执行模型一起落地时再补」。
   但设备模块**内部已经实现完整**（`DeviceHttpService.invoke` 一条龙：占用设备 → 落命令
   → 签发回调 → 阻塞等回调 → 超时收口）。所以这是**接线工作，不是从零造引擎**。

4. **「步骤类型注册表」不是 boheng 单方面落后。**
   labillion 至今**也没有** `StepTypeRegistry` / `StepTypeDescriptor`，它排在自己改造文档的 M1。

5. **不建议照搬** labillion 的项目隔离、LiteFlow、Task 队列、AGV/Lab 业务类型、产物+S3；
   也**不建议连带继承**它已自认的 7 处债务（见 §5）。

---

## 1. 规模对照

| 维度 | boheng（现状） | labillion（参照） | 差距 |
|---|---|---|---|
| Java 文件 | 36 | 202 | — |
| 代码行 | 2 808 | 17 265 | — |
| 定义层表 | 3（`flow_step` / `flow_definition` / `flow_node`） | 3（`pd_step_definition` / `pd_pipeline_definition` / `pd_pipeline_step`） | **结构对齐** |
| 运行层表 | 0 | 10（`pe_*`） | **全缺** |
| 引擎类 | 0 | 71（`engine/**`） | **全缺** |
| 契约类 | 3（`DeviceApi` 3 个方法） | 40（独立 `flow-api` 模块） | **全缺** |
| 前端页面 | definition / step / relation 三页 | 有 | 对齐 |
| 错误码段 | `1-005-*` | `1-040-*` | 命名差异 |
| 是否入库 git | **否（untracked）** | 是 | ⚠️ 见 §7 |

---

## 2. 定义层差异

### 2.1 步骤能力：`flow_step` vs `pd_step_definition`

| 维度 | boheng `flow_step` | labillion `pd_step_definition` | 评级 |
|---|---|---|---|
| 稳定标识 | `step_key` varchar(32) | `step_key` varchar(128) | 等价 |
| 步骤类型 | **无 `step_type` 列**（隐式固定 `device.command`） | `step_type` varchar(128) | **P1** |
| 类型专属配置 | 无（`device_code`/`action_code` 平铺成列） | `default_type_config` json | **P1** |
| 通用默认入参 | 无 | `default_params` json（节点可覆盖） | **P1** |
| 表单 Schema | 无 | `config_schema` json | P2 |
| 修订号 | 无（靠「被已发布流程引用即锁定」防护） | `revision` int，唯一键含 revision | P2 |
| 启用位 | `status` tinyint（0 启用 / 1 停用） | `enabled` tinyint(1) | P2（命名统一） |
| 超时 / 次数 | `timeout_ms` / `max_attempts` | 走 `default_type_config` | 等价 |
| 项目隔离 | `tenant_id` | `tenant_id` + `system_project_id` | 不适用 |

> **注意**：boheng 的 `max_attempts` 列注释写着「当前运行时尚未执行」——
> 参数已定义但无消费者，属于运行层落地时要接的点。

### 2.2 流程版本：`flow_definition` vs `pd_pipeline_definition`

| 维度 | boheng | labillion | 评级 |
|---|---|---|---|
| 状态 | `DRAFT` / `PUBLISHED` / `RETIRED` | `DRAFT` / `PUBLISHED` / `DISABLED` | 等价（命名不同） |
| 发布指纹 | `checksum`（节点顺序+设备+动作+超时+次数+映射 的 SHA-256） | `definition_checksum` | 等价 |
| 默认失败策略 | **无**（写死 `FAIL_FAST`） | `default_failure_strategy`（含 `CONTINUE_ON_FAIL`） | **P1** |
| 默认超时 / 次数 / 退避 | **无** | `default_timeout_ms` / `default_max_attempts` / `default_backoff_ms` | **P1** |
| 默认启动入参 | **无** | `default_input_params` json | P2 |
| 补偿失败策略 | 无 | `compensation_failure_strategy` | P2 |
| 项目隔离 | 仅 `tenant_id` | `tenant_id` + `system_project_id` | 不适用 |

### 2.3 节点：`flow_node` vs `pd_pipeline_step`

| 维度 | boheng | labillion | 评级 |
|---|---|---|---|
| 节点编码 | `node_id` | `node_id` | 等价 |
| 引用能力 | `step_id`（bigint，指向 `flow_step.id`） | `step_key` varchar | 等价（引用方式不同） |
| 名字 | 借自 `flow_step.name` | 节点自带 `name` | P2（同一步用两次时无法起别名） |
| 图角色 | **无**（隐式为 ACTION） | `role`：ACTION / CONTROL / WAIT / SUBFLOW | **P1**（做控制节点才需要） |
| 完成方式 | **无**（隐式 SYNC/CALLBACK） | `completion_mode`：SYNC / CALLBACK / SIGNAL / TIMER / CHILD | **P1** |
| 分发方式 | **无** | `dispatch_mode`：DIRECT / QUEUED | P2（boheng 无 Task 模块，QUEUED 无去处） |
| 汇合策略 | **无**（直线） | `join_policy`（ALL_ACTIVE / ANY_SUCCESS）+ `join_failure_policy`（SKIP / FAIL） | **P1**（做分支时必须） |
| 分支出口 | **无** | `branches` json（`{"pass":[...],"fail":[...]}`） | **P1**（做条件分支时必须） |
| 依赖 | `depends_on` json，**服务端按顺序自动写**，人不填 | `depends_on` json，可手填 | **boheng 更严格 ✅** |
| 输入映射 | `input_mapping`，3 种来源（`INPUT` / `NODE` / `LITERAL`），`NODE` **只允许紧邻上一步** | `input_mapping`，表达式 `${calculate.score}`，可引用**任意前驱** | **P1** |
| 输出映射 | **无**（原始响应留在设备域） | `output_mapping` json | **P1** |
| 节点级覆盖 | **无**（超时/次数全部继承 `flow_step`） | `timeout_ms` / `max_attempts` / `backoff_ms` / `failure_strategy`（NULL=继承） | **P1** |
| 资源等待 | 无 | `resource_wait_timeout_ms` | P2（boheng 无 ResourcePool） |
| 补偿 | 无 | `compensate_type` / `compensate_config` | P2 |
| 单步运行 | 无 | `standalone_runnable` + `engine/standalone` | P2 |
| 项目隔离 | 仅 `tenant_id` | 含 `system_project_id` | 不适用 |

---

## 3. 运行层差异（boheng 全缺）

### 3.1 表

| labillion 表 | 作用 | boheng 对应 |
|---|---|---|
| `pe_pipeline_execution` | 一次运行实例：11 态状态机、触发来源、业务键、**幂等键**、父子流程、乐观锁 `row_version` | **缺** |
| `pe_step_execution` | 一次**尝试**：attempt 新行重试、`run_mode`、`waiting_for`、`wait_key`、`callback_token_hash`、输入快照/输出、DEAD、对账与补偿字段 | **缺** |
| `pe_branch_decision` | 条件节点选中的分支 + 激活目标，与静态 DAG 共同决定本次激活子图 | 缺 |
| `pe_execution_context_item` | 分项上下文（namespace / node / key 三键），避免单行大 JSON 竞争 | 缺 |
| `pe_execution_artifact` | 产物元数据，原始内容在对象存储 | 缺 |
| `pe_step_signal` | 统一信号收件箱：6 类信号 × 5 种处理状态 + `signal_id` 幂等 | 缺 |
| `pe_step_reconciliation` | 物理动作对账历史，带租约 + `reconcile_run_id` 防旧结果回写 | 缺 |
| `pe_compensation_plan` | 补偿计划，反向拓扑执行 | 缺 |
| `pe_execution_event_log` | 追加式审计事件，用于时间线 | 缺 |
| `pe_event_outbox` | 可靠发件箱，`FLOW_EVALUATE_REQUESTED` 靠它兜住递归调度 | 缺 |

### 3.2 引擎组件（71 类）

| labillion 子包 | 类数 | 职责 | boheng |
|---|---|---|---|
| `engine/execution/executor` | 16 | 7 类执行器 + Factory + Mock + Direct | 缺 |
| `engine/execution/scheduler` | 9 | `DagScheduler` / `StepSubmitter` / `StepSuccessHandler` / `StepFailureHandler` / `BranchSkipper` / `DispatchSpiFactory` / `ResourceWaitRegistry` | 缺 |
| `engine/execution/event` | 9 | 事件模型 + 发布 + 外部应用转发 | 缺 |
| `engine/definition` | 7 | `PipelineGraphBuilder` / `PipelineGraphCache` / `PipelineGraphValidator` / `StepSemanticDefaults` / 图模型 | **校验部分 boheng 有（`FlowGraphChecker`）** |
| `engine/execution/exception` | 5 | `ExceptionEngine` / `RetryPolicy` / `RetryPolicyRegistry` / `FailureDecision` | 缺 |
| `engine/execution/callback` | 4 | `StepCallbackService` / `CallbackTokenCodec` / `TaskCompletedEventListener` | 缺 |
| `engine/execution/context` | 3 | `ExecutionContextManager` / `OutputMappingResolver` | 缺 |
| `engine/execution/statemachine` | 2 | `ExecutionStateMachine` / `StepStateMachine`（CAS） | 缺 |
| `engine/execution/signal` | 2 | `StepSignalInbox` / `StepSignalIds` | 缺 |
| `engine/execution/websocket` | 2 | 状态推送 | 缺 |
| `engine/execution/watchdog` | 1 | `TimeoutWatchdog` | 缺 |
| `engine/execution/recovery` | 1 | `RecoveryScanner` | 缺 |
| `engine/execution/reconcile` | 1 | `StepReconciliationCoordinator` | 缺 |
| `engine/compensation` | 1 | 补偿包（**实为空包**，见 §5） | 缺 |
| `engine/standalone` | 4 | 单步/子流程运行 | 缺 |

### 3.3 契约（labillion 有独立 `flow-api` 模块）

| labillion 契约 | 作用 | boheng |
|---|---|---|
| `DeviceCommandSpi` | 引擎 → 设备域发命令 | **缺，最大阻塞项** |
| `DeviceReconcileSpi` | 超时/ACK 丢失后的物理结果对账 | 缺 |
| `DispatchSpi` + `DispatchCallback` + `StepDispatchContext` | DIRECT / QUEUED 两种分发 | 缺 |
| `StepExecutor`（api 包） | 业务模块注册执行器 | 缺 |
| `MaterialCheckSpi` | 步骤前置物料核验 | 缺（boheng 有 WMS，可对接） |
| `ManualTaskSpi` | 人工任务 | 缺 |
| `ResourcePool` | 资源占用/租约/继承 | 缺（boheng 有 WMS 库位，可对接） |
| `PipelineLaunchApi` / `PipelineExecApi` / `StepStateApi` | 跨模块启动与状态 | 缺 |
| `StepResult`（`Success` / `Waiting` / `Failure` 互斥） | 步骤结果 | 缺 |

---

## 4. 分级待办

### P0 — 做运行层的地基（不做就没有运行）

| # | 事项 | 说明 |
|---|---|---|
| 1 | **设备命令契约** `DeviceCommandApi` | 补在 `boheng-module-device-api`。设备模块内部已有 `DeviceHttpService` 全流程，只需把「按 deviceCode 下发 + 带来源标记 + 返回结果」暴露出来。**唯一阻塞项** |
| 2 | 运行两张核心表 | `flow_execution`（一次运行）+ `flow_step_execution`（一次尝试）。是 labillion `pe_pipeline_execution` / `pe_step_execution` 的**直线最小子集**，不要一次上 10 张 |
| 3 | 状态机 + CAS | 直接借鉴 `StepStateMachine` / `ExecutionStateMachine`：状态变更必须 CAS，失败方不得产生副作用（`onStepComplete` 不推进下游）。**这是 labillion 里唯一零外部依赖、可整体照搬的一块** |
| 4 | 输入映射解析 | `INPUT` / `NODE` / `LITERAL` → 步骤入参；配合 `input_snapshot` 落库 |
| 5 | 输出与命令串联 | 步骤上落 `commandId` / `commandNo`，输出进 `output_data`；原始响应仍归设备域 |
| 6 | 后台线程的租户上下文 | 设备调用阻塞等回调（最长 600 秒），必须走独立线程池 + 显式 `TenantUtils.execute(tenantId, ...)`。**模式照抄设备模块的 `DeviceCommandSweeper`**（daemon 单线程调度器 + `@EventListener(ApplicationReadyEvent)` + `@PreDestroy`） |

### P1 — 跑通后紧接着

| # | 事项 | 说明 |
|---|---|---|
| 7 | 步骤类型注册表 `step_type` + `type_config` | 现在 `FlowConstants.STEP_DEVICE_COMMAND` 被硬编码进 `toDraft()`。两侧共同缺口（labillion 排 M1），先做骨架不开放新类型 |
| 8 | 节点级覆盖 | `timeout_ms` / `max_attempts` / `backoff_ms` / `failure_strategy`（含 `CONTINUE_ON_FAIL`），NULL = 继承 |
| 9 | 完成方式 `completion_mode` + `waiting_for` | 先支持 `CALLBACK`（设备侧已有）与 `SYNC`，`TIMER` / `SIGNAL` 后置 |
| 10 | 重试退避 `next_retry_at` | 现在只有次数，没有退避概念 |
| 11 | 幂等 | 运行的 `idempotency_key`；信号的 `signal_id`。设备侧已有命令级 token 摘要可复用 |
| 12 | 事件日志 | `pe_execution_event_log` 的轻量版，用于运行时间线与排障 |
| 13 | `output_mapping` | 把执行器返回值归一化进上下文 |
| 14 | 输入映射支持任意前驱 | 现在只允许紧邻上一步；有分支后就必然要放开 |

### P2 — 按需

| # | 事项 |
|---|---|
| 15 | 条件分支 + 汇合：`branches` / `join_policy` / `join_failure_policy` + `pe_branch_decision`，并修正「SKIPPED 无条件当成功」 |
| 16 | 子流程：`parent_execution_id` / `parent_node_id` / `parent_attempt` + CHILD 信号 |
| 17 | 上下文分项表，替代单行大 JSON |
| 18 | 资源等待（需先有 WMS `ResourcePool` 对应物） |
| 19 | 单步运行 `standalone` |
| 20 | 对账 SPI + 租约（`pe_step_reconciliation`） |

### 不建议做

| 项 | 原因 |
|---|---|
| `system_project_id` 项目隔离 | boheng 用 `tenant_id`，且没有 project 域；强加会污染所有表 |
| LiteFlow 依赖 | `compute.capability` 的一个实现方式而已，boheng 用 Spring Bean 就够 |
| Task 模块 QUEUED 分发 | boheng 没有 Task 模块，`dispatch_mode=QUEUED` 没有去处 |
| AGV / `SAMPLE_SPLIT` 等业务类型进引擎枚举 | labillion 自己正在把这些从枚举里赶出去 |
| `pe_execution_artifact` + S3/MinIO | boheng 还没有对象存储；原始数据先留在设备域 |
| 补偿计划全套（`pe_compensation_plan` + 状态机） | labillion 自己都是空包（见 §5） |
| WebSocket 状态推送 | 等有运行数据、有前端实时需求再说 |
| 13 张表一次性建 | labillion 自己的改造文档明确「不在本轮一次切完 DDL」 |

---

## 5. labillion 已自认的债务 —— 不要连带继承

来源：`docs/flow-module-refactor-design.md`（它自己的改造基线）

| 债务 | 影响 |
|---|---|
| `StepSubmitter` 职责叠加（DAG 推进里同步 `acquire` 资源） | 资源与调度焊死，无法拆分 |
| `BranchSkipper` 把 `SKIPPED` 无条件当 `SUCCESS` | **汇合节点会误判为 READY** |
| `StepResult(success, waiting)` 双 boolean | 可组合出矛盾态（既成功又等待） |
| 回调无 `signalId` | 迟到 / 重放判定弱 |
| `RecoveryScanner` 对 INSTRUMENT 做 `RUNNING → PENDING` | 物理指令可能已发出，重置会重复执行 |
| `DirectDispatchSpi` 找不到执行器**静默降级 Mock** | 生产环境掩盖配置错误 |
| `engine/compensation` 空包 + 步骤上 `compensateNodeId` | 补偿不可审计 |
| `StepTypeEnum` 混入 `SAMPLE_SPLIT` 业务类型 | 业务类型进引擎枚举 |
| 有 `pe_step_resource_hold` 资源占用表（所有权争议） | Flow 行上出现第二份"占用事实" |
| V2 DDL 明确「禁止对已有库直接执行」 | 没有迁移路径（boheng 的 `flow-step.sql` 反而有） |

---

## 6. boheng 现在比 labillion 更严 / 更好的地方（建议保留）

| 项 | 说明 |
|---|---|
| **依赖不由人填** | 步骤顺序就是表格顺序，服务端按顺序写 `depends_on`，从根上杜绝"画得出跑不了"的图 |
| **类型白名单 fail-fast** | 只接受 `device.command`，其他类型直接拒绝，库里不会留跑不了的节点 |
| **生成列 + 唯一索引兼容逻辑删除** | `node_key` / `version_key` / `step_key_guard` 是 `IF(deleted='0', ..., NULL)` 生成列，逻辑删除后释放编码。labillion 的唯一键直接含 `deleted` 列外的业务键，语义不同 |
| **旧库迁移脚本** | `flow-step.sql` 把内联节点拆成「步骤表 + 关联表」，可重复执行；labillion 的 V2 DDL 没有迁移路径 |
| **步骤锁定语义清晰** | 被**非草稿**版本引用即锁定，草稿引用不锁；由 `FlowStepService.assertNotLocked` 实现，比"直接禁止改"更可用 |
| **硬边界收敛** | 30 步上限、超时 1 秒–24 小时、次数 1–5，明确拦住编辑器误操作 |

---

## 7. 现状里值得顺手处理的小事

| # | 事项 | 说明 |
|---|---|---|
| 1 | **模块未纳入 git** | `boheng-module-flow/`、`sql/mysql/flow-*.sql`、`web/src/api/flow/` 全部是 untracked。建议尽快提交 |
| 2 | 迁移后 `flow_node.step_id` 可空 | `flow-step.sql` 用 `ADD COLUMN step_id bigint NULL` 先加列再回填，之后**没有改回 `NOT NULL`**；新库 `flow-definition.sql` 里是 `NOT NULL`。迁移过的库与新库约束不一致 |
| 3 | 硬编码的步骤类型 / 失败策略 | `FlowDefinitionServiceImpl.toDraft()` 里写死 `setStepType(STEP_DEVICE_COMMAND)` 与 `setFailStrategy(FAIL_FAST)`；`FlowNodeRespVO.failStrategy` 恒为 `FAIL_FAST`，前端若可编辑会误导 |
| 4 | 错误码命名跨域 | 步数上限报错用 `FLOW_RELATION_LIMIT`，常量却叫 `MAX_NODES`（`FlowConstants`），命名对不上 |
| 5 | **不是问题**：`flow_step` 在两个脚本里都有 | `flow-step.sql` 是刻意的旧库迁移脚本（自带 `IF NOT EXISTS`），注释已写明「新库只执行 flow-definition.sql，不要再跑本脚本」 |

---

## 8. 一句话路线

> 定义层不动。运行层从「设备命令契约 + 运行两张表 + 状态机 CAS」开始，
> 先把**直线流程**跑通（下发 → 等回调 → 结算 → 推进下一步 → 失败重试/终止），
> 再按 P1 补类型注册表与节点级覆盖，条件分支 / 子流程 / 补偿留到有真实需求时。
