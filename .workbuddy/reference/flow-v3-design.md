# 实验流程第三版 · 能力与编排

状态：设计稿。上一版 `flow-v2.md` 只做到「定义 + 直线」。
本版要回答：**步骤类型可以有多种，编排可以做并行 / 串行 / 合并 / 循环 / 子流程**。

对齐关系：`flow-v2.md` 是已落地的现状（step / definition / relation 三表），本文是它的下一版。对照参照项目 `labillion-module-flow` 的取舍见 `flow-labillion-gap.md`。

---

## 0. 一句话

> Flow = **能力**（`step_type`，这一步「怎么执行」）× **编排**（依赖边 + 容器节点，这些步骤「怎么串」）。
> 两者正交，分别建表、分别校验、分别扩展。

把这两件事合成一个枚举（例如 `SERIAL_DEVICE` / `PARALLEL_SCRIPT`）会立刻爆炸：
`device.command` 既可以串行、也可以并行、也可以放进循环体。**能力 × 拓扑是组合，不是枚举。**

---

## 1. 需求拆解

用户原话 → 三个可落地的诉求：

| 原话 | 落到模型上 |
|---|---|
| 我定义一个流程，步骤可复用 | `flow_step` 是可复用能力目录；`flow_node` 是它在某条流程里的一次引用 —— **v2 已实现** |
| 步骤类型可以是多个：设备 / 脚本 / JavaBean / 人工 / 通知，「需要去区分开来」 | **`step_type` 字段 + 类型注册表**。新增能力只注册，不改 Flow 核心 |
| 每个任务的完成要有并行 / 串行 / 合并 / 循环 / 子流程 | **编排维度**。前三个靠依赖边，后两个靠结构化容器节点 |

「区分开来」的正确做法是**可注册的字符串 key + Descriptor**，而不是子类爆炸或 if-else 堆在 ServiceImpl 里。

---

## 2. 维度 A：步骤类型（怎么执行）

### 2.1 现状

- `FlowConstants.STEP_DEVICE_COMMAND = "device.command"` 写死；其它类型直接拒绝。
- `flow_step` 只有 `device_code` / `action_code` 两个列，装不下「脚本内容」「Bean 名」「通知对象」。
- `FlowDefinitionServiceImpl.toDraft()` 里硬编码 `device.command` 和 `FAIL_FAST`。

即：**v2 把「步骤类型」等同于「设备动作」**。这就是要改的地方。

### 2.2 目标模型：注册表 + Descriptor + Executor

```java
public interface StepTypeDescriptor<C> {
    String type();                       // 唯一 key，如 "device.command"
    StepRole role();                     // ACTION / CONTROL / WAIT / SUBFLOW
    CompletionMode completionMode();     // SYNC / CALLBACK / SIGNAL / TIMER / CHILD
    Class<C> configType();               // type_config 的强类型
    boolean retryable();
    void validate(C config);             // 发布期校验
}

public interface StepExecutor<C> {
    String type();
    StepOutcome execute(StepExecutionContext<C> ctx);
}
```

启动时 `StepTypeRegistry` 校验：`type` 不重复；Descriptor 与 Executor 成对；流程发布时类型必须已注册、`type_config` 必须能反序列化并通过 `validate`。

### 2.3 一期注册的类型

| type | role | completion | 谁实现 | 说明 |
|---|---|---|---|---|
| `device.command` | ACTION | CALLBACK | device 模块 | 复用现有 `DeviceHttpService.invoke`，Flow 只认 `DeviceCommandApi` |
| `bean.invoke` | ACTION | SYNC | flow + Spring 容器 | 调一个已注册的 Spring Bean 方法（你说的「执行一个 JavaBean」） |
| `script.run` | ACTION | SYNC | flow | 执行脚本 / 表达式 |
| `manual.task` | WAIT | SIGNAL | 前端确认 → 信号 | 等人确认 |
| `notification.send` | ACTION | SYNC | 通知出口（SPI） | 发通知 |
| `route` | CONTROL | SYNC | flow 内建 | 出口路由（你说的「拆分」），见 §3.2 |
| `timer.wait` | WAIT | TIMER | infra job（已有 quartz） | 定时等待 |
| `loop` | CONTROL | SYNC | flow 内建 | 循环容器 |
| `subflow` | SUBFLOW | CHILD | flow 内建 | 子流程（SINGLE 静态引用 / FANOUT 动态扇出，见 §3.4） |

