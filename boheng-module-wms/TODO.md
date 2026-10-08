# WMS 模块 · 待办清单

> **位置约定**：本文件是 WMS 模块唯一的待办真相源，随模块走。
> 每条含【问题】【影响】【建议做法】，能动手的直接给命令/文件路径。
> **关闭一条就删一条**，不要留 `~~删除线~~`（历史结论见文末「已结案」区）。
>
> 图例：🔴 阻塞 / 🟠 建议尽快 / 🟡 需决策 / 🟢 可延后（二期）
> 最后更新：2026-09-27

---

## A. 立即处理（阻塞验收）

| # | 项 | 状态 |
|---|---|---|
| A1 | **主实例需重启，新代码才生效** | 🔴 |
| A2 | **测试夹具 `INST-580689` 去留待定** | 🟡 |

### A1 主实例重启
- **问题**：48080（PID 43855）是 IntelliJ 调试进程，**无 devtools**，跑的是改动前的代码。
- **影响**：区域删除校验（`1003000002` / `1003000003`）与整套流水功能**都不会生效**，
  现在验收会以为是 bug。
- **做法**：在 IDE 里重启调试即可。重启前确认没有别的实例占 48080。
  ```bash
  lsof -nP -iTCP:48080 -sTCP:LISTEN     # 应只有 1 个 java PID
  ```

### A2 测试夹具去留
- **背景**：本会话在 `Zone_Rack_Reagent_R1C2` 建了测试夹具 `INST-580689`（96 孔板 + 24 孔），
  实测后 A1~A6 → `USED current_count=0`、根实例 → `IN_USE`，并留下 9 条流水。
  库内共 26 条实例 = `instance-0960-001`(保留, R1C1) + 该夹具 25 条。**非历史数据**。
- **三选一**（脚本：`sql/mysql/wms-cleanup-test-data.sql`）：
  1. **保留现状**（推荐）→ 正好给新流水页做演示数据，方便验收前端
  2. **只恢复 A1~A6 + 根实例状态，保留流水轨迹** → 脚本内已注释好，取消注释即可
  3. **全清** → 跑脚本主体，库回干净
- ⚠️ 若选 3，注意脚本第 3 段会把 R1C2 复位为 `FREE`——
  生成列只按 `deleted` 门控，**删实例不会自动释放槽位**，必须显式复位，否则槽位永久锁死。

---

## B. 接口一致性（小改，建议尽快，趁没有外部调用方）

| # | 项 | 状态 |
|---|---|---|
| B1 | `consume` 与 `consume-by-container` 返回值语义不一致 | 🟠 |

- **问题**：
  - `POST /wms/material-instance/consume` 返回 `List<Long>` = **流水 id**
  - `POST /wms/material-instance/consume-by-container` 返回 `List<Long>` = **被消耗的实例 id**
  - 两者签名一样，调用方极易误用（拿流水 id 当实例 id 去查实例）
- **影响**：这是**给其他模块用的**接口，一旦有下游接入后再改就是破坏性变更。
- **建议**：统一为一个结果 VO，例如
  ```java
  public class MaterialInstanceConsumeRespVO {
      private List<Long> movementIds;           // 产生的流水
      private List<Long> consumedInstanceIds;   // 实际被消耗的实例
  }
  ```
  两个接口都返回它。`consume` 的 `consumedInstanceIds` 直接取入参 `items[].instanceId`。
- **落地位置**：`controller/admin/instance/MaterialInstanceController.java` +
  `service/instance/MaterialInstanceService(+Impl)` + 前端 `api/wms/instance/index.ts`。

---

## C. 流水功能补全（二期）

| # | 项 | 状态 |
|---|---|---|
| C1 | `RESERVE` / `RELEASE` 事件已定义但无写入方 | 🟢 |
| C2 | `STATUS_CHANGE` 无独立写入路径 | 🟢 |
| C3 | `ADJUST` 冲正无专用接口 | 🟢 |
| C4 | 槽位 `capacity` 是死字段 | 🟢 |

