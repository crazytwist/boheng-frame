# 设备域三块拆分 —— 实施计划（v6 · P0~P7 节点任务展开）

> 日期：2026-10-08
> 依据：`.workbuddy/reference/device-domain-architecture.md` §0.7 / §0.8
> 审核过程：`.workbuddy/reference/device-three-blocks-review.md`（v3）
> 本文是把 §0.8 的节点清单展开成「每个节点具体改哪些文件、做什么」，供逐节点执行。

---

## 〇、结论回顾（三块拆与不拆）

| 块 | 一期边界 | 二期边界 |
|---|---|---|
| ① 调用 invoke | 留在 `device` 模块，包名 `invoke` | 不拆 |
| ② 数据 ingest | 逻辑拆（过程数据独立时序库），物理同进程，包名 `ingest` | 拆独立模块 → 独立服务 |
| ③ 仿真 simulate | 不拆，包名 `simulate`，做 `DeviceDriver` 实现 | 拆独立模块 → 独立服务 |

**一期只做「接口可调通」级仿真**：走真实命令入口、确定性成功/失败、同构 L0，不产真实时序数据。

---

## 一、一期包结构（`boheng-module-device` 内，不新增 Maven 模块）

```
cn.boheng.frame.module.device
├── core        # 注册表 + SPI（device_info / DeviceDriver 接口）
├── invoke      # ① 调用域（现有 command/action/param_set 迁入）
├── ingest      # ② 数据域（raw/measurement/analysis 迁入）
├── simulate    # ③ 仿真域（SimulatedDriver，接口可调通级）
└── codec       # 协议编解码（①②共用）
```

> 现有代码分布在 `service/` + `dal/` + `controller/` 平铺结构，本次**只做增量抽取**，
> 不强行一次性搬迁全部旧代码（避免大爆炸重构）。新 SPI 与 SimulatedDriver 落在新包，
> 旧类保持原位，后续按需迁移。

---

## 二、节点任务展开

### P0 —— 抽 `DeviceDriver` SPI

**目标**：把焊死在 `DeviceHttpService`/`DeviceHttpGateway` 里的「查台账→发 HTTP」收敛为 `HttpDriver` 一个实现，`driver_type` 成为分派唯一依据。

**改动点**：

1. 新建 `core/spi/DeviceDriver.java` 接口，方法：
   - `String driverType()` —— 声明自己负责哪个 `driver_type`
   - `DeviceInvokeRespVO invoke(DeviceInfoDO device, DeviceActionDO action, String paramsJson)`
2. 新建 `invoke/driver/HttpDriver.java`，把 `DeviceHttpGateway.invoke()` 的 HTTP 逻辑搬进去（登录/重试/token 缓存保持不动）
3. 新建 `core/spi/DeviceDriverRegistry.java`（或 `DeviceDriverDispatcher`）：
   - 注入所有 `DeviceDriver` 实现，按 `device.driverType` 路由
4. 改 `DeviceHttpService.invoke()`：删掉 `"HTTP".equals(connection)` 硬编码分支，改为 `registry.dispatch(device).invoke(...)`

**验收**：`HttpDriver` 行为与现状完全等价；未来加驱动只新增一个 `DeviceDriver` 实现类。

---

### P0b —— 消歧三字段

**目标**：消除「SIMULATED 设备点调用抛错」，明确三字段职责。

| 字段 | 语义 | 规则 |
|---|---|---|
| `driver_type` | 用哪个驱动实现 | **分派唯一依据**（`TECAN_READER_NETWORK` / `SIMULATED` / ...） |
| `connection_type` | 物理怎么到达 | `HTTP` / `MQTT` / `NODE_RED` / `SIMULATED` |
| `simulation_mode` | 本应连真机、当前按仿真跑 | **dry-run 覆盖开关**，不是分类 |

**改动点**：
- `DeviceHttpService` 不再读 `connection_type` 判断能否调用（P0 已删）
- 分派逻辑：`simulation_mode=1` 时强制路由到 `SimulatedDriver`；否则按 `driver_type`
- 更新 `/portrait` 提示文案，说明 `driver_type` 才是分派依据

---