依赖现状：`boheng-dependencies` 已管理 `aviator 5.4.4`，`boheng-common` 已有 `spring-expression` → **表达式求值不用引新依赖**。但 `script.run` 若要跑真脚本（Groovy/JSR-223）需要新引；`通知` 在本精简基座里**没有 notify 模块**（infra 只保留了 job / file / websocket）。

### 2.4 `flow_step` 字段调整

| 动作 | 列 | 说明 |
|---|---|---|
| 新增 | `step_type` varchar(64) NOT NULL DEFAULT 'device.command' | 类型 key |
| 新增 | `type_config` json | 类型专属配置，由 Descriptor 反序列化 |
| 新增 | `default_params` json | 通用默认输入，节点可覆盖 |
| 收敛 | `device_code` / `action_code` | 见下 |

`device_code` / `action_code` 的归属是**本版第一个要你拍板的决策**：

- **方案 甲（推荐·彻底）**：删掉这两列，`device.command` 的配置全部放进 `type_config = {"deviceCode":..,"actionCode":..}`。能力完全由 `(step_type, type_config)` 表达，加新类型零改表。代价：改现有前端步骤表单与 `FlowGraphChecker` 的设备校验取数路径。
- **方案 乙（保守）**：保留两列作为 `device.command` 的「快捷列」，其余类型只写 `type_config`。代价：表上出现「有的类型用列、有的用 JSON」的双轨，长期会烂。

---

## 3. 维度 B：编排驱动（怎么串）

你给的五个词，落到模型上是**两种机制**——这一点必须分清：

| 词 | 机制 | 落点 |
|---|---|---|
| 串行 | 依赖边 | 单前驱单后继 |
| 并行 | 依赖边 | 扇出 fork：一个节点被多个后继 `depends_on` |
| 合并 | 依赖边 + 策略 | 扇入 join：一个节点 `depends_on` 多个 + `join_policy` |
| 循环 | **结构化容器节点** | `loop` 节点把循环体包住，对外无环 |
| 子流程 | **结构化容器节点** | `subflow` 节点，内部是一次子执行 |

**为什么前三个不需要新节点类型**：它们只是**边的形态**。一个节点有几个后继、几个前驱，是 `depends_on` 的图形结果，不是节点自己的属性。硬造 `parallel.node` 会导致「并行节点里能不能再放一个并行节点」这种无解问题。

### 3.1 依赖图（串行 / 并行 / 合并）

- `flow_node.depends_on` 从「服务端按顺序写死」**放开为可编辑**（v2 是刻意写死的，见 `flow-v2.md` §为什么是直线）。
- 并行 = 多个节点的 `depends_on` 指向同一个前置。**天然并行**：调度器只要「就绪即分发」，不需要专门的 fork 节点。
- 合并 = 一个节点有多个前置 + `join_policy`：

| join_policy | 语义 |
|---|---|
| `ALL_SUCCESS`（默认） | 全部前置成功才 READY |
| `ANY_SUCCESS` | 任一前置成功即 READY，其余前置被 SKIP |
| `ALL_DONE` | 全部前置进入终态即 READY（不论成败） |

配套 `join_failure_policy`：`FAIL`（前置失败 → 本节点失败）/ `SKIP`（裁剪本节点）。

### 3.2 分支与路由 `route`（你说的「拆分」）

> 需求原话：「一个节点的拆分，按照拆分的属性值走不同的分支」。
> 这就是**排他路由**：节点执行完，按上下文里的某个值，选**一条**出口往下走。

#### 3.2.1 先澄清一个歧义：「拆分」有两个意思

| 说法 | 含义 | 图形 | 实现 |
|---|---|---|---|
| **控制流拆分**（本节） | 一次运行**只走一条**出口 | 一个 `route` 节点，N 条出边，亮一条 | 与依赖图同构，**不需要动态建节点** |
| **数据流拆分** | 一份产出**裂成 N 份并行**（如 96 孔按结果分 3 组，每组各跑一遍） | 一个节点 → N 个**运行时实例** | 需**动态节点实例**（Airflow 的 dynamic task mapping）。运行期改图，重得多 |

**本节只做控制流拆分。** 数据流拆分若也要，是独立一期（见 §3.5），且它必须建立在 `iteration_no` 之上——因为「同一节点在同一轮里的第 k 个实例」要能被唯一标识。

