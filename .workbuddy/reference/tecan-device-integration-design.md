# 设备对接（Tecan）· 一期方案

> 状态：**待确认**（架构与范围待你拍板；接口细节待你提供 `Reader.NETwork` 文档）
> 关联：`boheng-module-wms`（位置与流水已就绪）、`.workbuddy/reference/wms-movement-ledger-design.md`
> 日期：2026-09-27

---

## 〇、先说结论

1. **架构**：新建 `boheng-module-device`（设备域）。**驱动内嵌后端**，但按「两层接口
   （`DeviceDriver` + `DeviceTransport`）+ 命令状态机」写，将来外置或换 MQ 时**业务代码不动**。
2. **执行模型**：**异步命令 + 状态机 + 轮询回收**。读板要几秒到几分钟，**绝不能 HTTP 同步阻塞**。
3. **与 WMS 的缝合点已经现成**：`wms_material_movement` 的
   `biz_source`(DEVICE/TASK) / `operator_type`(DEVICE) / `ref_type` / `ref_id`
   **就是为设备准备的，不用改表**。上机 = `TAKE_OUT`，下机回位 = `PUT_IN`。
4. **两个真缺口**（本次侦察新发现）：
   - 缺**设备台账表**：`wms_slot_info.device_code` 只是个裸字符串，无主数据。
   - 缺**容器类型 → Tecan 板型映射**：旧设计的 `device_labware_name` 已随 v1.4 退场，
     当前 `wms_container_type` **没有这个字段**（只有 `well_count/rows/cols`、`position_naming`、`spec_json`）。
5. **阻塞项**：`Reader.NETwork V1.9` 接口文档我**无法从公开渠道获取**（这类文档只随仪器交付），
   需要你提供（见 §六）。

---

## 一、现状盘点（侦察结果）

### 1.1 已有埋点 ✅

| 位置 | 字段 | 说明 |
|---|---|---|
| `wms_slot_info` | `device_code` | 「所属设备编码(本槽位是设备上的物理位置时填写，纯货架留空)」 |
| `wms_slot_info` | `device_position_no` | 「设备侧位置标识(厂商命名，如 Tecan Stack-3；协议下发用，与 slot_code 解耦)」 |
| `wms_slot_info` | `slot_type` | 含 `DEVICE`（设备器位）/ `BUFFER`（暂存） |
| `wms_container_type` | `well_count` / `well_rows` / `well_cols` | 96 孔板 = 96/8/12，**可直接算板图** |
| `wms_container_type` | `position_naming` | `ROW_COL`(A1) / `SEQ`(1..96) / `ROW_COL_LAYER` / `NONE`，**决定孔位码与 Tecan 索引的换算** |
| `wms_material_movement` | `biz_source` / `operator_type` / `ref_type` / `ref_id` | **设备事件留痕的口子，已预留** |

> 设计者显然已经预见到设备对接，`device_position_no` 的注释里直接就写了「Tecan Stack-3」，
> 且明确说「与 slot_code 解耦」——**槽位是我们的逻辑地址，设备位置是厂商地址，两者分开**。
> 这个设计是对的，一期直接沿用。

### 1.2 缺口 ❌

| 缺口 | 影响 |
|---|---|
| **无设备台账表** | `device_code` 无处注册，无法表达型号/地址/驱动类型/在线状态/能力 |
| **无容器→板型映射** | Tecan 读板要传「板型名」（如 `Greiner 96 Flat Bottom`），我们的 `type_code` 是内部编码，需对照表 |
| 无命令/结果表 | 无法记录下发过什么、拿到什么 |
| 无调度域 | 一期不做编排，只做「单次命令」的执行闭环 |

---

## 二、驱动放哪里（架构选型）

i-control 装在**连读板机的 Windows PC** 上，`Reader.NETwork` 在该 PC 暴露 HTTP 服务。
后端要去调它，先要回答「这段调用代码跑在哪」。

| 选项 | 说明 | 优 | 劣 |
|---|---|---|---|
| **A. 后端直连** | `boheng-frame` 直接 HTTP 调仪器 PC | 无额外部署单元；调试最直 | 后端与设备强耦；设备在实验室内网、后端可能在机房 → 防火墙/网段是坑；设备卡死会占后端线程 |
| **B. 边车驱动进程** | 每台（或每台 PC）部署一个小驱动服务，后端只调它 | 网络解耦；可本地缓存 + 断网重放；设备故障不拖垮后端 | 多一个部署单元（N 台设备 = N 个进程，需运维） |
| **C. 纯 MQ 异步** | 后端投任务到 MQ，驱动消费并回写 | 天然异步；多设备并行天然支持 | 调试链路长；一期设备少，收益不明显 |

