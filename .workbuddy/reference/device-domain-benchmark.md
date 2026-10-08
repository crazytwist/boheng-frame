# 实验室自动化 · 竞品与行业标准对标

> **用途**：为设备域设计提供外部依据。本文**只记录"别人怎么做"与"我们抄什么 / 不抄什么"**，不含设计正文。
> **设计正文见**：`device-domain-architecture.md`（v2，已按本文结论修订）
> 调研日期：2026-09-27

---

## 〇、全景：这个行业分四层，各有事实标准

| 层 | 事实标准 / 头部玩家 | 一句话 |
|---|---|---|
| **设备接口层** | **SiLA 2**（事实标准）、**OPC UA LADS**（工业派） | 设备如何自描述、被调用 |
| **设备管理 + 调度层** | **Cellario**(HighRes)、**Green Button Go**(Biosero)、**MegaFluent**(镁伽)、**FluentControl/i-control**(Tecan) | 驱动库 + 编排调度 |
| **设备接入中间层**（纯软件，不卖硬件） | **UniteLabs**（开源 CDK） | 只做连接，靠标准说话 |
| **数据平台层** | **TetraScience**、**Allotrope**（ADF/ASM/AFO）、**AnIML** | 原始 → 标准化 → 分析 |

---

## 一、必须先知道的事实：SiLA 2 已是行业事实标准

| 事实 | 出处 |
|---|---|
| 治理方 SiLA Consortium，董事会含 **Tecan、Roche、Novartis、GSK、Takeda、Novo Nordisk、Fraunhofer IPA、Zeiss** | sila-standard.com |
| **Tecan 自己开源了 SiLA2 SDK**（BSD-3、.NET），并用它把 **SPARK 酶标仪**接进实验室执行系统（LES） | 论文：SLAS Technology 28(5):334-344, 2023 |
| **Tecan + UniteLabs + Roche 联合开源了 Tecan Fluent 的 SiLA2 Server** | SiLA 设备目录 |
| 生态里已有现成驱动：`Tecan EVOware driver (SiLA 2)`、`Tecan FluentControl Connector`、`Waters LC/MS driver`、`Multidrop driver`、`Brooks TubeMarker 2`、`QInstruments BioShake`… | SiLA 设备目录 |
| **2026-07**：Roche / Bayer / Takeda / BioNTech / Lonza / Novo Nordisk 等签署意向书，**要求 2027 年起新采购仪器必须提供开放 API**，点名 SiLA 2 与 OPC UA LADS | UniteLabs 博客 |
| 他们要摆脱的是「**fragmentation tax**」——每接一台设备就重付一次集成成本 | 同上 |

### ★ 对我们的直接含义

我们对接的是 **Infinite 系列酶标仪（i-control）**；同一家 Tecan 的新一代产品 **SPARK 走的正是 SiLA2 路线**。

> **结论：先按 SiLA 的语义建模型，即使一期走私有 HTTP。** 将来切 SiLA 只换适配层，业务不动。
> 反过来，如果我们一期自拍一套语义，三年后接 SiLA 就要重构。

---

## 二、设备接口层

### 2.1 SiLA 2 —— 核心抽象

| 概念 | 含义 | 我们 v1 的对应 |
|---|---|---|
| **Server / Client** | 设备侧跑 Server，控制方是 Client | 我们的 Driver 是 Client |
| **Feature** | Server 的一组能力（功能单元） | 一组 `DeviceAction` |
| **Command** | 动作，带 Parameters，有返回值；**分 observable / unobservable** | `DeviceAction` + 命令 |
| **Property** | 可读、**可订阅**的属性 | ⚠️ **v1 漏了这半个** |
| **Defined Execution Error** | **受约束**的错误命名空间 | 我们的错误码段位（一致） |
| **FDL** | Feature Definition Language（XML），**设备自描述** | `param_schema` |
| **Discovery** | Server Discovery（mDNS）+ Feature Discovery（"你能做什么"） | 设备台账 + 能力表 |

**四条官方设计原则（原文）**：

1. *"concentrates on **functionality rather than device type**… representing **device behaviour** instead of underlying states"*
   → **不按设备型号建模，按行为建模**（我们的 `DeviceAction` 正是这个路子）
2. *"**Standardized Status**：客户端对错误的处理方式有限，状态命名空间应受约束"*
   → 错误码要收敛，别自由发挥（与我们的段位约定一致）
3. *"**Meta Data**：认证等横切关注点不应依赖接口内的数据交换，而要单独传输"*
   → 元数据与业务数据分离
