# flow 模块详细设计（v3 现状梳理）

> 基准：`boheng-module-flow` 当前主干代码，153 个 Java 文件 / 12,810 行，12 张表。
> 本文只描述**代码里已经存在的行为**，不写待建能力；待建与风险统一收在 §14。

## 配套分级图（archify 生成，均已过 validate / deliver / check / browser-check）

| 层级 | 图 | 路径 |
|---|---|---|
| L0 | ★ 引擎全景（六件事一张图：分类 / 推动 / 异常 / 图能力 / 追溯 / 参数） | `.archify/flow-l1-engine-map-20261111-110139/flow-engine-map.html` |
| L0 | 架构总览（接入 / 定义 / 运行 / 契约四层） | `.archify/flow-l0-architecture-20261111-110139/flow-l0-architecture.html` |
| L1 | 一轮推进时序（启动→判定→认领→分发→等待→回填→收尾） | `.archify/flow-l1-engine-sequence-20261111-110139/flow-l1-engine-sequence.html` |
| L1 | ★ 全量数据模型 ERD（**12 表 / 246 字段，一张图看完定义层 + 运行层 + 错误层**） | `.archify/flow-erd-all-tables-20261111-110139/flow-erd-all-tables.html` |
| L1 | 运行层数据模型 ERD（7 表 / 144 字段，全量图的运行期切片） | `.archify/flow-l1-execution-erd-20261111-110139/flow-l1-execution-erd.html` |
| L1 | 从能力到运行的全链路（含异常与回退） | `.archify/flow-l1-definition-to-runtime-20261111-110139/flow-l1-definition-to-runtime.html` |
| L2 | 节点执行状态机（8 态 12 迁移） | `.archify/flow-l2-step-lifecycle-20261111-110139/flow-l2-step-lifecycle.html` |
| L2 | ★ 节点解剖（`flow_node` 22 列按「身份 / 意义 / 功能 / 编排 / 审计」五组拆开） | `.archify/flow-l2-node-anatomy-20261111-110139/flow-node-anatomy.html` |
| L2 | 步骤类型体系树（11 种类型按 role 分组） | `.archify/flow-l2-step-type-tree-20261111-110139/flow-l2-step-type-tree.html` |

> 图在临时目录渲染后拷入（`/tmp` 渲染 + `cp`，避免 archify 原子写在本仓库内删隔离目录时被拦）。HTML 本身自包含，可直接打开；`*.finalize.json` 里的绝对路径仍指向 `/private/tmp/...`，不影响阅读。

### 全景图六块 ↔ 本文章节

| 全景图分区 | 一句话 | 对应章节 |
|---|---|---|
| ① 节点分类 | 11 种类型 / 4 类角色，角色决定调度待遇 | §2.3 · §5 |
| ② 推动方式 | 四个推进入口 + 五种完成方式 | §4.1–4.4 · §8.1 |
| ③ 异常与补偿 | 重试 / 跳过或整条失败 / DEAD 收口 / 错误台账 | §4.4 · §11 |
| ④ 图能力 | 循环展开、分支裁剪、合并判定、FANOUT 拆分 | §4.2 · §4.5–4.7 |
| ⑤ 执行追溯 | 运行 5 态 / 节点 8 态 / 决策快照 / 设备事件 | §3.3 · §6 |
| ⑥ 参数与上下文 | 三命名空间、参数衔接、输出契约、写回 | §9 |

---

## 1. 定位与边界

### 1.1 一句话定位

flow 是**编排执行器**，不是业务域：它把「设备、WMS、样本、通知」等能力按一张可版本的图串起来跑，自己**不持有任何实验业务数据**。
业务域通过 Spring Bean（`bean.invoke` / `validate`）、设备契约（`device.command` / `device.task`）、HTTP（admin/app 控制器）三种方式接入。

### 1.2 依赖方向（单向，无回环）

```
boheng-module-flow
  ├── boheng-module-device-api   ← 契约层（DeviceApi / DeviceCommandApi / DTO）
  ├── boheng-module-wms-api      ← 契约层（SampleApi 等）
  ├── starter-biz-tenant         租户拦截
  ├── starter-job                JobHandler
  ├── starter-security           @PreAuthorize / 登录用户
  ├── starter-mybatis            MP + BaseMapperX
  └── aviator                    表达式引擎
```

**关键约束**：flow 只依赖 `*-api` 契约，不依赖 `*-biz` 实现。设备台账/动作启用、参数集可用性、子流程是否已发布，全部通过回调接口问出去（`FlowGraphChecker.DeviceLookup` / `SubflowLookup`）。

### 1.3 明确不做的事

| 不做 | 原因 |
|---|---|
| 不存实验数据（样本、物料、结果） | 属 wms / 将来 data 域；flow 只存「这次运行发生了什么」 |
| 不做设备通信 | 全部经 `DeviceApi` / `DeviceCommandApi`，flow 不碰协议 |
| 不做业务补偿 | 节点失败只做「重试 / 跳过 / 整条失败」，业务补偿由节点背后的 Bean 自己实现 |
| 不做分布式锁 | 并发靠数据库条件更新（CAS）+ 唯一键，不引 Redis 锁 |
| 不做画布渲染，也不存画布坐标 | `flow_node` 22 列里没有 `x/y`：前端按 `depends_on` 自动布局，坐标是纯前端状态 |

---

## 2. 两层模型：能力 × 编排正交

这是 v3 与 v2 最大的结构差异，也是全模块的设计地基。

```
┌──────────── 定义层（设计期，可版本） ────────────┐
│  flow_step   能力目录    step_type + type_config │  ← 一种能力 = 一个类型 + 一份配置
│  flow_definition 流程版本 pipeline_key + version │  ← 一个版本 = 一张图
│  flow_node   节点       能力引用 或 控制类节点     │  ← 一次引用 = 编排形态
└──────────────────────────────────────────────────┘
                    ↓ 发布（校验 + checksum 冻结）
┌──────────── 运行层（运行期，只增不改） ──────────┐
│  flow_execution        一次运行（冻结 version）  │
│  flow_step_execution   一次节点尝试（失败新行）  │
│  flow_execution_context 上下文（每键一行）        │
│  flow_step_signal      信号收件箱（幂等）        │
│  flow_branch_decision  分支决策快照              │
│  flow_device_task(+event) 设备拉取队列           │
│  flow_error_record(+action) 错误台账             │
└──────────────────────────────────────────────────┘
```

