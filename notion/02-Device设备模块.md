# 设备域分析（Device）

> 模块：`boheng-module-device` + `boheng-module-device-api`
> 定位：把「一台仪器」抽象成可下发、可观测、可解析、可模拟的统一对象。回答——**有什么设备、能下什么命令、命令怎么到设备、结果怎么回来、怎么变成结构化数据**。

---

## 一、整体分层

设备域采用「双模块双 SPI」：`device-api`（纯契约，零 Spring/MyBatis）被 `device`（实现）与 `flow`（消费方）共同依赖，绝不反向依赖。

DDL 共 **11 张表**（`device_` 前缀），对应 11 个子域：

| 子域（表） | 一句话定位 |
|---|---|
| `device_info` | 设备台账（一台物理设备一行，也是占用锁主表） |
| `device_action` | 动作定义（「这台设备能被怎么启动」） |
| `device_property` | 可观测属性（「能被观测什么」：温度/仓门/耗材） |
| `device_param_set` | 参数集预设（「这一次下什么参数」的可复用模板） |
| `device_codec` | 响应解析规则（原始报文 → 统一字段） |
| `device_command` | 命令记录（**核心**，一次执行的完整事实） |
| `device_data_raw` | L0 原始数据（不可变，解析失败也保留） |
| `device_measurement` / `_data` | L1 测量头 + 孔级读数 |
| `device_analysis_result` | L2 分析结论（带规则版本，可重判） |
| `device_simulate_profile` | 仿真 HTTP 配置 |

---

## 二、核心模型关系

```
device_info (1) ──< device_action (N)            按 device_type_code 关联（动作按类型共用）
device_info (1) ──< device_property (N)          按 device_type_code
device_info (1) ──< device_param_set (N)         按 device_type_code + action_code
device_info (1) ──< device_command (N)           按 device_id/device_code
device_info (1) ──< device_simulate_profile (N)  按 device_id/device_code

device_action ──> device_codec                   action.codec_code → codec.codec_code
device_command (1) ──< device_data_raw (N)        command_id
device_data_raw (1) ──< device_measurement (N)    data_raw_id
device_measurement (1) ──< device_measurement_data (N)  measurement_id（孔 × 波长）
device_measurement (1) ──< device_analysis_result (N)   measurement_id
```

**数据三层链**：`device_data_raw`(L0 不可变) → `device_measurement`+`_data`(L1 标准化) → `device_analysis_result`(L2 可重判)。

---

## 三、关键字段速览

### device_info（台账）
- 身份：`deviceCode`（业务唯一，跨模块只认编码）、`deviceTypeCode`（行为建模如 `PLATE_READER`，**不按型号**）、`driverType`（决定走哪个驱动，如 `SIMULATED`）
- 状态：`status`（ONLINE/OFFLINE/MAINTENANCE/FAULT）
- 接入：`connectionType`（HTTP/MQTT/NODE_RED/SIMULATED）、`endpointUrl`、登录（`loginUsername/loginPassword` AES 加密）、`tokenPath/tokenHeader/tokenPrefix/tokenTtlSec`
- 回调/轮询：`callbackEnabled/callbackUrl/callbackTimeoutSec/pollIntervalSec`
- **忙闲与并发（v4 关键）**：`busyCheckPolicy`（AUTO/ALWAYS/NEVER）、`concurrencyPolicy`（EXCLUSIVE/DEVICE_QUEUED/PLATFORM_QUEUED）、`maxInflight/inflightCount`
- **占用锁（对齐 SiLA）**：`currentCommandId/lockHolder/lockType/lockAcquiredTime/lockExpireTime/lockReason`

### device_action（动作）
- `deviceTypeCode + actionCode`（唯一）、`paramSchema`（输入契约：参数名→{label,type,unit,required,default,constraints}）
- 下发：`httpMethod/bodyFormat/requestPath`；`requestTemplate` 仅作为高级厂商报文映射（`${param}` 占位），普通 JSON 接口不配置
- 回收：`resultMode`（SYNC_RETURN/CALLBACK/POLL）、`pollDoneExpr`、`codecCode`、`estimateDurationMs`

### device_command（命令，核心）
- 对外凭据：`commandNo`（`"C"+雪花`）
- 定位：`sourceType`（FLOW/MANUAL/API/...）、`businessExecId/nodeId/attempt`（幂等三键）
- 状态：`status`（ACQUIRED/SENT/WAITING/SUCCEEDED/FAILED/TIMED_OUT/CANCELLED）、`waitingFor`（SLOT_AVAILABLE/DEVICE_CALLBACK/POLL_TICK/...）
- 报文快照（可复现）：`paramsJson/payloadJson/requestJson/responseJson/errorMsg`
- 安全：`idempotentKey`、`callbackTokenHash`（SHA-256，禁明文）
- 时间戳：`acquiredAt/sentAt/finishedAt/timeoutAt`

---

## 四、关键机制

### 机制一：命令下发 → 设备回调 → 结果落库（主链路）

`DeviceHttpService.invoke` 编排：

1. **校验**：设备存在 → 动作存在且启用 → 判仿真 → 维护/故障拒绝 → 锁校验
2. **落命令**：`open(...)` 生成 `commandNo`，`status=ACQUIRED`
3. **抢占**：EXCLUSIVE/PLATFORM_QUEUED 走 CAS `tryOccupy`；DEVICE_QUEUED 按 `maxInflight` 放行；抢不到 → `DEVICE_BUSY`
4. **回调准备**（CALLBACK 模式）：`issue` 签发 token → `awaitCallback` 转 WAITING
5. **下发**：仿真走 `exchange`，真设备走 `gateway.invoke`（带 token，401 重登）
6. **回收**：非回调 `complete`；回调 `recordSent` → 阻塞 `await` → 取结果
7. **释放**：`finally` 里 `releaseOccupy`（只释放自己抢到的那次）

