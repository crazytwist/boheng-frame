# 旧设计对标：librax `lab_device_*` → boheng 设备域

> 对标对象：`/Volumes/External_1TB/Projects/librax/`（git 8 个提交，最后一个 `【流程引擎】设备模块更新`）
> 对标时间：2026-09-30
> 结论导向：**哪些吸收、哪些不采纳、我们要改哪几列**

---

## 〇、先说结论

1. **这不是纸面设计，是一套跑过的系统**。`librax-module-device`(38 文件) + `librax-module-flow`(145) + `librax-module-lab`(75) 都在，代码可读。
2. **参考价值很高，但价值不在 DDL、在代码**。DDL 本身有 **7 处不能学**、2 处是 bug、外键写法直接执行会报错；而它的 `DeviceGatewayImpl` + `DeviceStateCache` + `TimeoutWatchdog` 是真实跑通的。
3. **最关键的发现不在设备域**：我们打算从零写的**接入层（MQTT/HTTP transport）**，芋道 IoT 网关已经做完了（见 §六）。这可能省掉 v3 一整章的工作量。

---

## 一、旧设计的真实完成度（别被表数量骗了）

| 表 | 设计 | 实现 | 证据 |
|---|---|---|---|
| `lab_device_info` | ✅ | ✅ **全实现** | DO + Service + 3 VO + Controller |
| `lab_device_command` | ✅ | ⚠️ **只有 CRUD** | `DeviceCommandServiceImpl` 是 codegen 产物，**8 个字段从没被任何代码读过** |
| `lab_device_codec` | ✅ | ⚠️ **只有 CRUD** | `DeviceCodecServiceImpl` 同样是纯 CRUD，**JSON/HEX/REGEX/SCRIPT 解析引擎不存在** |
| `lab_device_model` | ✅ | ❌ **未实现** | 全项目搜不到 DO，`@TableName` 清单里没有它 |
| `lab_device_execution` | ✅ | ❌ **未实现** | 全项目**零引用**，状态机没落地 |

`DeviceCommandDO` 的 8 个"僵尸字段"：`contentType` / `httpMethod` / `httpPath` / `retryable` / `codecId` / `pollPath` / `pollDoneExpr` / `pollMaxTimes`。
`DeviceGatewayImpl.sendCommand` 这 5 步里，**只用到了 `requestTemplate`**：

```java
// DeviceGatewayImpl.sendCommand —— 真正跑通的全部逻辑
1. deviceCommandMapper.selectByTypeAndCode(deviceType, commandCode)   // 查指令配置
2. deviceSelector.select(deviceType)                                   // 选空闲设备
3. renderTemplate(command.getRequestTemplate(), params)                // ${param} 替换
4. driverFactory.getDriver(device.getProtocol()).send(...)             // 按协议取驱动
5. stateCache.markBusy(deviceId, taskId, executionId, nodeId)          // 写 Redis + 反向索引
```

→ **`execution` 表是"设计意图"，不是"生产验证过的结构"。它的代码比它的表可信。**

### ⚠️ 这份 DDL 作为脚本是坏的，别当基线执行

```sql
ALTER TABLE "public"."lab_device_execution" ADD CONSTRAINT "fk_lab_device_execution_command"
  FOREIGN KEY ("model_code","model_code","command_code","command_code")
  REFERENCES "public"."lab_device_command" ("command_code","model_code","command_code","model_code");
```
**列数不匹配（4 列，应为 2 列）+ 顺序互相颠倒**。`fk_lab_device_info_heartbeat` 同病：
```sql
FOREIGN KEY ("model_code","model_code","heartbeat_command","heartbeat_command")
REFERENCES ... ("command_code","model_code","command_code","model_code")
```
这是导出工具的产物（列被重复展开两遍）。**只作结构参考。**

---

## 二、逐表对照