- **C1**：预留/释放归**调度域**。等调度模块设计预留机制时，
  在 `MaterialInstanceServiceImpl` 加 `reserve()` / `releaseReserve()`，
  写 `RESERVE` / `RELEASE` 流水（填 `before/after_status` + `ref_type=TASK`）。
  `consume-by-container` 的 `includeReserved` 参数就是为它预留的（默认 `false`，不抢预留件）。
- **C2**：当前只有 `CONSUME` 会顺带带状态变化；**手工**通过 `update` 改 `instanceStatus`
  目前**不走流水**。若审计要求「状态变更全留痕」，需要在 `updateMaterialInstance` 里
  比对 `instanceStatus`，变了就补一条 `STATUS_CHANGE`。
- **C3**：错账现在只能人工 SQL 冲正。建议补
  `POST /wms/material-movement/adjust`，参数为「要冲正的流水 id + 原因」，
  自动生成一条反向 `ADJUST` 流水（**不删原记录**）。
  ⚠️ 这是唯一允许出现在 `MaterialMovementController` 的写接口，仍须遵守只增不改。
- **C4**：`wms_slot_info.capacity`（标准位数）目前恒为 1，
  因为 `uk_root_slot` 明确禁止一槽多实例（一期约定「1 个实例 = 1 个标准位」）。
  跨位实例（如一个实例占 2 个标准位）二期引入 `capacity_units` 时一起激活。

---

## D. 过期资产清理（技术债）

| # | 项 | 状态 |
|---|---|---|
| D1 | `sql/mysql/wms-dict.sql` 已过期，引用了不存在的表 | 🟠 |
| D2 | 遗留字典 4 类 14 条（旧单据域） | 🟠 |
| D3 | `sql/mysql/wms.sql`（18 表 / v1.4）是历史脚本，未标状态 | 🟡 |
| D4 | `wms_codegen.py` 不含 `wms_material_movement`（**刻意**，需在生成器里写明） | 🟢 |

### D1 `wms-dict.sql` 过期
- **问题**：脚本头注释与 INSERT 都指向 `wms_material_info.material_type` 与
  `wms_zone_material_type.material_type`——**这两张表已删**（当前库只有 6 张 `wms_` 表）。
  且查库确认 `wms_material_type` 字典**根本没入库**（`system_dict_type` 里没有该 type）。
- **影响**：谁按这个脚本执行，会得到一个无人引用的孤儿字典，并误导后续开发以为表还在。
- **建议**：二选一
  - **删掉**（若 `wms_content_def.content_type` 确定用 Java 枚举维护，不进字典）
  - **重写**为指向 `wms_content_def.content_type`，值域对齐
    `REAGENT/STANDARD/BUFFER/SAMPLE/WASTE/MEDIA/SOLVENT/OTHER`
- ⚠️ 注意：前端 wms 页面目前**只用了 `DICT_TYPE.COMMON_STATUS`**，没有任何 wms 专属字典依赖，
  所以删除是安全的。

### D2 遗留字典
- 库中残留 4 类旧单据域字典，对应表（order / receipt / shipment / inventory）已全部删除：
  | dict type | 名称 | 条数 |
  |---|---|---|
  | `wms_order_type` | WMS 单据类型 | 4 |
  | `wms_order_status` | WMS 单据状态 | 3 |
  | `wms_receipt_order_type` | 入库单类型 | 4 |
  | `wms_shipment_order_type` | 出库单类型 | 3 |
- **建议**：确认一期不做出入库单据后删除（走「系统管理 → 字典管理」删，
  或写脚本 `DELETE FROM system_dict_type/system_dict_data WHERE type IN (...)`）。
  若二期要做单据，则保留并在本文件标注「保留待用」。

### D3 `wms.sql` 标状态
- `sql/mysql/wms.sql`（61 KB，18 表，v1.4）与 `sql/mysql/wms-instance-minimal.sql`（当前 5 表）
  并存，容易误执行旧的。