### 2.1 为什么拆两张表

`flow_step` 是**可复用能力**（"离心 5 分钟"、"读板 OD600"），`flow_node` 是**这条流程里的这一次使用**。同一个能力可以在一条流程里出现三次，各自有不同的前置、参数映射、超时、失败策略——这些差异只能挂在 `flow_node` 上，不能污染 `flow_step`。

节点的**生效类型与配置**遵循唯一一条规则（`FlowDraftAssembler.toDraft`，全模块唯一实现）：

```
生效 stepType   = node.step_type   非空 ? node.step_type   : step.step_type
生效 typeConfig = node.type_config 非空 ? node.type_config : step.type_config
```

发布校验、结构校验、详情展示、运行期 `PipelineGraph.of` 全部走这一条规则，保证「校验看到的图」= 「跑起来的图」。

### 2.2 控制类节点不挂能力

`route` / `loop` / `subflow` / `timer.wait` 是控制类（`StepTypeRegistry.isControlType`），它们的 `step_id` 必须为空，逻辑全在 `type_config` 里。
反向约束同样成立：`createRelation` 时若给了 `stepType` 但不是控制类，直接抛 `FLOW_STEP_CONTROL_TYPE`——**能力型类型不允许在节点上凭空写配置**，必须走 `flow_step`。

### 2.3 节点的解剖：22 列分成五组

`flow_node` 22 列 = 主键 1 + 业务列 15 + 审计列 6。业务列按「**谁在读它**」正好分成四组（配图：L2 节点解剖树）：

| 组 | 列 | 谁读 | 改它的后果 |
|---|---|---|---|
| 身份 | `definition_id` `node_id` `node_key` | 唯一键、`loadGraph` | 等于换节点，历史 `flow_step_execution.node_id` 对不上 |
| **意义** | `step_type` `name`（`output_schema` 在 `flow_step` 上） | 人（画布 / 评审）、下游节点、发布校验器 | 等于改设计，必须新版本 + 新 checksum |
| **功能** | `step_id` `type_config` `run_config` | 引擎 + 具体 `StepExecutor` | 只是调参，图不变 |
| 编排 | `sort_no` `depends_on` `join_policy` `join_failure_policy` `branches` `input_mapping` `output_mapping` | `DagScheduler` / `BranchResolver` / 表达式引擎 | 等于改拓扑，要重判就绪与并行度 |

还有两个「不落库」的派生概念，容易和列混起来：

- `role`（ACTION / WAIT / CONTROL / SUBFLOW）：由 `step_type` 查 `StepTypeRegistry` 得出，改 `step_type` 时自动跟着变，所以不该有列。
- `completionMode`（SYNC / CALLBACK / SIGNAL / TIMER / CHILD）：由 `StepTypeDescriptor` 声明，同上。

它们决定调度器怎么对待这个节点，但都不是列——**能派生的一律不落库**，避免两处真相。

### 2.4 意义（meaning）与功能（function）怎么区分

一句话判据：

> **换掉这个值之后，「这张图还是不是同一个流程」？**
> 不是 → 这是**意义**；还是 → 这是**功能**。

| 维度 | 意义 | 功能 |
|---|---|---|
| 回答的问题 | 这个节点在图里**代表什么** | 它靠**什么**跑起来 |
| 承载列 | `step_type` / `name` / `output_schema`（step 上） | `step_id` / `type_config` / `run_config` |
| 读者 | 人、下游节点、`FlowGraphChecker` | `FlowExecutionEngine` + `StepExecutor` |
| 变更路径 | 改 → 重新评审 → 新版本 + 新 checksum | 改 → DRAFT 内直接改 → 只需重新发布 |
| 例子 | `device.command` → `manual.task`：这一步从「自动离心」变成「等人按按钮」 | `runConfig.maxAttempts` 3 → 5：还是离心，只是多试两次 |

三个具象判例，把边界钉死：

1. **`step_type` 从 `device.command` 换成 `device.task`** → 意义变了。推变成拉，下游看到的数据形态、失败时延全变，必须重新评审。
2. **`type_config.deviceCode` 改型号** → 功能变了。图、前置、产出都没动。
3. **`depends_on` 加一个上游** → 既不是意义也不是功能，是**编排**。`flow_step` 上根本没有这一列，因为它描述的是「这张图怎么连」，不是「这个能力是什么」。

### 2.5 为什么必须这样分（三条硬理由）

这三条不是审美，是代码里已经落下来的约束：

1. **校验拦意义层，不拦调参。** `FlowGraphChecker.checkPublish` 只查五类：类型是否注册、控制类是否误挂能力、`type_config` 结构（`StepTypeDescriptor.validate`）、设备是否在台账且动作启用、`output_mapping` 是否落在 `output_schema` 声明里（§10.2）。全部是「这张图对不对」。而 `run_config`（超时 / 重试 / 失败策略）一个都不拦——调参不该阻塞发布。
2. **冻结冻的是意义 + 编排。** `flow_execution` 钉死 `definition_id + version + checksum`，即冻结「这张图长什么样」。而 `run_config` 允许运行期按节点覆盖：`FlowExecutionEngine` 先取 `node.runTimeoutMs()` / `runMaxAttempts()` / `runFailureStrategy()`，为空才回落到 `flow_step.timeout_ms` / `max_attempts`（`FlowExecutionEngine:1028/1043`、`:487`）。功能层可覆盖、意义层不可覆盖——这是分层带来的直接好处。
3. **复用只能靠拆分。** 若把 `type_config` 写进 `flow_step`，同一台离心机在一条流程里出现三次就共用一份配置，超时无法逐个调；若把 `depends_on` 写进 `flow_step`，同一个能力就无法在不同流程里换前置。反过来，若把 `step_type` 只写在 `flow_node` 上，就没有「能力目录」可复用，每张图都要重配一遍。

