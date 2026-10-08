# 设备传输层：iot-gateway 借用边界评估

> 结论日期：2026-09-30
> 触发：用户拍板「iot-gateway 暂时借用，但必须评估借用范围，最好保证 device 的整体模块化」
> 关联：`device-domain-architecture.md`（v5 §三）、`device-design-prior-art-librax.md`
> 被评估对象：`/Volumes/External_1TB/Projects/librax/librax-module-iot/`（core 23 + gateway 41 + biz 245 文件）

---

## 〇、一句话结论

**借「协议接法」，不借「模块 + 物模型 + 部署形态」。**

`iot-gateway` **不是传输层，是「物模型网关（sidecar）」**——它跑在独立进程里，通过 RestTemplate RPC 回调主程序，消息接缝是被物模型语义占满的 `IotDeviceMessage`。
直接把 device 模块挂上去，等于**把 device 的状态机劈成两半、多背一个进程、再拖进 rocketmq**。

正确姿势：**device 自己定义 `DeviceTransport` SPI**，iot-gateway 降级为「可选的协议实现参考」。一期我们自带 HTTP/MQTT/仿真三实现，**零依赖**。

---

## 一、先看清它是什么（别按名字想象）

| 维度 | 事实 | 出处 |
|---|---|---|
| 部署形态 | **独立 Spring Boot 应用**（有 `main`、有 `spring-boot-maven-plugin`、`finalName` 独立打包） | `IotGatewayServerApplication` |
| 与主程序通信 | **HTTP RPC**：`RestTemplate` POST 到 `rpc.url`，只有 2 个方法 `authDevice` / `getDevice` | `IotDeviceApiImpl` |
| 消息接缝 | `IotDeviceMessage{id, reportTime, **deviceId(Long)**, tenantId, serverId, requestId, method, params, data, code, msg}` | `iot-core/mq/message` |
| 语义 | 物模型：`method` 取 `property/set`、`property/get`、`event/report`、`service/invoke`… | `IotDeviceMessageMethodEnum` |
| 编解码接缝 | `IotDeviceMessageCodec { byte[] encode(IotDeviceMessage); IotDeviceMessage decode(byte[]); String type(); }` —— **强耦合物模型消息** | `codec/` |
| 协议装配 | `@ConditionalOnProperty(prefix="...protocol.{http,emqx,tcp,mqtt}", name="enabled")` —— **本身就是可裁剪的**（这是它唯一讨喜的地方） | `IotGatewayConfiguration` |
| MQTT 形态 | **两种都有**：`protocol/mqtt` = 平台自己起 `MqttServer`（Vert.x 当 broker）；`protocol/emqx` = 连外部 EMQX + 处理认证事件 | `protocol/` |
| 依赖重量 | `rocketmq-spring-boot-starter` + `vertx-web/mqtt` + `spring-web` | gateway `pom.xml` |
| 消息总线抽象 | 极薄：`IotMessageBus { void post(String,Object); void register(IotMessageSubscriber<?>); }`，三实现 local/redis/rocketmq | `iot-core/messagebus` |

### ⚠️ 与我们要害冲突的三点

1. **`deviceId` 是 `Long`（DB 主键）** —— 我们跨模块**只认 `device_code`**。硬接就得在两个进程间同步一份 id↔code 映射。
2. **`method` 是物模型语义** —— 我们的动作标识是 `action_code`，`device_command` 有自己的状态机。两套语义并存 = 两套状态机。
3. **它是 sidecar** —— 引入即意味着「多一个进程 + 一套 RPC 契约 + 部署编排」，与「保证 device 整体模块化」直接对冲。

---

## 二、借用范围（分三层，依赖方向单向）

| 层 | 归属模块 | 内容 | 对 iot 的依赖 | 一期上不上 |
|---|---|---|---|---|
| **L1 契约** | `boheng-module-device`（主模块） | `DeviceTransport` SPI + `TransportRequest` / `TransportResult` | **零** | ✅ 上 |
| **L2 实现** | `boheng-module-device`（主模块） | `HttpTransport`(OkHttp) / `MqttTransport`(paho) / `SimulatedTransport` | **零** | ✅ 上（仿真优先） |
| **L3 适配** | `boheng-module-device-transport-iot`（独立模块，按需启用） | `IotGatewayTransport implements DeviceTransport`，内部做 `TransportRequest` ↔ `IotDeviceMessage` 转换 | 单向依赖 `iot-core` | ⏸ 按需，二期 |