- **建议**：在 `wms.sql` 头部加一行 `-- ⚠️ 历史脚本，已被 wms-instance-minimal.sql 取代，请勿执行`，
  或移到 `sql/mysql/archive/`。

### D4 生成器说明
- `wms_codegen.py` 的 `TABLES` 只含 5 张表（zone / slot / container_type / content_def / material_instance），
  **不含 `wms_material_movement`——这是刻意的**：流水表只增不改，没有 create/update/delete 端点，
  生成 CRUD 代码反而会开出绕过流水的口子。
- **建议**：在 `TABLES` 上方加注释写明这一点，避免后来者「补全」它。

---

## E. 前端验收（未做浏览器实测）

| # | 项 | 状态 |
|---|---|---|
| E1 | 流水查询页未在浏览器实测 | 🟠 |
| E2 | 槽位抽屉操作区未在浏览器实测 | 🟠 |
| E3 | 「消耗」一期不接界面（**按设计，不是待办**） | ✅ |

- **E1/E2**：`ts:check` 在 wms 目录零错误，但没跑过真实页面。建议验收清单：
  - 流水页：筛选（类型/实例编码/内容物/区域/时间）→ 表格渲染 → 点实例编码开轨迹时间线
  - 槽位抽屉：空闲槽位「上架」选未落位实例 → 占用槽位「下架」二次确认 →
    「转移」目标槽位下拉是否已过滤掉自身/停用/非 FREE/DEVICE
  - 操作后三块（槽位状态 / 当前占用 / 最近流水）是否同时刷新
  - 菜单「物料流水」是否正常挂载（keep-alive 依赖 `component_name = WmsMovement`）
- **E3**：`consume` / `consume-by-container` 一期**只提供 API**（用户口径：给其他模块用），
  不做界面入口。**这是设计决定，不是遗漏**。

---

## F. 更远的模型演进（仅登记，本期不做）

| # | 项 | 状态 |
|---|---|---|
| F1 | 跨位实例（`capacity_units`） | 🟢 |
| F2 | 整树转移的路径前缀 UPDATE | 🟢 |
| F3 | 流水「库存账面重放」定位 | 🟡 |

- **F1**：一期约定 1 实例 = 1 标准位，`slot.occupied_qty` 直接按实例数累加。
  引入跨位实例后需改为按 `capacity_units` 累加。
- **F2**：`root_slot_id` / `root_instance_id` / `instance_path` / `depth` 是**整树冗余**，
  整树移动必须用**路径前缀 UPDATE**，禁止单改某一层。
  当前 `transfer` 只搬顶层实例（子实例不落 slot，所以没触发），跨父搬迁时才需要。
- **F3**：流水目前定位是**操作日志**。若未来要承担「库存账面重放」
  （即能从流水完整重建当前库存），需要复核：`CREATE` 只记顶层、`CONSUME` 内联在实例上、
  手工改状态不走流水 —— 这三点都会让重放不完整。

---

## G. 设备域（Tecan）牵连到 WMS 的改动