| 旧设计 (librax) | 我们的 (boheng) | 判定 |
|---|---|---|
| `lab_device_info` | `device_info` | 一一对应；**我们更完整**（锁四字段 + `simulation_mode`） |
| `lab_device_command`（**配置**） | `device_action`（配置） | 我们多 `param_schema`；它多 `request_template` + `poll_*` |
| `lab_device_codec` | **无** | **我们缺一张表** |
| `lab_device_model` | **无**（`device_type_code` 散在各表） | 我们缺"型号"这一层 |
| `lab_device_execution`（**记录**） | `device_command`（记录） | 语义对应；**我们字段更全** |

### ★★ 命名撞车（必须写进术语表）

| 概念 | librax | boheng |
|---|---|---|
| **配置**：这台设备能被怎么启动 | `command` | `action` |
| **记录**：一次执行的事实 | `execution` | `command` |

**同一个作者（你）在两套系统里对 `command` 用了完全相反的语义。**
→ 不建议改名（我们 5 张表 + 全栈代码已落地，改名成本远大于收益），但**必须在架构文档里显式写死**。否则二期对接/数据迁移时 100% 踩——尤其是在两套系统的代码同时存在的时候。

---

## 三、值得吸收的 8 项

### P0 —— 趁 `device_command`/`device_action` 还没铺，现在改成本最低

#### 1. ★ flow → device 的接缝契约（最高价值）

```java
// librax: DeviceGateway（设备域对外唯一入口）
String sendCommand(String deviceType, String commandCode, Map<String,Object> params,
                   String executionId, String nodeId, String callbackToken);  // → taskId
```

调用方 `flow/InstrumentStepExecutor` 把它翻译成：
```java
String taskId = deviceGateway.sendCommand(deviceType, command, inputParams,
                                          executionId, node.getNodeId(), callbackToken);
return StepResult.waitForDevice(Map.of("deviceTaskId", taskId, ...));  // 返回 WAITING，不阻塞
```

**三个要点：**
- 参数只有 **6 个，且不含 `deviceId`** —— 选设备是**设备域自己的事**（`DeviceSelector` 内部完成）。这正好印证我们「调度只给条件、不硬编 `device_code`」的决策。
- **`executionId + nodeId` 才是业务侧定位一次设备调用的键**，不是单一 `executionId`。我们原设计只有 `ref_type`/`ref_id`，不够。
- `WAITING` 是**统一等待态**，用 `WaitingForEnum{DEVICE_CALLBACK, MANUAL_APPROVE, EXTERNAL_EVENT, TIMER}` 区分来源。
  → **推论：我们的 `device_command.status` 只该表达"设备侧"的状态，不要把"等审批/等定时"混进来。**

**改动**：`device_command` 加 `business_exec_id` + `node_id`；`device-api` 接口签名固化为上面形状。

#### 2. ★ 复合幂等键（修正我原来的单列设计）

```sql
UNIQUE KEY uk (source_type, business_execution_id, node_id, attempt)
```

**为什么必须改**：流程重试时，同一节点第 2 次尝试**应该是新记录**。若只有单列 `idempotent_key`，重试会被拦成"返回原命令"——
**调度域永远看不到第 2 次执行的事实，重试等于没重试。**

**两个键分工，别合并：**

| 键 | 管什么 | 空值语义 |
|---|---|---|
| `(source_type, business_exec_id, node_id, attempt)` | **业务幂等**（同一节点同一次尝试不重复创建） | 手工触发时为空 → MySQL 唯一索引 **NULL 不参与去重**，正好符合「手工命令不做业务幂等」 |
| `idempotent_key` | **传输幂等**（MQTT 重投 / 网络重发） | 为空不参与约束 |

#### 3. ★ `callback_token_hash` —— 回调鉴权只存摘要

我们目前**完全没有回调鉴权字段**。`librax` 的做法：
- `InstrumentStepExecutor` 生成 `UUID` token → 随命令下发给设备
- 设备回调时 `DeviceCallbackReqVO.callbackToken` **必填**
- DB 只存 `callback_token_hash bpchar(64)`（SHA-256），注释明确「**禁止保存或记录明文令牌**」

