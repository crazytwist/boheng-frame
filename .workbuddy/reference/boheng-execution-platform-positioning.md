# 定位修正：我们做的是「自动化执行平台」，不是研发数据平台

> 2026-11-21 ｜ 前置文档：`benchling-research-2026-11.md`
> 触发：确认产品定位为**自动化执行平台**（orchestration / Lab OS 执行层）
> 结论：Benchling 从"对标对象"降级为"上游数据平台 + 集成目标"，真正要对标的是 Automata / UniteLabs / Biosero / Agilent VWorks / Tecan 这一层

---

## 一、为什么定位一变，Benchling 的角色就变了

Benchling 的自动化能力边界，它自己讲得很清楚：

> "Design and orchestrate your instruments and automation — **send instructions out; bring results back**."
> "At launch, partner integrations include HighRes, Hamilton, Celltrio, Opentrons, Ginkgo Bioworks, and **Automata — for end-to-end lab orchestration**."

拆开看它这一层是什么：

| Benchling 自己做的 | 它交给伙伴做的 |
|---|---|
| 工作流定义（可视化流程图 + Python） | **物理编排**（Automata 端到端调度） |
| 200+ 仪器连接器（数据接入为主） | **工作单元整机**（HighRes / Celltrio 的 workcell fleet） |
| 结果落库、挂到样本与实验、自动分析 | **液体处理原生控制**（Hamilton / Opentrons） |
| 仪器不支持时用 **file watcher 兜底**（说明大量设备实际只有文件级集成） | **数据质量基建**（Ginkgo） |

也就是说：**Benchling 拥有"流程定义权"和"数据记录权"，但不拥有"设备执行权"。** 执行权在伙伴手里，而伙伴是硬件绑定的（HighRes/Celltrio 卖整机，Hamilton/Opentrons 卖本家液体处理器）。

这个缺口正是我们的位置：
- 客户已经有 Benchling（或 LIMS/ELN），但**执行层要么靠人工，要么被硬件厂绑定**；
- 我们要做的是**中间那层：不绑硬件、能私有化、能把任意设备编排起来，并把执行结果按上游要的格式交出去**。

---

## 二、真正的竞争地图（2026 年这层在打什么）

2026 年 SLAS，一周内三家同时发 "Lab OS"，这层的战争已经开始了（R&D World 称之为 *lab OS wars*）：

| 玩家 | 打法 | 对我们的威胁 |
|---|---|---|
| **Automata LINQ** | AI-native OS，$45M C 轮（Dimension 领投，Danaher Ventures 参投）；对外喊"open by design"，但 Danaher 把 Molecular Devices / Beckman Coulter 列为集成伙伴 | 高——"开放"是话术，实际垂直整合 |
| **UniteLabs** | 纯软件，Python-native SDK，100+ 连接器，**开源仓库**，**SiLA 2 合规**；已落地 Cradle / LSMC / 某 top5 药企 | **最高**——和我们定位几乎重合，且用开源+标准抢生态 |
| **Biosero Green Button Go** | 老牌 workcell 编排，多工作单元协调，数百个驱动；BICO 集团，装机量 4.8 万台；AI 明确说"仅辅助"（合规卖点） | 中高——装机存量与驱动库是壁垒 |
| **Agilent VWorks** | 设备可用性协调 + 共享资源仲裁 + 详细执行日志 + 电子签名，绑 Agilent 生态 | 中—— regulated 场景强 |
| **Tecan FluentControl Scheduler** | 只在自己生态内做调度到执行映射 | 中——客户被硬件绑架时是默认选项 |
| **PAA Overlord / Sapio Scheduler** | 主打**失败恢复状态机**（出错后不重跑整条计划、减少 schedule drift） | 参考价值高——这是我们最该补的能力 |
| **Atinary** | 自驱动实验室，闭源 DMTA 闭环（ABB/Agilent/Bruker/Chemspeed/Mettler） | 低——卖的是闭环结果，不是平台 |

行业事实基准（可作为我们路演时的外部证据）：Ginkgo + OpenAI GPT-5 已在云端实验室跑通闭环——36,000 反应 / 580 块板 / 15 万数据点 / 6 轮迭代 / 成本降约 40%，人的角色只剩配液与上板。**闭环本身已被证明可行，剩下的竞争就是"谁来当那层 OS"。**

---

## 三、修正前一轮的建议：保留 / 降级 / 作废