> **总架构 v3**：`.workbuddy/reference/device-domain-architecture.md`
> （决策 1/3/4/5/9 + **v3 新定 10/11/12**；剩 C/D/E/F/H/I/J 待确认）
> **竞品与标准对标**：`.workbuddy/reference/device-domain-benchmark.md`（SiLA 2 / LADS / Cellario / GBG / 镁伽 / TetraScience / Allotrope）
> **Tecan 一期对接**：`.workbuddy/reference/tecan-device-integration-design.md`
> → 已被总架构**收编为其中一个驱动实现**；两者不一致时**以总架构文档为准**
> **★ 旧设计对标（2026-09-30）**：`.workbuddy/reference/device-design-prior-art-librax.md`
> → 对标已跑通的同类系统 `librax`；**设备域 DDL 有 5 处待改（见 H 区）**，且发现 `boheng-module-iot` 可能省掉接入层一整章
> ⚠️ 设备域方案未拍板前，G1 / G2 **不要动手**（表结构可能变）
>
> ✅ **2026-09-28 更新**：设备域已落地 —— 双模块建好、`device.sql` 9 表已执行到库、
> `device_info` 台账全栈 CRUD 已实现（含图片字段）。G1 / G2 现已可择期动手（表结构已定）。
>
> ★ 关键结论（会影响 WMS）：
> - 设备域 **双模块 + 两个 SPI**；一期 **9 张表**；错误码段位 `1-004-000-000`
> - **v3 接入形态**：平台**只认 HTTP + MQTT**；串口 / 私有协议由 **Node-RED** 翻译；
>   Node-RED 只是「**外置 Transport**」，**不做业务**，且**非必需**（原生支持 HTTP/MQTT 的设备直连）
> - **v3 三态调用统一建模**（`dispatch_mode` × `result_mode`）：同步 / 异步回调 / 异步轮询，
>   **同一张命令表、同一个状态机、同一套幂等键**，对调度域透明
> - 数据分 **raw / standardized / analytic** 三层，**上下文必须在摄入时挂上**
> - **仿真驱动先做**（可并行推进，不必等 Tecan 文档）
> ⚠️ 调度域将**基于 BPMN 引擎**（竞品 UniteLabs / Roche AutoLab 均用 Camunda）；
> 项目根 pom 有 `boheng-module-bpm` 占位但**目录不存在**，需从芋道上游恢复 —— 属调度域决策
> ⚠️ **基建前置**：v3 需本地起 **EMQX** 容器（当前 OrbStack 只有 mysql/redis/neo4j/portainer/it-tools）；
>   MQTT 依赖**已在 `boheng-dependencies` 预置**（`paho 1.2.5` / `vertx-mqtt`），**无需引新依赖**

| # | 项 | 状态 |
|---|---|---|
| G1 | `wms_container_type` 补 `device_labware_name`（容器类型 → 仪器板型名） | 🟡 |
| G2 | `wms_slot_info.device_code` 升级为强引用，加删除校验 | 🟡 |

- **G1**：旧 v1.4 设计里有这个字段，精简时丢了。当前 `wms_container_type` 只有
  `well_count/rows/cols`、`position_naming`、`spec_json`，**没法表达「这块板在 Tecan 里叫什么」**。
  建议**显式加列**而不是塞 `spec_json`——它要参与上机前校验（这块板能不能上这台机），塞 JSON 无法索引校验。
- **G2**：`device_code` 现在只是裸字符串，没有设备台账。设备域建 `device_info` 后，
  必须补「设备下有槽位不许删」的校验（`DEVICE_HAS_SLOTS`）——
  **这就是上次 `deleteZone` 留下悬空 `zone_code` 的同一个坑**，`zone ↔ slot` 也靠字符串引用边，
  别再犯第二次。做法照抄 `ZoneServiceImpl.validateZoneDeletable`。

---

## H. 旧设计（librax）对标结论 —— 2026-09-30 新增

> ★ 对标文档：`.workbuddy/reference/device-design-prior-art-librax.md`
> 对标对象：`/Volumes/External_1TB/Projects/librax/`（**已跑通的同类系统**，非纸面设计）
> 一句话：**设备域 DDL 参考价值有限（7 处不能学 + 2 个 bug），但它的代码和另外三个模块价值极高。**

### H1. 设备域 DDL 改强 —— ✅ 已执行（决策 K，2026-09-30）