4. 线格式用 HTTP/2 + Protobuf + gRPC；**observable 命令流式返回「当前步骤 / 预计剩余时间 / 子任务进度」**

### 2.2 ★ SiLA 标准特性集（Part C）—— 几乎就是我们的功能清单

**这是本次最有价值的发现。** SiLA 已经把「设备该有哪些管理能力」标准化了：

| SiLA 标准特性 | 作用 | 我们 v1 里的对应 |
|---|---|---|
| **Initialization Controller** | 设备初始化 / 启动 | ✅ 就是用户说的「启动」 |
| **Parameter Defaults Provider** | 参数默认值 | ⚠️ 只做了 schema，没做默认值 |
| **Parameter Constraints Provider** | 参数约束（范围/枚举） | ⚠️ 约束要有，且**单位是一等公民** |
| **Lock Controller** | 设备互斥锁 | ✅ 「设备占用锁」，但**标准语义更细** |
| **Simulation Controller** | 仿真模式开关 | ❌ **v1 完全没做——但一期就该有** |
| **Observable Command Controller** | 长命令进度订阅 | ⚠️ 有 RUNNING 状态，**缺 progress** |
| **Heart Beat Provider / Keep Alive** | 心跳保活 | ✅ `online_status` + `last_heartbeat_time` |
| **Duration Provider** | 预计耗时 | ⚠️ 只当超时用，没当「预计耗时」用 |
| **Error Recovery Service** | 错误恢复 | ❌ 没做 |
| **Server Monitoring / Alarm / Logging** | 监控告警日志 | 🟡 排在二期 |
| **Audit Trail Service** | 审计追踪 | ✅ 命令表 + 参数快照 |
| **Discovery / Server Registry** | 自动发现与注册 | ❌ 一期「手工录设备」 |
| **Time Normal / Time Sync Provider** | 时间同步 | ❌ 多机时间对齐没考虑 |
| **Orchestration Services** | 编排服务 | 属调度域 |
| **CDS Services**（色谱数据） | 特定领域扩展 | 不适用 |

> **结论**：v1 自己想的东西与标准**重合度约 70%**。
> 重合的**直接改用标准的名字与语义**（省得以后翻译）；没做的 4 项——
> **仿真必做**，错误恢复 / 自动发现 / 时间同步**留字段、可延后**。

### 2.3 OPC UA LADS —— 另一条路线

| 事实 | 说明 |
|---|---|
| 制定方 | OPC Foundation + SPECTARIS + VDMA，**2023-12-14 发布 1.0** |
| 建模方式 | **设备类型无关（device-type-agnostic）**，明确「按设备类型逐个建模会产生不可控的复杂度」 |
| 五个高层用例 | **监控与控制 / 通知 / 程序与结果管理 / 资产管理 / 维护** |
| 合规方式 | **Profile + Facet 分层**：LADS BaseServer 必选 + Maintenance / ProgramManager / ExtendedFunctionalUnit 可选 → **渐进式合规** |
| 与数据标准 | 与 **AnIML** 集成 |

**借鉴两点**：
1. **「程序与结果管理」与「资产管理」被并列为一等用例** → 我们 `device_action`（程序）与 `device_info`（资产）分开建表是对的
2. **Facet 式渐进合规**值得抄 → 一期不必实现全部能力，但要能**声明「我实现了哪部分」**

### 2.4 UniteLabs —— 最接近我们定位的纯软件玩家

- 不卖硬件，**只做连接层**：*"We sell no hardware, so we have no reason to keep instruments closed"*
- 开源 **CDK（Connector Development Kit）**：给行业一套造驱动的框架
- **Introspection 的威力（原文，最重要）**：
  > *"A SiLA 2 server describes its own interface. Introspection means a client reads the device's features, constraints, and units, then **generates itself against them**. **No more custom-built drivers per instrument.**"*
- **约束与单位活在接口里** → 内部校验让「**畸形命令根本无法被构造出来**，更别说发出」
- **连接方向（原文）**：
  > *"instruments run as independent nodes, **distributed, instead of being tied to single Windows desktop computer**. The best part: **the server can open the connection outward**, which we lean on heavily, because it means setup **survives a segmented enterprise network** without a week of IT tickets."*
- 遗留设备：串口 / 文件交换的自定义 connector 纳入同一抽象
- 对 AI 开放：*"Instruments publish **machine-readable capabilities** and are controllable at low level through the API, which lets an AI system execute an experiment, read the result and adjust the next run."*

**借鉴四条硬结论**：
1. **能力必须机器可读、可被程序消费**（不只给人看的表）
2. **约束与单位进接口**，让非法命令构造不出来
3. **连接方向要能反过来**（设备侧向外拨号），解决跨网段 / 内网隔离
4. 遗留设备（串口 / 文件）也要纳入同一抽象

