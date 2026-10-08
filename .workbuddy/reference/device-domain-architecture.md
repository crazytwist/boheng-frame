# 设备域总体架构方案（v6 · 已并入三块拆分与分期落地计划）

> 状态：**v4 改动已落库**（`device.sql` 升 v2.0-device，共 10 张表）
> **v2 变更**：并入 `.workbuddy/reference/device-domain-benchmark.md` 的 5 处对标结论，
> 补上 v1 的真缺口（可观测属性 / 命令进度 / 仿真 / 能力自描述）
> **v4 变更**：并入 `.workbuddy/reference/device-design-prior-art-librax.md`（既往系统对标）的 5 处修订，
> 见 **§0.5**；架构图 `.workbuddy/reference/device-architecture-v4.html`
> **v5 变更**：并入 `.workbuddy/reference/device-transport-boundary.md`（传输层借用边界评估），
> 并补上**忙闲校验 / 并发策略三档开关**，见 **§0.6**
> **v6 变更（本轮）**：并入 `.workbuddy/reference/device-three-blocks-review.md`（三块审核 v3），
> 固化「调用 / 数据 / 仿真」三域的**拆与不拆决策 + 分期落地计划**，见 **§0.7 / §0.8**
> **★ 单页完整版（含数据流图，推荐阅读）**：`.workbuddy/reference/device-domain-architecture-v5.html`
> —— 6 张图：分层总览 / 命令流端到端 / 数据三层 L0→L1→L2 / 接入四条路径 / 五级抽象链 / 忙闲并发矩阵
> 关联：`boheng-module-wms`（位置与流水已就绪）、`wms-movement-ledger-design.md`
> 上一轮：`tecan-device-integration-design.md`（**已被本文收编为其一驱动实现**）
> 日期：2026-09-27 起 · v5 修订 2026-09-30 · v6 修订 2026-10-08

---

## 〇、先说结论

### 0.1 已定

| # | 决策 | 结论 |
|---|---|---|
| 1 | 模块切分 | **拆两个 Maven 模块**（`device-api` 纯契约 + `device` 实现） |
| 3 | 动作模型一期上 | **上**（SiLA 已标准化，竞品全部这么做，晚做要推倒） |
| 4 | 参数集一期上 | **上** |
| 5 | 设备占用 | **台账 CAS 锁**，语义对齐 SiLA `Lock Controller` |
| 9 | SPI 回调一期定义 | **一期定义** |
| **10** | **接入协议**（v3） | **平台只开放 HTTP + MQTT**；串口/私有协议一律由接入层翻译，不进平台代码 |
| **11** | **Node-RED 定位**（v3） | **外置 Transport（协议翻译），不做业务**；且**非必需**（原生支持的设备直连） |
| **12** | **调用方式**（v3） | **三种统一建模**（`dispatch_mode` × `result_mode`），**不是三个接口** |

### 0.2 竞品对标带来的五处修订（v1 → v2）

| # | 对标发现 | 修订 |
|---|---|---|
| **1** | SiLA 的**自描述**：客户端 *"reads the device's features, constraints, and units, then **generates itself against them**"* | ❌ v1 把能力当**我们手工填的台账**<br>✅ 改为 **驱动上报能力（自动）+ 人工可覆盖**，且带来源标记 |
| **2** | SiLA Part C 已标准化一批设备管理特性（v1 自己想，重合约 70%） | ✅ **直接用标准名与语义**；补 v1 缺的 4 项（**仿真必做**） |
| **3** | 连接方向要能**反过来**（Server-Initiated），解决内网/跨网段 | ❌ v1 是 A/B 二选一<br>✅ **「设备侧向外拨号」升为首选形态**（v3 进一步改用 **MQTT** 实现，见 §3.6） |
| **4** | 调度用 **BPMN + 工作流引擎**（UniteLabs、Roche AutoLab 均用 Camunda） | ✅ 明确调度域**基于 BPMN 引擎，不自研**（项目已有 `boheng-module-bpm` 占位） |
| **5** | 数据要 *"**context attached at ingestion**, not reconstructed later"* | ✅ 补 L1 落库时的**上下文硬约束**；术语对齐 raw / standardized / analytic |

### 0.3 v1 的真缺口（对标后才发现）

| 缺口 | 说明 |
|---|---|
| ❌ **可观测属性（Property）漏了** | SiLA 的 Server 能力**一半是 Command，一半是 Property**（温度、仓门、耗材余量、当前程序进度，可读可订阅）。v1 只有动作，**没有遥测** |
| ❌ **长命令没有进度** | SiLA 的 observable 命令流式返回「**当前步骤 / 预计剩余时间 / 子任务进度**」。v1 只有 `RUNNING` 一个状态，前端只能转圈 |
| ❌ **没有仿真开关** | Cellario / GBG / 镁伽**三家共识**。而且**它能解掉我们的阻塞**——没有 Tecan 文档也能先把链路跑通 |

### 0.4 v3 修订：接入形态收敛（本轮新增）

| # | 修订 | 依据 |
|---|---|---|
| **1** | 平台只开放 **HTTP + MQTT** 两种协议 | 行业标准分层：`物理仪器 → 中间件/硬件网关 → 数据总线/中央 API → LIMS`（Zendo LIMS 归纳） |
| **2** | **Node-RED 作为接入层**，但划**四条硬边界**（§3.4） | Node-RED 官方自我定位即「协议网关」，且声明不适合硬实时控制、不替代 SCADA |
| **3** | v2 的「设备代理（RedisMQ）」→ 改用 **MQTT**（RedisMQ 退回内部事件） | 设备侧零 Java 依赖；行业标准；Node-RED 原生支持；生态有 SiLA 2 节点 |
| **4** | **三种调用方式统一建模**（§5.2） | 避免三套状态机 / 三套超时 / 三套测试 |
| **5** | **MQTT Topic + 消息信封契约一期定死**（§3.5） | 含两条硬约束：**幂等必需**、**大载荷转文件引用** |

### 0.5 v4 修订：参考既往系统（librax）之后（本轮新增）

> **对标细节**：`.workbuddy/reference/device-design-prior-art-librax.md`
> **架构图**：`.workbuddy/reference/device-architecture-v4.html`（四张图 + 吸收/保留/不采纳清单）
> **一句话**：**借它的三条运行时不变量，反着做它的两处 bug，保留我们比它强的地方。**

| # | 修订 | 性质 |
|---|---|---|
| **1** | 命令生命周期从 `CREATED` 起 —— **先落事实，再去抢设备**（抢不到也留痕，可统计资源缺口）<br>推论：`device_id` / `device_code` **必须改可空** | ★ 新增 |
| **2** | `status` × `waiting_for` **正交**：`SLOT_AVAILABLE`(等空闲设备) / `DEVICE_CALLBACK` / `POLL_TICK` / `MANUAL_APPROVE` / `EXTERNAL_EVENT` | ★ 新增 |
| **3** | 判 `TIMED_OUT` 前**先对账**（追加一次轮询确认真实状态）—— 防「设备已跑完却被判失败」 | ★ 新增 |
| **4** | 幂等**三键分工**：`uk_command_no`(对外) / `uk_idempotent`(传输重投) / `uk_business(tenant_id, source_type, business_exec_id, node_id, attempt)`(业务执行事实) | 吸收 |
| **5** | `callback_token_hash`（仅存 SHA-256）+ 时间戳重排（`acquired_at`/`sent_at` 替代 `start_time`、`timeout_at` 替代 `callback_deadline`）+ `version` 乐观锁 | 吸收 |

**★ 接缝契约（`device-api`）v4** —— 6 必填 + 5 可选，**不含 `deviceId`**：

```java
String sendCommand(DeviceCommandSendReqDTO req);
// 必填: sourceType, businessExecId, nodeId, attempt, deviceType, actionCode, params
// 可选: paramSetId?, preferredDeviceCode?, dispatchMode?, resultMode?, timeoutMs?, idempotentKey?
// 返回: commandNo   ← 设备侧 job id 单独存 device_job_id，不作幂等键
```

**三类超时分开**（各自挂在不同 `waiting_for` 上）：`queue_timeout`（排队等设备 → 诊断资源缺口）
/ `exec_timeout`（设备慢）/ `callback_timeout`（等回调 → **先降级轮询兜一次**，不可直接判失败）。
优先级链：命令级显式 → `device_action.estimate_duration_ms × 安全系数` → `device_info` 默认 → 全局兜底。

**保留不许退步**：多租户 / 配置与记录分离 / 能力用独立表（**不退回型号表塞大 JSON**）/ 数据三层 / 锁可过期。
**不采纳**：无 `tenant_id`、平台直连 TCP+串口+SDK、执行态只放缓存、先建字段后补实现、
解析脚本存 Groovy（代码注入面）、解析与标准化焊死一处。