| # | 项 | 状态 |
|---|---|---|
| H1a | `device_action` 加 **5 列**：`need_busy_check` / `status_command_code` / `request_template` / `poll_done_expr` / `poll_max_times` | ✅ 已落库<br>⚠️ 原拟名 `dispatch_mode` **已改名 `need_busy_check`**（与 `device_command.dispatch_mode` = SYNC/ASYNC 撞名，必须消歧） |
| H1b | `status_command_code`（忙闲查询命令，下发前第二道闸） | ✅ 已落库 |
| H1c | `device_command` **新增 11 列 + 1 改名 + 删 4 列**（见下表） | ✅ 已落库 |
| H1d | 幂等键**改三键分工**：`uk_command_no`（对外）+ `uk_idempotent`（传输重投）+ `uk_business(tenant_id, source_type, business_exec_id, node_id, attempt)`（业务执行事实） | ✅ 已落库并**实测通过** |
| H1e | 新增 `device_codec` 表（一期 9 → **10 张**），**只开 JSON/REGEX** | ✅ 已落库 |
| H1f | ⚠️ `device_command.device_id` / `device_code` **改可空**（生命周期从 `CREATED` 起） | ✅ 已落库 |
| H1g | ⚠️ 时间戳重排：拆出 `acquired_at` / `sent_at`，`callback_deadline` → 通用 `timeout_at` | ✅ 已落库 |
| **H1h** | ⚠️ 删 `ref_type` / `ref_id` —— 被 `source_type` / `business_exec_id` / `node_id` 取代，避免两套并行 | ✅ 已落库 |
| **H1i** | ★★ **`device_info` +4 列**：`busy_check_policy` / `concurrency_policy` / `max_inflight` / `inflight_count`（**用户本轮新增要求**） | ✅ 已落库 |

`device_command` 精确变化：

| 动作 | 列 |
|---|---|
| 新增 11 | `source_type` / `business_exec_id` / `node_id` / `attempt` / `waiting_for` / `codec_code` / `callback_token_hash` / `acquired_at` / `sent_at` / `timeout_at` / `version` |
| 改名 1 | `end_time` → `finished_at` |
| 删除 4 | `ref_type` / `ref_id`（被业务定位三列取代）/ `start_time`（≡ `create_time`）/ `callback_deadline`（被 `timeout_at` 收口） |

**H1d 实测证据**（`device_command` 空表，事务内验证后回滚，未污染数据）：

| 场景 | 结果 |
|---|---|
| 同业务节点同 attempt 第 2 条 | `ERROR 1062 Duplicate entry '0-FLOW-EXEC-1-NODE-A-1'` ✅ **拦住了** |
| 手工命令（`business_exec_id`/`node_id` = NULL）连插 2 条 | 都成功 ✅ **NULL 豁免生效** |
| 同节点 `attempt=2` | 成功落库 ✅ **重试不再被吞** |

> 执行记录：`sql/mysql/device.sql`（升 v2.0-device）+ `sql/mysql/device-v4-upgrade.sql`（增量迁移，已执行）

**H1d 为什么必须改**：流程重试时同一节点第 2 次尝试**必须是新记录**。只有单列 `idempotent_key` 时，
重试会被拦成"返回原命令"，**调度域永远看不到第 2 次执行的事实 → 重试等于没重试**。
⚠️ `business_exec_id`/`node_id` 手工触发时为空 → MySQL 唯一索引 **NULL 不参与去重**，
正好实现「手工命令不做业务幂等」，**这个性质要写进列注释**。

**H1e 为什么只开 JSON/REGEX**：`script_content` 存 Groovy/JS = **代码注入面**，
要安全得沙箱 + 类白名单 + 超时 + 禁 IO/反射，成本远高于收益；JSON/REGEX 已覆盖 90% 场景。
`unit_conversions`/`valid_range` **不放 codec** —— 属 L1 标准化层，塞进去会把解析与标准化焊死。

**H1f 为什么要改**：原设计把「选设备 + 抢占 + 下发」写在一次调用里，**抢不到设备就整条调用失败、什么都不留**。
改成**先落 `CREATED` 事实再去抢设备**后：① 抢不到也留一条可统计的「没排上」记录（资源缺口可量化）；
② 崩溃后可扫描 `CREATED` 记录恢复；③ `acquired_at` 才有落点。⚠️ 直接影响 `device_id` 必须可空。