**为什么是硬需求**：没有它，任何人构造一个 POST 就能往系统里灌伪造的测量数据（实验数据造假）。
**改动**：`device_command` 加 `callback_token_hash varchar(64)`。

#### 4. 四个时间戳 + `version` 乐观锁

| librax | 我们现状 |
|---|---|
| `acquired_at` / `sent_at` / `timeout_at` / `finished_at` | 只有 `start_time` / `end_time` |
| `version int`（状态流转递增） | 无 |

**为什么**：`acquired_at → sent_at` = **排队等了多久**；`sent_at → finished_at` = **设备跑了多久**。
没有第一个点，出问题时分不清是「**选不到空闲设备**」还是「**设备太慢**」——这两种故障的处理方式完全不同。

**改动**：`start_time`/`end_time` 扩成三段时间戳 + `timeout_at`（扫描用，比 `callback_deadline` 更通用）+ `version int`。

### P1 —— 有价值但可稍后

#### 5. `poll_done_expr` + `poll_max_times`：完成判定配置化

librax：`poll_done_expr = $.status == "DONE"`，配置在表里。
我们 `device_command` 有 `poll_count`，但**"什么算完成"没地方配**，会硬编码进驱动 → 每接一种设备改一次 Java。

**改动**：`device_action` 加 `poll_done_expr` + `poll_max_times`。

#### 6. 表达式引擎用 Aviator（别自研）

`librax` 的 `StepTypeEnum.CONDITION` 注释写明「**Aviator 表达式求值**」，轮询判定用的是同一套。
**行动项**：先查 `boheng-dependencies` 是否已有 Aviator；有就直接复用，别自己写 JSONPath + 手写比较。

#### 7. `dispatch_mode` 的 `CHECK_BEFORE_SEND` + `status_command_code`

librax：发业务指令前**先发一条状态查询命令问设备忙不忙**，忙就不发。
我们：平台侧 CAS 锁。

**两者不是替代，是互补：**

| 机制 | 解决什么 |
|---|---|
| 平台锁（`lock_type`） | 「**别的模块 / 人工**不要插队」 |
| 设备侧忙闲查询 | 「**设备自己**因上次没跑完、或有人在本地操作而忙」 |

**改动**：`device_action` 或 `device_info` 加 `dispatch_mode`(DIRECT_SEND / CHECK_BEFORE_SEND) + `status_command_code`，作为**下发前的第二道闸**。

#### 8. `request_template` `${param}` 占位 —— 与 `param_schema` 并存

| | librax | boheng |
|---|---|---|
| 哲学 | **配置驱动 + 通用驱动**（不写代码就能接 HTTP 设备） | **型号驱动 SPI**（复杂场景写 Java） |
| 落点 | `request_template` 占位符 | `param_schema` + `vendor_ref` |

**判断：两条都要，各管一段。**

| 层 | 负责 | 落点 |
|---|---|---|
| 参数层 | 参数怎么填（校验 / 默认值 / 单位 / 前端表单渲染） | `param_schema` |
| 报文层 | 填完怎么变成报文 | `request_template` |

**优先级**：`vendor_ref`（有驱动）→ `request_template`（有模板）→ 报错。
这样大部分 HTTP/JSON 设备**不用写驱动**，模板覆盖不了的（Tecan 私有协议）再落 Java 驱动。

---

## 四、明确不采纳的 7 项