**依赖方向（不可逆）**：

```
boheng-module-device-api   ←── 契约，谁都能依赖
        ↑
boheng-module-device       ←── 主模块，只认自己的 DeviceTransport SPI
        ↑
boheng-module-device-transport-iot  ←── 可选适配器，依赖 iot-core
        ↑
（芋道）iot-core / iot-gateway
```

**关键：主模块永远不知道 iot 的存在。** 哪天不用了，删掉适配器模块即可，主模块零改动。

---

## 三、借什么 / 怎么借（三档）

### 第 1 档：**抄实现，不引依赖**（一期就能用，零成本）

| 抄什么 | 用途 | 落点 |
|---|---|---|
| `IotEmqxAuthEventProtocol` + `IotEmqxAuthEventHandler` | **EMQX 的 HTTP 认证/ACL 回调报文形状**（纯协议对接，没有物模型污染） | 若选 EMQX 做 broker，直接照形状实现我们的认证接口 |
| `IotMqttUpstreamProtocol` + `IotMqttConnectionManager` | **「平台自己当 broker」的 Vert.x `MqttServer` 起法** + 连接管理 | 若现场不愿独立部署 broker，用 `EmbeddedMqttTransport` 实现 `DeviceTransport` |
| `IotMqttTopicUtils` | topic 拼装规则 | 对齐我们的 topic 五组契约 |
| `IotTcpBinaryDeviceMessageCodec` | TCP 二进制/JSON 编解码 | **二期**，若要做协议收敛 |

> 为什么是「抄」不是「引」：这几个类都直接 import `IotDeviceMessage`，引进来就把物模型一起拖进来了。抄的是**协议层怎么做**，不是**代码本身**。

### 第 2 档：**只在需要时引 `iot-core`**（纯契约，23 文件，不含 rocketmq）

| 引什么 | 前提 |
|---|---|
| `IotMessageBus` 抽象 | 仅在**确实需要跨进程事件广播**时。它的接口只有 2 个方法，我们甚至可以自己写一个等价的，不值得为它引依赖 |
| `IotDeviceMessage` | ⚠️ 必须做字段转换（`deviceId(Long)` ↔ `device_code`）。**建议不要引**，在适配器里自定义轻量 DTO |

### 第 3 档：**不引 `iot-gateway` 本体**（明确否决）

| 否决理由 | 后果 |
|---|---|
| 它是独立进程 | 多一个部署单元 + RPC 契约 + 网络故障面 |
| `rocketmq-spring-boot-starter` | 一个消息中间件被拖进主应用 |
| 接缝是物模型 | 我们的 `action_code` + 状态机被劈开 |
| RPC 回调主程序 | 双向依赖，违背「device 不可被反向依赖」 |

---

## 四、`DeviceTransport` SPI 设计（一期落地形态）

```java
package cn.boheng.frame.module.device.transport;

/**
 * 设备传输层 SPI —— device 模块自定义。
 *
 * 设计意图：把「怎么把报文送到设备、怎么把响应取回来」与「命令是什么语义」彻底解耦。
 * 传输层只管搬运，不含任何业务语义（不认 action_code、不碰状态机、不知道幂等）。
 *
 * 外部网关（芋道 iot-gateway 等）只能作为本接口的实现接入，且必须放在独立适配器模块，
 * 禁止让主模块依赖任何网关 SDK。
 */
public interface DeviceTransport {

    /** 传输类型标识，与 device_info.connection_type 对齐（HTTP / MQTT / NODE_RED / SIMULATED） */
    String type();

    /** 单次发送；异步与轮询由上层驱动编排，传输层保持无状态 */
    TransportResult send(TransportRequest request);
}
```

```java
public class TransportRequest {
    /** ★ 只认编码，不认 Long 主键 —— 跨模块寻址的唯一凭据 */
    private String deviceCode;
    private String endpointUrl;
    /** MQTT 专用 */
    private String topic;
    /** 已渲染的报文（device_action.request_template 渲染结果） */
    private String payload;
    private String contentType;
    private Integer timeoutMs;
    private Map<String, String> headers;
}

public class TransportResult {
    private boolean success;
    /** 原文，直接落 L0（device_data_raw），不做解析 */
    private String rawResponse;
    private String errorCode;
    private String errorMsg;
    private long costMs;
}
```