**H1g 为什么要改**：`acquired_at → sent_at` = **排队等了多久**，`sent_at → 结束` = **设备跑了多久**。
缺第一个点则分不清「选不到空闲设备」还是「设备太慢」，运维无法归因。

**H1c 的 `waiting_for` 是新增项（比既往系统更进一步）**：
`status` 只表达设备侧事实，`waiting_for` 表达「**在等什么**」：
`SLOT_AVAILABLE`（等空闲设备）/ `DEVICE_CALLBACK` / `POLL_TICK` / `MANUAL_APPROVE` / `EXTERNAL_EVENT`。
三类超时各自挂在对应等待原因上 —— `queue_timeout` / `exec_timeout` / `callback_timeout`。

**★ 另一条比既往系统强的不变量（不改表、改行为）**：判 `TIMED_OUT` 前**先对账** ——
追加一次轮询确认真实状态再判，防「设备实际已跑完却被判失败」。与 v3 的「CALLBACK 超时先降级 POLL」同源。

> 可视化：`.workbuddy/reference/device-architecture-v4.html`（四张图 + 吸收/保留/不采纳清单）

### H2. ✅ 已评估：`boheng-module-iot` 借用边界（决策 L，2026-09-30）

**结论：借「协议接法」，不借「模块 + 物模型 + 部署形态」。**
评估全文：`.workbuddy/reference/device-transport-boundary.md`

| 判断 | 依据 |
|---|---|
| ❌ **不整体启用** `boheng-module-iot` | 它是 **sidecar**（独立 Spring Boot 应用 + `RestTemplate` RPC 回调主程序）→ 多进程 + 多契约，与「device 模块化」直接对冲 |
| ❌ **不引 `iot-gateway` 依赖** | 接缝是物模型 `IotDeviceMessage`（`deviceId` 是 **Long 主键**，我们跨模块只认 `device_code`）；且拖 `rocketmq-spring-boot-starter` |
| ✅ **device 自定义 `DeviceTransport` SPI** | 主模块只依赖自己的接口；外部网关只能作为它的**实现**接入 |
| ✅ **抄实现、不引依赖的三块** | EMQX 认证事件报文形状 / Vert.x `MqttServer`（平台自当 broker）起法 / MQTT topic 拼装 |
| ✅ **MQTT 一期** | 先 HTTP 直连（当前 2 台设备全仿真）；真机接入时优先连外部 EMQX；三方案皆为 `DeviceTransport` 实现，**选哪个都不锁死架构** |

依赖方向（不可逆）：
`device-api` ← `boheng-module-device`（含 SPI + Http/Mqtt/Simulated 三实现）← `boheng-module-device-transport-iot`（可选适配器，按需启用）

> v3 原担心的四件事（自写 HttpTransport / 起 EMQX / Node-RED 必要性 / 自己定 MQTT 契约）**结论不变**：
> 仍然自己做，但**都收进 `DeviceTransport` 的实现里**，不引入任何外部进程。

### H3. 待评估：librax flow 引擎 = 我们的调度域

`librax-module-flow`（145 文件）已完整实现：`DagScheduler` / `StepStateMachine` / `ExecutionStateMachine` /
`TimeoutWatchdog` / **`RecoveryScanner`** / `RetryPolicyRegistry`+`ExceptionEngine` /
`ExecutionEventPublisher`（7 种事件）/ `ExecutionContextManager`+`OutputMappingResolver` /
`StepCallbackService` / **`StandaloneExecutionService`**（单步调试）/ `LiteFlowController` /
`PipelineGraphCache`+`Validator`。节点类型 `StepTypeEnum{INSTRUMENT, COMPUTE, CONDITION, WAIT, NOTIFY, SAMPLE_SPLIT}`。