反例清单（写错会怎样）：

| 错误写法 | 后果 |
|---|---|
| 把 `type_config` 写进 `flow_step` | 三个节点共用一份配置，超时/重试无法逐节点调 |
| 把 `depends_on` 写进 `flow_step` | 同一能力无法在不同流程里换前置 |
| 在 `flow_node` 上给能力型类型直接写 `type_config` 而不给 `step_id` | 被 `FLOW_STEP_CONTROL_TYPE` 拒绝，发布不出去 |

---

## 3. 数据模型

### 3.1 12 张表速查

| 表 | 粒度 | 写入方式 | 关键约束 |
|---|---|---|---|
| `flow_definition` | 流程版本 | 草稿可改，发布后只读 | `uk(tenant_id, version_key)`，`version_key = pipeline_key#version` 生成列 |
| `flow_step` | 能力 | CRUD，被引用后禁改 | `uk(tenant_id, step_key_guard)` |
| `flow_node` | 节点 | 只允许在 DRAFT 下改 | `uk(tenant_id, node_key)`，`node_key = definition_id#node_id` 生成列 |
| `flow_execution` | 运行 | 只增 + 状态推进 | `uk(tenant_id, execution_no_guard)`、`uk(tenant_id, business_key_guard)` |
| `flow_step_execution` | 节点尝试 | 只增（重试新行） | `uk(tenant_id, row_key)`，`row_key = exec#node#iter#attempt` |
| `flow_execution_context` | 上下文键 | 只增（同键先到者赢） | `uk(tenant_id, ctx_row_key)`，`ctx_row_key = exec#ns#node#key` |
| `flow_step_signal` | 信号 | 只增 | `uk(tenant_id, signal_id_guard)` |
| `flow_branch_decision` | 决策 | 只增 | 无唯一键（一次决策一行） |
| `flow_device_task` | 设备任务 | 状态机推进 | `uk(tenant_id, task_no_guard)` |
| `flow_device_task_event` | 任务事件 | 只增 | 无 |
| `flow_error_record` | 错误台账 | 只增 + 状态 | `uk(tenant_id, dedup_key_guard)` |
| `flow_error_action` | 处置动作 | 配置表 | — |

> 字段级全貌见全量 ERD：`.archify/flow-erd-all-tables-20261111-110139/flow-erd-all-tables.html`（12 表 / 246 字段，定义期 3 表在左、运行 7 表中列、错误 2 表在右）。字段数核对：定义层 16+18+22 = 56，运行层 29+29+14+19+15+24+14 = 144，错误层 34+12 = 46，合计 246。

### 3.2 ★ 统一模式：逻辑删除安全的唯一键

全库 0 个裸 UNIQUE。所有业务唯一键都用「生成列 + 唯一键」：

```sql
`row_key` varchar(96) GENERATED ALWAYS AS (
    IF(`deleted` = b'0', CONCAT(`execution_id`,'#',`node_id`,'#',`iteration_no`,'#',`attempt`), NULL)
) VIRTUAL,
UNIQUE KEY `uk_flow_step_execution` (`tenant_id`, `row_key`)
```

逻辑删除后生成列变 `NULL`，MySQL 唯一键对 `NULL` 不生效 → **删掉的行自动让出唯一性**，不需要 `deleted = 0` 这类会破坏索引下推的写法。

### 3.3 运行层 ERD 要点（全量图的运行期切片）

- `flow_execution` 是运行层唯一的「根」，`flow_step_execution` / `flow_execution_context` / `flow_branch_decision` 三张表都以 `execution_id` 聚合（**逻辑外键，DDL 无 FK**，靠应用保证）。
- 自引用：`flow_execution.parent_execution_id` + `parent_node_id` + `parent_attempt` + `parent_group_index` 四元组定位「我是哪个父节点的第几组子运行」。SINGLE 子流程 `group_index = NULL`，FANOUT 为组下标。
- `flow_step_execution` 是**最热**表：一次运行 N 个节点，每个节点每次重试、每轮循环都新增一行。它是「时间线」而不是「当前状态」——当前状态 = `max(iteration_no, attempt)` 那一行（`latestByNode`）。
- `flow_execution_context` 是 **K-V 表**而不是一行 JSON：好处是增量写入无锁竞争，坏处是装载上下文要扫全表（`selectByExecutionId`），一次运行几十上百行，可接受。

### 3.4 运行表上的冻结字段

`flow_execution` 冻结了 4 个定义侧字段：`pipeline_key`、`version`、`definition_id`、`checksum`。
设计意图是「一次运行永远看到同一张图」。**但当前实现只冻结不校验**：`loadGraph(execution)` 每次 `advance` 都按 `definition_id` 重新查定义表并 `freeze()`，不比对 `execution.checksum`。配合 `rollbackDraft`（已发布版本可退回草稿再编辑），运行中改图会影响在跑的运行。见 §14 R1。

---

## 4. 引擎执行链

`FlowExecutionEngine`（1,117 行）是全模块唯一的推进器。**它不持有任何内存态运行对象**：每一轮都从库里重新装载，因此任意进程、任意时刻都能接管推进。

### 4.1 主链路

```
start(def, input, ...)
  ├─ freeze(definition)                    # 定义 → PipelineGraph（节点 + 能力快照）
  ├─ insert flow_execution                 # 冻结 version / checksum
  ├─ saveInput / saveSystem(3 个)          # 上下文 INPUT + SYSTEM
  ├─ 为「不在任何 loop body 内」的节点各插一行 PENDING(iter=0, attempt=1)
  └─ advance(execId)

advance(execId)
  ├─ 幂等前置：非 RUNNING 直接返回
  ├─ loadGraph + selectByExecutionId → latestByNode
  ├─ DagScheduler.decide(graph, latest)
  │    ├─ skipByFailure → 按 join_failure_policy 置 SKIPPED(skip) 或 DEAD(FAIL)
  │    └─ readyNodeIds  → 逐个 CAS 认领 → dispatch
  ├─ driveLoops()                          # body 全终态的 loop 求值是否再来一轮
  ├─ 再 decide + 认领一轮                   # 收下刚展开的新一轮 body 行
  └─ checkFinish()                         # 全终态 → 汇总输出 → finalize

dispatch(node)
  ├─ loop     → driveLoopBegin()            # 展开 body 第 0 轮
  ├─ subflow  → driveSubflow()              # 建子执行 + 父节点 WAITING/CHILD
  ├─ device.command → worker.submit()       # 阻塞调用必须离开推进线程
  └─ 其余      → executeStep() + settle()    # 轻量类型当场执行
```