**DDL 增量（决策 K 已执行 ✅）**：`device_action` **+5 列**；`device_command` **新增 11 列 + 1 改名 + 删 4 列 / 唯一键 2→3 / `device_id` 改可空**；
`device_info` **+4 列**；新增 **`device_codec`**（9 → 10 张，只开 JSON / REGEX）。
执行记录：`sql/mysql/device.sql`（升 v2.0-device）+ `sql/mysql/device-v4-upgrade.sql`（增量迁移，实测通过）。

### 0.6 v5 修订：传输层边界 + 忙闲/并发开关（本轮新增）

> **评估全文**：`.workbuddy/reference/device-transport-boundary.md`

| # | 修订 | 依据 |
|---|---|---|
| **1** | ★★ **忙闲校验必须有开关** —— `device_info.busy_check_policy`(`AUTO`/`ALWAYS`/`NEVER`) × `device_action.need_busy_check` | **有的仪器不返回空闲状态**，写死则这类设备永远下不发命令 |
| **2** | ★★ **并发策略必须有开关** —— `device_info.concurrency_policy`(`EXCLUSIVE`/`DEVICE_QUEUED`/`PLATFORM_QUEUED`) + `max_inflight` + `inflight_count` | **有的仪器自带本地队列**，强制独占会白白浪费仪器吞吐；三档都不写死 |
| **3** | **传输层是 device 自己的 SPI** —— `DeviceTransport`（主模块内定义），外部网关只能作为其**实现**接入 | 保住「device 整体模块化」：删掉适配器模块，主模块零改动 |
| **4** | **iot-gateway 借「协议接法」，不借「模块 + 物模型 + 部署形态」** | 它是 **sidecar（独立 Spring Boot 应用 + RestTemplate RPC）**，消息接缝是物模型 `IotDeviceMessage`（`deviceId` 是 **Long 主键**），且会拖进 `rocketmq-spring-boot-starter` |
| **5** | MQTT 一期：**连外部 broker（EMQX）** 或 **HTTP 直连**；「平台自当 broker」为备选 | 三者**都是 `DeviceTransport` 的实现**，切换不影响上层任何代码 |

**这两个开关解决了什么**：三类仪器行为差异被收敛为**两个枚举字段**，而不是三套代码路径：

| 仪器类型 | `busy_check_policy` | `concurrency_policy` | 下发行为 |
|---|---|---|---|
| 标准仪器（能报空闲、一次一条） | `AUTO` | `EXCLUSIVE` | 台账 CAS 抢占，抢不到排队 |
| **不报空闲状态**的仪器 | `NEVER` | `EXCLUSIVE` | **跳过预检**，直接下发 |
| **自带本地队列**的仪器 | `AUTO` 或 `NEVER` | `DEVICE_QUEUED`（`max_inflight`=队列深度） | **不做独占**，连续下发 |
| 既不报空闲也不支持并发 | `NEVER` | `PLATFORM_QUEUED` | 平台侧排队，依次下发 |

⚠️ **消歧**：`device_command.dispatch_mode`（`SYNC`/`ASYNC`，**下发方式**）与「下发前要不要查忙闲」**不是一回事**；
后者命名为 `device_action.need_busy_check` + `device_info.busy_check_policy`，**刻意不叫 `dispatch_mode`**
（librax 对那个名字用了相反语义，避开以免二期对接踩坑）。

---

### 0.7 ★ v6 三域拆与不拆（本轮定案）

> 完整审核过程见 `.workbuddy/reference/device-three-blocks-review.md`（v3）。
> 本节是把「调用 / 数据 / 仿真」三块的**模块与部署边界**定死，是 §0.8 分期计划的依据。

**一句话结论**：三块的分类轴不统一 —— ①调用是**控制面**（命令下行），②数据是**数据面**（数据上行），
③仿真不是并列的第三块，而是**控制面里一个可替换的驱动实现**。按「控制面 / 数据面」这一刀切，才不产生旁路。

| 块 | 本质 | 一期边界（现在） | 二期边界（升级触发点） |
|---|---|---|---|
| ① 调用 invoke | 控制面 | **留在 `device` 模块**，包名 `invoke` 子域 | 不拆（它是设备域本体） |
| ② 数据 ingest | 数据面 | **逻辑上拆**：过程时序数据进独立时序库；物理上一期同进程，包名 `ingest` 子域 | **拆独立模块 → 独立服务**（数据平台团队就位 / 数据量上来） |
| ③ 仿真 simulate | 控制面的一种实现 | **不拆**：留在 `device` 模块，包名 `simulate` 子域，做成 `DeviceDriver` 的一个实现 | **拆独立模块 → 独立服务**（升级为真实时序模拟时） |

**三条硬判断标准（为什么这么拆）：**

| 块 | 要不要现在拆服务 | 唯一判断标准 | 触发条件是否已满足 |
|---|---|---|---|
| 仿真 | 不要 | 是否要**产过程时序数据** | 一期「接口可调通」不产 → 不满足；二期模拟真实时序 → 满足 |
| 数据平台 | **要（逻辑边界现在就定死）** | 数据流方向是否与业务**相反** | 天然相反（命令下行 vs 数据上行）→ 一期就存在 |
| 调用 | 不要 | —— | 它就是设备域本体，无「拆」一说 |

**仿真的「独立」分两层（二期才做，且先做 A 再做 B）：**

```
A. 独立 Maven 模块（同进程，代码隔离）      ← 二期先做这个
B. 独立部署服务（独立进程 + 独立库 + 独立 broker）← 参数扫描/数据量真上来才做
```

**数据平台的「拆」也分两层（逻辑 vs 物理）：**

```
逻辑边界（一期就定死）：只认 command_no 关联，不共享 Java 对象，过程时序独立库
物理部署（一期可缓）：同进程 ingest 包先跑通；团队/数据量上来再拆独立服务
```

**三条铁律（违反即技术债，写进代码评审清单）：**

| # | 铁律 | 违反后果 |
|---|---|---|
| 1 | 仿真必须走真实命令入口，禁止 `if (simulated) return fake` 旁路 | 状态机/幂等/落库在仿真下不验证 = 真机一接就崩 |
| 2 | 仿真数据与真机同构（同一 codec / L0→L1 路径） | 仿真跑通 ≠ 真机跑通 |
| 3 | 过程数据绝不塞进 `device_measurement_data`（那是孔×波长二维快照，无时间轴） | 千万行 + 概念污染 |

---

### 0.8 ★ v6 分期落地计划（每个节点做什么）

> 这是「现在该动手干什么」的执行清单，按依赖排序，不是按块排序。
> 两条主线（设备域 / 数据平台）可并行，**汇合点在 P4**（端到端联调）。

**阶段总览（依赖图）：**

```
设备域主线（现有仓库）            数据平台主线（新系统）
P0 抽 DeviceDriver SPI              D0 时序库选型 + 建库
P1 SimulatedDriver（接口可调通）     D1 元数据目录（属性字典）
P2 「读板」路径闭环（L0→L1→L2）        │
        └──────────┬────────────────┘
                   ▼
        P3/P4 共享契约 + 遥测写入通道（★ 汇合点）
                   │
              端到端联调：仿真设备 → 命令 → 数据 → 查询
```

**节点清单（每个节点 = 一件可验收的事）：**