| # | 不采纳 | 理由 |
|---|---|---|
| 1 | **无 `tenant_id`** | 它 DO 继承 `BaseDO` 而非 `TenantBaseDO`。我们多租户基座，所有业务表必须带 |
| 2 | **平台直连 TCP / SERIAL / SDK** | 与 v3「平台只开放 HTTP+MQTT，其余走 Node-RED」冲突。`sdk_class` 反射加载厂商 SDK 会污染平台进程 |
| 3 | **执行态只放 Redis** | `device:state:{id}` 与 `device:exec:{exec}:{node}` 都是 **24h 过期**；进程重启或过期后**事实就丢了**。我们 `device_command` 落库是对的，Redis 只做加速 |
| 4 | **先建字段后补实现** | 8 个僵尸字段（见 §一）。表承诺了没兑现的能力 = 后端最贵的债 |
| 5 | **ServiceImpl 跨模块 import** | `DeviceCommandServiceImpl` 第 10 行 `import com.librax.lab.module.lab.controller.admin.devicecommand.vo.*;` —— device 模块**反向依赖 lab 模块的 controller VO**，codegen 垃圾。**我们的双模块强制依赖方向正好防这个** |
| 6 | **自定义 `DeviceException extends RuntimeException`** | 项目规范是 `exception(ErrorCode)`。它 `throw new DeviceException("指令配置不存在: ...")` 让错误码体系彻底失效 |
| 7 | **`base_url_backup` 把运行态写回配置表** | 配置文件被运行时改写；切到 SIM 后崩溃/忘记切回 = 真实地址状态混乱。我们的 `simulation_mode` 标记 + 驱动分派更干净 |

> **但第 7 条吸收一个点**：切 SIM 必须**可审计**（走流水或独立状态日志），不能是个谁都能点的开关。

### ★ 它自己的两个 bug（引以为戒）

**bug 1：设备选择非原子 —— 并发下会重复选中同一台设备**

```java
// DeviceSelector.select
List<DeviceInfoDO> candidates = deviceInfoMapper.selectEnabledByType(deviceType);
return candidates.stream().filter(d -> stateCache.isIdle(d.getDeviceId()))
                 .findFirst().orElse(null);            // ← 先查
// DeviceStateCache.markBusy → redisTemplate.opsForHash().putAll(...)   // ← 后改，非原子
```
**check-then-act，无锁无 CAS。** 多实例 / 多线程并发时两台仪器会同时收到同一条命令。
它的 `device_info.max_concurrent` 字段**形同虚设**（`select` 里根本没读它）。

→ 我们计划的「台账 CAS 锁」必须写成**原子抢占**，而不是 select-then-update：
```sql
UPDATE device_info SET current_command_id = ?
 WHERE device_code = ? AND current_command_id IS NULL AND deleted = b'0';
-- affected rows == 1 才算抢占成功
```

**bug 2：`@Scheduled(fixedDelay = 10_0000)`**

`10_0000` 里下划线是**数字分隔符**，实际值 = `100000` ms = 100 秒（注释写"每 10 秒"，实际是 100 秒）。注释与代码不一致，写法极易误读。
→ 我们写常量时别学这个，用 `Duration.ofSeconds(10).toMillis()` 或明确注释。

---

## 五、超时设计：它最值得抄的一段

```java
// TimeoutWatchdog
private static final long DEFAULT_TIMEOUT_MS = 60_000L;            // 全局兜底
private static final long DEFAULT_WAITING_TIMEOUT_MS = 300_000L;   // WAITING 单独 5 分钟

// timeout_ms 来源优先级（按序取，先命中先用）：
//   1. pd_pipeline_step.timeout_ms          步骤级配置
//   2. pd_pipeline_definition.default_timeout_ms   流程级默认
//   3. 全局兜底 60_000ms
// 两个独立扫描：步骤级 10s 一次 / 流程级 30s 一次
// 超时后行为 = 等同于执行失败 → onStepComplete(FAILED) → 走正常重试 / DEAD 链路
```

**三条要点：**
1. **`WAITING` 的超时必须与 `RUNNING` 分开**。设备跑 30 分钟很正常，但"等回调"等 30 分钟不正常。这两个数不是同一个量级。
2. **超时不是终态，是"触发失败链路"**。它把 TIMEOUT 归约成 FAILED → 复用重试/降级/告警。**别为超时单独写一套处理**。
3. **超时值走优先级链**，而不是一处写死。

**对我们的改动**：`device_command.callback_deadline` 语义不变，但补**优先级链**：
`device_action.estimate_duration_ms × 系数` → `device_info` 默认 → 全局兜底。
这样每个动作有自己的合理阈值（读板 30s vs 孵育 30min），不需要一张全局表。

---