> **✅ 已拍板（D4）**：本轮**只做控制流拆分**（一次运行只走一条出口）。数据流拆分一期**不做**，也**不预留** `instance_key` 列。

#### 3.2.2 关键边界：**决策**与**执行**必须切开

用户直觉是「在 JavaBean 里拆分，然后去走不同的流程节点」。**前半句对，后半句必须收回。**

| 职责 | 归谁 | 为什么 |
|---|---|---|
| 算出走哪条路（返回一个 key） | **决策器**（表达式 / Bean / 脚本） | 这是业务逻辑，该让业务写 |
| 拿 key 找目标节点、激活它、裁剪其余 | **调度器 `BranchResolver`** | 这是图的真相源，不能让业务碰 |

**Bean 绝对不能自己去触发下游节点**，三条破坏：

1. **绕过就绪判定** —— `join_policy`（`ALL_SUCCESS` / `ANY_SUCCESS` / `ALL_DONE`）在调度器里算；Bean 直接激活下游，汇合节点会提前 READY。
2. **双写状态** —— 节点状态以库里的 CAS 为准，Bean 自己推 = 两处都在改节点生命周期。
3. **并发不安全** —— 并行分支同时跑时，Bean 各自为政会重复激活同一节点。

所以决策器是**纯函数**：`上下文 → key`。它不 import 任何调度类，也不写任何运行表。

#### 3.2.3 三种决策载体（选型）

| 载体 | `type_config.router.kind` | 谁配 | 可审计 | 表达力 | 安全面 | 建议 |
|---|---|---|---|---|---|---|
| **表达式** | `expression` | 业务人员在画布上配 | 高（明文落库） | 够 ~80% 场景（比大小、判空、枚举匹配、组合条件） | 低（求值沙箱，无副作用） | **默认** |
| **注册 Bean** | `bean` | 开发者写代码 | 中（只记 Bean 名与返回值） | 高（可查库、调外部、多字段组合） | **高**（见下） | **逃逸口** |
| **脚本** | `script` | 开发者写 | 低（代码在字符串里） | 最高 | 最高（要引 Groovy/JSR-223 + 沙箱） | 一期不做 |

- **表达式不用引新依赖**：`boheng-dependencies` 已管理 `aviator 5.4.4`，`boheng-common` 已有 `spring-expression`。
- **Bean 必须白名单**（同 Q4）：只允许显式注册为 `FlowRouteAction` 的 Bean，**禁止按任意类名反射调用** —— 否则「配一个 Bean 名」等于「任意代码执行」。
- 一期实现 `expression`，`bean` 只留接口与注册表（与 `bean.invoke` 共用同一张白名单）。

> **✅ 已拍板（D5）**：一期 `expression` **做全**（画布可配、可审计、免代码）；`bean` **只留 SPI + 白名单注册表**，不提供 UI 与注册治理；`script` 不做。

```java
public interface FlowRouteAction {
    /** 只返回分支 key，不接触图、不写库 */
    String decide(RouteContext ctx);
}
```

```jsonc
// flow_node.type_config（route 节点）
{
  "router": { "kind": "expression", "expression": "#{read_plate.value > 3 ? 'high' : 'low'}" },
  "branches": {
    "high":    ["dilute_high"],
    "low":     ["archive"],
    "default": ["archive"]
  },
  "matchMode": "exclusive",          // exclusive(默认) / inclusive
  "missingKeyPolicy": "default"      // default(走 default 出口) / fail
}
```

```jsonc
// 换成 Bean 形态，只改 router 一段，branches 不动
{ "router": { "kind": "bean", "beanName": "assayRouteAction" } }
```

#### 3.2.4 激活语义

| 概念 | 说明 |
|---|---|
| `exclusive`（默认） | 只激活一个 key 对应的一组目标 |
| `inclusive` | 激活 **全部命中的 key**（多条件并行）—— BPMN 的包容网关 |
| `default` 出口 | 没有 key 命中、或表达式求值异常时的兜底。**发布期强制要求存在** |
| `branch_scope` | key 对应的目标若是一个子图，**整段子图被激活**；未被选中的 key 的下游子图统一**裁剪** |

> **✅ 已拍板（D6）**：一期 **UI 只开放 `exclusive`**；`inclusive` 仅在模型与校验层支持（枚举保留），不提供画布入口。