### 机制二：设备回调落库

`POST /device/callback/{commandNo}`（`@PermitAll` + `@TenantIgnore`，设备不带租户）：
- 按 `commandNo` 跨租户查 → 令牌常量时间比较（`MessageDigest.isEqual`）→ `completeCallback`（CAS `WHERE status=WAITING` 抢状态）→ 唤醒等待线程
- 返回：200 / 403（令牌错）/ 404 / 409（已结束）/ 413（超 1MB）

### 机制三：超时扫描兜底（DeviceCommandSweeper）

服务重启后线程丢失，命令会停在 WAITING。单守护线程每分钟：
- `timeoutExpiredWaiting`：WAITING 且超 `timeoutAt` 30s → TIMED_OUT
- `failStaleAcquired`：ACQUIRED/SENT 超 15 分钟 → FAILED

### 机制四：模拟设备

平台与仿真设备走**本机真实 HTTP 回环**（带 `X-Simulate-Secret` 口令）：
- SYNC：立即返回 `responseBody`
- CALLBACK：回 ack → 延迟 `asyncWaitMs` 后 POST 回调平台
- POLL：入 `SimulateJobStore`，平台按 `pollIntervalMs` 轮询，次数用尽 `DEVICE_SIMULATE_POLL_TIMEOUT`

### 机制五：编解码（L0 → L1）

`applyCodec`：取 codec → `DeviceCodecParser.parse`（JSON 字段映射 / REGEX 捕获组，**带 2 秒正则超时保护**）→ `ingest` 拆成 measurement + 孔级读数 + 分析结果。解析失败只记 `errorMsg`，**L0 原始数据保留**可重建。

### 机制六：输入契约 → 参数集 → 设备报文

设备动作采用“业务参数与厂商协议分离”的模型：

1. `device_action.param_schema` 是动作输入的唯一契约，定义参数类型、必填、默认值和约束。
2. `device_param_set.params_json` 是命名参数方案，只保存一组可复用取值，保存和标记已验证时必须符合动作输入契约。
3. 流程映射或人工输入形成运行时覆盖值；进入设备域后统一由 `DeviceParamResolver` 展开默认值并执行校验。
4. 最终业务参数写入 `device_command.params_json`，作为本次执行快照。
5. 普通 JSON 接口不需要请求模板，最终参数直接序列化下发。
6. 只有厂商字段名、嵌套结构或报文格式不同（如 XML、特殊 TEXT）时才配置 `request_template`；渲染后仍有未解析占位符时拒绝下发。
7. 实际发送的报文写入 `device_command.payload_json/request_json`，用于排障和复现。

参数值优先级遵循：

```text
输入契约默认值 → 参数集 → 流程映射或人工输入
```

其中输入契约负责“允许什么”，参数集负责“常用的一组值”，高级报文映射只负责“如何翻译成厂商协议”，三者不互相替代。

---

## 五、契约层（供 flow 调用）

| 接口 | 方法 | 用途 |
|---|---|---|
| `DeviceApi` | `selectDevices(req)` | 按「类型+在线+空闲」选设备 |
| `DeviceApi` | `getDeviceByCode` | 取设备（不存在返回 null） |
| `DeviceApi` | `hasEnabledAction` | 动作存在且启用 |
| `DeviceApi` | `listActionCodesByDevice` | 设备能力集 |
| `DeviceCommandApi` | `send(req)` | 下发命令并**阻塞等到终态**，异常收成 `success=false` |

flow 侧两种用法：
- `device.command`（同步推，设备在线）：调 `DeviceCommandApi.send`，阻塞等终态
- `device.task`（异步拉，可离线排队）：`FlowDeviceTaskService.enqueue` 入队，机器端 `claim` 时按能力集 CAS 认领

---

## 六、关键设计原则

1. **回调令牌安全**：库里只存 SHA-256 摘要，常量时间比较防时序攻击。
2. **占用锁必须能过期**（`lockExpireTime`），否则设备永久锁死。
3. **命令只增不改**，业务侧无删除入口。
4. **唯一键「生成列 + 唯一索引」**，逻辑删除后释放编码。
5. **状态与等待原因正交**：`status` 表达「走到哪」，`waitingFor` 表达「在等什么」。
6. **数据三层可重建**：L0 不可变 → L1 标准化 → L2 可重判。
7. **输入契约单一来源**：参数默认值、类型、必填和约束只在 `param_schema` 定义；参数集与运行时输入统一通过后端解析器校验。
8. **报文映射按需使用**：平台参数与厂商字段一致时直接下发 JSON；`request_template` 只处理协议差异，不作为必填配置。

---

## 七、⚠️ DDL（v4 目标）vs 代码（一期）差异

写代码/读文档时需注意，DDL 是目标架构，代码是一期子集：

| 维度 | DDL 目标 | 当前代码 |
|---|---|---|
| 命令状态机 | CREATED→ACQUIRED→SENT→WAITING→终态 | 实际 ACQUIRED→(WAITING)→终态，无 CREATED/SENT |
| 幂等三键 | business_exec_id/node_id/idempotent_key | DO 未落地（Flow 来源暂按 MANUAL 记） |
| 忙闲预检 | busyCheckPolicy×needBusyCheck×statusCommandCode 联动 | 仅实现 concurrencyPolicy 的 EXCLUSIVE/DEVICE_QUEUED |
| 命令扩展字段 | progress_*/eta_sec/recovery_action/param_set_id | DO 未映射 |
| 测量/分析上下文 | driver_version/plate_instance_*/rule_code/rule_version | DO 简化 |