### 4.2 ★ 并行不是 fork，是「就绪判定的天然结果」

`depends_on` 是唯一的图真相源（`sort_no` 只用于展示排序）。一个节点被多个节点 `depends_on` 引用，它就自动并行——**没有 fork / join 节点类型**。
`DagScheduler.decide` 是纯函数（静态方法，不碰库），输入冻结图 + 最新行，输出 `(readyNodeIds, skipByFailure)`，可单测（`DagSchedulerTest`）。

就绪判定矩阵：

| `join_policy` | 前置未全终态 | 前置全终态 |
|---|---|---|
| `ALL_SUCCESS`（默认） | WAIT | 全满足 → READY；否则 FAIL |
| `ANY_SUCCESS` | 任一满足即 READY | 任一满足 → READY；否则 FAIL |
| `ALL_DONE` | WAIT | 一律 READY（成败都放行） |

「满足」的定义（`isSatisfied`）：`SUCCESS`，或 `SKIPPED` 且原因为 `BRANCH` / `UPSTREAM`（**裁剪不是失败**）。

前置判失败后本节点怎么办：`join_failure_policy = SKIP` → 本节点 `SKIPPED/FAILURE`；`= FAIL`（默认）→ 本节点 `DEAD` + `errorCode=FLOW_JOIN_FAILED`。

### 4.3 ★ 认领是 CAS，不是锁

```java
update flow_step_execution
   set status='RUNNING', started_at=now()
 where id=? and status='PENDING'
// 返回 1 才算认领成功，其余线程空转返回
```

同一时刻多个线程（信号回填、worker 回调、人工确认、定时扫描）都可能调 `advance`，只有一个能认领同一个 PENDING 行。这是全模块并发安全的唯一机制——**没有分布式锁，没有 select-for-update**。

### 4.4 结算（settle）只从两个状态收口

`settle` 开头就判：

```java
if (!RUNNING.equals(fromStatus) && !WAITING.equals(fromStatus)) return false;
```

然后所有写入都带 `WHERE status = fromStatus`（`casStep`）。这解决两个真实竞态：

1. **设备回报 vs 超时扫描同时到达** → 后到的一方改不到行，静默返回 false，不会出现「先成功后失败」。
2. **重复信号** → 第一次结算把状态改走，第二次 `casStep` 返回 0。

四种 `StepOutcome.Kind`：

| Kind | 落库 | 副作用 |
|---|---|---|
| `SUCCESS` | `SUCCESS` + output | 写上下文 NODE 命名空间（按 output_mapping 归一化） |
| `FAILED` | 未到上限 → `FAILED` + 新插 attempt+1 行；到上限 → `DEAD` | `recordStepError`；FAIL_FAST 时整条 `failExecution` |
| `WAITING` | `WAITING` + `waiting_for` + `wait_ref` + `command_id/no` + `deadline_at` | 等外部事实 |
| `BRANCH` | `SUCCESS` + `selected_branch` | 落 `flow_branch_decision` 快照 + `BranchResolver.apply` 裁剪 |

### 4.5 分支裁剪（BranchResolver）

route 节点只负责算出一个出口 key，**图操作归 BranchResolver**：

1. 选中出口的目标保持 PENDING；
2. 未选中出口的直接目标 → `SKIPPED/BRANCH`；
3. 从这些目标出发 BFS，凡只能由它们到达的下游 → `SKIPPED/UPSTREAM`。

BFS 只看 `graph.dependents`（正向扇出反查表），**不判断「是否还有其它存活入边」**——一个被裁剪节点若同时被选中分支依赖，会被误裁。当前靠发布校验（参数映射只能取直接前驱）+ 手工编排习惯规避，属于**待加固点**（§14 R4）。

### 4.6 循环（loop）是引擎展开子图，不是引擎递归

loop 节点自己不执行任何业务，它的行长期停在 `RUNNING`：

```
loop 开始 → 为 body 每个节点插 (iter=0, attempt=1) PENDING → loop 行保持 RUNNING
每轮 advance → driveLoops():
    body 当前轮是否全终态？
      否 → continue
      是 → 求值 whileExpression（且 currentIter + 1 < maxIterations）
           真 → 为 body 插 (iter+1) 行
           假 → loop 置 SUCCESS，output = {"iterations": N}，写上下文
```

- body 范围 = `sort_no` 在 `[bodyEntryNodeId, bodyExitNodeId]` 闭区间内的节点（`loopBody`）。这是 `sort_no` **唯一**参与语义的地方，其余纯展示。
- **启动时 body 节点不建行**（`collectLoopBodyNodes`），否则会和展开的行冲突。
- 循环条件求值失败按「退出」处理（`log.warn`，不抛）。

### 4.7 子流程 / 扇出（subflow）

| 模式 | 行为 |
|---|---|
| `SINGLE` | 建 1 个子执行，父节点 `WAITING/CHILD`，`wait_ref = exec:node:attempt` |
| `FANOUT` | 调分发 Bean 算计划（`DispatchPlanEntry[]`，上限 50 组），幂等建 N 个子执行，父节点 `WAITING/CHILD` |

**幂等三件套**：

1. Java 侧先按 `business_key` 查（`findByBusinessKey`），存在就不再建；
2. `business_key` 格式固定 `父exec:父node:attempt:组号`（`fanoutBusinessKey`）；
3. DB 侧 `uk_flow_execution_fanout` 兜底并发。

**汇合**：每个子执行 `finalizeExecution` 时给父发 CHILD 信号 → `onChildFinished` 检查「全部子执行是否终态」→ 按 `completion_policy`（`ALL_SUCCESS` / `ANY_SUCCESS`）结算父节点。空计划（0 组）直接 SUCCESS + `{"children":0}`。