---

## 三、设备管理与调度层

### 3.1 Cellario（HighRes Biosolutions）

| 事实 | 说明 |
|---|---|
| **DriverHost**（4.4 起，取代 Remote Driver Wrapper） | **驱动跑在独立宿主进程里**；目标是「提升设备通信可靠性」+ 为「**远程驱动更新**」打基础 |
| **DDK（Driver Development Kit）** | 让客户自己开发驱动 |
| **500+ 经验证的驱动库** | 这是他们的护城河 |
| ★ **机器可读的能力模型（为 AI 准备）** | *"an AI Scientist can query the lab… for the instruments, assays, methods, and **constraints** available on the floor. The lab returns a **machine-readable capability model** so experiments are designed against **real-world conditions rather than assumptions**."* |
| **执行前仿真** | *"Generated protocols are simulated **before any hardware moves**"* |
| **Protocol Lock-In** | 只有**已验证**的协议能被发起（合规） |
| **审计追踪范围** | 用户与协议管理 / 自动恢复事件 / 系统状态变化 / 维护动作 / 运行仿真 / 脚本库变更 |
| 调度能力 | 并行执行、日历排程、动态逻辑、**Python 脚本注入** |

**借鉴四点**：
1. **驱动独立宿主进程** —— 印证 v1 的「边车」退路；他们真做了，说明收益真实
2. **能力模型给 AI 消费** —— 能力表不该只服务前端下拉，**应作为 SPI 暴露给 AI / 调度**
3. **仿真开关是刚需**（无硬件也能开发联调 + 上线前验证）
4. **「只有已验证的协议能跑」** → 参数集应有 `validated` 状态

### 3.2 Green Button Go（Biosero）

| 事实 | 说明 |
|---|---|
| ★ **两层产品切分** | **GBG Scheduler** = **workcell 内**多设备编排；**GBG Orchestrator** = **跨 workcell / 全实验室**端到端流程 → **调度天然分两层：岛台级 vs 实验室级** |
| **驱动即插件** | 「instrument plug-in modules」，300+ 驱动；**驱动与更新可从云端下载** → 驱动是**可分发制品** |
| 集成方式 | **RESTful API + 数据库钩子** |
| 合规 | 21 CFR Part 11（审计追踪、电子签名） |
| 错误处理 | **给操作员可操作的恢复选项**，不是抛日志 |
| 通知通道 | 邮件 / 声音 / 三色灯 / Slack |

**借鉴三点**：
1. **调度分「岛台级 / 实验室级」两层** → 我们的调度域至少预留这个分层
2. **驱动制品化**（能分发、能单独更新）→ 这是「双模块」决策的**技术理由**
3. **错误要给恢复动作**，不只是错误码

### 3.3 镁伽 MegaRobo（国内最接近的玩家）

| 事实 | 说明 |
|---|---|
| **软件分工四件套** | **中央调度软件 + 数据治理软件 + 信息化管理软件 + 生信分析软件** |
| **五层技术架构** | ① 仪器+自动化硬件 → **单点自动化**；② **实验调度与过程控制** → 工作流整合；③ **实验室高级应用**（无人化智能调度、ELN、**设备/资产/项目信息化管理**）；④ **数据**（数据是核心资产，数据安全是核心痛点）；⑤ **系统级智能决策与自主优化** |
| 软件栈 | **MegaFluent**（调度/编排层：动态+静态调度、并行实验队列、**多厂商设备集成**、样品追踪、远程监控）、**Labillion / LibraX 操作系统**、**Laminar 数字孪生**（布局规划 → 岛台参数建模 → 业务流程仿真） |
| **痛点原话** | 「不同品牌的设备通常接口不一，数据格式也存在大量噪音」→ 靠中央调度 + 数据治理软件**降噪**，保证有效收集、结构化、分析、追溯 |
| **「技术货架」模式** | 新场域开发：**60% 从货架继承 + 20% 工具配置 + 20% 定向研发** |

**借鉴三点**：
1. **五层架构可直接当我们的路线图**（我们正处第 2~3 层之间）
2. **「技术货架」= 驱动可复用 + 参数可配置** → 与 v1 的「动作/参数集」同源，且被证明能压低成本
3. **仿真（数字孪生）** 与 Cellario、GBG 一致 → **三家共识，必须进一期**