**最容易出事的一点**：被裁剪（裁剪 SKIP）≠ 前置失败（失败 SKIP）。labillion 的 `BranchSkipper` 把两者无脑当成功，导致汇合节点误判 READY。boheng 必须在 `flow_step_execution.status` 上区分 `SKIPPED_BY_BRANCH` 与 `SKIPPED_BY_FAILURE`，`JoinResolver` 对前者**视为满足**、对后者按 `join_failure_policy` 处理。

#### 3.2.5 落库与审计

- 决策结果写 `flow_branch_decision`：`route_node_id` / `attempt` / `iteration_no` / `selected_branch` / `match_mode` / `activated_targets` / `decision_data`（表达式文本 + 求值时的变量快照）。
- **为什么要落库**：事后要能回答「这次为什么走了这条路」。只记 key 不够，得连「当时读到的值」一起记。

#### 3.2.6 前提依赖（必须先有）

1. **`flow_node.output_mapping`（v3 新增）** —— 「属性值」得先被规范化进上下文，路由才读得到。现在 `flow_node` **只有 `input_mapping`**，上游节点的输出还没进上下文，这是路由能工作的硬前提。
2. **表达式求值器**（`engine/expression/`）—— 路由、`loop.whileExpression`、节点的 `input_mapping` 三处共用同一个求值器，必须一次做对。
3. **图校验**（§6）—— `branches` 的目标节点必须存在；出口名与 Descriptor 声明一致；`default` 必须存在。

#### 3.2.7 与 labillion 的对照

labillion 有 `condition` 概念 + `pe_branch_decision` 表，但它的分支求值实现绑在 `DagScheduler` 内部，且 `BranchSkipper` 有上述 SKIP 语义缺陷。boheng 的做法是**把决策器抽成可注册的 SPI**（`FlowRouteAction`），调度器只认 key —— 这也是补齐两边共同缺口（`StepTypeRegistry`）的一部分。

---

### 3.3 循环 `loop`（结构化容器）

**关键约束：DAG 定义上没有回边。** 支持循环只有两条路：

| 方案 | 做法 | 评价 |
|---|---|---|
| **(a) 结构化容器（推荐)** | `loop` 是一个节点，`type_config` 描述循环体范围与退出条件；循环体是它内部的一段子图。对外仍无环 | 可静态校验、可视化简单、不会死循环（有 `maxIterations` 上限）。Airflow / Argo / BPMN 都是这个思路 |
| (b) 自由回边 | 允许画布连回边，调度器处理迭代计数与退出条件 | 图不再无环，校验/调度/可视化全变复杂，必须靠 `maxIterations` 硬兜底。不推荐 |

`loop` 的 `type_config` 草案：

```json
{
  "bodyEntryNodeId": "incubate",
  "bodyExitNodeId": "check_growth",
  "maxIterations": 100,
  "itemsExpression": "${input.plates}",
  "itemVariable": "plate",
  "whileExpression": "${node.check_growth.passed} == false"
}
```

- 语义：loop 节点在执行期接管 body 的迭代 —— 每轮重置 body 各节点、注入 `item` 变量、驱动 body 子图跑完、求值退出条件。
- **对外表现**：loop 就是一个「耗时 = 整个循环」的节点，它的后继只依赖 loop 节点。
- **一期建议**：只支持「线性循环体 + `maxIterations` + `whileExpression`」，`itemsExpression`（遍历集合）二期再上。

### 3.4 子流程 `subflow`

#### 3.4.1 单一子流程（SINGLE，静态引用）

- `subflow` 节点，`type_config = {"dispatchMode":"SINGLE","pipelineKey":"quality_check","version":2,"inputMapping":{...}}`。
- 执行：创建**一个**子 `flow_execution`（带 `parent_execution_id` / `parent_node_id` / `parent_attempt`），子流程终态后发 **CHILD 信号**唤醒父节点。
- 校验：子流程引用链**不能成环**（A 引 B、B 引 A），发布时做可达性检查。

#### 3.4.2 动态扇出（FANOUT）——「96 样本拆机」的正确形态

> 需求场景：96 个样本拆成 48 个去 A 机器、48 个去 B 机器，A、B 各自跑自己的流程，**并行、互不等待**，都完成后汇总。