| 前一轮建议 | 结论 | 理由 |
|---|---|---|
| ① `wms_content_def` 加完整字段类型系统（TEXT/DROPDOWN/ENTITY_LINK/COMPUTED/SNAPSHOT） | **降级为 P2** | 那是 Registry 的能力。我们只需支撑"耗材/试剂/板/样本"的**执行所需属性**，不需要通用科研建模 |
| ② schema 级唯一性规则配置化 | **降级为 P2** | 执行层唯一性靠**条码 + 板位 + 批号**即可，不必做可配置约束引擎 |
| ③ 四类数据分离（是什么/在哪/结果/谁用过） | **保留，但范围收窄** | 我们只需两层：**资源与位置**（instance + slot + 占用）与**执行结果**（run result）。"是什么"交给上游 Registry |
| ④ 对外 API 统一规范（items+nextToken / returning / batch / bulk / stable-beta / 429） | **保留，升为 P0** | 执行平台的 API 就是产品。我们要对接上游 ELN/LIMS 与客户 MES，契约质量直接决定能不能被集成 |
| ⑤ 权限加 `principal_type = USER\|APP\|DEVICE` | **保留，升为 P0** | 执行层天然有非人主体：设备、调度器、上游系统、AI Agent。没有这个抽象，审计和授权都会烂 |
| ⑥ 归档 archive + reason | **保留，P1** | 运行记录与样本消耗必须可追溯、不可物理删 |

新增（前一轮完全没覆盖的，执行层真正的核心）：

| 新增项 | 说明 | 优先级 |
|---|---|---|
| **⑦ 设备能力模型（Capability）** | 设备不是"一个 ID"，是"一组能力 + 参数约束"（可执行哪些 action、每个 action 的入参 schema、量程、精度、耗时、并发度）。调度靠能力匹配，不靠设备型号硬编码 | P0 |
| **⑧ 指令契约（Command / Workstep）** | 统一 `command` 信封：`action_code + params + 超时 + 重试策略 + 幂等键 + 前置/后置条件`；厂商 SDK 差异收敛在 driver 适配层 | P0 |
| **⑨ 运行记录（Run Record）** | 以 **run → step → device action** 三层记录执行事实：谁、在哪台设备、什么参数、实际耗时、产出、异常。**这是执行平台的"账本"，不是样本表** | P0 |
| **⑩ 可恢复执行状态机** | 失败不重跑整条计划：`PENDING→CLAIMED→RUNNING→(FAILED→RECOVERING)→DONE`，保留中间态与已完成的副作用，支持断点续跑/跳过/人工接管 | P1 |
| **⑪ 事件出口（Event Out）** | 执行事件（run.started / step.completed / sample.consumed / qc.failed）主动推给上游，而不是等上游来轮询 | P1 |
| **⑫ SiLA 2 / OPC UA 适配** | SiLA 2（HTTP/2 + Protobuf）已成事实标准，UniteLabs 直接用它做卖点；不接这个标准，海外设备接入成本会翻倍 | P2（视客户设备池） |

---

## 四、我们现有资产的重新评估

| 资产 | 在执行平台语境下的价值 | 判断 |
|---|---|---|
| **Flow v3（能力×编排正交，route/loop/subflow/timer.wait）** | 这是**编排层**的核心资产，比 Benchling Workflows（偏人工任务流转）更"可执行" | **强项，继续投入** |
| **flow_node 统一承载能力+控制类节点** | 等于已经有"控制流图"，是 run record 的天然宿主 | 强项 |
| **device-task（PENDING/CLAIMED/DONE/FAILED/CANCELLED + CAS 认领）** | 设备主动拉取模式——**封闭内网/气隙场景的天然优势**，海外玩家做不了 | **强项，是差异化卖点** |
| **Sweeper 补偿 / 信号收件箱 / FANOUT 幂等** | 已经是"可恢复执行"的雏形，比多数国产同类强 | 强项，需补"人工接管"出口 |
| **WMS 实例域（六态 + slot 真相源 + 移动流水）** | 执行平台的"资源与位置"层，已经比 Benchling Inventory 更严谨（它 slot 不存占用方，我们也没存，方向一致） | 强项 |
| **样本域（八态 + 谱系五件套）** | 支撑"消耗与产出"追溯，够用 | 够用，不要继续膨胀成 Registry |
| **缺：设备 capability 模型** | 现在设备类型靠字典 + actionCode 反查，缺参数级约束与能力声明 | **P0 缺口** |
| **缺：run record 持久化账本** | 执行事实散在 flow 执行表与 device-task 里，缺统一"运行账本"与对外查询 | **P0 缺口** |
| **缺：事件出口** | 上游（ELN/LIMS）只能轮询 | P1 缺口 |
| **缺：驱动适配层** | 厂商 SDK 差异未收敛，新增设备=改代码 | P1 缺口 |

---