### 4.8 收尾

- `checkFinish`：全节点行终态 → 有 `DEAD/FAILED` 则整条 FAILED，否则汇总各节点 `output_data` 成 `{"nodeId": {...}}` 写 `flow_execution.output_data` 并 SUCCESS。
- `failExecution`：未开始的 PENDING 全部 CANCELLED，运行 FAILED。
- `cancel`：同上，运行为 CANCELLED。
- `finalizeExecution` 里：清节点维度日志上下文 + 是子执行就给父发 CHILD 信号。

---

## 5. 节点类型体系

### 5.1 注册机制

一种类型 = `StepTypeDescriptor`（说明书）+ `StepExecutor`（执行体），各自实现同一个 Spring Bean 即可，**不改引擎一行代码**。

`StepTypeRegistry.init()` 在启动期做自检，任何一条不满足**直接让应用起不来**（类型系统是发布校验的地基，不能带病启动）：

- `type()` 不能为空、不能重复；
- Executor 必须有对应 Descriptor；
- Descriptor **允许**没有 Executor（`loop` / `subflow` / `route` 由调度器直接驱动）。

### 5.2 现有 11 种类型

| type | role | completionMode | configType | retryable |
|---|---|---|---|---|
| `device.command` | ACTION | CALLBACK | `DeviceCommandConfig` | ✔ |
| `device.task` | ACTION | CALLBACK | `DeviceTaskConfig` | ✔ |
| `bean.invoke` | ACTION | SYNC | `BeanInvokeConfig` | ✔ |
| `script.run` | ACTION | SYNC | `ScriptConfig` | ✔ |
| `notification.send` | ACTION | SYNC | `NotificationConfig` | ✔ |
| `manual.task` | WAIT | SIGNAL | `ManualTaskConfig` | ✘ |
| `timer.wait` | WAIT | TIMER | `TimerWaitConfig` | ✘ |
| `route` | CONTROL | SYNC | `RouteConfig` | ✘ |
| `validate` | CONTROL | SYNC | `ValidateConfig` | ✘ |
| `loop` | CONTROL | SYNC | `LoopConfig` | ✘ |
| `subflow` | SUBFLOW | CHILD | `SubflowConfig` | ✘ |

**retryable 的语义**：物理动作（设备命令、脚本、Bean 调用）可以重发；人工确认和路由重试无意义，失败直接 DEAD。

### 5.3 ★ 两类设备节点的区别（容易混）

| | `device.command` | `device.task` |
|---|---|---|
| 交互 | 平台**推**给在线设备，设备域内部阻塞等回调 | 平台**入队**，设备端**拉**取执行后回报 |
| 设备离线 | 直接失败 | 任务留在队列里等 |
| 并发影响 | 占一个 `flow-worker` 线程直到回调或超时 | 立即返回 WAITING，不占线程 |
| 回调凭据 | `commandId`（设备域命令主键） | `taskNo` + `callbackToken`（常量时间比对） |
| 结算入口 | `deviceCommandSignal`（sweeper 轮询命令终态） | `deviceTaskSignal`（`/app-api` 主动 complete） |

### 5.4 类型扩展点（三种，互不干扰）

1. **新增节点类型**：写 Descriptor + Executor → `@Component` → 自动进注册表 + 发布校验 + 前端类型下拉（`/flow/step-type/beans`）。
2. **新增扇出策略**：实现 `FlowDispatchAction` → 注册进 `FlowDispatchRegistry` → `subflow.type_config.dispatch.beanName` 引用。
3. **新增路由策略**：实现 `FlowRouteAction` → `FlowRouteRegistry`。

`bean.invoke` 另有一层白名单：`@PostConstruct` 时从 `ObjectProvider<FlowBeanAction>` 收集所有实现类，发布校验时 `validate()` 检查 beanName 已注册——**不能通过填任意 bean 名调到容器里的任意对象**。

---

## 6. 状态机

### 6.1 运行（`FlowRuntimeEnums.ExecutionStatus`）

```
PENDING ──▶ RUNNING ──┬──▶ SUCCESS
                      ├──▶ FAILED      （节点 DEAD / failExecution）
                      └──▶ CANCELLED   （人工取消）
终态：SUCCESS / FAILED / CANCELLED
```

注意：`start()` 直接建 `RUNNING`，**PENDING 实际不会被写入**——`cancelExecution` 允许 PENDING/RUNNING，但库里只会有 RUNNING。

### 6.2 节点（8 态）

```
                    ┌──────────── 重试（新插 attempt+1 行）
                    ▼                    │
PENDING ──CAS──▶ RUNNING ──┬──▶ SUCCESS  │
                           ├──▶ FAILED ──┘
                           ├──▶ DEAD        （次数用尽 / join 失败）
                           ├──▶ WAITING ──┬─▶ RUNNING（信号回填）
                           │              └─▶ ? （超时由 sweeper 结算成 FAILED）
                           ├──▶ SKIPPED     （BRANCH / UPSTREAM / FAILURE）
                           └──▶ CANCELLED   （运行取消 / FAIL_FAST）
终态：SUCCESS / FAILED / DEAD / SKIPPED / CANCELLED
```

**FAILED 也是终态**：它表示「这次尝试结束了，可重试」，下一行才是真正继续。所以 `isTerminal` 把 FAILED 算终态，`checkFinish` 里同时看 `DEAD || FAILED` 判整条失败。

### 6.3 设备任务（5 态）

```
PENDING ──claim(CAS)──▶ CLAIMED ──complete──▶ DONE
   │                       │                └▶ FAILED
   └──────timeout───▶ FAILED ◀──timeout────┘
   └──────取消──────▶ CANCELLED
```

每次状态迁移都写一行 `flow_device_task_event`（insert-only），带 `fromStatus / toStatus / claimedBy / payload / occurredAt`。

---

## 7. 并发与幂等：五道防线