这就是 §3.5「数据流拆分」想解决的真实场景，但**不需要 §3.5 的动态节点实例**——
因为 A、B 的流程**本身就不同**（不同机器、不同步骤），天然该建**两个子流程定义**；
一个 FANOUT 节点按运行时算出的**拆分计划**各启动一个子执行。与 Airflow 的
`TriggerDagRunOperator`（task 触发 N 次子 DAG、payload 各异）同构，是成熟模式。

```text
[前处理] → [fanout 节点] ─┬→ 子执行#1：A 线流程（48 样本） ─┬→ （fanout 节点完成）→ [汇总]
                           └→ 子执行#2：B 线流程（48 样本） ─┘
```

- **fanout 节点自身就是汇合点**：它等全部 N 个子执行终态才算完成，后继天然拿到「全部完成」语义，**不需要再画 join**。

**计划从哪来：还是 Bean，但边界不变。** 拆分计算确实是业务逻辑（怎么分组、按什么属性），归 Bean；
但**启动子执行必须归引擎**，理由比 §3.2.2 更硬：

1. 子执行必须带 `parent_execution_id` 挂在图上。Bean 自己启动的执行是**孤儿**——监控、恢复、取消、审计全都找不到它。
2. 拆分计划必须**先落库、再执行**。否则 Bean 启动了 #1、在启动 #2 前崩溃，没人知道本该有几个子执行；落库后恢复逻辑可按计划补建。

```java
public interface FlowDispatchAction {
    /** 只返回拆分计划：不启动执行、不写库、不 import 引擎类 */
    List<DispatchPlanEntry> dispatch(DispatchContext ctx);
}
// DispatchPlanEntry = { pipelineKey, version?, input(json), groupIndex 由引擎分配 }
```

```jsonc
// fanout 节点 type_config
{
  "dispatchMode": "FANOUT",
  "dispatch": { "kind": "bean", "beanName": "plateSplitDispatcher" },
  "completionPolicy": "ALL_SUCCESS"   // 全部成功才算成功；ANY_SUCCESS 允许部分失败
}
```

**运行语义**：

| # | 规则 |
|---|---|
| 1 | 节点执行时**先 CAS 落拆分计划**（每组一条，含 `group_index`），再逐条创建子执行 |
| 2 | 子执行 `business_key = {parent_execution_id}:{node_id}:{attempt}:{group_index}` → **幂等**：恢复/重试时按计划补建缺失的子执行，不重复建 |
| 3 | 每个子执行终态发 **CHILD 信号**（带 `parent_attempt`）；节点收齐 N 个后按 `completionPolicy` 结算 |
| 4 | **空计划**（某组没样本、Bean 返回空列表）：节点直接 SUCCESS，不建子执行 |
| 5 | **部分子执行失败**：默认**不取消兄弟**（物理命令可能已在执行，取消不安全——同不继承 labillion `RecoveryScanner` 债的理由），等全部终态后按 `completionPolicy` 结算；是否提供主动取消开关见 Q10 |
| 6 | A、B 流程**完全相同**（只是样本不同）：一个子流程定义，计划里两条 entry 引用同一个 `pipelineKey`，不必建两个定义 |

**与 §3.5 的关系**：FANOUT 覆盖了「按属性分组、各组走（可能不同的）流程」；
§3.5 的同图动态节点实例更没有理由做——**有了 FANOUT，`instance_key` 可以彻底放弃**（D4 的「不预留」继续成立）。

**表结构增量**：`flow_execution` 加 `parent_group_index int NULL`（区分同一父节点的第几个子执行；
这是**子执行上的列**，不是 D4 否掉的 `flow_step_execution.instance_key`，两者不冲突）。

### 3.5 数据流拆分（同图动态节点实例，一期不做）

> **✅ 已拍板（D4）**：一期**不做**，且**不预留** `instance_key` 列。
> 「按属性分组、各组并行跑流程」的场景已由 **§3.4.2 FANOUT 子流程扇出**覆盖（更优解：各组流程可不同、可独立定义、天然隔离）。
> 本节形态（同一张图里、同一节点运行期裂成 N 个实例）继续搁置——有了 FANOUT，更没有理由做它。

- 与 `loop` 的区别：`loop` 是**串行跑 N 轮**（`iteration_no` 递增）；同图数据流拆分是**并行跑 N 个实例**。
- 实现代价：运行期动态生成节点实例 → `flow_step_execution` 要加 `instance_key`（产出集合里的元素标识），`iteration_no` 不再够用。
- Airflow 的 dynamic task mapping、Argo 的 `withItems` 是同类。**有 FANOUT 兜底后，除非出现「必须在同图内看齐进度」的真实需求，否则不再排期。**