> ⚠️ **需你确认**：镁伽的实验室操作系统名为 **Labillion / LibraX**，与我们的基座命名 `labillion-frame` 高度接近，
> 且项目内 grep 不到 `labillion` 字样（说明这个名字来自项目之外）。
> **若同源 / 内部项目，请告知** —— 我会把口径对齐到内部架构，而不是当外部竞品对标。

### 3.4 Tecan 自己的软件分工（我们的目标设备厂商）

| 产品 | 职责 | 对我们的启示 |
|---|---|---|
| **i-control** | Infinite 系列酶标仪控制（**我们要对接的**） | 设备侧软件 |
| **Magellan** | **所有酶标仪的通用数据分析软件**（多检测模式、数据还原） | ★ **分析不只在我们这侧做**，要能接 Magellan 的产出 |
| **FluentControl** | 工作站方法编辑：拖拽、**3D 仿真器**、动态调度、碰撞规避、Labware 感知布局 | 方法 + 仿真 + 动态调度 |
| **Introspect** | 云端**实验室洞察平台**：仪器与工作流数据 → 可操作洞察（利用率、质量、性能），自动错误检测 | ★ 我们的「设备利用率分析」对应它 |
| **Tecan Connect** | 移动端**监控** App（Fluent / Freedom EVO），状态变化推送 | ★ 我们的「告警推送」对应它 |
| **Fluent Gx** | 合规版：用户管理、电子签名、方法审批、**合规性检查器**做完整性验证 | 合规能力的形状 |

> **Tecan 把「控制 / 监控 / 洞察 / 数据分析」拆成四个产品**（i-control / Connect / Introspect / Magellan）。
> **我们的设备域不该把这四件事混成一张表**：控制 → `device_command`；监控 → 心跳+告警；
> 洞察 → 利用率/错误统计；数据分析 → L2（且可能对接 Magellan 产出）。

---

## 四、数据平台层

### 4.1 TetraScience —— 原始 → 标准化 → 分析，行业最成熟

**四步流水线（原文）**：

| 步 | 做什么 | 关键点 |
|---|---|---|
| **1 Connect** | 容器化 connector **部署在你的环境里**（云/本地/混合），监控仪器输出、共享目录、文件服务，**自动拉取，不打断实验流程** | 不要求篡改仪器 |
| **2 Parse and standardize** | 原始文件 → **IDS（Intermediate Data Schema）**：「vendor-agnostic、normalized，让数据可**跨仪器、跨站点、跨时间**比较」 | 标准化的落点 |
| **3 Enrich with context** | 实验元数据、样品 ID、仪器类型、站点、研究者在**摄入时**挂上 | ★★★ *"**attached at ingestion, not reconstructed later**"* |
| **4 Publish** | 标准化数据流向 ELN / LIMS / 分析 / AI / 看板 | 一次标准化，多处消费 |

**其他关键事实**：

- **Medallion 架构**（Databricks）：**RAW → 精炼/清洗/协调 → 聚合**（bronze / silver / gold），用 Delta Lake 承载
- **IDS 标准化四件事**：**命名、数据类型、数据范围（如必须为正）、数据层级**；字段可**链接外部本体**做语义互操作
- **IDS 组件库**：让新建 IDS「像拼 LEGO」
- **Connector SDK**：仪器不在库里时客户自己造，**复用同一套容器化架构、部署模型、输出格式**
- 最刺眼的两句：
  - *"AI models don't fail because of algorithms. **They fail because of data.**"*
  - *"Every script is tied to a specific instrument and a specific software version. **When the vendor pushes an update, the connection fails**"*

**借鉴四点**：
1. **v1 的 L0/L1/L2 与 Medallion 完全同构** → 方向对，**术语可对齐**（raw / standardized / analytic）
2. ★★★ **上下文必须在摄入时挂上**，不能事后重建 → `device_measurement` 必须在**落 L1 时**就关联板实例 / 实验 / 操作人 / 设备
3. **标准化要管「命名 / 类型 / 范围 / 层级」四件事**，范围校验进 schema
4. **connector 三件套统一**（架构 / 部署 / 输出格式）→ 对应我们的「驱动 SPI 统一契约」

### 4.2 Allotrope —— 本体与格式标准

- **四件套**：**AFO**（本体：**Equipment / Material / Process / Results** 四域）、**ADM**（SHACL 约束本体用法）、**ADF**（HDF5 + RDF 归档格式，含 audit trail 与电子签名）、**ASM**（JSON 简化模型，双向兼容 ADM）

> **四域分类正好覆盖我们已有的模块，是一次很好的边界校验**：
> **Equipment** → 设备域（本次）｜**Material** → WMS（已做）｜**Process** → 调度域｜**Results** → 数据层。
> **我们的四域划分与行业本体一致，没有漏域。**