### 推荐：**一期取 A 的部署形态 + B/C 的接口设计**

即：**驱动代码内嵌在后端**（少一个部署单元），但**接口按「将来一定要拆出去」来写**：

```
业务层  DeviceCommandService          ← 只管命令状态机，不认识 Tecan
           │
           ▼
驱动层  DeviceDriver (接口)           ← 一个厂商一个实现，声明 supports(deviceType)
           │                            TecanReaderDriver / (将来) FluentDriver
           ▼
传输层  DeviceTransport (接口)        ← HttpTransport(一期) / MqTransport(二期)
           │                            负责寻址、超时、重试、认证
           ▼
       i-control Reader.NETwork
```

**为什么值得这么写**：从 A 切到 B/C 时，**只换 `DeviceTransport` 实现 + 改配置**，
`DeviceDriver` 与业务代码一行不动。这是花很小成本买到的退路。

### 一期不做的（明确划界）

- ❌ 任务编排 / 步骤依赖 / 条件分支（属**调度域**，下一期）
- ❌ 液体处理工作站联动（Freedom EVO / Fluent，先登记不实现）
- ❌ 建方法 / 改仪器参数（一期只「用已有方法跑板 + 取数」）
- ❌ 仪器闭环控制（如自动进板机械臂）

---

## 三、执行模型：异步命令 + 状态机

**问题**：一次读板几秒到几分钟。HTTP 同步调用会把后端线程占死，且仪器侧超时就断联。

**做法**：命令表 + 状态机 + **轮询回收**（一期轮询，简单可靠；二期可换 i-control 回调）。

```
POST /device/command/execute
   │
   ├─ 1. 幂等校验（uk_idempotent）→ 重复请求直接返回原命令
   ├─ 2. 落 device_command (status=PENDING)，立即返回 commandId   ← HTTP 到此结束
   │
   └─ 3. 异步线程执行
          ├─ 校验设备在线（heartbeat 未超时）
          ├─ DeviceDriver 组装请求 → 调 i-control → 拿到 deviceJobId
          ├─ status=RUNNING，存 device_job_id
          ├─ 定时轮询 deviceJobId
          │     ├─ 完成 → 拉结果 → 落 measurement / measurement_data
          │     │        → status=SUCCESS
          │     └─ 失败/超时 → status=FAILED，存 device 侧错误原文
          └─ 全程记 request_json / response_json / 耗时（排障用）
```

**状态机**：`PENDING → DISPATCHED → RUNNING → SUCCESS | FAILED | TIMEOUT | CANCELLED`

**为什么必须有 `device_command` 表**：设备对接最常见的三个坑——
① 网络抖动导致「发了不知道成没成」；② 仪器跑完了但结果没取回；
③ 人工重试导致同一块板测两次。
有命令表 + 幂等键，这三件事都能查清、能重放、能对账。

---

## 四、数据模型（一期 4 张表）

> 命名说明：建议前缀 **`device_`**（与 `wms_` 同构，一个模块一套前缀）。
> 若你希望统一挂 `lab_` 前缀，做之前说一声，成本很低。

### 4.1 `device_info` — 设备台账

| 字段 | 类型 | 说明 |
|---|---|---|
| `device_code` | varchar(64) | 设备编码（**被 `wms_slot_info.device_code` 字符串引用**） |
| `device_name` | varchar(128) | 名称，如「一楼酶标仪 1 号」 |
| `device_type` | varchar(32) | 类型：`PLATE_READER` / `LIQUID_HANDLER` / `ROBOT` / `INCUBATOR` / `CENTRIFUGE` / `WASHER` / `OTHER` |
| `vendor` / `model` / `serial_no` | varchar | `TECAN` / `Infinite 200 PRO` / SN |
| `driver_type` | varchar(32) | 驱动实现标识：`TECAN_READER_NETWORK` —— 决定用哪个 `DeviceDriver` |
| `driver_version` | varchar(32) | 期望的协议版本，如 `V1.9`（便于将来并存多版本） |
| `endpoint` | varchar(512) | 仪器 PC 地址，如 `http://192.168.0.50:8080` |
| `auth_type` / `auth_config` | varchar / json | 认证方式与凭据（`NONE` / `BASIC` / `TOKEN`；凭据**建议加密存储**，一期可先放 JSON 并标注待加固） |
| `status` | tinyint | 0 启用 / 1 停用（`CommonStatusEnum`） |
| `online_status` | varchar(16) | `UNKNOWN` / `ONLINE` / `OFFLINE`（心跳推导，不手工维护） |
| `last_heartbeat_time` | datetime | 最近一次探活成功时间 |
| `capability_json` | json | 能力声明：支持板型、检测模式（吸光/荧光/发光）、温控范围、支持的脚本名 |
| `location` | varchar(128) | 物理位置描述 |
| `description` | varchar(512) | 备注 |