---

## 4. 落表

### 4.1 `flow_step` 增量（§2.4）

### 4.2 `flow_node` 增量

```sql
ALTER TABLE `flow_node`
  ADD COLUMN `step_type`       varchar(64)  NULL     COMMENT '节点级类型覆盖。NULL 则取 flow_step.step_type',
  ADD COLUMN `role`            varchar(32)  NULL     COMMENT 'ACTION/CONTROL/WAIT/SUBFLOW。NULL 则取 Descriptor',
  ADD COLUMN `completion_mode` varchar(32)  NULL     COMMENT 'SYNC/CALLBACK/SIGNAL/TIMER/CHILD',
  ADD COLUMN `join_policy`     varchar(32)  NOT NULL DEFAULT 'ALL_SUCCESS' COMMENT 'ALL_SUCCESS/ANY_SUCCESS/ALL_DONE',
  ADD COLUMN `join_failure_policy` varchar(32) NOT NULL DEFAULT 'FAIL'     COMMENT 'FAIL/SKIP',
  ADD COLUMN `branches`        json         NULL     COMMENT '控制节点出口映射，如 {"pass":["report"]}',
  ADD COLUMN `output_mapping`  json         NULL     COMMENT '节点输出归一化到上下文的映射',
  ADD COLUMN `node_config`     json         NULL     COMMENT '节点级覆盖：timeoutMs/maxAttempts/backoffMs/failureStrategy',
  ADD COLUMN `loop_config`     json         NULL     COMMENT 'loop 节点的 type_config（循环体范围与退出条件）';
```

要点：
- **节点级覆盖用 JSON 而不是再加 4 个列**（labillion 是 4 个独立列）。boheng 上层的 `timeout_ms` / `max_attempts` 已经在 `flow_step` 上，节点级只需少数覆盖项，JSON 更省。
- `flow_node` 仍然是**关联表**：`step_id` 指向能力，`step_type` 仅在需要节点级不同类型时才填。

### 4.3 运行层新表

| 表 | 作用 | 对应 labillion |
|---|---|---|
| `flow_execution` | 一次运行。绑定义版本 + checksum + 入参/状态；`parent_execution_id` 支持子流程 | `pe_pipeline_execution` |
| `flow_step_execution` | 一次步骤尝试。`attempt` 新行不覆盖；记 `command_id` / 状态 / 输入输出快照 | `pe_step_execution` |
| `flow_step_signal` | 统一信号收件箱：设备回调 / 人工 / 定时 / CHILD / 外部，都转成同一模型 | `pe_step_signal` |
| `flow_execution_context` | 分项上下文（INPUT / NODE / SYSTEM 命名空间） | `pe_execution_context_item` |
| `flow_branch_decision` | route 节点的分支决策 | `pe_branch_decision` |

**不建议一期就搬**：`pe_execution_artifact`（产物引用）、`pe_step_reconciliation`（对账）、`pe_compensation_plan`（补偿）、`pe_execution_event_log`（事件日志）、`pe_event_outbox`（发件箱）。这五张等有真实需求再加。

### 4.4 沿用 v2 的两条约定

- 唯一键继续用「**生成列 + 唯一索引**」兼容逻辑删除（`uk_flow_definition_version` 那套）。
- 全部带 `tenant_id`，实体继承 `TenantBaseDO`（不加 `@TenantIgnore`）。

---

## 5. 执行侧

1. **就绪判定**：前置都满足 `join_policy` → 节点 READY。
2. **并行**：READY 的节点各自抢 CAS `PENDING → RUNNING`，抢到的分发。**并行是调度器的天然结果，不需要 fork 节点。**
3. **完成**：
   - `SYNC`：执行器同步返回 → 直接结算
   - `CALLBACK`：下发后进 `WAITING`，等 `flow_step_signal`
   - `SIGNAL`：人工在页面确认 → 写信号
   - `TIMER`：到点由 infra job 发信号
   - `CHILD`：子流程终态发 CHILD 信号