### P1 —— `SimulatedDriver`（接口可调通级）

**目标**：仿真设备点「调用」走真实命令入口，返回可预测成功/失败，产同构 L0。

**改动点**：

1. 新建 `simulate/SimulatedDriver.java` 实现 `DeviceDriver`，`driverType() = "SIMULATED"`
2. 行为：读 `device_action` 或参数里的「剧本」（恒成功/恒失败/按规则），返回确定性响应
3. 响应结构对齐 `DeviceInvokeRespVO`（与 `HttpDriver` 同构），让 codec 路径一致
4. 命令生命周期照常走 `DeviceCommandService.open/complete/fail`（复用 P0 的 dispatch）

**验收**：仿真设备「调用」→ 状态机流转 → L0 落库 → 返回可预测结果；无 `if(simulated)` 旁路。

---

### P2 —— 「读板」路径闭环

**目标**：`ingest` 子域内打通 raw → measurement → analysis，仿真设备也能走通。

**改动点**：
1. 确认 `SimulatedDriver` 产出的 L0 能被现有 codec 解析成 L1
2. 打通 L1 → L2 基础标记（`DeviceAnalysisResultService`）
3. 验证一次仿真调用产出完整 L0/L1/L2 链

**验收**：仿真设备跑一次「读板」，从 L0 原文到 L2 结论全链路有数据。

---

### D0 —— 时序库选型 + 建库（数据平台主线，可与 P0 并行）

**目标**：为「过程实时推流」二期铺路，先用实测锁定选型。

**改动点**：
1. 拉 TDengine（首选）/ TimescaleDB（次选）本地或容器实例
2. 实测：无模式写入 + 乱序补传 + 降采样
3. 建超表 `telemetry`（TAG：`device_code`/`property_code`/`command_no`/`data_origin`）

**验收**：压测数据支撑选型结论；`telemetry` 超表建好。

---

### D1 —— 元数据目录

**目标**：过程数据要有语义。

**改动点**：属性字典（`property_code`/`unit`/`value_type`/`device_type_code`），与 `device_property` 对齐。

---

### P3 —— 共享契约 + 遥测写入通道（★ 汇合点）

**目标**：设备域与数据平台用契约解耦，不用对象解耦。

**改动点**：
1. 新建独立模块 `boheng-module-device-contract`（两侧都依赖它，不反向依赖）
2. 契约内容：设备身份 / topic 命名 / 上行信封 / 时间语义 / 上下文最小集
3. 遥测写入走「方案 A」：设备域分流写时序库

**验收**：两侧只依赖契约；设备域一发版不强制数据平台跟着发。

---

### P4 —— 端到端联调（★ 本轮验收标准）

**验收**：仿真设备 → 命令 → 过程数据 → 时序库 → 查询出曲线。

---

### P5 —— 仿真完整形态（此时才升级独立模块/服务）

虚拟时钟 / 录制回放 / 参数扫描。**触发仿真从「驱动实现」升级为「独立模块/服务」。**

---

### P6 —— 上行接入层独立化

MQTT 订阅 + 设备认证 + 幂等 + 断网续传（真机接入前就位）。

---

### P7 —— 真机接入

Tecan 驱动 / Node-RED / 调度域对接 / WMS 流水中已就绪部分验证。

---

## 三、立即开工序列

```
P0（抽 SPI）→ P0b（消歧三字段）→ P1（SimulatedDriver）→ P2（读板闭环）
                    D0（时序库选型）→ D1（元数据目录）   [并行]
                              ↓
                    P3（共享契约）→ P4（端到端联调）
```

**P0 → P2 与 D0 → D1 可立即开工，不依赖任何待决策项。**

---

## 四、遗留待决策（排期靠后，不阻塞一期）

| # | 待决策 | 建议 | 见 |
|---|---|---|---|
| 1 | 遥测写入拓扑 A/B/C | 一期 A（设备域分流） | 审核文档 §3.0.1 |
| 2 | 跨系统映射表方案 | 2（设备域发 MQ 事件） | 审核文档 §3.3 |
| 3 | 时序库最终选型 | TDengine（次选 TimescaleDB） | 审核文档 §3.1 |