> `capability_json` 的用法：**前端下拉只显示该设备「声明支持」的板型与模式**，
> 不把仪器能力的约束硬编在代码里。上机前校验「这块板能不能上这台机」。

### 4.2 `device_command` — 命令记录（**只增不改**，同流水思路）

| 字段 | 说明 |
|---|---|
| `command_no` / `id` | 命令编号 |
| `device_code` | 目标设备 |
| `command_type` | `PROBE` 探活 / `RUN_MEASUREMENT` 跑板 / `FETCH_RESULT` 取数 / `CANCEL` / `QUERY_STATUS` |
| `status` | 上述状态机 |
| `device_job_id` | 仪器侧任务号 |
| `request_json` / `response_json` | 请求与响应原文（**排障的生命线**） |
| `measurement_id` | 跑板命令完成后关联的测量记录 |
| `idempotent_key` | 幂等键（唯一索引 `uk_idempotent(tenant_id, idempotent_key)`，**沿用流水表的做法**） |
| `error_code` / `error_msg` | 失败原因（含仪器侧原文） |
| `retry_count` | 重试次数 |
| `submit_time` / `start_time` / `finish_time` / `cost_ms` | 时间与耗时 |
| `operator` / `operator_type` | 操作者（`USER` / `DEVICE` / `AUTO`） |
| `ref_type` / `ref_id` | 归属（将来挂 `TASK` 任务号） |

### 4.3 `device_measurement` — 测量头

| 字段 | 说明 |
|---|---|
| `device_code` / `command_id` | 来源 |
| `plate_instance_id` / `plate_instance_code` | **关联 WMS 的板实例**（缝合点，可空=手工上机） |
| `plate_type_code` | 容器类型编码（对应 `wms_container_type.type_code`） |
| `device_labware_name` | **实际下发给仪器的板型名**（快照，便于排查「为什么仪器认不出板」） |
| `script_name` / `method_name` | 仪器侧脚本/方法名 |
| `detection_mode` | `ABSORBANCE` / `FLUORESCENCE` / `LUMINESCENCE` / ... |
| `wavelength_nm` / `temperature_c` | 关键参数快照 |
| `measurement_time` | 测量时间 |
| `status` | `RUNNING` / `SUCCESS` / `FAILED` |
| `data_file_id` | 原始文件（存 infra 文件服务，如 i-control 导出的 xlsx/xml） |
| `raw_summary_json` | 仪器返回的汇总信息原文 |

### 4.4 `device_measurement_data` — 孔级读数

| 字段 | 说明 |
|---|---|
| `measurement_id` | 所属测量 |
| `well_code` | 孔位码（按 `position_naming` 规则，如 `A1` / `1` / `A1-L1`） |
| `row_no` / `col_no` / `layer_no` | 行列层（便于前端画热力图，避免每次解析 `well_code`） |
| `read_type` | `SAMPLE` / `BLANK` / `STANDARD` / `CONTROL`（若有） |
| `value` | 读数值（`decimal`） |
| `unit` | 单位（`OD` / `RFU` / `RLU` / ...） |
| `wavelength_nm` | 该读数对应波长（多波长时一行一个组合） |
| `flag` | 仪器侧标记（`OVER_RANGE` 等），原文保留 |

> 行数：96 孔板 × 1 波长 = 96 行；× 3 波长 = 288 行。**不做合并**，
> 因为「按波长筛数据」「画 96 孔热力图」都要求行级可查。

### 4.5 配套 DDL 变更（对既有表）

| 变更 | 说明 |
|---|---|
| `wms_container_type` **加 `device_labware_name`** | 容器类型 → 仪器板型名对照。一期建议**显式加列**而非塞 `spec_json`，因为要参与上机前校验，塞 JSON 无法索引/校验 |
| `wms_slot_info.device_code` 升级为强引用 | 同 zone↔slot 的做法：**加删除校验**（设备下有槽位不许删），错误码 `DEVICE_HAS_SLOTS`。这是上次 `deleteZone` 踩过的坑，别再犯 |

---

## 五、与 WMS 的缝合（关键收益）

**上机流程在数据上就是一次位置与状态的组合变更**，接上刚做好的流水，**零改表**：