| # | 场景 | 机制 | 落点 |
|---|---|---|---|
| 1 | 多线程抢同一个 PENDING 节点 | `update ... where status='PENDING'`，影响行数=1 才算赢 | `claim()` |
| 2 | 结算竞态（回报 vs 超时） | `update ... where status=fromStatus` | `casStep()` |
| 3 | 重复插入节点行（并发展开同一轮 body） | `uk_flow_step_execution(row_key)` + 捕获 `DuplicateKeyException` 静默跳过 | `insertStepRow()` |
| 4 | 重复信号 | `uk_flow_step_signal(signal_id_guard)`，signalId 由 `类型_exec_node_attempt` 拼 | `FlowSignalDispatcher.save()` |
| 5 | 重复扇出建子执行 | `business_key` 先查 + `uk_flow_execution_fanout` 兜底 | `driveSubflow()` |

外加 **上下文行唯一键**（`ctx_row_key`）：并发 settle 同节点同轮同 key 时，先到者写入，后到者 `DuplicateKeyException` 静默跳过。

**这套机制的前提是「单库单写」**：所有状态迁移都是 `UPDATE ... WHERE`，没有跨库事务。多实例部署天然安全，但**依赖数据库行锁**，热点运行会在这张表上排队。

---

## 8. 信号与后台扫描

### 8.1 四个信号入口（统一走 `FlowSignalDispatcher`）

| 入口 | signalId 格式 | 触发者 |
|---|---|---|
| `manualSignal` | `manual_{exec}_{node}_{attempt}` | `POST /flow/signal/manual` |
| `timerSignal` | `timer_{exec}_{node}_{attempt}` | Sweeper 到期 |
| `dispatchChildSignal` | `child_{childExecId}` | 子执行 finalize |
| `deviceTaskSignal` | `devicetask_{exec}_{node}_{attempt}` | `POST /app-api/flow/device-task/complete` |
| `deviceCommandSignal` | `devicecommand_{commandId}` | Sweeper 轮询命令终态 |

统一套路：**定位等待行 → 校验 waiting_for/attempt 匹配 → 查重 signalId → 抢业务行（任务 finish / 命令终态）→ settle → 落收件箱**。
顺序很关键：先抢业务行再 settle，抢不到就**不占信号号**，否则会出现「信号已记、节点没推进」的假成功。

### 8.2 Sweeper（`FlowExecutionSweeper`）

单线程守护线程，`scheduleWithFixedDelay(30s 首延, 20s 周期)`：

```
TenantUtils.executeIgnore:
  select flow_execution where status='RUNNING'
    for each → TenantUtils.execute(tenantId):
        selectWaiting(execId)   # status='WAITING'
          ├─ waiting_for=DEVICE_COMMAND_CALLBACK → deviceCommandSignal(row)   # 不看 deadline
          ├─ deadline_at 为空                    → 跳过（MANUAL 无限期）
          ├─ TIMER 到期                          → timerSignal
          └─ DEVICE_CALLBACK 到期                → deviceTaskTimeout
```

跨租户用 `executeIgnore` + 逐个 `execute(tenantId)`，照设备域 Sweeper 的模式。**异常全吞**（`log.error`），不让调度线程死掉。

### 8.3 三条超时语义

| 配置 | 语义 |
|---|---|
| `timeoutMs > 0` | 等待 deadline = now + timeoutMs，到期按失败结算 |
| `timeoutMs < 0`（-1） | 永不超时，`deadline_at` 不落，Sweeper 跳过 |
| 不配 | 回落 `flow_step.timeout_ms`，再回落全局 300s |

生效优先级：**节点 `run_config.timeoutMs` > 能力 `flow_step.timeout_ms` > 全局默认**（`effectiveTimeout`）；`maxAttempts` 同理（`run_config` > `flow_step` > 1，上限 5）。

---

## 9. 上下文与表达式

### 9.1 三个命名空间，一张展平 Map

`loadEnv(execId)` 从 `flow_execution_context` 装载并拼成：

```json
{
  "input": { "sampleCode": "S001" },
  "sys":   { "executionNo": "exe_123", "executionId": 123, "businessKey": "" },
  "node":  { "weigh": {"output": {...}, "weight": 12.5}, "weigh.weight": 12.5 }
}
```

`node` **同时保留嵌套和平铺两种写法**（`flatten`），所以表达式里 `node.weigh.weight` 和 `node['weigh.weight']` 都能命中。

### 9.2 参数解析优先级（`resolveParams`）

```
参数集默认值 (device 节点 bindParamSetId)
  → 能力默认输入 (flow_step.default_params，剥掉 __comments__)
    → 节点参数映射 (flow_node.input_mapping，三种来源)
```

映射三种来源：

| source | 取值 | 示例 |
|---|---|---|
| `INPUT` | `pathGet(input, field)` | `sampleCode` |
| `NODE` | `pathGet(node[nodeId], field)` | `children[0].executionId` |
| `LITERAL` | `plainValue(value)`（尽量还原创始类型） | `3000` |

`pathGet` 支持点分下钻 + 数组下标（`a.b[2].c`），是 INPUT/NODE 共用的**唯一实现**。取不到返回 `null` 不抛异常。

### 9.3 输出归一化

`saveNodeOutput(execId, nodeId, iter, outputJson, outputMapping)`：
- `output_mapping` 为空 → 整个输出存成 `node.{nodeId}.output`；
- 非空 → `{源字段路径: 上下文键}`，逐字段 `pathGet` 后存 `node.{nodeId}.{上下文键}`。

发布期 `checkOutputContract` 会校验：源字段必须落在 `flow_step.output_schema` 声明里；两个源字段归一化到同一上下文键 → 报错（后者会覆盖前者）。

### 9.4 表达式（aviator）

`ExpressionEvaluator` 被四处共用：`route.routerExpression`、`loop.whileExpression`、`script.run`、`notification` 内容占位。
`checkSyntax` 在**发布期**编译一次，写错当场拒绝，不等运行期才炸。

### 9.5 ★ 一个必须知道的坑

`ExecutionContextHolder.encodeValue` 对 `String` 用 `JSONUtil.quote()` 而不是 `toJsonStr()`——因为 hutool 的 `toJsonStr(String)` 直接返回原值，写进 MySQL `JSON` 列会报「Invalid JSON」。读回时 `parseValue` 按首字符分派（`{` / `[` / `"` / 字面量）。这条注释在代码里留了 3 处，**改这块前先读它**。