→ 状态：🔴 **列为移植候选**。尤其 `RecoveryScanner`（崩溃恢复）与 `StandaloneExecutionService` 是我们架构里没细想的。
⚠️ 迁移时要改：librax 里 flow **Maven 硬依赖** device（`InstrumentStepExecutor` import `DeviceGateway`），
我们要改成**面向 `device-api` 契约**编程（方向本身没错，flow → device）。

### H4. 待评估：**样本域**（我们完全没有）

`librax-module-lab`（75 文件）：`lab_sample_info/_step/_result/_event/_relation` + 生命周期/追溯/结果处理三类 Service
+ `SampleBindHook` + `SampleSplitStepExecutor` + `SampleFlowIntegrationListener`。

→ 我们 WMS 只有「板 / 孔 / 容器」，**缺"样本从哪来、属于哪个实验、结果归谁"**。
→ 状态：🔴 **待评估**。前置问题：`lab_sample_info` ↔ `wms_material_instance` 如何对齐。

### H5. 术语撞车警告（★ 必须写进架构文档）

| 概念 | librax | boheng |
|---|---|---|
| **配置**：这台设备能被怎么启动 | `command` | `action` |
| **记录**：一次执行的事实 | `execution` | `command` |

**同一个作者在两套系统里对 `command` 用了相反语义。** 不改名（成本大于收益），但必须显式写死术语表，
否则二期对接 / 数据迁移时必踩。

### H6. 已否决（勿再讨论，除非有新证据）

| # | 不采纳 | 理由 |
|---|---|---|
| 1 | 无 `tenant_id` | 它继承 `BaseDO` 非 `TenantBaseDO`；我们多租户必须带 |
| 2 | 平台直连 TCP/SERIAL/SDK | 冲突 v3「只开放 HTTP+MQTT」；`sdk_class` 反射加载厂商 SDK 污染进程 |
| 3 | 执行态只放 Redis | `device:state:*` / `device:exec:*` **24h 过期**，重启或过期即事实丢失 |
| 4 | 先建字段后补实现 | `DeviceCommandDO` 有 **8 个僵尸字段**从没被任何代码读过 |
| 5 | ServiceImpl 跨模块 import controller VO | `DeviceCommandServiceImpl:10` 反向依赖 lab 模块；我们双模块强制方向正好防这个 |
| 6 | 自定义 `DeviceException extends RuntimeException` | 项目规范是 `exception(ErrorCode)` |
| 7 | `base_url_backup` 运行态写回配置表 | 配置被运行时改写，切 SIM 后崩溃/忘切回即状态混乱。**但吸收**：切 SIM 必须可审计 |
| 8 | 型号表 `capabilities` JSON 大字段 | 我们有独立 `device_action`+`device_property` 表，可查询/可约束/前端可渲染 —— **别退回 JSON** |

### H7. ★ 两个反面教材（我们的实现里不许出现）

1. **非原子设备选择**：`DeviceSelector.select` = `selectEnabledByType()` → `filter(isIdle)` → `findFirst()`，
   而 `markBusy` 是普通 `putAll`。**check-then-act 无锁** → 多实例并发下两台仪器会收到同一条命令。
   它的 `max_concurrent` 字段**形同虚设**（`select` 里根本没读）。
   → 我们的台账 CAS 抢占**必须写成原子 UPDATE**：
   ```sql
   UPDATE device_info SET current_command_id = ?
    WHERE device_code = ? AND current_command_id IS NULL AND deleted = b'0';
   -- affected rows == 1 才算抢占成功
   ```
2. **`@Scheduled(fixedDelay = 10_0000)`** —— 下划线是数字分隔符，实际 **100 秒**（注释却写"每 10 秒"）。
   我们写常量用 `Duration.ofSeconds(10).toMillis()` 或明确注释。

### H8. 值得抄的一段：超时优先级链