| 节点 | 归属 | 内容（做什么） | 验收标准 | 依赖 |
|---|---|---|---|---|
| **P0** | 设备域 | 抽 `DeviceDriver` SPI：把焊死在 `DeviceHttpService`/`DeviceHttpGateway` 的「查台账→发 HTTP」收敛为 `HttpDriver` 一个实现；`driver_type` 定为分派唯一依据 | 新增驱动无需改上层；`HttpDriver` 行为与现状等价 | 无 |
| **P0b** | 设备域 | 消歧三字段：`driver_type`(分派) / `connection_type`(物理到达) / `simulation_mode`(dry-run 覆盖开关)；消除「SIMULATED 设备点调用抛错」 | `connection_type=SIMULATED` 能正确路由到 `SimulatedDriver` | P0 |
| **P1** | 设备域 | 实现 `SimulatedDriver`（接口可调通级）：走真实命令入口 + 确定性成功/失败 + 同构 L0 | 仿真设备点「调用」经状态机+落 L0，返回可预测结果 | P0 |
| **P2** | 设备域 | 「跑完读板」路径闭环：`ingest` 子域内打通 raw→measurement→analysis，仿真设备也能走通 | 一次仿真调用产出完整的 L0/L1/L2 | P1 |
| **D0** | 数据平台 | 时序库选型验证（TDengine 无模式写入 + 乱序补传实测）+ 建库建超表 | 压测通过，选型结论有数据支撑 | 无（与 P0 并行） |
| **D1** | 数据平台 | 元数据目录（属性字典/标签定义），与 `device_property` 对齐 | 过程数据具备语义 | D0 |
| **P3** | 双侧 | **共享契约模块 `device-contract`**（设备身份/topic 命名/上行信封/时间语义/上下文最小集）+ **遥测写入通道**（一期走 A：设备域分流写时序库） | 两侧只依赖契约，无反向依赖 | P0+P1+D0 |
| **P4** | 双侧 | **第一次端到端联调**：仿真设备 → 命令 → 过程数据 → 时序库 → 查询出曲线 | ★ 本轮的验收标准达成 | P3 |
| **P5** | 设备域 | 仿真完整形态：虚拟时钟 / 录制回放 / 参数扫描（此时才升级为「独立模块/服务」） | 场景 C（实验预演）成立 | P4 + 时间基契约 |
| **P6** | 设备域 | 上行接入层独立化：MQTT 订阅 + 设备认证 + 幂等 + 断网续传 | 真机主动上报可用 | P3 |
| **P7** | —— | 真机驱动 / Node-RED 接入 / 调度域对接 | 真实仪器跑通一块板 | 前述全部 |

**立即能开工（不依赖任何待决策项）：P0 → P0b → P1 → P2** 与 **D0 → D1** 并行。
真正需要停下来对齐的只有 **P3（共享契约）** 一个点。

---

## 一、模块切分（决策 1 已定：双模块）

| 模块 | 内容 | 硬约束 |
|---|---|---|
| **`boheng-module-device-api`** | **纯契约**：接口 + DTO + 枚举 | **零 Spring、零 MyBatis、零 DAL** |
| **`boheng-module-device`** | 实现：controller / service / dal / driver / job / mq | 依赖 `device-api`，反向实现契约 |

**理由（含竞品依据）**：
1. **编译期强制依赖方向**。现有项目是**包级 api**（`module/system/api/`、`module/infra/api/`，已核实），**靠自觉、不设防**——谁顺手 `@Resource` 到别人的 `ServiceImpl` 也不报错。拆 jar 后**物理上拿不到**。
2. **竞品印证**：Biosero 的驱动是「**instrument plug-in modules**」，且**驱动与更新可从云端下载**；HighRes 有独立 **DDK**。
   → 行业把驱动当**可分发制品**，而不是代码里的 if-else。**制品要能独立依赖，就必须有独立契约 jar。**
3. **退路**：将来设备域拆独立进程，`device-api` 原样复用，只换 ApiImpl。

### 1.1 ★ 两个 SPI（这是「SPI 格式」的完整含义）

```
        ┌──────────────────────────────────────────────────────┐
        │  对外 SPI：业务模块 → 设备域                          │
        │  boheng-module-device-api                            │
        │  DeviceApi / DeviceCommandApi / DeviceResultApi       │
        │  / DeviceCallback（消费方实现）                       │
        └──────────────────────────────────────────────────────┘
                              ▲ 消费方只依赖它
        ┌─────────────────────┴──────────────────────────────┐
        │  boheng-module-device（实现）                       │
        │                                                     │
        │  ┌───────────────────────────────────────────────┐  │
        │  │  对内 SPI：设备域 → 驱动实现                   │  │
        │  │  ...device.driver.spi.DeviceDriver             │  │
        │  │  一厂商一实现；声明能力 + 执行命令 + 采集数据   │  │
        │  └───────────────────────────────────────────────┘  │
        └────────────────────────────────────────────────────┘
```

**驱动 SPI 放哪里**：一期放**实现模块内**（`...device.driver.spi` 独立包名），因为一期驱动是我们自己写。
二期若要开放第三方驱动市场，**把这个包整体抽成 `boheng-module-device-driver-api`** ——包路径不变，**迁移成本几乎为零**。

### 1.2 `device-api` 内容清单

```
cn.boheng.frame.module.device.api
├── DeviceApi              查设备 / 按条件选设备 / 读能力（给前端与 AI）
├── DeviceCapabilityApi    ★ 机器可读能力模型（供调度域与 AI 消费，见 §二.3）
├── DeviceCommandApi       ★ 调度域主要入口：下发 / 查状态 / 查进度 / 取消
├── DeviceResultApi        取测量结果与原始数据（给分析 / 报表 / AI）
├── DeviceCallback         同步回调（消费方实现，device 调用）
├── enums/   DeviceTypeEnum / CommandTypeEnum / CommandStatusEnum /
│            DeviceOnlineStatusEnum / BizSourceEnum / RecoveryActionEnum
└── dto/     DeviceRespDTO / DeviceCapabilityRespDTO / DeviceSelectReqDTO /
             DeviceCommandCreateReqDTO / DeviceCommandRespDTO（含进度）/
             DevicePropertyRespDTO / MeasurementRespDTO / MeasurementDataRespDTO
```

**纪律**：`controller` / `service` / `dal` / `driver` **一律不进 `-api`**。
`-api` 只允许出现「接口 + record/DTO + 枚举」，`package-info` 写明「提供给其它模块的 API」。

---

## 二、设备管理域内部

### 2.1 抽象链（v2：补上了属性与仿真）

```
设备类型 DeviceType           这类设备能干什么（按行为建模，不按型号）
   │ 1:N
   ▼
设备动作 DeviceAction         「能被怎么启动」
   ├─ standard_feature        ★ 映射 SiLA 标准特性名
   ├─ param_schema            ★ 参数模式：默认值 + 约束 + 单位
   ├─ vendor_ref              厂商映射（i-control 脚本/方法名）
   └─ source                  ★ 能力来源：DEVICE_DECLARED / MANUAL
   │ 1:N
设备属性 DeviceProperty       ★ v2 新增：「能被观测到什么」（遥测，可读可订阅）
   │                          温度 / 仓门状态 / 耗材余量 / 当前程序进度
   │ 1:N
参数集 DeviceParamSet         「这一次下什么参数」
   ├─ params_json             取值
   └─ validated               ★ 是否已验证（只有 validated 才能被发起）
   │
   ▼
命令 DeviceCommand            「实际下发了什么」+「跑到哪了」
   ├─ params_json / payload_json   参数快照 + 渲染后 payload
   ├─ progress_percent / current_step / step_total / eta_sec   ★ v2 进度
   └─ recovery_action          ★ v2 失败时给「可操作的恢复动作」
   │
   ▼
驱动 DeviceDriver（对内 SPI）  「怎么变成厂商协议」
   ├─ describeCapabilities()   ★ 自描述：上报 action/property 清单 + 参数模式
   ├─ execute() / queryStatus() / fetchResult()
   └─ 实现：TecanReaderNetworkDriver / SimulatedDriver(仿真) / …
```

### 2.2 ★ 参数模式（param_schema）的完整形态

对标 SiLA 的 `Parameter Defaults Provider` + `Parameter Constraints Provider`，**单位是一等公民**：

```jsonc
{
  "wavelength_nm": {
    "label": "检测波长",
    "type": "integer",
    "unit": "nm",                      // ★ 单位进接口（SiLA 明确要求）
    "required": true,
    "default": 600,                    // ★ 默认值（v1 缺）
    "constraints": { "min": 200, "max": 1000, "step": 1 },   // ★ 约束（v1 缺）
    "source": "DEVICE_DECLARED"        // ★ 驱动上报 vs 人工填写
  }
}
```

**为什么约束要进 schema**（UniteLabs 原话）：*"internal validation prevents a **malformed command from ever being created**, let alone being sent."*
→ 非法参数**在构造阶段就被拦住**，不消耗一次设备往返。

**为什么 `source` 重要**：这是「能力自描述」的落点（§2.3）。

### 2.3 ★ 能力自描述机制（本次最大的架构升级）

**v1 的做法（错）**：能力是**我们手工填的表**，接一台新设备要人工录一遍动作和参数。
**v2 的做法（对标 SiLA introspection）**：

```
① 装驱动 → 驱动自带能力描述（代码声明，或随驱动包一份 FDL 式描述文件）
② 注册设备 / 首次探活 → 设备域读 drivr.describeCapabilities()
③ 自动导入 → device_action / device_property 打上 source=DEVICE_DECLARED
④ 人工可覆盖 → 新增/修改的打 source=MANUAL，且**留痕**（谁改的、原值是什么）
⑤ 冲突策略 → MANUAL 优先，但驱动升级导致声明变化时**提示人工复核**（不静默覆盖）
```