## 六、★★★ 顺带发现：可能改写 v3 一整章

侦察时发现 librax 有三个我们"未开工"的域，其中**接入层已经做完**。

### 6.1 `boheng-module-iot` 被注释掉了，但它自带 MQTT / TCP / EMQX 网关

`boheng-frame/pom.xml` 第 26 行：`<!-- <module>boheng-module-iot</module> -->`。
而 `librax-module-iot`（309 文件，三个子模块 `iot-biz` / `iot-core` / `iot-gateway`）里已有：

```
iot-gateway/protocol/mqtt/     内建 MQTT broker（Vert.x）+ IotMqttConnectionManager
iot-gateway/protocol/emqx/     EMQX 集成：auth event + upstream/downstream subscriber + router
iot-gateway/protocol/http/     HTTP 接入 + IotHttpAuthHandler
iot-gateway/protocol/tcp/      TCP 接入 + IotTcpConnectionManager
iot-gateway/codec/tcp/         TCP 二进制 / JSON 编解码
iot-gateway/codec/alink/       阿里云 Alink
iot-gateway/service/auth/      IotDeviceTokenService —— 设备级鉴权
iot-biz/mq/consumer/device/    IotDeviceMessageSubscriber —— 消息上下行订阅
```

**对 v3 §三 的直接影响：**

| v3 原结论 | 实际情况 |
|---|---|
| 「需本地起 **EMQX** 容器」（待决策点 H） | 它**已内建 MQTT broker**，EMQX 是可选上游 → 选型其实已有默认答案 |
| 「`HttpUtils` 太弱，`HttpTransport` 需基于 **OkHttp** 新写」 | `protocol/http` **已实现** |
| 「串口/私有协议交 **Node-RED**」（待决策点 I） | 还有 `protocol/tcp` + `codec/tcp` → Node-RED 的必要性进一步下降 |
| 「MQTT topic 五组 + 统一信封要自己定契约」 | 已有 `IotMqttTopicUtils` + `IotDeviceMessageCodec` 体系 |

→ **建议：把「启用 `boheng-module-iot` 并复用其传输层」作为 v3 §三 的替代方案单独评估。**
这可能省掉整整一章的工作量，且它自带设备鉴权（我们目前缺）。

### 6.2 `librax-module-flow`（145 文件）= 我们的调度域，且已完整实现

```
pd_pipeline_definition / _step / _trigger     流程定义
pd_step_definition                            节点定义
pe_pipeline_execution / _step_execution       执行实例
pe_execution_context / pe_execution_event_log 上下文 / 事件日志

引擎：DagScheduler / StepStateMachine(351行) / ExecutionStateMachine
     TimeoutWatchdog / RecoveryScanner
     RetryPolicyRegistry + ExceptionEngine + FailureActionHelper + FailureDecision
     ExecutionEventPublisher（7 种事件）
     ExecutionContextManager + OutputMappingResolver
     StepCallbackService / StandaloneExecutionService / LiteFlowController
     PipelineGraphCache + PipelineGraphBuilder + PipelineGraphValidator

节点类型：StepTypeEnum{INSTRUMENT, COMPUTE, CONDITION, WAIT, NOTIFY, SAMPLE_SPLIT}
步骤状态：StepStatusEnum{PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, DEAD,
                        COMPENSATING, COMPENSATED, WAITING}
```

→ 这正好回答我们「**调度域不自研流程引擎**」的决策。
**建议：不自研，列为移植候选。** 尤其 `RecoveryScanner`（崩溃恢复）+ `StandaloneExecutionService`（单步调试）是我们架构文档里没细想的两个点。

⚠️ 注意方向：librax 里 `flow` **依赖** `device`（`InstrumentStepExecutor` import 了 `DeviceGateway`）。
这**违反**我们 v3 的硬约束「**device 永不反向依赖 flow**」吗？——不违反，方向是对的（flow → device）。
但它**是 Maven 硬依赖**，而不是我们计划的 `device-api` 契约 + SPI。迁移时要改成面向 `device-api` 编程。