| 环节 | 动作 | 产生的流水 |
|---|---|---|
| 从货架取板 | `take-out` | `TAKE_OUT`，`from_slot=货架位`，`biz_source=DEVICE`(或 TASK) |
| 板进仪器栈 | `put-in` 到设备槽位 | `PUT_IN`，`to_slot=设备栈位`（`slot_type=DEVICE`、`device_position_no=Tecan Stack-3`） |
| 跑板 | `RUN_MEASUREMENT` | `STATUS_CHANGE`：`AVAILABLE → IN_USE`，`ref_type=DEVICE`，`ref_id=device_code` |
| 出仪器 | `take-out` | `TAKE_OUT` |
| 放回货架 / 报废 | `put-in` / 状态变更 | `PUT_IN` 或 `STATUS_CHANGE → USED/DISCARDED` |

于是「**这块板什么时候上的机、测了什么、回哪去了**」一条链全通——
这正是流水表当初预留 `biz_source` / `ref_type` 的目的，**现在兑现**。

---

## 六、阻塞项：需要你提供 / 确认

### 6.1 必须提供（否则无法动接口层）🔴

1. **`Reader.NETwork V1.9` 接口文档**
   - 请放到 `.workbuddy/reference/`（或直接贴关键部分）。需要：
     - **endpoint 清单**（路径、方法）
     - **认证方式**（无 / Basic / Token / 会话）
     - **请求与响应的完整样例**（建测量、查状态、取结果各一）
     - **结果数据结构**（是否有 API 直取孔级数值？还是只给文件需要解析？）
   - 我已在公开渠道检索过，**拿不到**（这类文档只随仪器交付），不猜接口。

### 6.2 需要确认（影响架构选型）🟡

| # | 问题 | 为什么影响设计 |
|---|---|---|
| 1 | 几台读板机？型号（Infinite 200 PRO / F200 / M1000 / Spark）？ | 决定要不要一期就上多设备调度 |
| 2 | i-control 所在 Windows PC 与后端服务器**同网段可直连**吗？ | **决定 A / B 选型**。若跨网段/跨网闸，直接排除 A |
| 3 | 一期范围：只「跑板取数」，还是要「建方法/改参数」？ | 后者要写方法模板管理，工作量翻倍 |
| 4 | 结果格式：i-control 导出 Excel / XML / 写数据库？ | 决定是「API 取数」还是「取文件 + 解析器」两条完全不同的路 |
| 5 | 板型对照表由谁维护？（我们 `PLATE_96_WELL` ↔ Tecan labware 名） | 决定 `device_labware_name` 是手工填还是设备自动探测 |
| 6 | 是否还有 Tecan 液体处理工作站（Freedom EVO / Fluent）要一起规划？ | 若是，驱动抽象要一次留够接口 |
| 7 | 前缀用 `device_` 还是 `lab_`？ | 建表前定，之后改成本高 |

---

## 七、一期交付清单（确认后执行）

| 序 | 内容 |
|---|---|
| 1 | 新建 `boheng-module-device` 模块（pom + 装配 + 菜单 + 权限点） |
| 2 | DDL：`device_info` / `device_command` / `device_measurement` / `device_measurement_data` |
| 3 | DDL 变更：`wms_container_type` 加 `device_labware_name`；`device_code` 加删除校验 |
| 4 | `DeviceDriver` / `DeviceTransport` 两层接口 + `HttpTransport` 实现 |
| 5 | `TecanReaderNetworkDriver` 实现（**依赖 §6.1 文档**） |
| 6 | 命令状态机 + 异步执行 + 轮询回收 + 幂等 |
| 7 | 设备台账页 + 设备状态页 + 测量结果页（96 孔热力图） |
| 8 | 上机/下机接 WMS 流水（复用 `put-in` / `take-out`） |
| 9 | 端到端验证：真实仪器跑一块板，核对读数与流水 |

---

## 八、风险

| 风险 | 缓解 |
|---|---|
| 仪器侧无 API 直取孔级数，只能解析导出文件 | 先做「文件导入 + 解析器」兜底路径，API 通了再切；`measurement.data_file_id` 已留 |
| Windows PC 不稳定 / IP 变动 | `device_info.endpoint` 可改 + `online_status` 心跳可见；不做硬编 |
| i-control 版本升级导致接口变 | `driver_version` 字段 + `DeviceDriver.supports()` 按版本分派 |
| 设备卡死拖垮后端 | 命令表 + 超时 + `CANCEL`；异步执行与 HTTP 请求解耦 |
| 凭据明文 | 一期 `auth_config` 至少不与业务表同库；标注待加固（可用 infra 的配置加密） |