**收益（竞品已验证）**：
- UniteLabs：*"**No more custom-built drivers per instrument**"* —— 客户端**对着设备声明的接口自己生成**
- Cellario：能力模型是**机器可读**的，**AI 直接查询**实验室「有哪些仪器、方法、约束」，*"so experiments are designed against **real-world conditions rather than assumptions**"*
- 对我们：**接新设备 = 装驱动 + 注册 + 能力自动带出，不写前端页面**

### 2.4 设备属性（Property）—— v1 漏掉的半个 SiLA

| 概念 | 说明 | 存储 |
|---|---|---|
| **属性定义** | 这台设备有哪些可观测属性（code / 名称 / 数据类型 / 单位 / 是否可订阅） | `device_property`（一期） |
| **属性当前值** | 快照，供列表页与"按状态选设备"用 | `device_info.telemetry_json`（一期） |
| **属性历史** | 时间序列（画温度曲线、算利用率） | `device_status_log`（**二期**） |

> 一期只需「定义 + 快照」就能支撑：设备状态看板、按状态选设备、故障时看当时温度。
> **历史轨迹排二期**，因为它是观测数据，与「原始/标准化/分析」三层不同源，别混进去。

### 2.5 设备选择：A 指定 / B 条件

| 模式 | 输入 | 用途 | 期次 |
|---|---|---|---|
| **A 指定** | `device_code` | 手动操作、调试、固定工位 | **一期** |
| **B 条件选择** | `{类型, 能力, 属性状态, 在线, 空闲}` | "随便哪台酶标仪都行"、负载均衡、**AI 规划实验** | 二期 |

> B 模式是「能力不能塞 JSON」的**根本原因**——要能 **JOIN 查询**。
> v2 因为一期就建了 `device_action` / `device_property` 独立表，**二期不必再加表**，只加查询接口。

---

## 三、接入形态（v3 重写：平台只认 HTTP + MQTT，其余交给 Node-RED）

> **v3 修订说明**：v2 把「连接形态」理解为 A/B 二选一（后端直连 vs 设备代理）。
> v3 把它升级为**协议收敛**问题——**平台对外只暴露两种协议，仪器无论多老旧，都由接入层翻译成这两种**。
> 这一修订同时把 v2 的「设备代理」从"二期备选方案"降级为"接入层的其中一种实现"。

### 3.1 ★ 决策：平台侧只开放两种协议

| 协议 | 方向 | 定位 | 理由 |
|---|---|---|---|
| **HTTP/REST** | 平台 → 设备（请求响应） | **同步短命令** | 通用、可调试（curl 即可）、防火墙友好 |
| **MQTT** | 双向 | **异步命令 + 上报 + 遥测** | 物联网事实标准；**设备侧主动连出**，天然穿透网段隔离 |

**其余一切协议**（串口 RS-232/485、私有 TCP、Modbus、OPC UA、厂商 DLL、文件轮询、SCPI……）
→ **一律不进平台代码**，由**接入层翻译成上面两种协议之一**。

> **这个决策的行业依据**（Zendo LIMS 对 LIMS 集成架构的归纳，非我们自创）：
> ```
> 物理仪器 → 中间件/硬件网关（捕获输出、翻译、规范化为统一结构）→ 数据总线/中央 API → LIMS
> ```
> 「中间件/硬件网关」这一层**本来就该存在**，问题是**用什么实现**。我们的答案：**Node-RED**。

### 3.2 三层接入拓扑

```
┌─ 后端（企业网） ─────────────────────────────────────────────────────┐
│  DeviceCommandService / DeviceDriver                                 │
│         │                                                            │
│         ├── HttpTransport   ──── HTTP ────┐                          │
│         ├── MqttTransport   ──── MQTT ────┼──────────────┐           │
│         └── SimulatedDriver（一期必做，见 §四）            │           │
└──────────────────────────────────────────────────────────┼───────────┘
                                                           │
        ┌──────────────────────────────────────────────────┘
        │
        ├─ ◯ 路线 A：原生直连（仪器自带 HTTP / MQTT 服务）
        │    后端 ──HTTP/MQTT──▶ 仪器         [少一跳，少一个故障点]
        │
        ├─ ◯ 路线 B：Node-RED 网关（仪器是串口 / 私有协议 / DLL / 文件）
        │    后端 ──HTTP/MQTT──▶ Node-RED ──串口/私有/TCP──▶ 仪器
        │
        └─ ◯ 路线 C：仿真（无硬件）
             后端 ──SimulatedDriver──▶ 假数据         [一期先跑通全链路]
```

**三条路线对后端完全等价** —— 因为都收敛到 `DeviceTransport` 接口之后。
`device_info.connection_type` 决定走哪条（`HTTP` / `MQTT` / `NODE_RED_HTTP` / `SIMULATED`）。

### 3.3 ★ Node-RED 的定位：**外置 Transport，不是中间业务层**

**结论：它的位置是对的，但必须当成"网线"用，不能当成"服务器"用。**

**为什么位置对**（三条独立证据）：

1. **行业标准分层的第二层就是它**（见 §3.1 引用）——中间件/硬件网关。
2. **Node-RED 官方自我定位就是协议网关**：Modbus / OPC UA / MQTT / 串口 / 私有 TCP 全覆盖，
   且**明确声明自己不替代 SCADA、不适合硬实时控制** —— 说明**它自己也不认为该承载业务**。
3. **实验室自动化生态已接纳它**：社区有官方 SiLA 2 节点 `@synefex/node-red-sila2`
   （可当 SiLA 2 客户端），**未来仪器若走 SiLA 2，Node-RED 接线即可，不写代码**。

**它替我们解决的问题**：私有协议驱动的**现场可维护性**。
老旧仪器的协议差异本该由现场工程师用拖拽解决，而不是每次改一次 Java 发一次版。

### 3.4 ★ 四条硬边界（不划清，半年后必成技术债）

| # | 边界 | 违反后果 |
|---|---|---|
| **1** | **只做协议翻译与传输**。命令状态机、重试策略、业务校验、结果解析**一律在后端** | Node-RED **无持久化**（重启 state 全丢）、**无事务**、**flow 是 JSON 无法 code review**、错误处理弱、单进程。业务逻辑一旦落进去 = **隐形业务系统**，无法测试、无法审计、无法回滚 |
| **2** | **不是必需品，是可选实现**。仪器自带 HTTP/MQTT 时**直连，不绕 Node-RED** | 没有意义的额外一跳 = 额外故障点 + 额外延迟 + 额外运维面 |
| **3** | **flow 必须纳入 git**（导出 JSON 提交），且**命名规范统一** | Node-RED 的配置漂移**无声无息**：现场改了流，平台不知道，排障时两边对不上 |
| **4** | **必须开认证 + 网络隔离**，禁止暴露到公网 | Node-RED 默认无认证 + 暴露开发工具 = **远程代码执行**风险（官方风险清单第一条） |

**另外一条工程约束**：Node-RED 的可用性靠"**流备份 + 配置管理 + 日志 + 监控**"保障，
官方明确列出失效场景包含**卡死的流 / 内存泄漏 / broker 连接丢失 / 脚本缺陷** ——
所以它**必须配监控**（我们用 `boheng-spring-boot-starter-monitor` 的思路，做**心跳超时告警**：接入层超过 N 秒无心跳即告警）。

### 3.5 MQTT 契约（一期必须定死，避免二期改契约）

**Broker**：EMQX（推荐，国内生态好、有免费版、Web 控制台）或 Mosquitto（更轻）。

**Topic 规范**（`{ns}` 建议 = `boheng`）：

| Topic | 方向 | QoS | 内容 |
|---|---|---|---|
| `{ns}/device/{deviceCode}/cmd` | 平台 → 设备 | 1 | 命令下发（含 `commandNo` / 参数 payload） |
| `{ns}/device/{deviceCode}/cmd/ack` | 设备 → 平台 | 1 | **已收到**（≠ 已完成，仅表示接受） |
| `{ns}/device/{deviceCode}/progress` | 设备 → 平台 | 0 | 进度、当前步骤、ETA（`progress` 允许丢） |
| `{ns}/device/{deviceCode}/result` | 设备 → 平台 | 1 | 完成结果（**小载荷**，见下方约束） |
| `{ns}/device/{deviceCode}/telemetry` | 设备 → 平台 | 0 | 属性/遥测上报（温度、耗材余量…） |
| `{ns}/device/{deviceCode}/status` | 设备 → 平台 | 1 | 在线状态；**用 LWT 遗嘱消息实现离线检测** |