### 6.3 `librax-module-lab`（75 文件）= **样本域**，我们完全没有

```
lab_sample_info / _step / _result / _event / _relation
SampleLifecycleService         生命周期
SampleTraceService             追溯（含 SampleTraceReportVO / SampleJourneyVO / SampleTimelineVO）
SampleResultHandleService      结果处理
SampleBindHook                 Hook 挂载
SampleSplitStepExecutor        样本拆分执行器（对应 StepTypeEnum.SAMPLE_SPLIT）
SampleFlowIntegrationListener  流程集成监听
TestItemReferenceRegistry      检测项引用注册表
AbnormalFlagEnum / ReviewStatusEnum / SampleStatusEnum / SampleStepStatusEnum
```

→ 我们 WMS 现在只有「板 / 孔 / 容器」，**缺"样本从哪来、属于哪个实验、结果归谁"**。
→ **建议**：G 区加一条待办 —— 样本域与 WMS「板/孔」如何对齐（`lab_sample_info` ↔ `wms_material_instance`）。

---

## 七、对设备域 DDL 的具体修改清单（待确认后执行）

### 7.1 `device_action` 加 4 列

| 字段 | 类型 | 吸收自 |
|---|---|---|
| `request_template` | `text` | §三.8 —— 报文模板，`${param}` 占位 |
| `poll_done_expr` | `varchar(512)` | §三.5 —— 轮询完成判定表达式 |
| `poll_max_times` | `int` | §三.5 |
| `dispatch_mode` | `varchar(32) DEFAULT 'DIRECT_SEND'` | §三.7 —— DIRECT_SEND / CHECK_BEFORE_SEND |

### 7.2 `device_action` 或 `device_info` 加 1 列

| `status_command_code` | `varchar(64)` | §三.7 —— 忙闲查询命令编码 |

### 7.3 `device_command` 加 7 列

| 字段 | 类型 | 吸收自 |
|---|---|---|
| `business_exec_id` | `varchar(128)` | §三.1 —— 上游业务执行标识（flow executionId） |
| `node_id` | `varchar(128)` | §三.1 —— 上游节点标识（直接执行固定 `direct`） |
| `attempt` | `int NOT NULL DEFAULT 1` | §三.2 —— 同一节点的尝试次数，重试各自成行 |
| `callback_token_hash` | `varchar(64)` | §三.3 —— 仅存 SHA-256，**禁存明文** |
| `acquired_at` / `sent_at` | `datetime` | §三.4 —— 抢占时刻 / 已发送时刻 |
| `timeout_at` | `datetime` | §三.4 —— 超时扫描落点 |
| `version` | `int NOT NULL DEFAULT 0` | §三.4 —— 状态流转乐观锁 |

`start_time` / `end_time` 保留（语义：业务视角开始 / 结束），与 `acquired_at`/`sent_at`/`finished_at` 并存不冲突。

### 7.4 唯一键调整（双键分工）

```sql
-- 原：UNIQUE KEY `uk_idempotent` (`tenant_id`, `idempotent_key`)
-- 改为两个键：
UNIQUE KEY `uk_business`   (`tenant_id`, `source_type`, `business_exec_id`, `node_id`, `attempt`),
UNIQUE KEY `uk_idempotent` (`tenant_id`, `idempotent_key`),   -- 保留，管 MQTT 重投
```
⚠️ `business_exec_id`/`node_id` 在手工触发场景为空 → **MySQL 唯一索引下 NULL 不参与去重**，正好实现「手工命令不做业务幂等」。**这个性质要写进列注释。**

### 7.5 新增表：`device_codec`（一期 9 张 → 10 张）

采纳 §二 判断，但**大幅收窄**：