---

## 10. 定义层校验矩阵

`FlowGraphChecker`（457 行，纯静态、不碰库）+ `FlowDraftAssembler` 组成了「画得出 ≠ 跑得起来」的全部防线。

### 10.1 两档校验

| 时机 | 方法 | 覆盖 |
|---|---|---|
| 保存草稿 / 增删节点后 | `checkStructure` | 节点身份 + 节点数上限 + 图关系 + 环检测 |
| 发布（`publishDefinition`） | `checkPublish` | 上者全部 + 类型已注册 + Descriptor 校验 + 设备台账 + 子流程已发布 + 输出契约 |

`assertStructure` 在 `createRelation` / `updateRelation` / `deleteRelation` 之后各调一次，保证**画的过程中就不会画出非法图**。

### 10.2 校验项清单

| 类别 | 项 |
|---|---|
| 身份 | nodeId 匹配 `^[a-z][a-z0-9_]{0,31}$`、不重复；name 非空 ≤128；stepType 非空；节点数 ≤ 60 |
| 图关系 | 前置存在；无环（Kahn）；单前置不能用 ANY_SUCCESS；`NODE` 映射只能取直接前驱；route 出口目标存在；loop 区间端点存在且不含自己 |
| 类型配置 | 类型已注册；控制类不能挂 step_id；`Descriptor.validate(config)`；表达式语法 |
| 外部引用 | 设备在台账 + 动作启用（`DeviceLookup`）；子流程已发布（`SubflowLookup`）；子流程链无环、深度 ≤ 5 |
| 输出契约 | `output_mapping` 源字段 ⊆ `output_schema`；上下文键不撞车 |

### 10.3 发布指纹（checksum）

对节点集合做**规范化排序后**拼接（nodeId / stepType / typeConfig / dependsOn / joinPolicy / timeout / maxAttempts / failStrategy / outputMapping / branches / mappings），SHA-256。同一张图每次发布得到同一个值。

---

## 11. 错误处理（E-1 ~ E-3）

三层，**不互相替代**：

| 层 | 落点 | 用途 |
|---|---|---|
| E-1 节点失败 | `flow_step_execution.error_code/error_message` | 这次尝试为什么失败 |
| E-2 错误分类 | `FlowErrorClassifier` → `source_type / category / severity / certainty / retry_mode` | 失败是不是可重试的、谁的责任 |
| E-3 错误台账 | `flow_error_record`（`dedup_key` 幂等）+ `flow_error_action` | 跨运行汇总、人工处置、统计 |

`recordStepError` 在 `settle` 的 FAILED 分支调用，**失败不影响主结算路径**（try-catch 吞掉，只 `log.warn`）——错误台账是旁路，不能因为它挂了把运行挂了。

`dedup_key` 保证同一个（运行, 节点, 轮次, 尝试）只落一条：`selectByDedupKey` 先查，有就复用。

---

## 12. 日志

`FlowLogContextHolder`（ThreadLocal：`executionNo` + `nodeId`）+ `FlowLogConverter`（logback `%flow` 转换词）。

输出形如：

```
2026-11-09 10:00:01 INFO [exe=exe_123 node=weigh] c.b.f.m.f.e.FlowExecutionEngine - [flow] 节点开始 node=weigh type=device.command iter=0 attempt=1
```

非流程线程无上下文，输出空串，**不污染全系统日志**。

跨线程必带：`FlowExecutionWorker.submitDeviceStep` 在任务入口显式 `setExecutionNo/setNodeId`，`finally` 里 `clear()`。这是唯一一处跨线程传递，漏了就是设备节点日志串不到运行上。

---

## 13. 对外接口

### 13.1 admin（`/admin-api/flow/**`，全部 `@PreAuthorize`）

| 分组 | 端点 |
|---|---|
| 定义 `/flow/definition` | create / save / delete / get / page / simple-list / validate / publish / retire / rollback-draft / revise |
| 编排 `/flow/relation` | create / update / delete / move / layout / page |
| 能力 `/flow/step` | create / update / delete / get / page / list |
| 类型 `/flow/step-type` | beans（前端类型下拉 + 配置 schema） |
| 运行 `/flow/execution` | start / cancel / get / page / step-page |
| 信号 `/flow/signal` | manual |
| 设备任务 `/flow/device-task` | page |
| 错误 `/flow/error-record` | get / page |

### 13.2 app（`/app-api/flow/device-task`，`@PermitAll`）

| 端点 | 说明 |
|---|---|
| `claim` | 机器端拉任务。`deviceCode` 必传，`actionCode` 可选；平台按设备类型反查能力集匹配 |
| `complete` | 机器端回报。`taskNo` + `callbackToken` + success + result/error |

`@PermitAll` 是**有意的**：机器端不走用户登录体系，安全边界落在 `callbackToken`（`MessageDigest.isEqual` 常量时间比对）+ `taskNo` 不可预测（雪花）。

### 13.3 定时触发

`FlowTriggerJobHandler implements JobHandler`，参数 JSON：

```json
{ "tenantId": 1, "definitionId": 100, "inputParams": "{\"a\":1}", "businessKey": "daily-001" }
```

不走 `@TenantJob` 逐个租户跑——租户由参数显式指定，避免一条任务串跑所有租户。

---

## 14. 已知约束与风险

按「会不会咬人」排序，全部是**代码事实**，不是猜测。