**统一消息信封**（所有 topic 共用）：
```json
{ "commandNo": "CMD20260927-0001", "deviceCode": "TECAN-READER-01",
  "type": "RESULT", "ts": "2026-09-27T12:00:00+08:00", "payload": { } }
```

**两条必须写进契约的硬约束**：

1. ⚠️ **QoS 1/2 = at-least-once，消息必然可能重复** → **`idempotent_key` 不是可选项**
   （与 `device_command.uk_idempotent` 是同一套机制，正好复用）。
2. ⚠️ **大载荷禁止走 MQTT**。孔级数据可能几 MB，会把 broker 打爆。
   规则：**> 64KB 的载荷一律转文件**——设备侧（或 Node-RED）上传到 **infra 已有的文件服务**（S3/SFTP 客户端已就绪），
   MQTT 只传 `{ fileUrl, size, sha256, summary }`。**这一条一期不定，二期数据量上来必炸。**

### 3.6 与 v2「设备代理（RedisMQ）」的关系

v2 曾提出用 `RedisMQTemplate` 做设备代理，实现「设备侧主动向外连」。
**结论：MQTT 方案更好，RedisMQ 降级为内部事件通道。**

| 维度 | v2 RedisMQ 代理 | v3 MQTT 网关 |
|---|---|---|
| 穿透网段隔离 | ✅（但需设备侧跑 Java 代理） | ✅（**只需一个 MQTT 客户端，Node-RED 原生支持**） |
| 设备侧依赖 | **要写 Java 代理** | **Node-RED / 任意 MQTT 库，零 Java** |
| 是否行业标准 | ❌ 私有 | ✅ IoT/Lab 事实标准 |
| 与 SiLA 2 的关系 | 无关 | Node-RED 有现成 SiLA 2 节点 |

**两者共存、各司其职**：`RedisMQ` 继续做**模块间内部事件**（已有），`MQTT` 专门做**对外设备通信**。不冲突。

### 3.7 一期怎么落（务实）

| 顺序 | 内容 | 依赖条件 |
|---|---|---|
| **1** | `Transport` 抽象 + **`SimulatedDriver`** | **零外部依赖，先跑通全链路**（见 §四） |
| **2** | `HttpTransport`（直连仪器）+ **接入层骨架** | 仪器支持 HTTP 即可 |
| **3** | **MQTT starter + broker 容器**（EMQX） | 本地 `docker` 起 EMQX；依赖已在 `boheng-dependencies` |
| **4** | Node-RED 容器 + 一条串口/MQTT 示范流 | 有需要接的串口仪器时 |
| **5** | Tecan 真实驱动 | ⚠️ **仍需接口文档**（见 §十） |

**基建工作量提示**：现有 `HttpUtils.post/get` **只有"发出去拿回字符串"**，无超时 / 重试 / 连接池 / 响应解析——
**太弱，不能直接当 `HttpTransport`**。需新增基于 **OkHttp**（`boheng-dependencies` 已备 `okhttp 4.12.0`）的封装。
这是本次必须计入的工作项。

### 3.8 一期明确不做

❌ 任务编排 / 步骤依赖（属**调度域**）
❌ 液体处理工作站联动、机械臂自动进板
❌ 建方法 / 改仪器参数（一期只"用已有方法跑板 + 取数"）
❌ 自动发现（mDNS/Zeroconf）—— 一期手工录设备，但 `device_info.discovery_type` 留字段
❌ 3D / 数字孪生仿真 —— 一期只做**逻辑仿真（假驱动）**
❌ **在 Node-RED 里做业务逻辑**（硬边界 1，永久不做）

### 3.9 ★ 传输层归属：为什么 device 自定义 SPI（v5 新增）

**结论：传输层是 device 自己的 SPI，外部网关（芋道 `iot-gateway` 等）只能作为它的「实现」接入。**

评估全文见 `.workbuddy/reference/device-transport-boundary.md`。三条否决直接引用的理由：

| 理由 | 事实 |
|---|---|
| 它是 **sidecar** | 独立 Spring Boot 应用 + `RestTemplate` RPC 回调主程序 → 多一个进程、多一套契约 |
| 它的接缝是**物模型** | `IotDeviceMessage.deviceId` 是 **Long 主键**（我们跨模块只认 `device_code`）；`method` 是物模型语义（不是我们的 `action_code`） |
| 它拖**重依赖** | `rocketmq-spring-boot-starter` 会被带进主应用 |

**落地形态（三层，依赖单向）**：

```
device-api（纯契约）
   ↑
boheng-module-device（主模块）— 自定义 DeviceTransport SPI + Http/Mqtt/Simulated 三实现
   ↑
boheng-module-device-transport-iot（可选适配器，按需启用）— 依赖 iot-core
```

```java
public interface DeviceTransport {
    String type();                               // 与 device_info.connection_type 对齐
    TransportResult send(TransportRequest req);  // 只搬运，不含业务语义
}
// TransportRequest 入参是 deviceCode（★不是 deviceId）；TransportResult 只回原文，解析交给 device_codec
```

**为什么这样才叫「模块化」**：主模块不知道 iot 存在；删掉适配器模块，主模块零改动；新增协议只需 `implements DeviceTransport`。

**抄实现、不引依赖的三块**（一期就能用，零成本）：EMQX 认证事件报文形状 / Vert.x `MqttServer` 起法 / MQTT topic 拼装。

---

## 四、★ 仿真开关一期必做（对标后的新增结论）

**三家竞品共识**：
- Cellario：*"Generated protocols are simulated **before any hardware moves**"*
- GBG：*"Use GBG Scheduler's **simulation environment** to test and optimize workflows before going live"*
- 镁伽：**Laminar 数字孪生**，布局规划 → 岛台参数建模 → 业务流程仿真，"先胜后战"
- SiLA：**`Simulation Controller` 是标准特性**（不是我们发明的）

**它顺便解掉了我们当前的硬阻塞**：

| 现状 | 有了仿真驱动之后 |
|---|---|
| Tecan 接口文档拿不到 → **接口层完全动不了** | **仿真驱动先跑通全链路**（下发 → 状态机 → 进度 → 收数 → 落 L0/L1 → 出分析标记） |
| 没有第二台仪器 → 多设备场景没法测 | 想开几台仿真设备就开几台 |
| 前端要等后端接通才能联调 | 前端对着仿真设备联调 |

**实现**：`DeviceDriver` 的第二个实现 `SimulatedDriver`（按 `device_info.simulation_mode` 或 `driver_type=SIMULATED` 分派），
按 `param_schema` 校验后**返回符合真实形状的假数据**（如 96 孔正弦波 OD 值）。

> **成本极低，收益极高。** 这应该是设备域第一个能跑起来的东西——**先于 Tecan 驱动**。

---

## 五、执行模型：异步命令 + 状态机 + 进度 + 锁 + 错误恢复

### 5.1 状态机

`PENDING → QUEUED → DISPATCHED → RUNNING → SUCCESS | FAILED | TIMEOUT | CANCELLED`

```
POST /device/command/execute
   │
   ├─ 1. 幂等校验（uk_idempotent）→ 重复请求直接返回原命令
   ├─ 2. 参数校验：按 param_schema 的 constraints 校验 → 非法参数当场拒绝（不消耗设备往返）
   ├─ 3. 抢占设备（CAS lock）→ 抢不到则 QUEUED 排队（或 ifBusy=REJECT 快速失败）
   ├─ 4. 落 device_command(PENDING)，立即返回 commandId     ← HTTP 到此结束
   │
   └─ 5. 异步执行
          ├─ 心跳校验在线 → Driver.execute() → 拿 deviceJobId
          ├─ status=RUNNING + 存 device_job_id
          ├─ 轮询/订阅进度 → 更新 progress_percent / current_step / eta_sec
          ├─ 完成 → 拉结果 → 写 L0 raw → 解析出 L1（**同时挂上下文**）→ 标记 L2
          │        → status=SUCCESS → 发 MQ 事件 → 调 SPI 回调
          └─ 失败/超时 → status=FAILED + 仪器原文 + **recovery_action**
          └─ 全程记 request_json / response_json / 耗时（排障的生命线）
```

### 5.2 ★ 三种调用方式：不是三个接口，是同一个命令的三种策略

> **要集成，但必须统一建模。** 反面做法是给 sync / 异步 / 轮询各开一个接口、各写一条代码路径 ——
> 后果是**状态机三套、超时逻辑三套、事件三套、测试三套**，最终必然出现"某条路径漏了幂等、漏了流水"。

**正面做法：两个正交维度。**

