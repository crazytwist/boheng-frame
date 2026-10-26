# 流程引擎分析（Flow）

> 模块：`boheng-module-flow`
> 定位：实验室自动执行的**编排引擎**。把「能力」和「编排」解耦——能力是可复用步骤，编排是流程版本里节点的 DAG。回答——**流程怎么定义、怎么跑、怎么调度、怎么校验、怎么定时触发**。

---

## 一、三层结构

```
1. 定义层    可复用步骤（能力目录）+ 流程版本 + 节点编排（DAG + 汇合 + 分支出口）
2. 类型系统  StepTypeRegistry 开放注册步骤类型（设备/Bean/脚本/人工/通知/路由/校验/循环/子流程/定时）
3. 运行层    执行引擎（CAS 认领 + DAG 调度 + 分支裁剪 + 循环展开 + 子流程扇出）+ 信号收件箱 + 执行上下文
```

核心信条：**能力 × 编排正交**。
- `flow_step`（能力目录）= `step_type + type_config`，控制类类型拒绝入目录
- `flow_node` 统一承载「挂能力」与「控制类」节点（route/loop/subflow/validate/timer.wait，`stepId` 为空）

---

## 二、定义层

### 2.1 能力目录 flow_step

可复用步骤，`step_type` 是开放的注册表 key，`type_config` 是类型专属配置。**控制类类型（route/loop/subflow/validate/timer.wait）不能存为可复用步骤**，只能在节点上直接配置（`FLOW_STEP_CONTROL_TYPE`）。

### 2.2 流程版本 flow_definition

- `pipelineKey`（流程编码）+ `version`（版本）+ `checksum`（指纹）+ `status`（DRAFT/PUBLISHED/RETIRED）
- **启动只收已发布版本**，运行时绑死 definitionId + checksum，防定义漂移
- 一个编码同时只保留一个草稿

### 2.3 节点编排 flow_node

- `nodeId`（小写开头）+ `dependsOn`（前置，DAG）+ `joinPolicy`（ALL_SUCCESS/ANY_SUCCESS/ALL_DONE）+ `joinFailurePolicy`（FAIL/SKIP）
- `inputMapping`（参数映射：INPUT 启动入参 / NODE 前置输出 / LITERAL 固定值）+ `outputMapping`
- `branches`（route/validate 的出口映射）+ `runConfig`（超时/次数覆盖）

**「类型生效规则」唯一实现 = `FlowDraftAssembler`**（节点级覆盖 > 步骤表），校验与展示共用。

---

## 三、类型系统（SPI）

```
StepTypeDescriptor  类型说明（type/role/completionMode/retryable/configType/validate）
StepExecutor        执行器（execute(StepExecutionContext) → StepOutcome）
StepTypeRegistry    注册表
StepOutcome         结果（SUCCESS/FAILED/WAITING/BRANCH）
```

内置类型清单：

| type key | 角色 | 完成方式 | 说明 |
|---|---|---|---|
| `device.command` | 能力 | SYNC | 同步推命令到在线设备 |
| `device.task` | 能力 | CALLBACK | 异步任务，设备离线排队拉取 |
| `bean.invoke` | 能力 | SYNC | 调白名单 Bean（FlowBeanAction） |
| `script.run` | 能力 | SYNC | 求值 aviator 表达式 |
| `manual.task` | 能力 | CALLBACK | 等人工确认 |
| `notification.send` | 能力 | SYNC | 发通知 |
| `route` | 控制 | SYNC | 出口路由（按属性选分支） |
| `validate` | 控制 | SYNC | **校验门**（Bean + 完整上下文 → pass/fail） |
| `timer.wait` | 控制 | — | 等定时到点 |
| `loop` | 控制 | — | 循环容器（子图跑 N 轮） |
| `subflow` | 控制 | — | 子流程（SINGLE 引用 / FANOUT 扇出） |

`StepOutcome` 语义：`SUCCESS`（产出）/ `FAILED`（可重试）/ `WAITING`（等外部事件）/ `BRANCH`（路由/校验选出口，可携带 outputJson）。

---

## 四、运行层（五表 + 引擎）