### 4.3 AnIML

- ASTM 标准：「分析信息标记语言」，用于**方法数据与结果数据**
- **与 SiLA 深度绑定**：*"AnIML is embedded into SiLA which serves as the vehicle to transport method and result data throughout an integrated lab"*；SiLA 的 C# 实现直接带 AnIML 模块

> **借鉴**：结果格式不必自造。一期 `raw_json` 可先自定义，但**结果层字段命名建议向 AnIML 靠**，避免三期大改。

---

## 五、五处关键对标结论（直接导致设计修订）

| # | 对标发现 | 对 v1 的修正 |
|---|---|---|
| **1** | 设备能**自描述**（SiLA FDL introspection），客户端 *"generates itself against them"* | v1 把能力当**我们手工填的台账** ❌ → 改为 **驱动上报能力（自动）+ 人工可覆盖** |
| **2** | SiLA Part C 已标准化一批设备管理特性 | v1 靠自己想（重合约 70%）→ **直接用标准名与语义**，缺的 4 项补位 |
| **3** | 连接方向要能**反过来**（Server-Initiated），解决内网 / 跨网段 | v1 的 A/B 二选一 → **「设备侧向外拨号」升为首选形态** |
| **4** | 调度用 **BPMN + 工作流引擎**（UniteLabs、Roche AutoLab 均用 Camunda） | v1 只说「调度域是另一个模块」→ **明确基于 BPMN 引擎，不自研流程引擎** |
| **5** | 数据要 *"context attached at ingestion"* + Medallion 分层 | v1 的 L0/L1/L2 方向对 → **补「摄入时挂上下文」的硬约束**，术语对齐 |

---

## 六、明确不借鉴的（避免被标准拖重）

| 不抄 | 为什么 |
|---|---|
| OPC UA / gRPC / Protobuf 线格式 | 太重。一期 REST/JSON，**只借语义不借协议**；将来可加 SiLA 适配器 |
| 500 个驱动的野心 | 一期只有一台读板机，**驱动库不是一期目标** |
| 云端驱动分发 | 无云端基建；但**驱动制品化**的接口形态要有 |
| 数字孪生 / 3D 仿真（Laminar、FluentControl 3D 编辑器） | 重投入。一期只做**逻辑仿真（假驱动）**，不做 3D |
| RDF / OWL / SHACL 本体（Allotrope ADF） | 学习与维护成本过高；**只借四域分类** |
| 21 CFR Part 11 / GxP 全套 | 一期不面向合规交付；但**审计追踪与签名字段预留** |
| 在 BPMN 之外再自研排程优化 | 排程优化属调度域二期，一期不做 |

---

## 七、一句话结论

**我们要设计的东西，行业已经用「SiLA 2 的语义 + TetraScience 的分层 + Cellario/GBG 的驱动形态」回答了约 70%。**

一期最该做的四件事：
1. **沿用标准语义**（别自己发明词汇，三年后再翻译一遍）
2. **把仿真开关做进内核**（顺带解掉「没有 Tecan 文档就完全动不了」的阻塞）
3. **把连接方向反过来**（设备侧拨号，用现成的 RedisMQ 即可实现）
4. **把上下文在摄入时挂上**（否则半年后数据不可信）

---

## 附：信息源

| 来源 | 用途 |
|---|---|
| sila-standard.com（`/standards/`、FAQ、Part C 索引 PDF、设备目录） | SiLA 2 核心概念、标准特性集、生态驱动 |
| SLAS Technology 28(5):334-344 (2023) | Tecan SiLA2 SDK 论文（SPARK 酶标仪接 LES） |
| highresbio.com / highres.com 博客 | Cellario DriverHost、能力模型、审计追踪、NVIDIA 合作 |
| biosero.com / bico.com / prnewswire | GBG 两层产品、驱动库、仿真、RESTful 集成 |
| tetrascience.com / developers.tetrascience.com | IDS、Medallion、connector SDK、摄入时注入上下文 |
| opcfoundation.org / opcua-lads.com / reference.opcfoundation.org | LADS 五个用例、Facet 分层 |
| allotrope.org / docs.allotrope.org | ADF / ADM / AFO / ASM，四域本体 |
| unitelabs.io（blog / llm-info） | Introspection、连接方向、CDK、行业意向书 |
| megrobo.com / medbot.cn / lab168.com / tonacea.com | 镁伽五层架构、四件套、MegaFluent、Laminar、技术货架 |
| lifesciences.tecan.cn / diagnostics.tecan.com / labautowiki.org | i-control / Magellan / FluentControl / Introspect / Connect / Fluent Gx |