4. **状态机**：纯 CAS，每次重试 `attempt + 1` 新行（照搬 labillion 最省事的一块）。
5. **阻塞调用**：设备调用是阻塞等回调，**必须走独立线程池并带上租户上下文**，不能让 HTTP 线程挂住。
6. **容器节点的特殊处理**：`loop` / `subflow` 对调度器只暴露「一个节点」，内部子图由容器自己驱动，不参与外层就绪判定。
7. **路由节点的特殊处理**：`route` 执行器**只算出 key 就返回**（`SYNC` 结算），紧接着由 `BranchResolver` 接管 ——
   查 `branches` → 激活目标子图 → 其余出口的下游逐节点写 `SKIPPED` + `skip_reason=BRANCH` → 落 `flow_branch_decision`。
   **执行器与 `BranchResolver` 之间只传 key，没有任何对象引用**，这是 §3.2.2 那条边界在代码上的样子。

---

## 6. 发布校验（fail-fast）

| 层 | 校验 |
|---|---|
| 保存 | 编码/名称合法、超时与次数范围、参数来源合法 |
| 发布 | 至少一步；每节点类型已注册；`type_config` 能反序列化并通过 `validate`；设备类节点设备在台账且动作启用 |
| 图 | `depends_on` 无环；`join_policy` 与前置数量一致；未声明前置却引用上一步输出 → 拒绝 |
| 容器 | `loop` 的 body 区间连续且不含自身；`subflow` 引用链无环；`subflow` 目标版本必须是 `PUBLISHED`；FANOUT 的 `dispatch.kind=bean` 时 Bean 在白名单且实现 `FlowDispatchAction` |
| 分支 | `branches` 的目标节点必须存在于本版本；出口名与 Descriptor 声明一致；**必有 `default` 出口**；表达式可解析；`router.kind=bean` 的 Bean 在白名单且实现 `FlowRouteAction`；`EXCLUSIVE` 下不同 key 的目标子图不得交叠 |

> v2 的「依赖不由人填」是**防错设计**（从根上杜绝「画得出跑不了」）。本版放开 `depends_on` 后，这个防线要由上面的图校验补回来 —— 这是本版最大的风险点。

---

## 7. 分期建议

| 期 | 内容 | 依赖 |
|---|---|---|
| P1 | 类型注册表 + `flow_step` 加 `step_type` / `type_config` + 发布 fail-fast | 只动定义层，不动运行 |
| P2 | 运行闭环：`flow_execution` / `flow_step_execution` + 直线调度 + `device.command` + `DeviceCommandApi` | 需 device-api 配合 |
| P3 | `bean.invoke` / `script.run`（SYNC 类型，最便宜） | P2 |
| P4 | 并行 / 合并（放开 `depends_on` + `join_policy`） | P2 |
| P5 | 分支与路由 `route`（表达式求值器 + `output_mapping` + Bean 决策器 + `branches` 出口映射） | P2（求值器与 `output_mapping` 是硬前提），与 P4 可并行 |
| P6 | 人工 `manual.task` + 通知 `notification.send` | P2 |
| P7 | 循环 `loop` | P5（要先有表达式求值器与路由） |
| P8 | 子流程 `subflow` | P2 + 定义层递归校验 |

P1 + P2 是「能跑起来」的最小闭环；P3 极便宜可提前；**P7 / P8 最重**（唯一需要容器语义的两项），放最后。

---

## 8. 与 labillion 的取舍（摘要）

- **照搬**：状态机（纯 CAS、零外部依赖）、`flow_step_signal` 统一信号模型、`attempt` 新行重试、条件分支 + 分支决策表。
- **改造**：`role` / `completion_mode` / `join_policy` 从 4 个独立列收敛为「列 + `node_config` JSON」；`depends_on` 保留但补图校验。
- **不搬**：`ProjectContextHolder`（项目隔离，boheng 用 `tenant_id`）、LiteFlow、Task 队列分发、AGV/Lab 业务类型、WebSocket、对账/补偿/Outbox（一期无需求）。
- **不继承的债**：labillion `flow-module-refactor-design.md` 自认的两条 —— `BranchSkipper` 把 `SKIPPED` 无条件当成功（汇合节点会误判 READY）；`RecoveryScanner` 对仪器步骤做 `RUNNING→PENDING` 重置（物理指令可能已发出）。boheng 实现时必须区分「裁剪的 SKIP」与「失败的 SKIP」。
- **两边共同的缺口**：`StepTypeRegistry` / `StepTypeDescriptor` labillion 也还没有（排在它自己的 M1）。本版 P1 就把这块做掉，是 boheng 可以领先的点。