### 4.1 五张运行表

`flow_execution`（运行）/ `flow_step_execution`（节点执行行）/ `flow_branch_decision`（分支决策回放）/ `flow_execution_context`（上下文，每键一行）/ `flow_step_signal`（信号收件箱）。

### 4.2 引擎生命周期（FlowExecutionEngine）

一次运行的推进：
```
start（冻结图 + 初始化节点行）→ advance（就绪判定）→ 认领 → 分发 → 结算 → 再推进 → 终态收尾
```

- **CAS 认领**：节点 PENDING → RUNNING 用条件更新，多线程只有一个抢到，其余空转
- **DAG 调度**（DagScheduler）：纯函数，就绪 = 前置按 join_policy 满足；前置失败按 join_failure_policy 裁剪或置 DEAD
- **分支裁剪**（BranchResolver）：route/validate 选出口后激活目标、裁剪未选分支
- **循环展开**：loop body 按迭代展开子图，while 表达式由循环体内节点驱动
- **子流程扇出**（FANOUT）：分发 Bean 算计划 → 幂等建 N 个子执行 → CHILD 信号汇合
- **信号收件箱**：人工确认 / 子流程汇合 / 设备回调走信号
- **执行上下文**（ExecutionContextHolder）：`input.*` / `node.{nodeId}.*` / `sys.*` 三层命名空间，`loadEnv` 拿完整上下文，参数映射 `resolveParams` 支持点分下钻 + 数组下标

### 4.3 参数映射

- 来源：INPUT（启动入参）/ NODE（前置输出）/ LITERAL（固定值）
- 发布期校验：**NODE 来源只能取直接前驱**（`FLOW_MAPPING_NOT_DEPENDENCY`），保证「数据依赖 ⊆ 拓扑依赖」，运行时不会空转
- 取值支持 `detail[0].executionId` 这类结构化路径（子流程扇出后取第 N 个子运行）

---

## 五、两个新增能力

### 5.1 校验节点（validate）

- SPI：`FlowValidateAction`（白名单），`validate(env, args)` 返回 null/空串 = 通过，非空 = 不通过原因
- 配置：`ValidateConfig{beanName, argsJson, branches{pass, fail}}`
- 执行：拿**完整上下文**（input/node/sys + params）→ 通过 `branch("pass")`，不通过 `branch("fail", {"reason":...})`
- 不通过原因写进节点输出，fail 出口下游可读

### 5.2 定时启动流程（JobHandler）

- `FlowExecutionService.startScheduled(definitionId, inputParams, businessKey, tenantId)`：在 `TenantUtils.execute(tenantId)` 里查定义、确认已发布、`engine.start(..., "CRON", "job", ...)`
- `FlowTriggerJobHandler implements JobHandler`：解析参数 JSON `{tenantId, definitionId, inputParams?, businessKey?}`，调 startScheduled
- **不走 `@TenantJob`**（租户是参数显式指定，避免一条任务串跑所有租户）
- 与流程内部 `timer.wait` 语义不同：`timer.wait` 是「节点等到点再走」，定时启动是「按 cron 触发新流程实例」

---

## 六、权限与错误码

- 运行菜单：12827-12831（flow:execution:query/create/update/signal）
- 错误码段：`1-005-000-000`（定义层）~ `1-005-004-000`（运行层）
- 权限模型：菜单 type=2 + 按钮 type=3，super_admin 短路，普通角色需显式 role_menu

---

## 七、关键设计原则

1. **能力 × 编排正交**，控制类类型不入能力目录。
2. **决策与执行分离**：控制流节点只产出 `StepOutcome.BRANCH`，激活/裁剪归 `BranchResolver + DagScheduler`。
3. **数据依赖 ⊆ 拓扑依赖**（发布期强制参数取直接前驱），运行时零竞态。
4. **CAS 认领 + 幂等**（FANOUT 建行用 business_key 唯一索引双保险）。
5. **启动冻结定义**（绑 definitionId + checksum），防漂移。
6. **上下文三层命名空间**，`loadEnv` 供校验/路由/脚本/通知共用。
