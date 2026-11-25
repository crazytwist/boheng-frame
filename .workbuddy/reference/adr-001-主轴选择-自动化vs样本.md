# ADR-001：主轴选择 —— 自动化 vs 样本跟踪

- **状态**：已决策（2026-11-21）
- **决策者**：产品 + 架构
- **相关**：`boheng-execution-platform-positioning.md`、`benchling-research-2026-11.md`

---

## 一、背景

现有代码里其实已经长出了两根柱子：

- **自动化柱**：`flow_definition / flow_node / flow_step` + 执行五表 + 引擎（DAG、分支裁剪、循环展开、FANOUT 幂等、信号收件箱、Sweeper）+ `flow_device_task`（CAS 认领、拉取→执行→回报）
- **样本柱**：`wms_content_def / wms_sample_info / wms_material_instance` + 八态/六态状态机 + slot 位置 + 谱系五件套

问题不是"要不要两根柱子"，而是**哪根是主干、哪根是枝干**——因为这决定了未来 80% 的研发投入方向、数据库的演进方式、以及对外 API 的第一等资源是什么。

---

## 二、决策

> **主轴 = 自动化（run-centric）。样本降级为贯穿维度，只保留四件事：标识、位置、状态、消耗流水。**

不是否定样本的重要性，而是**把它从"要被建模的对象"改成"必须被满足的约束"**。二者的差别在于：

- 当主轴是样本 → 你会不断加字段、加 schema、加录入界面、加查询报表 → 最终做成一个 LIMS/WMS，进入红海，且和上游（Benchling / 客户 ELN）正面撞车。
- 当主轴是自动化 → 你投入在驱动覆盖、调度正确性、异常恢复、节拍优化 → 这些是客户愿意单独付钱、且竞品难抄的东西。

---

## 三、理由（按权重排序）

### 3.1 客户为哪件事付钱

买执行平台的客户，验收标准是"**这条线能不能无人值守跑通**"。样本台账是**准入条件**（没有它方案直接被否），不是**付费理由**。

> 一个判断法则：约束条件不该当主轴。客户不会因为"你的样本字段比别家多 20 个"而签单，但会因为"你的线跑了三个月没停过"而续约。

### 3.2 壁垒在哪

| 能力 | 可复制性 | 结论 |
|---|---|---|
| 样本表 + 状态机 + 谱系 | 高——任何团队三个月能搭一个 | 不能作为壁垒 |
| 设备驱动覆盖（尤其是国产设备） | 低——靠时间和现场磨 | **壁垒** |
| 调度正确性（多 run 争抢、节拍、并发） | 低——靠真实产线验证 | **壁垒** |
| 异常恢复（失败不重跑全链、断点续跑） | 低——靠踩坑积累 | **壁垒** |

样本域做到"够用"就停，把余量全部投到右边三行。

### 3.3 与上游的边界

Benchling / ELN / LIMS 已经把"样本是什么"建模得很完整了（Schema + Entity + 唯一性 + 谱系）。我们再建一套，结果是**客户要做两次建模、两套主数据、还要对账**——这是集成灾难，不是卖点。

正确姿态：**我们承认上游是样本主数据的 owner，我们是样本的"使用者与消耗者"**。这样我们永远是被集成方，不会被替换。

### 3.4 现有资产的实际重心

flow 引擎的复杂度（CAS、幂等、FANOUT、Sweeper、信号收件箱）远高于样本域。**资产重心已经在自动化这边了**，主轴顺着力走，不用重构；反过来选样本，等于把最重的资产变成配角。

---

## 四、两根轴各自 own 什么（硬性边界）

这条表比任何架构图都重要，建议直接落成 code review 检查项。

| 域 | 必须 own | 严禁 own |
|---|---|---|
| **设备域** | 能力声明、驱动、连接状态、健康度、心跳 | 样本状态、流程定义、业务判定 |
| **编排域（flow）** | 流程定义、run 状态机、step 实例、分支决策 | 样本属性、设备参数字节流细节 |
| **调度/执行** | 排队、认领、超时、重试、幂等、补偿 | 任何业务语义 |
| **样本域（WMS）** | 标识（条码/编码）、位置（slot）、状态机、谱系、消耗流水 | 设备控制、流程编排、检测值 |
| **结果域** | 检测值、方法、仪器、原始文件引用 | 样本主数据、流程控制 |