| # | 风险 | 证据 | 影响 | 建议 |
|---|---|---|---|---|
| **R1** | **定义漂移不检测**：`loadGraph` 每次 `advance` 按 `definition_id` 重查，`execution.checksum` 从不比对；`rollbackDraft` 允许已发布版本退回草稿再改 | `FlowExecutionEngine.loadGraph`；`ErrorCodeConstants.FLOW_EXECUTION_CHECKSUM_DRIFT` **声明了但全模块 0 处使用** | 运行中改图 → 在跑的运行换了一张图继续跑，`checksum` 形同虚设 | `loadGraph` 里比对 `definition.getChecksum()` vs `execution.getChecksum()`，不一致抛 `CHECKSUM_DRIFT`；或 `rollbackDraft` 前检查有无 RUNNING 运行 |
| **R2** | **RUNNING 无租约**：进程崩溃后 `flow_step_execution` 行永久停在 `RUNNING`，`selectWaiting` 只捞 WAITING → 该运行永远不终态 | `FlowExecutionSweeper.scanOne`；`claim` 只写 `started_at` 无心跳 | 一次崩溃留下僵尸运行，占着业务流程不往前走 | 加 `heartbeat_at` 或复用 `started_at` + 超时：Sweeper 扫 `RUNNING 且 started_at 早于 N 分钟` 的行，置 `FAILED(可重试)` |
| **R3** | **device.command 阻塞占线程**：`DeviceCommandStepType.send` 内部等回调，Worker 池固定 8 线程 | `FlowExecutionWorker.POOL_SIZE = 8`（注释「一期固定」） | 8 台设备同时长等待 → 池满，后续设备节点排队，`advance` 不再推进 | 池大小配置化；或改 `device.command` 为「下发即 WAITING」，回调只走 sweeper（现在部分已如此，取决于设备域 `send` 是否阻塞） |
| **R4** | **分支裁剪会误裁汇聚节点**：BFS 只看向下游扇出，不判「是否还有存活入边」 | `BranchResolver.apply` | route 两条分支汇到同一节点时，后选中的那支的下游被裁掉，汇聚节点永不执行 | BFS 时检查目标节点的全部前置：若存在非裁剪路径可达的前置，则不裁（或改 SKIPPED 为 PENDING 等下一轮判定） |
| **R5** | **递归推进**：`settle → advance → dispatch → executeStep → settle` 是同步递归 | `settle` 末尾 `advance(row.getExecutionId())` | 60 节点直线流程 = 60 层嵌套 + 每层多次 SQL；极深时可栈溢出 | 改为「推进里程碑」：settle 只置脏标记，由一个循环 driver 统一推进；或对同步类型改用显式队列 |
| **R6** | **上下文全表装载**：每次 `loadEnv` 都 `selectByExecutionId` 全扫 | `ExecutionContextHolder.loadEnv`，被 `executeStep` / `recordDecision` / `resolveParams` 每次调用 | 长运行（循环 100 轮 × 10 节点）上下文上千行，每步全量装载 → O(n²) | 加 `iteration_no` 过滤或按 key 命中查询；或缓存在 run 级 ThreadLocal（有跨进程失效风险，需权衡） |
| **R7** | **`loop` body 内含 route 指向 body 外**：外部节点在第 0 轮就被裁成 SKIPPED，后续轮不再恢复 | `driveLoops` + `BranchResolver` 都按 `iterationNo` 取最新行，SKIPPED 是终态 | 循环里做「命中条件就跳出并执行外部节点」这种玩法不成立 | 文档约束：route 出口目标必须在同一 loop body 内；或引入 `break` 语义 |
| **R8** | **`PENDING` 运行态未使用**：`start` 直接写 RUNNING | `FlowExecutionEngine.start` | `cancelExecution` 允许 PENDING 是死代码；前端状态枚举多一个不可达值 | 要么用起来（start 先 PENDING 再推进），要么从枚举里去掉 |
| **R9** | **`flow_error_action` 表无运行期消费者** | 只有 Mapper + VO，`FlowErrorRecordServiceImpl` 未引用 `errorActionMapper` 的写入路径 | 处置动作配置了不生效 | 明确为 V2 预留，或补 `FlowErrorActionService` |
| **R10** | **`device.task` 认领无优先级/超时淘汰**：`selectClaimable` 未按 `createTime` 排序的语义未在 DDL 层强制 | `FlowDeviceTaskService.claim`（取一条 + CAS，失败重试 3 次） | 老任务可能被反复跳过 | 加 `order by create_time asc limit 1` 的索引支撑（`idx` 需覆盖 `status + device_code + create_time`） |

**R1 / R2 / R4 是三个「上线后会真出事」的点**，其余是容量与可维护性问题。

---

## 15. 自测与验收清单

现有单测 10 个（`src/test`）：`DagSchedulerTest` / `ExecutionContextHolderTest` / `FlowGraphCheckerTest` / `FlowExecutionEngineTest` / `FlowExecutionEngineErrorRecordTest` / `FlowSignalDispatcherTest` / `FlowErrorClassifierTest` / `FlowErrorRecordServiceImplTest` / `FlowTraceKeysTest` / `WaterRegisterTraceTest`。

补齐建议（按风险优先级）：

1. **R1**：发布后改图 → 运行中 `advance` 应抛 `CHECKSUM_DRIFT`（现在会静默跑新图）。
2. **R2**：模拟 RUNNING 行 + 超过阈值 `started_at` → Sweeper 应把它结算掉（现在不动）。
3. **R4**：route 两支汇到同一节点 → 汇聚节点应执行（现在会被裁）。
4. **并发**：两个线程同时 `advance` 同一运行 → 同节点只能被认领一次（现有 `claim` 覆盖，需补集成测试）。
5. **扇出幂等**：并发两次 `driveSubflow` 同一父节点 → 只建 N 个子执行（DB 唯一键 + Java 查双保险，需验证）。

验收口径（与现有实现对齐）：

- 一条 8 节点直线流程能跑完，`output_data` 汇总 8 个节点输出；
- 一个 route 三分支，只执行选中分支，另两支 `SKIPPED/BRANCH` 且下游 `UPSTREAM`；
- 一个 loop 跑 3 轮，body 三节点各 3 行（`iteration_no = 0/1/2`），loop 行为 `{"iterations":3}`；
- 一个 FANOUT 3 组的 subflow，父节点等 3 个子执行全终态后按策略结算；
- 一个 `manual.task` 挂 5 分钟后人工确认 → `SUCCESS`；不确认则 Sweeper 到期置 `FAILED`；
- 一个 `device.task` 由 `/app-api` claim + complete → 节点 `SUCCESS`；错误 token 被拒；
- 任一节点 `maxAttempts=2` 且首次失败 → 库里有 attempt=1（FAILED）+ attempt=2 两行。