```
timeout = 步骤级 timeout_ms → 流程级 default_timeout_ms → 全局兜底 60_000ms
WAITING 单独一个 300_000ms（等回调）—— 必须与 RUNNING 分开
超时后行为 = 等同于执行失败 → 走正常重试 / DEAD 链路（别为超时单独写一套处理）
```
→ 我们的落点：`device_action.estimate_duration_ms × 系数` → `device_info` 默认 → 全局兜底。
每个动作有自己的合理阈值（读板 30s vs 孵育 30min），不需要一张全局表。

---

## 已结案（曾待定，已有结论，勿再讨论）

| 项 | 结论 | 日期 |
|---|---|---|
| 流水粒度：单条事件 vs 双条借贷 | **单条事件**（MOVE 带 from/to），批量用 `operation_id` 归组 | 2026-09-26 |
| 下架后实例怎么处理 | **不引入新状态**：位置（`root_slot_id`）与状态（`instance_status`）正交 | 2026-09-26 |
| 下架是否强制填原因 | **不强制**（`remark` 可空） | 2026-09-26 |
| 流水查询入口 | **独立菜单 + 槽位内嵌，两处都要** | 2026-09-26 |
| 转移是否要记录 | **必须记**（否则「下架+上架」与「一次转移」无法区分；温控链追溯硬需求） | 2026-09-26 |
| 消耗接口是否接前端 | **不接**，一期只给其他模块用 API | 2026-09-26 |
| 消耗计量方式 | **三选一**：volUl / qty / **都不传 = 整件消耗** | 2026-09-26 |
| `wms_material_info` / `wms_material_batch` / `wms_zone_material_type` / `wms_inventory` 去留 | **全部退场**（已从库中删除，当前 6 张 `wms_` 表） | 2026-09-26 |
| 区域删除留下悬空 `zone_code` | **已修**：`ZoneServiceImpl.validateZoneDeletable`，`1003000002` / `1003000003` | 2026-09-26 |
| 槽位一槽多实例 | **禁止**，`uk_root_slot` 唯一索引（含 `deleted` 门控的生成列） | 2026-09-26 |
| 设备域 v4 五处改动是否现在落 DDL（决策 K） | **现在落**（趁 `device_command` 未铺）。`device.sql` 升 v2.0-device + `device-v4-upgrade.sql` 已执行并实测通过 | 2026-09-30 |
| 传输层是否复用芋道 `iot-gateway`（决策 L） | **借「协议接法」，不借「模块 + 物模型 + 部署形态」**。device 自定义 `DeviceTransport` SPI，iot-gateway 降级为可选适配器（独立模块） | 2026-09-30 |
| **仪器不返回空闲状态怎么办** | **必须有开关，不许写死** —— `device_info.busy_check_policy`(`AUTO`/`ALWAYS`/`NEVER`) × `device_action.need_busy_check` | 2026-09-30 |
| **仪器自带本地队列怎么办** | **必须有开关，不许写死** —— `device_info.concurrency_policy`(`EXCLUSIVE`/`DEVICE_QUEUED`/`PLATFORM_QUEUED`) + `max_inflight` | 2026-09-30 |
| 忙闲预检字段是否叫 `dispatch_mode` | **不叫**。`dispatch_mode` 已占给 `device_command`(SYNC/ASYNC)；预检命名 `need_busy_check`，避免与 librax 撞名 | 2026-09-30 |

---

## 附：相关文件索引

| 用途 | 路径 |
|---|---|
| 流水设计方案（含验证记录） | `.workbuddy/reference/wms-movement-ledger-design.md` |
| 流水建表 | `sql/mysql/wms-movement.sql` |
| 流水菜单与权限 | `sql/mysql/wms-menu-movement.sql` |
| 测试数据清理 | `sql/mysql/wms-cleanup-test-data.sql` |
| 槽位唯一索引 | `sql/mysql/wms-instance-slot-unique.sql` |
| 槽位占用修复 | `sql/mysql/wms-slot-occupancy-fix.sql` |
| 核验与排障套路（Skill） | `~/.workbuddy/skills/boheng-wms-verify/SKILL.md` |