### 三条不变式（invariant）

1. **样本状态的唯一写入口是 run 结算，不是设备。**
   设备只能上报 `action 完成 + 产出描述`；由 run 引擎结算后写入 ledger，再由 ledger 驱动样本状态变更。
   → 好处：设备掉线、重复上报、乱序上报都不会污染样本主数据。

2. **任何一次 run 对样本的影响，必须能在 ledger 里找到对应行。**
   → 好处：任取一个样本，能回答"它被哪些 run 消耗过、产出过"；任取一个 run，能回答"它动了哪些样本"。

3. **样本域不存"我经过了哪些设备"。**
   那是 run 的事实，不是样本的属性。样本只存"当前状态 + 当前位置 + 谱系"。
   → 好处：避免样本表变成一张巨大的、不断加列的流水表（这是 LIMS 常见的腐化路径）。

---

## 五、铆接层：把两根柱子焊住

主轴定了，剩下的工程问题是**两根柱子怎么连**。答案是一张纯粹记账的表，不含业务逻辑：

```sql
-- 运行-样本 消耗/产出流水（铆接层，只记账）
CREATE TABLE flow_run_io (
  id                  bigint        NOT NULL COMMENT '主键(雪花 ASSIGN_ID)',
  run_id              bigint        NOT NULL COMMENT '运行实例ID',
  step_id             bigint        NULL     COMMENT '步骤实例ID，NULL=整单级',
  device_action_id    bigint        NULL     COMMENT '设备指令ID',
  direction           varchar(8)    NOT NULL COMMENT 'INPUT=消耗 / OUTPUT=产出',
  instance_id         bigint        NOT NULL COMMENT '物料/样本实例ID',
  quantity            decimal(20,6) NULL     COMMENT '数量',
  unit                varchar(32)   NULL     COMMENT '单位',
  root_slot_id        bigint        NULL     COMMENT '发生时的位置快照',
  status_before       varchar(32)   NULL     COMMENT '变更前状态',
  status_after        varchar(32)   NULL     COMMENT '变更后状态',
  idempotency_key     varchar(128)  NOT NULL COMMENT '幂等键 = run:step:action:instance',
  occurred_at         datetime      NOT NULL COMMENT '实际发生时间(设备时钟校正后)',
  creator             varchar(64)   NULL,
  create_time         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater             varchar(64)   NULL,
  update_time         datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted             bit(1)        NOT NULL DEFAULT b'0',
  tenant_id           bigint        NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_run (run_id),
  KEY idx_instance (instance_id, occurred_at),
  KEY idx_idem (idempotency_key)
) ENGINE=InnoDB COMMENT='运行-样本消耗产出流水';
```

注意几点（对齐现有底座约定）：

- **不建 UNIQUE 索引**——遵循本工程"全库 0 UNIQUE + Service 层校验"的约定，幂等靠 `idempotency_key` 在 Service 层做插入前去重（配合 `SELECT ... FOR UPDATE` 或先查后插 + 唯一性兜底日志）。
- `deleted bit(1)` + 审计四列，与 `BaseDO` 一致；含 `tenant_id` 则继承 `TenantBaseDO`。
- `occurred_at` 与 `create_time` 分开：前者是**设备侧实际发生时间**（要考虑时钟偏移校正），后者是**入库时间**。追溯用的是前者，排查用的是后者。
- `status_before/after` 冗余存一份，是为了让审计视图**不依赖联表回溯**（样本状态可能已被后续 run 改过多次）。

配套再加一张 run 账本（P0 清单里的第 3 项），`flow_run_io` 挂在它下面：