| 维度 | 取值 | 决定什么 |
|---|---|---|
| `dispatch_mode` **下发方式** | `SYNC` / `ASYNC` | **调用什么时候返回** |
| `result_mode` **回收方式** | `SYNC_RETURN` / `CALLBACK` / `POLL` | **结果怎么回来** |

组合出三种实际形态（正好覆盖你提的三种）：

| # | dispatch | result | 形态 | 典型场景 | 超时来源 |
|---|---|---|---|---|---|
| ① | SYNC | SYNC_RETURN | **同步**：一次往返拿到结果 | 读属性、开/关门、取设备状态、停止、急停 | HTTP 超时（3~5s） |
| ② | ASYNC | CALLBACK | **异步回调**：立即返 `commandNo`，设备完成主动推 | 跑板（仪器能推完成事件 / Node-RED 推 result） | `callback_timeout` |
| ③ | ASYNC | POLL | **异步轮询**：立即返 `commandNo`，平台定时查状态 | 老仪器只支持查询、无推送能力 | `poll_interval` × `max_poll` |

**遥测（属性）是 ③ 的复用场景**：温度这类不适合高频推送，按 `poll_interval` 批量轮询刷新 `device_property` 当前值。
若仪器支持 MQTT telemetry 上报，**自动升级为推送，代码不用改**——只是 `result_mode` 从 `POLL` 变 `CALLBACK`。

**统一的地方（这是"集成"的真正含义）**：

| 统一项 | 说明 |
|---|---|
| 同一张表 | 三种都写 `device_command`，**只增不改** |
| 同一个状态机 | 都走 `PENDING → QUEUED → DISPATCHED → RUNNING → SUCCESS/FAILED/TIMEOUT/CANCELLED` |
| 同一套幂等 | 都用 `uk_idempotent`，**重复请求返回原命令**（MQTT 重投 / 人工重试都被拦） |
| 同一套锁 | 都先抢 `device_info` 的 CAS 锁 |
| 同一套后处理 | 完成都走 `写 L0 → 解析 L1（挂上下文）→ 标 L2 → 发 MQTT 事件 → 调 SPI 回调` |
| **同一个对外返回** | 都返回 `commandNo`（① 同步额外直接带结果） |

**★ 对调度域完全透明**：调度域只说「跑一个 `READ_PLATE`，参数用参数集 #12」，
**从不关心是同步还是轮询** —— 它拿到的永远是同一个 `commandNo` + 同一个完成事件。

**由谁决定用哪种？四层决策，后者覆盖前者**：

| 层 | 依据 | 例子 |
|---|---|---|
| 1 | `device_info.connection_type` | 决定 Transport（HTTP / MQTT / NODE_RED / SIMULATED） |
| 2 | `device_action.estimate_duration_ms` | **< 3000ms 才允许 `SYNC`**（长命令同步 = 必超时） |
| 3 | `device_info.callback_enabled` | 仪器能否主动推 → 决定 `CALLBACK` 还是 `POLL` |
| 4 | **调用方显式指定 `result_mode`** | 调度域知道语义时覆盖 |

**为什么必须有第 4 层**：设备能力只说明"**能不能**"，调度语义才决定"**要不要等**"。
同一台仪器的同一个动作——手动调试时想同步看结果，流程编排时想异步并行——**只有调用方知道。**

**超时与兜底（三种都要有，只是形态不同）**：

| 方式 | 兜底机制 |
|---|---|
| SYNC | HTTP 超时 + **失败不落 RUNNING**（避免"发了不知道成没成"的悬空命令） |
| CALLBACK | `callback_timeout` 到期 → **降级 POLL 兜一次** → 仍无果才 `TIMEOUT` |
| POLL | `max_poll` 次后 `TIMEOUT`；**每次轮询都更新 `progress_percent`**（前端不转圈） |

> **★ CALLBACK 降级 POLL 这条很重要**：MQTT QoS 0 可能丢消息、客户端可能掉线，
> 回调不可靠**不能直接判失败** —— 先兜一次轮询再判，否则会**误报"设备没跑完"而实际早已跑完**。

### 5.3 设备占用锁（对齐 SiLA `Lock Controller`）

v1 只有 `current_command_id` 一个字段，**太薄**。SiLA 的 `Lock Controller` 语义更细，v2 对齐：

| 字段 | 作用 |
|---|---|
| `lock_holder` | 谁持锁（`command_no` 或 `USER:1` 或 `TASK:xxx`） |
| `lock_type` | `COMMAND` / `MANUAL` / `MAINTENANCE`（人工检修也能锁设备） |
| `lock_acquired_time` / `lock_expire_time` | **锁必须能过期**，否则设备永远锁死 |
| `lock_reason` | 人工加锁时必填 |

**为什么必须有 `lock_type=MANUAL`**：工程师现场修设备时，系统不该把命令投进去。
竞品（Cellario `Maintenance` / LADS 的 `Maintenance` Facet）都把「维护」当一等场景。

---

## 六、数据流：三层 + 上下文注入 + 双上报

### 6.1 三层（术语对齐行业：raw / standardized / analytic）

```
设备 ──采集──▶ L0 raw ──解析──▶ L1 standardized ──分析──▶ L2 analytic
              原始报文/文件        测量头 + 孔级读数       结论/标签(带规则版本)
              (不可变)             ★摄入时挂上下文          (可重判)
                    │                      │                     │
                    └──── 排障 / 审计 ─────┴───── 上报 ──────────┘
                                                       │
                                    ┌──────────────────┴──────────────────┐
                                    ▼                                     ▼
                        同步 SPI 回调（要返回值）                  MQ 事件（广播）
                        DeviceCallback → 调度域做分支判断          看板/LIMS/AI/审计
```

> 术语说明：TetraScience 的 **IDS** ↔ 我们的 **L1 standardized**；Databricks **Medallion**（bronze/silver/gold）↔ 我们的 **raw/standardized/analytic**。**分层同构，方向已验证。**

### 6.2 ★ L1 落库的上下文硬约束（对标 TetraScience 铁律）

TetraScience 原话：*"context **attached at ingestion, not reconstructed later**. The data arrives **already understood**."*

**硬约束（写进代码评审清单）**：`device_measurement` 在**落 L1 那一刻**必须写齐：

| 必填上下文 | 为什么不能事后补 |
|---|---|
| `plate_instance_id` / `plate_instance_code` | 板子会移动、会被消耗、会被销毁 —— 事后查不到"当时是哪个" |
| `plate_type_code` + `device_labware_name`（下发快照） | 板型映射会改 —— 事后不知道"当时下发了什么板型名" |
| `experiment_ref` / `task_ref`（若来自调度） | 调度实例会归档 |
| `operator` / `operator_type` | 人会说谎，上下文不会 |
| `device_code` + `driver_version` | 驱动升级后，同一台机行为会变 |
| `script_name` / 方法名 + 关键参数快照 | 方法会被改 |

> **反例（我们要避免的）**：只存一个 `file_path`，然后分析时靠文件名猜样品号。
> TetraScience 说得直白：*"The patches break … **When the vendor pushes an update, the connection fails**"*，
> 以及 *"data that **nobody can trust six months later**"*。

### 6.3 上报双通道

| 通道 | 时机 | 用途 |
|---|---|---|
| **SPI 回调（同步）** | 命令完成后紧随触发 | 调度域**做分支判断**（"OD>1.5 就走稀释分支"）——必须同步可拿 |
| **MQ 事件（异步）** | 命令完成后发 | **多个下游广播**；**只带 id + 摘要，不带孔级数据** |

### 6.4 分析归类

| 子项 | 内容 | 期次 |
|---|---|---|
| **基础标记** | `OVER_RANGE` / `BLANK_FAIL` / `CV_EXCEED` | **一期** |
| **质控判定** | Z'因子、CV%、曲线拟合、阴阳性 | 二期（`device_analysis_rule` 可配置） |
| **归类打标** | 按实验 / 板型 / 检测模式 / 触发来源 | 一期两维，其余二期 |

**两条必须守住的**：
1. L2 表带 `rule_code` + `rule_version` → 规则升级后能**重判历史**并对比新旧
2. **Tecan 的 Magellan 本身就是酶标仪数据分析软件** —— 一期不重复造，`device_analysis_result` 预留「外部分析来源」标识，便于将来对接 Magellan 产出

---

## 七、表设计（一期 10 张 / 二期 5 张）

> 前缀 **`device_`**（与 `wms_` 同构）；**10 张表已入库**（`device.sql` v2.0-device）。

### 一期：单设备全闭环 —— 10 张