## 五、架构主张（一句话）

> **以"运行（run）"为中心，不以"样本"为中心。**

Benchling 以实体（entity）为中心是对的——它是数据平台，实体的生命周期就是它的主线。
执行平台不同：**一次运行才是原子事实**。样本是运行中被消耗/产出的资源，设备是运行中被占用的资源，结果（result）是运行的产出。所以：

```
Run（一次执行，有起点终点、有编排定义、有状态机）
 ├─ Step（流程节点的一次实例化，对应 flow_node 实例）
 │   └─ DeviceAction（落到某台设备的一个指令，有参数、耗时、幂等键）
 ├─ ResourceClaim（本次运行占用的设备/工位/耗材批次，含释放）
 └─ Output（产出样本实例 / 检测结果 / 原始文件）
```

这条主线一旦立住，四件事自动成立：可追溯（哪个结果来自哪次运行哪台设备）、可恢复（失败定位到 step/action 级）、可计费（设备占用时长）、可对接（上游要 run 记录，我们直接给）。

---

## 六、落地优先级（按"能不能卖"排序）

**P0（3 个月内，决定产品是否成立）**
1. 设备能力模型：`device_capability`（device_id, action_code, param_schema, constraints, estimated_duration, concurrency）+ 能力匹配调度
2. 指令契约统一：`command` 信封（action_code / params / timeout / retry / idempotency_key / precondition）
3. 运行账本 `run_record`（run → step → action 三层，含实际起止时间、设备、参数快照、产出、异常码）
4. 对外 API 规范（照 v3 约定）+ `principal_type`

**P1（3–6 个月，决定能不能进大客户）**
5. 可恢复执行：中间态保留、断点续跑、人工接管入口、失败不重跑全链
6. 事件出口：run/step/sample 事件推送到上游（webhook + 重试 + 死信）
7. 驱动适配层：把厂商 SDK 差异收敛到 `DeviceDriver` 接口（connect / send / poll / cancel / parse）
8. 归档语义 + 审计 actor

**P2（6 个月后，看客户设备池）**
9. SiLA 2 / OPC UA 适配
10. 板位/工位级资源仲裁（多 run 争抢同一 workcell 时的时间窗分配与仿真）
11. 与上游数据平台的标准对接包（Benchling / 国内 ELN / LIMS 各一套映射）

---

## 七、对我们的战略含义（写给自己看）

1. **不要去做 Registry。** 那是 Benchling 的地盘，做一半反而两头不靠。我们做"资源与执行"，边界清晰，也更容易被上游集成而不是被替代。
2. **"设备主动拉取 + 私有化 + 气隙可用"是海外玩家结构上做不到的。** UniteLabs 再开放也是云 SDK，Automata 绑 Danaher 硬件。国内客户的内网实验室是我们的主场。
3. **最大的风险不是 Benchling，是 UniteLabs。** 它用开源 + SiLA 2 + Python SDK 抢"标准制定者"位置。我们要么跟进标准（至少读得懂 SiLA 2，能接），要么在"封闭内网 + 整线交付 + 本地服务"上做它做不到的深度。
4. **闭环已被证明（Ginkgo），下一步是"谁当 OS"。** 我们已有 flow 引擎 + device-task + 幂等补偿，其实离"可编排、可恢复"只差 run 账本与能力模型两件事。**这两件做完，叙事就从"设备管理后台"变成"实验室执行 OS"。**

---

## 八、仍需确认

1. 客户是佰翱得内部，还是要对外卖？（本方案按"可能对外"设计，内部自用可砍掉 P1 的事件出口）
2. 设备池以哪些厂商为主？决定驱动适配层与 SiLA 2 的先后（Tecan/Hamilton 走厂商 SDK，国产设备多半走文件/串口）
3. 是否需要接客户的 Benchling/LIMS？需要 → 事件出口与 run 记录对外契约要提前定稿

---

## 参考

- Benchling Automation 产品页：benchling.com/automation（200+ 连接器、send instructions out、伙伴清单、file watcher 兜底）
- R&D World《The lab OS wars: 15 companies at SLAS 2026》（Automata/UniteLabs/Atinary 同日发布、三层编排哲学、Ginkgo+GPT-5 闭环数据）
- IntuitionLabs《Lab Scheduling Software for Biotech: Booking vs Orchestration》（booking/planning/orchestration 三分、SiLA 2 与 OPC UA LADS）
- Sigmadax《Top 10 Lab Automation Scheduling Software 2026》（VWorks/Overlord/Sapio 的执行状态与恢复模型）
- intuitionlabs LIMS 指南（Benchling 在 QC 场景的能力边界）