```sql
CREATE TABLE flow_run_record (
  id            bigint       NOT NULL,
  flow_id       bigint       NOT NULL COMMENT '流程定义ID',
  flow_version  int          NOT NULL COMMENT '编排版本(关键：定义可改，历史不可变)',
  run_no        varchar(64)  NOT NULL COMMENT '人可读运行号',
  status        varchar(32)  NOT NULL COMMENT 'RUNNING/RECOVERING/DONE/FAILED/CANCELLED',
  started_at    datetime     NULL,
  finished_at   datetime     NULL,
  device_count  int          NULL COMMENT '涉及设备数',
  trigger_type  varchar(16)  NULL COMMENT 'MANUAL/SCHEDULE/EVENT/AGENT',
  trigger_by    varchar(64)  NULL COMMENT 'principal: user/app/device/agent',
  ...
)
```

`flow_version` 字段是刻意的：**编排定义允许改，但历史运行必须锁定当时的版本**，否则审计时"当时跑的是哪版流程"说不清。

---

## 六、三段进化路线（每段有明确出口）

### 阶段一：把自动化立住（现在 → 3 个月）
- 设备能力模型 `device_capability`（action_code + param_schema + 量程/精度 + 预估耗时 + 并发度）
- 指令契约 `command` 信封（params / timeout / retry / idempotency_key / precondition）
- 运行账本 `flow_run_record` + `flow_run_io`
- **出口判据**：能完整回放任意一次历史运行（哪个 run、哪台设备、什么参数、动了哪些样本、耗时多少），且不联表查样本表。

### 阶段二：样本收敛（3 → 6 个月）
- 样本域做减法：冻结新增业务字段；只保留标识 / 位置 / 状态 / 谱系 / 流水
- 所有状态变更改为**只能由 run 结算触发**（拆掉现有直连修改的入口）
- **出口判据**：样本表结构在本阶段内**零新增加列**，且所有状态变更都能反查到 `flow_run_io` 行。

### 阶段三：反向输出（6 个月后）
- 把 `run → sample` 的 ledger 做成对外事件（`sample.consumed` / `sample.produced` / `run.completed`）
- 出追溯视图与对接包（Benchling / 国内 ELN / LIMS 各一套映射）
- **出口判据**：客户的上游系统能只靠我们的事件流，还原出样本的完整流转。

> 注意阶段顺序：**先有事实，再有视图**。反过来做（先做追溯视图再补 ledger）会得到一张靠猜和联表拼出来的假账。

---

## 七、什么情况下应该反过来（诚实版）

如果出现以下任一情况，主轴应翻转为**样本优先**：

1. 客户主体是**生物样本库 / 样本中心 / 中心实验室收样**，第一笔预算科目是"样本库建设"而不是"自动化产线"
2. 招投标文件里出现"样本全生命周期管理"作为**评分项**（而不是准入门槛）
3. 我们的实际交付里，自动化只占 30% 以下工作量，其余是收样、编码、入库、出库、销毁
4. 出现必须与客户既有 LIMS 做**主数据双向同步**的需求（说明我们要当 owner 而不是 consumer）

翻转的代价要提前说清：需要重构样本域为完整 schema 体系、补唯一性规则引擎、补录入与查询体系——**约等于重做一次 Benchling Registry 的简化版，3~6 个月**。所以能不翻就不翻。

---

## 八、风险提示

| 风险 | 表现 | 对冲 |
|---|---|---|
| 只自动化、不追溯 | 线跑得起来，但客户审计时说不清"这批样本经历了什么" | 阶段一的 ledger 必须先于任何"效率优化"落地 |
| 样本域"顺手加个字段" | 半年后 content_def 变成 80 列宽表，无人敢改 | 阶段二冻结字段 + code review 拦截 |
| ledger 成为性能瓶颈 | 高频 run 时写入放大，影响调度 | 按 run_id 分表或按月分区；ledger 写入异步化但**先落本地事务**再发事件 |
| 设备直连改样本状态 | 重复上报/乱序导致状态错乱 | 不变式 1：唯一写入口是 run 结算 |

---

## 九、一句话总结

> **自动化决定我们能不能活着（卖得出去、跑得稳），样本决定我们能不能被信任（说得清、审得过）。**
> 但只有一个是主轴：**主轴是自动化，样本是它的影子账。**