### 三条设计约束

1. **`deviceCode` 而非 `deviceId`** —— 守住「跨模块只认编码」，不给 Long 主键语义开口子。
2. **传输层无状态** —— 不缓存、不加锁、不管状态机。锁与幂等全在 `device_command` + `device_info` 上。
3. **响应只回原文** —— 解析是 L0→L1 的事（`device_codec`），传输层不得越权。

---

## 五、MQTT 一期选型（对应决策点 H）

| 方案 | 形态 | 适用 | 建议 |
|---|---|---|---|
| **A. 连外部 broker（EMQX）** | 平台作为 MQTT client 订阅上行 topic | 客户现场已有/愿意部署 EMQX | ✅ **一期推荐**：broker 不进主应用，模块化最干净 |
| **B. 平台自当 broker** | 主应用内起 Vert.x `MqttServer`（照抄 iot-gateway） | 现场不愿装独立 broker | ⏸ 备选；仍是 `DeviceTransport` 的一个实现，不破坏模块化 |
| **C. HTTP 直连** | OkHttp 直连设备 | 设备支持 HTTP、量少 | ✅ **一期可先走这条**（当前种子 2 台设备全仿真） |

**共同点：三者都是 `DeviceTransport` 的实现，切换不影响任何上层代码。** 这正是「不限制死」的落点。

---

## 六、与「device 模块化」的对账

| 模块化要求 | 本方案是否满足 | 依据 |
|---|---|---|
| 主模块不依赖任何网关 SDK | ✅ | 主模块只依赖自己的 `DeviceTransport` 接口 |
| 传输实现可替换、可删除 | ✅ | 删适配器模块 → 主模块零改动 |
| 协议扩展不需要改主模块 | ✅ | 新增实现只需 `implements DeviceTransport` |
| 不引入额外部署单元 | ✅ | 一期三实现全在主模块内 |
| 不把状态机劈到两个进程 | ✅ | 状态机只在 `device_command`，传输层无状态 |
| 跨模块只认编码 | ✅ | SPI 入参是 `deviceCode` |

---

## 七、来源索引

| 结论 | 证据文件（相对 `librax-module-iot/librax-module-iot-gateway/src/main/java/com/librax/lab/module/iot/gateway/`） |
|---|---|
| 独立 Spring Boot 应用 | `IotGatewayServerApplication.java` |
| RPC 回主程序 | `service/device/remote/IotDeviceApiImpl.java` |
| 物模型消息结构 | `../../iot-core/src/main/java/com/librax/lab/module/iot/core/mq/message/IotDeviceMessage.java` |
| 编解码接缝 | `codec/IotDeviceMessageCodec.java` |
| 协议条件装配 | `config/IotGatewayConfiguration.java` |
| MQTT 自当 broker | `protocol/mqtt/IotMqttUpstreamProtocol.java`（`MqttServer.create(vertx, options)`） |
| EMQX 认证事件 | `protocol/emqx/IotEmqxAuthEventProtocol.java`、`protocol/emqx/router/IotEmqxAuthEventHandler.java` |
| 消息总线抽象 | `../../iot-core/src/main/java/com/librax/lab/module/iot/core/messagebus/core/IotMessageBus.java` |
| 基座已预置依赖版本 | `boheng-frame/boheng-dependencies/pom.xml`（vertx 4.5.26 / paho 1.2.5 / okhttp 4.12.0 / rocketmq 2.3.6 / californium 3.14.0） |

---

## 八、状态

- **决策 K**：✅ 已执行（改动落进 `device.sql` + `device-v4-upgrade.sql`）
- **决策 L**：✅ 已定 —— **借协议接法，不借模块**；主模块自定义 `DeviceTransport` SPI，iot-gateway 降级为可选适配器（独立模块，按需启用）
- **决策 H**：建议一期 A（连外部 EMQX）或 C（HTTP 直连），B 作为备选；三者皆为 `DeviceTransport` 实现