| # | 表 | 作用 | v2 / v3 / v4 / v5 变化 |
|---|---|---|---|
| 1 | `device_info` | 台账（含**占用锁**与**调度开关**） | v2：`simulation_mode` / `discovery_type` / `telemetry_json` / 锁四字段<br>v3：接入五字段 `connection_type`(HTTP\|MQTT\|NODE_RED\|SIMULATED) / `endpoint_url` / `mqtt_topic_prefix` / `callback_enabled` / `poll_interval_sec`<br>**v5 ★★ 忙闲与并发四字段：`busy_check_policy` / `concurrency_policy` / `max_inflight` / `inflight_count`** |
| 2 | `device_action` | 动作定义（启动方式） | v2：`standard_feature` / `source`(DEVICE_DECLARED\|MANUAL)，`param_schema` 含 default + constraints + unit<br>v3：`estimate_duration_ms`（决定能否 SYNC）<br>**v4 +5 列：`need_busy_check` / `status_command_code` / `request_template` / `poll_done_expr` / `poll_max_times`** |
| 3 | **`device_property`** | **★ 可观测属性定义（遥测）** | **v2 新增** |
| 4 | `device_param_set` | 参数集预设 | `validated` / `validated_by` / `validated_time` |
| 5 | **`device_codec`** | **★ 响应解析规则（L0 → L1）** | **v4 新增**；一期**只开 JSON / REGEX**（`SCRIPT`/`HEX` 建字段不开放，代码注入面） |
| 6 | `device_command` | 命令（只增不改；**三态调用统一落点**） | v2：进度四字段 + `recovery_action`<br>v3：`dispatch_mode` / `result_mode` / `poll_count` / `next_poll_time`<br>**v4：新增 11 列（`source_type`/`business_exec_id`/`node_id`/`attempt`/`waiting_for`/`codec_code`/`callback_token_hash`/`acquired_at`/`sent_at`/`timeout_at`/`version`）+ 改名 1（`end_time`→`finished_at`）+ 删 4（`ref_type`/`ref_id`/`start_time`/`callback_deadline`）；唯一键 2→3；`device_id`/`device_code` 改可空** |
| 7 | `device_data_raw` | **L0 raw（不可变）** | 无 |
| 8 | `device_measurement` | **L1 头** | 上下文必填字段（§6.2），`driver_version` 快照 |
| 9 | `device_measurement_data` | **L1 孔级读数** | 无 |
| 10 | `device_analysis_result` | **L2 结论/标签** | `source_system`（区分自算 / Magellan 等外部来源） |

> 表 8 行数：96 孔 × 1 波长 = 96 行；× 3 波长 = 288 行。**不做合并**——按波长筛、画热力图都要行级可查。

### 二期 —— 5 张

| # | 表 | 作用 | 触发条件 |
|---|---|---|---|
| 11 | `device_status_log` | 属性/状态历史 + 上下线轨迹 | 要画曲线 / 算利用率时 |
| 12 | `device_alarm` | 告警（超温/耗材缺/自检失败/离线超时） | 要做主动告警时 |
| 13 | `device_analysis_rule` | 分析规则可配置化 | 质控规则要业务自己改时 |
| 14 | `device_data_relation` | 数据血缘（L2 ← L1 ← L0） | 要做审计追溯时 |
| 15 | `device_command_step` | 长命令子步骤明细 | 进度要精确到子任务时 |

> **v1 的 `device_capability`（能力独立表）已取消** —— 因为一期就有了 `device_action` + `device_property` 两张独立表，
> 「按能力选设备」的 JOIN 查询二期直接可用，**不需要再加表**。这是对标带来的净收益。

### 配套 DDL 变更（对既有表，已登记 WMS TODO G 区）

| 变更 | 说明 |
|---|---|
| `wms_container_type` **加 `device_labware_name`** | 容器类型 → 仪器板型名对照。**显式加列**而非塞 `spec_json`——要参与上机前校验，塞 JSON 无法索引 |
| `wms_slot_info.device_code` 升级为强引用 | **加删除校验**（设备下有槽位不许删，`DEVICE_HAS_SLOTS`）。**这就是上次 `deleteZone` 留悬空 `zone_code` 的同一个坑**，别再犯第二次 |

---

## 八、三个域的边界

> ⚠️ 本节「三个域」指**业务域**（设备 / 调度 / WMS）的横向切分；
> 与之正交的是 **§0.7 的控制面/数据面纵向切分**（调用/数据/仿真三块）。
> 两者不要混：前者回答「谁负责什么业务」，后者回答「数据往哪流、怎么拆服务」。

| 域 | 一句话职责 | 明确不做 |
|---|---|---|
| **设备域**（本次） | 这台设备**怎么启动、参数怎么下发、跑完数据怎么收/怎么归** | ❌ 不决定跑谁、不决定顺序 |
| **调度域**（未来） | **什么时候、用哪台、先后依赖、并行、异常重试** | ❌ 不碰厂商协议、不解析结果 |
| **WMS**（已落地） | **板子在哪、何时上下机、谁占了槽位** | ❌ 不碰设备协议 |

> 补充（v6）：设备域内部再按 §0.7 切成 `invoke`（控制面本体）/ `ingest`（数据面，逻辑拆）/ `simulate`（控制面可替换实现）三个包子域；
> 其中 `ingest` 的过程时序数据归**数据平台**（独立时序库），跨系统只认 `command_no`。

### 8.1 ★ 调度域不自研流程引擎（决策 4 修订）

**竞品事实**：UniteLabs 的编排「**based on BPMN with Camunda**」；**Roche 的 AutoLab 也是 Camunda-based**。

**我们的机会**：项目根 pom 里**已有 `boheng-module-bpm` 占位**（芋道自带的 BPMN 引擎，基于 Flowable）。
⚠️ 已核实：**该模块目录不存在**（mini 版精简掉了），只是 pom 里注释掉的占位 → **需从芋道上游恢复**。

**分工（必须切清）**：

| 关注点 | 归属 | 工具 |
|---|---|---|
| **流程定义**：步骤、依赖、分支、人工节点、异常路径 | 调度域 | **BPMN 引擎**（现成） |
| **资源分配**：此刻用哪台、并行怎么排、优先级 | 调度域 | 自研（BPMN 不解决这个） |
| **单设备执行**：启动、参数、收数 | 设备域 | 本次方案 |

> **诚实提醒**：BPMN 只解决「**流程定义**」这 60%，「**资源分配/排程优化**」仍要自研。
> 但那属调度域，**不影响设备域设计**——设备域只需保证「会被 BPMN 节点调用时，能携带 `ref_type=TASK` + `ref_id=流程/节点`」。

### 8.2 与 WMS 的缝合（零改表）

| 环节 | 流水 |
|---|---|
| 从货架取板 | `TAKE_OUT`（`biz_source=TASK`/`DEVICE`） |
| 板进仪器栈 | `PUT_IN` 到 `device_position_no` 对应槽位 |
| 跑板 | `STATUS_CHANGE`：`AVAILABLE → IN_USE`，`ref_type=DEVICE` |
| 出仪器 / 回位 | `TAKE_OUT` / `PUT_IN` |

> 这条链与 SiLA 官网举的**自家示例流程一模一样**：
> *"The LIMS orders a Plate Handler to put plates into a Plate Reader → returns finished → triggers the next call to the Plate Reader: Read the plates → returns data description → calls the Handler again to put the plates back."*
> **说明我们的场景是行业标准场景，不是特例。**

---

## 九、与上一轮 Tecan 方案的关系

| 项 | 上一轮 `tecan-device-integration-design.md` | v2 |
|---|---|---|
| 定位 | 一台读板机的一期对接 | **设备域总体架构**，Tecan 收编为其一驱动 |
| 模块 | 单模块 | **双模块双 SPI** |
| 表数 | 4 张 | **一期 9 张**（+ property / action / param_set / data_raw / analysis_result） |
| 能力 | `capability_json` 塞 JSON | **`device_action` + `device_property` 独立表 + 驱动自描述** |
| 参数下发 | 未展开 | **`param_schema`（default+constraints+unit）+ `param_set`（validated）** |
| 连接 | A/B 二选一 | **设备代理（Server-Initiated）+ 用 RedisMQ 实现** |
| 仿真 | 未提 | **一期必做（顺带解掉文档阻塞）** |
| 调度 | 「一期无调度域」 | 明确**调度域基于 BPMN**，设备只提供 SPI |

**仍然有效**：异步命令 + 状态机、`device_command` 表与幂等键、驱动/传输两层、与 WMS 流水的缝合。

---

## 十、决策点（v5：K/L 已定，H 已细化）