| 字段 | 采纳 | 说明 |
|---|---|---|
| `codec_code` / `codec_name` | ✅ | 编码带版本：`LEICI_PH_RESULT_V1` |
| `parse_type` | ✅ **但只放行 JSON / REGEX** | Service 层拒绝 `HEX`/`SCRIPT` |
| `field_mapping`（JSONPath） | ✅ | |
| `regex_rules` | ✅ | |
| `hex_rules` | ⏸ 建字段，不开放 | 等真实串口设备 |
| `script_engine` / `script_content` | ⏸ **建字段，Service 层直接拒绝保存** | 见下方风险 |
| `unit_conversions` / `valid_range` | ❌ **不放 codec** | 属 L1 标准化层职责（见下） |

**为什么拒绝 `script_content`**：存 Groovy/JS = **代码注入面**。要安全就得沙箱 + 类白名单 + 超时 + 禁 IO/反射，
成本远高于收益；而 JSON/REGEX 已覆盖 HTTP/JSON 类设备 90% 场景。
**表结构和字段都建好**（避免以后改表），但 Service 层 `parse_type=SCRIPT` 直接抛错，字段保留不开放。

**为什么不把 `unit_conversions`/`valid_range` 放 codec**：这属于「解析」之后的「标准化」，
塞进 codec 会把两层焊死 —— 解析器升级会连带动单位规则。我们的 L0→L1→L2 分层更清楚。

### 7.6 我们比它强、不要退步的地方

| 我们的优势 | librax 对应 | 结论 |
|---|---|---|
| **独立 `device_action` + `device_property` 表** | 型号表上一个 `capabilities` JSON | **别退回 JSON 大字段**。独立表可查询、可约束、前端可按 schema 渲染表单 |
| **`param_schema`** | 无 | 保留。这是「前端动态表单 + 参数校验」的地基 |
| **`device_param_set`（参数集预设 + `validated`）** | 无 | 保留。它没有"同型号不同批次参数差异"的落点 |
| **数据三层 L0/L1/L2** | 只有 `execution.output_data` 一个 JSON | 保留。`output_data` 塞一个 JSON 无法重建、无法重判 |
| **双 Maven 模块强制依赖方向** | 单模块 + 跨模块 import controller VO | 保留，正是防 §四.5 那种债 |

---

## 八、待拍板

| # | 问题 | 我的建议 |
|---|---|---|
| 1 | 是否现在就把 §七 的修改做进 `device.sql`？ | **尽快**。`device_command` 还没铺，现在改 DDL 几乎零成本；铺完再改要动 DO/VO/前端 |
| 2 | `device_codec` 一期上不上？ | **上，但只开 JSON/REGEX**；SCRIPT 建字段不开放 |
| 3 | 是否评估启用 `boheng-module-iot` 做传输层？ | **强烈建议评估**（§6.1），可能省掉一整章 |
| 4 | librax flow 引擎是否列为调度域移植候选？ | **列为候选**（§6.2），尤其 `RecoveryScanner` / `StandaloneExecutionService` |
| 5 | 样本域（§6.3）是否进 WMS TODO G 区？ | **进**，需先定"样本 ↔ 板/孔"的对齐关系 |

---

## 附：来源索引

| 结论 | 证据文件 |
|---|---|
| 接缝契约 | `librax-module-flow/.../executor/InstrumentStepExecutor.java` |
| 反向索引 / Redis 状态 | `librax-module-device/.../gateway/DeviceStateCache.java` |
| 非原子选择（bug） | `librax-module-device/.../gateway/DeviceSelector.java` |
| 超时优先级链 | `librax-module-flow/.../watchdog/TimeoutWatchdog.java` |
| 僵尸字段 | `librax-module-device/.../dataobject/devicecommand/DeviceCommandDO.java` |
| 跨模块 import | `librax-module-device/.../service/devicecommand/DeviceCommandServiceImpl.java:10` |
| 回调契约 | `librax-module-device/.../controller/vo/DeviceCallbackReqVO.java` |
| 等待语义 | `librax-module-flow/.../enums/WaitingForEnum.java` / `StepStatusEnum.java` |
| IoT 网关 | `librax-module-iot/librax-module-iot-gateway/.../protocol/{mqtt,emqx,http,tcp}/` |
| 我们模块已注释 | `boheng-frame/pom.xml:26` |