| # | 决策 | 选项 | 我的建议 |
|---|---|---|---|
| ~~1~~ | ~~模块拆分~~ | —— | ✅ **已定：双模块** |
| ~~3~~ | ~~动作模型一期上~~ | —— | ✅ **已定：上** |
| ~~4~~ | ~~参数集一期上~~ | —— | ✅ **已定：上** |
| ~~5~~ | ~~设备占用~~ | —— | ✅ **已定：台账 CAS 锁，对齐 SiLA Lock Controller**（v5 补充：仅 `EXCLUSIVE` 模式用锁，见 §0.6） |
| ~~9~~ | ~~SPI 回调一期定义~~ | —— | ✅ **已定：一期定义** |
| **A** | **仿真驱动做不做一期第一个** | 做 / 不做 | **强烈建议做**。它把「等 Tecan 文档」的阻塞变成「可以并行推进」 |
| ~~B~~ | ~~连接形态~~ | —— | ✅ **已定（v3）：平台只认 HTTP + MQTT；Node-RED 做接入层；Transport 抽象保留** |
| **C** | 表前缀 | `device_` / `lab_` | **`device_`**（与 `wms_` 同构） |
| **D** | MQ 事件契约 | 一期定 / 二期定 | **一期定**（规避二期改契约） |
| **H** | **MQTT broker 选型**（v3 新增，v5 细化） | 连外部 EMQX / 平台自当 broker / 先 HTTP | **一期先 HTTP 直连**（当前 2 台设备全仿真）；真机接入时优先**连外部 EMQX**，`@ConditionalOnProperty` 那套照芋道做法。⚠️ 三方案**都只是 `DeviceTransport` 的一个实现**，选哪个都不锁死架构 |
| **I** | **Node-RED 是否一期就上**（v3 新增） | 一期就上 / 有需要再上 | **有需要再上**——先备好 `HttpTransport` / `MqttTransport`，**只有真要接串口/私有协议仪器时才装** |
| **J** | **大载荷阈值**（v3 新增） | 64KB / 256KB / 1MB | **64KB**（保守），超出转文件引用（§3.5） |
| ~~K~~ | ~~v4 五处改动是否现在落 DDL~~ | —— | ✅ **已定（本轮）：现在落**。`device.sql` 升 v2.0-device；`device-v4-upgrade.sql` 已执行并通过行为验证 |
| ~~L~~ | ~~传输层是否复用芋道 `iot-gateway`~~ | —— | ✅ **已定（本轮）：借「协议接法」，不借「模块 + 物模型 + 部署形态」**<br>device 自定义 `DeviceTransport` SPI；iot-gateway 降级为**可选适配器**（独立模块，按需启用）。详见 `device-transport-boundary.md` |
| **E** | 能力自描述一期做到哪 | 驱动全自动上报 / 先手工填 + 留 `source` 字段 | **先手工填 + 留字段**（一期只有一台机，但**机制要留**，否则二期重构） |
| **F** | 分析归类深度 | 基础标记 / 完整质控 | **一期基础标记** |
| **G** | 是否恢复 `boheng-module-bpm` 做调度域 | 是 / 先不做 | 属**调度域**决策，与设备域解耦 —— 但**建议尽早立项**，因为它是设备域的主要调用方 |

### 仍需你提供

| # | 内容 | 状态 |
|---|---|---|
| 1 | **`Reader.NETwork V1.9` 接口文档**（endpoint / 认证 / 请求响应样例 / **结果数据结构**） | 🔴 **硬阻塞**（有仿真驱动可先并行） |
| 2 | 镁伽 `Labillion` 与我们的 `labillion-frame` **是否同源** | 🟡 影响对标口径 |
| 3 | 读板机台数与型号 / i-control PC 与后端是否同网段 / 是否含液体处理工作站 / 结果格式 | 🟡 见 v1 文档 §十 |

---

## 十一、确认后的交付清单

> 已按 §0.8 的节点重排，标注每个交付物对应的节点。

| 序 | 内容 | 节点 |
|---|---|---|
| 1 | 新建 `boheng-module-device-api`（纯契约）+ `boheng-module-device`（实现），装配 + 菜单 + 权限点 | 已完成 |
| 2 | `device-api`：4 个 Api + `DeviceCallback` + 枚举 + DTO（**不含任何实现**） | 已完成 |
| 3 | DDL：一期 9 张表；错误码段位 `1-004-000-000` | 已完成 |
| 4 | ★ **抽 `DeviceDriver` SPI**（`HttpDriver` 收敛）+ **消歧三字段** | **P0 / P0b** |
| 5 | ★ **`SimulatedDriver`（接口可调通级）** —— 走真实命令入口、确定性响应、同构 L0 | **P1** |
| 5b | MQTT 基建：`boheng-spring-boot-starter-mq` 加 MQTT 支持 + EMQX 容器 + 五个 Topic 订阅器 | P6（一期先 HTTP，真机接入前就位） |
| 5c | 三态调用统一模型：`dispatch_mode` × `result_mode` + CALLBACK 降级 POLL 兜底 | 已完成 |
| 5d | Node-RED 接入示范（有价值时才做） | P7 |
| 6 | 命令状态机 + 异步执行 + 进度 + 幂等 + 设备锁 + 错误恢复 | 已完成 |
| 7 | ★ **「读板」路径闭环**：L0→L1→L2，仿真设备也能走通 | **P2** |
| 8 | DDL 变更：`wms_container_type.device_labware_name`；`device_code` 加删除校验 | 已完成 |
| 9 | `TecanReaderNetworkDriver`（依赖 §十 文档，可在仿真驱动之后） | P7 |
| 10 | 前端：设备台账 / 动作与参数集 / 命令监控 / 测量结果（96 孔热力图） | P2 后 |
| 11 | 上机下机接 WMS 流水（复用 `put-in` / `take-out`） | P7 |
| 12 | 端到端验证：真实仪器跑一块板，核对读数与流水 | P7 |
| 13 | ★ **共享契约模块 `device-contract`** + 遥测写入通道（方案 A） | **P3** |
| 14 | ★ 端到端联调：仿真设备 → 命令 → 过程数据 → 时序库 → 曲线 | **P4** |
| 15 | 仿真完整形态：虚拟时钟 / 录制回放 / 参数扫描（此时升级独立模块） | P5 |
| 16 | 上行接入层独立化：MQTT 订阅 + 认证 + 幂等 + 断网续传 | P6 |

---

## 十二、风险

| 风险 | 缓解 |
|---|---|
| **仪器无 API 直取孔级数，只能解析文件** | 两条路都设计；`data_raw.content_type` 已留 FILE 分支 |
| Windows PC 不稳定 / IP 变动 | `endpoint` 可改 + 心跳可见；**不做硬编**；二期上设备代理 |
| **仪器在内网隔离区，后端连不到** | **设备代理（出向连接）**；SiLA 也为同一问题加了 Server-Initiated Connection |
| i-control 版本升级改接口 | `driver_version` 快照 + `DeviceDriver.supports()` 按版本分派 |
| 设备卡死拖垮后端 | 命令表 + 超时 + `CANCEL` + **`lock_expire_time`**；异步执行与 HTTP 解耦 |
| 设备被人工占用时命令打进去 | **`lock_type=MANUAL` / `MAINTENANCE`** |
| **能力塞 JSON 导致无法按能力选设备** | 一期就建 `device_action` / `device_property` 独立表 |
| **L1 没在摄入时挂上下文，半年后数据不可信** | §6.2 硬约束进代码评审清单 |
| **L0 没存全，无法重算 L1/L2** | L0 不可变、不裁剪、不解释 |
| **自拍语义，三年后接 SiLA 要重构** | 一期就沿用 SiLA 语义与标准特性名 |
| 凭据明文 | `auth_config` 一期先 JSON 存储并**标注待加固** |
| **Node-RED 里长出业务逻辑**（v3） | **硬边界 1**（§3.4）进代码评审清单；flow 变更必须走 git |
| **Node-RED 配置漂移**（现场改了流，平台不知道）v3 | flow JSON 纳入 git + **心跳超时告警** + 命名规范统一 |
| **MQTT 消息重复投递**（QoS 1/2 = at-least-once）v3 | **`idempotent_key` 一期就落**，与 `device_command.uk_idempotent` 复用同一套机制 |
| **大载荷打爆 broker**（v3） | **>64KB 转文件引用**（§3.5），用 infra 已有 S3/SFTP 文件服务 |
| **回调丢失导致误判命令失败**（v3） | CALLBACK 超时**先降级 POLL 兜一次**再判（§5.2） |
| **Node-RED 默认无认证被利用（RCE）**（v3） | 强制开认证 + 网络隔离，**禁止暴露公网**（§3.4 边界 4） |
| 模块切法不统一，后期混乱 | **已定双模块**，写进项目 MEMORY 作为约定 |
