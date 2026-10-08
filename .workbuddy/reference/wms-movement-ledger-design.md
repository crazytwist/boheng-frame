# WMS 物料流水（上架 / 下架 / 转移 / 消耗）设计方案

> 状态：**已落地（v1.5）** — 表已建、后端已实现并端到端实测通过、前端流水页与槽位操作区已完成
> 关联：
> - `sql/mysql/wms-movement.sql`（流水表，已执行）
> - `sql/mysql/wms-menu-movement.sql`（菜单与权限点，已执行）
> - `sql/mysql/wms-instance-minimal.sql`（v1.4 五表）
> - `.workbuddy/reference/lab-wms-ddl-v1.0-original.sql`（`lab_inventory_history` 原设计）
> 日期：2026-09-26

---

## 〇、落地摘要

| 项 | 结果 |
|---|---|
| 流水表 | `wms_material_movement`（库中第 6 张 `wms_` 表 = v1.4 精简 5 表 + 本表） |
| 事件粒度 | **单条事件**（MOVE 带 from/to），批量明细用 `operation_id` 归组 |
| 事件类型 | 9 种：`CREATE` / `PUT_IN` / `TAKE_OUT` / `MOVE` / `CONSUME` / `STATUS_CHANGE` / `RESERVE` / `RELEASE` / `ADJUST` |
| 写接口 | 4 个：`put-in` / `take-out` / `transfer` / `consume` + `consume-by-container` |
| 读接口 | 4 个（纯 GET，无写接口）：`page` / `list-by-instance` / `list-by-root-instance` / `list-by-slot` |
| 关键约束 | `update` **拒绝**修改 `rootSlotId`（否则绕过流水），错误码 `1003004007` |
| 只增不改 | Service 只暴露 insert / select，错账走 `ADJUST` 冲正 |

---

## 一、先厘清一个前提：上下架/转移其实是**同一个字段的变更**

当前 v1.4 模型是「**位置即实例**」——槽位的占用状态不是独立记账，而是由
`wms_material_instance.root_slot_id` 反查推导出来的（`refreshSlotOccupancy`）。

所以三种操作在数据层面是同构的：

| 操作 | `root_slot_id` 变化 | 业务语义 | 是否离开在架状态 |
|---|---|---|---|
| 上架 PUT_IN | `NULL → S` | 入位 | 否（进入体系） |
| 下架 TAKE_OUT | `S → NULL` | 出位 | 是（离开在架状态） |
| 转移 MOVE | `S1 → S2` | 换位 | 否（仍在体系） |
| 创建（带槽位） | `NULL → S` | 诞生并入位 | 否 |

**结论：流水本质上就是 `root_slot_id` 的变更日志。** 这决定了流水表的主体应该是
**实例**，而不是原设计 `lab_inventory_history` 的「槽位 × 批次 × 数量」账。

> 原设计 `lab_inventory_history` 是**数量账**视角（before_qty/change_qty/after_qty +
> `uk_detail_change` 幂等键 + 入/出/移库单三套单据）。当前一期没有 `lab_inventory`
> 聚合表、也没有单据体系，直接照搬会引入一堆无源字段。本方案保留其**只增不改、
> 幂等键、operator_type、单据关联**四个优秀设计，去掉数量账包袱。

---

## 二、核心设计决策

### 决策 1：流水主体 = 实例（不是槽位 × 批次）

| 选项 | 说明 | 评价 |
|---|---|---|
| **A. 实例为主** ✅ | 一行 = 一个实例的一次变更 | 与「位置即实例」同构；能直接回答「这盒东西去过哪」 |
| B. 槽位×批次聚合为主 | 一行 = 某位置某批次的数量增减 | 一期没有数量库存概念，主体也不是实物 |

### 决策 2：一次操作写几条记录

| 选项 | 条数 | 优点 | 缺点 |
|---|---|---|---|
| **A. 单条事件**（MOVE 带 from/to）✅ | 1 | 一个事实一条记录；实例轨迹查询直白；整树转移只 1 条 | 按槽位统计进出需 `from=? OR to=?` |
| B. 双条借贷（MOVE_OUT + MOVE_IN） | 2 | 槽位维度进出明细天然清晰；SUM 即账面 | 记录翻倍；两条之间会有「下了没上」中间态；整树转移刷屏（1 载体 + 96 孔位 = 194 条） |
| C. 操作头 + 明细 | 1+N | 天然有「一次操作」概念，支持批量/整树 | 多一张表，一期偏重 |

**采用 A**，理由：
1. 流水首先要回答的是「这个物料去过哪里」，A 直接命中
2. 槽位维度的进出统计，用 `from_slot_id` / `to_slot_id` 双索引 + `UNION` 即可
3. 一期无数量账面，B 的「借贷平衡」优势用不上；而 B 的中间态问题会污染合规追溯
4. **用 `operation_id` 吸收 C 的优点**——一次批量操作的所有明细共享一个
   `operation_id`，既能归组又不加表

### 决策 3：转移**必须记录**

必须记，四条硬理由：

1. **不记就出现断层**：A 槽位一条 TAKE_OUT、B 槽位一条 PUT_IN，事后无法判断
   这是「一次转移」还是「先下架、又上架了另一件东西」
2. **温控链追溯**：4℃ 冰柜 → 室温台面 的移动在 GLP/GMP 下必须留痕
3. **回答不了「上次在哪」**：出问题回溯时，看不到历史位置就没有定位线索
4. **区分「离开/未离开体系」**：下架是离开在架状态、转移是没离开。混淆会导致
   「在库时长」等统计算错

### 决策 4：只增不改（append-only）

流水不提供任何 update / delete 接口。操作错了走**反向冲正流水**（compensating entry），
不删原记录。这是审计表的标准做法。

> 实现上仍继承 `TenantBaseDO`（保持与其余表一致、走租户拦截器），
> 但 Service 层**只暴露 insert / select**，不写 update / delete。

### 决策 5：下架**不引入新状态**（对「下架后实例怎么处理」的回答）

「**在不在架上**」是**位置**（`root_slot_id`），「**能不能用**」是**状态**（`instance_status`），
两者**正交**，不用状态去表达位置。

这一条也反过来约束了实现：`takeOut` 的 `afterStatus` 是**可选**字段——不传就保持原状态。
下架后实例只是 `root_slot_id` 变空，`instance_status` 不动；它是临时取出还是报废出库，
由 `instance_status` 与流水 `remark` 表达，不需要新增「已下架」这类状态。

**下架不强制填原因**（`remark` 可空）——符合用户确认的口径。

---

## 三、表设计：`wms_material_movement`

> 以下与 `sql/mysql/wms-movement.sql` **完全一致**（含 `int` / `decimal(10,2)` 精度）。

```sql
CREATE TABLE `wms_material_movement` (
  `id` bigint NOT NULL COMMENT '主键',
  `movement_type` varchar(32) NOT NULL COMMENT 'CREATE 建账/PUT_IN 上架/TAKE_OUT 下架/MOVE 转移/CONSUME 消耗/STATUS_CHANGE 状态变更/RESERVE 预留/RELEASE 释放预留/ADJUST 冲正',
  `biz_source` varchar(32) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 手工/TASK 调度任务/DEVICE 设备/IMPORT 导入',
  `operation_id` varchar(64) NULL COMMENT '操作批次号(一次批量操作的多条明细共享)',

  `instance_id` bigint NOT NULL COMMENT '实例编号(可以是根实例也可以是子实例)',
  `instance_code` varchar(64) NOT NULL COMMENT '实例编码(冗余，编码寻址)',
  `root_instance_id` bigint NULL COMMENT '根实例编号(定位本条流水属于哪个顶层容器)',
  `container_type_code` varchar(64) NULL COMMENT '容器类型编码快照',
  `content_def_code` varchar(64) NULL COMMENT '内容物编码快照(便于按物质筛流水)',
  `content_type` varchar(32) NULL COMMENT '内容物类型快照',

  `from_slot_id` bigint NULL COMMENT '源槽位编号(NULL=上架/建账)',
  `from_slot_code` varchar(64) NULL,
  `from_zone_code` varchar(64) NULL,
  `to_slot_id` bigint NULL COMMENT '目标槽位编号(NULL=下架/消耗)',
  `to_slot_code` varchar(64) NULL,
  `to_zone_code` varchar(64) NULL,

  `before_status` varchar(32) NULL COMMENT '变更前实例状态',
  `after_status` varchar(32) NULL COMMENT '变更后实例状态',

  `before_qty` int NULL COMMENT '变更前数量(个)，离散计数类填',
  `change_qty` int NULL COMMENT '变更量(个，正为增负为减)',
  `after_qty` int NULL COMMENT '变更后数量(个)',
  `before_vol_ul` decimal(10,2) NULL COMMENT '变更前体积(μL)，液体类填',
  `change_vol_ul` decimal(10,2) NULL COMMENT '变更量(μL，负为消耗)',
  `after_vol_ul` decimal(10,2) NULL COMMENT '变更后体积(μL)',

  `ref_type` varchar(32) NULL COMMENT 'TASK 调度任务/ORDER 单据/API 外部调用/CHECK 盘点',
  `ref_id` varchar(64) NULL COMMENT '关联业务编号(跨模块只认编码，不建外键)',
  `ref_no` varchar(64) NULL COMMENT '关联业务单号',
  `idempotent_key` varchar(128) NULL COMMENT '幂等键(为空不参与唯一约束)',

  `operator` varchar(64) NULL COMMENT '操作人(手工=登录用户；调度/设备=system 或设备编码)',
  `operator_type` varchar(16) NOT NULL DEFAULT 'USER' COMMENT 'USER 人工/DEVICE 设备/AUTO 自动',
  `operate_time` datetime NOT NULL COMMENT '业务操作时间(≠入库时间，支持补录)',
  `remark` varchar(512) NULL COMMENT '备注/操作原因(下架不强制)',

  -- 框架审计字段：creator / create_time / updater / update_time / deleted / tenant_id
  PRIMARY KEY (`id`),
  INDEX `idx_instance`(`instance_id`,`operate_time`),
  INDEX `idx_root_instance`(`root_instance_id`,`operate_time`),
  INDEX `idx_from_slot`(`from_slot_id`,`operate_time`),
  INDEX `idx_to_slot`(`to_slot_id`,`operate_time`),
  INDEX `idx_operation`(`operation_id`),
  INDEX `idx_content`(`content_def_code`,`operate_time`),
  INDEX `idx_operate_time`(`operate_time`),
  INDEX `idx_movement_type`(`movement_type`),
  INDEX `idx_ref`(`ref_type`,`ref_id`),
  UNIQUE INDEX `uk_idempotent`(`tenant_id`,`idempotent_key`)
) COMMENT = '物料流水(只增不改：上架/下架/转移/消耗/状态变更)';
```

### 字段设计说明

| 设计点 | 理由 |
|---|---|
| **不存 `movement_no`** | 人类可读单号需要分布式序列，一期收益低。`id` + `operate_time` 足够 |
| **有 `operation_id` 而非单号** | 一次「批量上架/批量消耗 N 个」给同一个 `operation_id`，比流水号更实用 |
| **`operate_time` 与 `create_time` 并存** | 支持补录/回填历史场景，业务时间 ≠ 入库时间 |
| **冗余 `content_def_code` / `from_zone_code` / `to_zone_code`** | 要能「按物质」「按区域」直接筛流水，避免 join 实例表/槽位表 |
| **数量组用 `int`、体积组用 `decimal(10,2)`** | 数量是离散件数（离心管根数），体积是连续量（μL），精度需求不同 |
| **`idempotent_key` 唯一索引** | 外部模块调用 / 调度重试时防重复过账 |
| **`before_status` / `after_status`** | 状态变更（如 AVAILABLE→USED）也要留痕，与位置变更共用一张表 |

### 与逻辑删除唯一约束的兼容

和 `wms_material_instance.uk_root_slot` 不同，`idempotent_key` **允许为空即可**
（NULL 不参与唯一约束），**不需要生成列**——因为流水表永不逻辑删除，
不存在「逻辑删掉的行占着唯一键」的问题。

### 幂等键的派生规则（批量场景关键）

一次请求的 N 条明细共享同一个 `operationId`，但 `idempotent_key` 必须**逐条互异**，
否则第二条起就会撞 `uk_idempotent`。规则：

```
第 index 条明细的幂等键 = 调用方传入的 idempotentKey + "#" + index
```

实测确认：`demo-consume-001` 派生出 `demo-consume-001#0` ~ `#3`，四条各自独立落库；
重复投同一请求则第一条就撞键，抛 `1003005008` 并**整体回滚**（不会半执行）。

---

## 四、事件类型与 `root_slot_id` 的映射

```
              ┌─────────────┐
   CREATE ───▶│  NULL 未落位 │
   (无槽位)    └──────┬──────┘
                     │ PUT_IN 上架
                     ▼
                ┌──────────┐   MOVE 转移   ┌──────────┐
                │  S1 已落位 │─────────────▶│  S2 已落位 │
                └─────┬────┘◀─────────────└──────────┘
                      │ TAKE_OUT 下架
                      ▼
              ┌─────────────┐
              │  NULL 未落位 │
              └─────────────┘
                     │ CONSUME 消耗（子实例，不影响顶层槽位占用）
                     ▼
              ┌─────────────┐
              │    USED     │
              └─────────────┘
```

| 流水类型 | from_slot | to_slot | before/after_status | 触发时机 |
|---|---|---|---|---|
| `CREATE` | 空 | 可为空 | — → 初始态 | 创建**顶层**实例（仅一层，不刷子实例） |
| `PUT_IN` | 空 | S | 不变 | 未落位 → 落位 |
| `TAKE_OUT` | S | 空 | 可选（不传则不变） | 落位 → 未落位 |
| `MOVE` | S1 | S2 | 不变 | 落位 → 另一槽位 |
| `CONSUME` | 空 | 空 | 有值 | 消耗，填数量/体积 + 状态变化 |
| `STATUS_CHANGE` | 空 | 空 | 有值 | 纯状态变更 |
| `RESERVE` / `RELEASE` | 空 | 空 | 有值 | 调度域预留 / 释放预留（预留占位，本期未接） |
| `ADJUST` | 见明细 | 见明细 | 见明细 | 错账冲正 |

---

## 五、各场景记录示例（全部为实测过的真实形态）

### 5.1 创建一个顶层实例并直接落位
```
CONSUME?  no
CREATE   instance=INST-580689  from=—  to=Zone_Rack_Reagent_R1C2
```
**只记 1 条**。且只有**顶层实例**记 CREATE —— 一块 96 孔板不会因为建了 96 个孔实例
就刷出 97 条流水（这是实测中刻意规避的刷屏点）。

### 5.2 把 R1C2 的实例移到 R3C1
```
MOVE   instance=INST-580689  from=Zone_Rack_Reagent_R1C2  to=Zone_Rack_Reagent_R3C1
```
**只记 1 条**。这是与「双条借贷」的核心差异点。实测返回 `2103865798156787714`，
两边槽位状态同步刷新（源回 FREE、目标转 OCCUPIED）。

### 5.3 整盒转移（1 载体 + 24 孔位）
```
MOVE   instance=ROOT  from=R1C2  to=R3C1
```
**只记顶层 1 条**。子实例跟着动，同属一个事务，不单独出流水。

### 5.4 批量消耗：24 根离心管用掉 4 根（**用户原始需求**）
```
POST /consume-by-container  { rootInstanceId, count: 4, strategy: "POSITION" }
→ 返回被消耗的 4 个子实例 id：[A1, A2, A3, A4]

operation_id=3faf794cd81641bdbe4ffba18504f71e
CONSUME  INST-580689-A1  before_qty=1  change_qty=-1  after_qty=0  A→USED  idempotent_key=demo-consume-001#0
CONSUME  INST-580689-A2  ...                                                          #1
CONSUME  INST-580689-A3  ...                                                          #2
CONSUME  INST-580689-A4  ...                                                          #3
```
- **4 条流水共享一个 `operation_id`**，可还原「这是一次领用」
- 每个子实例 `instance_status` 同步转 `USED`、`current_count` 归 0
- 再领 2 根时**接着挑 A5、A6**（`POSITION` 策略按位置码自然序，已消耗的不会被重复挑）

### 5.5 操作录错了要撤销
```
MOVE     instance=I1  from=R1C1  to=R2C3   ← 错误操作
ADJUST   instance=I1  from=R2C3  to=R1C1   ref_no=MOV-50  remark="冲正"
```
**不删 MOV-50**。

---

## 六、后端实现（已落地）

### 6.1 落位操作从 `update` 中**剥离**（关键一致性设计）

原来 `updateMaterialInstance` 能直接改 `rootSlotId`——这会**绕过流水**。已收敛：

```java
if (!Objects.equals(oldInstance.getRootSlotId(), updateReqVO.getRootSlotId())) {
    throw exception(MATERIAL_INSTANCE_SLOT_ONLY_VIA_OPERATION);   // 1003004007
}
updateObj.setRootSlotId(oldInstance.getRootSlotId());
updateObj.setRootSlotCode(oldInstance.getRootSlotCode());
updateObj.setParentInstanceId(oldInstance.getParentInstanceId());
updateObj.setInstancePath(oldInstance.getInstancePath());
updateObj.setRootInstanceId(oldInstance.getRootInstanceId());
```

实测：`PUT /update` 带不同 `rootSlotId` → `1003004007 落位请使用上架 / 下架 / 转移接口，
更新接口不接受槽位字段（否则会绕过物料流水）`。

### 6.2 接口清单（实际路径与权限点）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/wms/material-instance/put-in` | 上架 `{instanceId, slotId, remark, ...}` | `wms:material-instance:put-in` |
| POST | `/wms/material-instance/take-out` | 下架 `{instanceId, afterStatus?, remark}` | `wms:material-instance:take-out` |
| POST | `/wms/material-instance/transfer` | 转移 `{instanceId, targetSlotId, remark}` | `wms:material-instance:transfer` |
| POST | `/wms/material-instance/consume` | 按实例精确消耗（明细列表） | `wms:material-instance:consume` |
| POST | `/wms/material-instance/consume-by-container` | 按容器领 N 件（自动挑子实例） | `wms:material-instance:consume` |
| GET | `/wms/material-instance/list-unplaced` | 未落位的顶层实例（上架弹窗选源） | `wms:material-instance:query` |
| GET | `/wms/material-movement/page` | 流水分页（类型/实例/内容物/槽位/区域/时间） | `wms:movement:query` |
| GET | `/wms/material-movement/list-by-instance` | 某实例的轨迹时间线 | `wms:movement:query` |
| GET | `/wms/material-movement/list-by-root-instance` | 某顶层容器的全部子流水 | `wms:movement:query` |
| GET | `/wms/material-movement/list-by-slot` | 某槽位的进出明细（含 limit 上限夹紧） | `wms:movement:query` |

> ⚠️ `MaterialMovementController` **只有 GET，无任何写接口**——这是「只增不改」在
> 接口层的落地形态。所有写入只能由实例服务在事务内调 `recordMovement` 触发。

### 6.3 消耗语义：**三选一**（用户第 5 点需求的核心）

```java
boolean byVolume = volUl != null && volUl.compareTo(BigDecimal.ZERO) > 0;
if (byVolume) {
    // 传了 volUl → 按体积扣减
    afterVol = beforeVol.subtract(volUl);
} else {
    // current_count 为空时视为单件实物（一期约定 1 个实例 = 1 件）
    beforeQty = instance.getCurrentCount() == null ? 1 : instance.getCurrentCount();
    int consumeQty = qty == null ? beforeQty : qty;   // 两者都不传 → 整件消耗
    afterQty = beforeQty - consumeQty;
}
```

| 传参 | 语义 | 典型场景 |
|---|---|---|
| `volUl` | 按体积扣减 | 500 mL 缓冲液用掉 200 mL |
| `qty` | 按数量扣减 | 一个实例里装了 10 片，用掉 3 片 |
| **都不传** | **整件消耗**（`after` 归零） | 24 根离心管用掉 4 根 → 每个子实例各整件消耗 |

> 最初设计只允许「qty / volUl 二选一」，实测 `consume-by-container` 直接报
> `1003005003 必须指定数量 qty 或体积 volUl`——因为「整件消耗」本就是最典型场景。
> 已修正为三选一，并删除错误码 `INSTANCE_CONSUME_MEASURE_MISSING`。

**状态推导**：剩余量归零 → `USED`；仍有剩余 → `IN_USE`（调用方可用 `afterStatus` 覆盖）。

### 6.4 `consume-by-container` 的挑件规则

| 参数 | 作用 |
|---|---|
| `rootInstanceId` | 顶层容器（如 96 孔板） |
| `count` | 要领几件 |
| `strategy` | `POSITION`（默认，按位置码自然序 A1 < A2 < A10）/ 其他值则按 id 先后 |
| `afterStatus` | 消耗后状态，默认 `USED` |
| `includeReserved` | 默认 `false`——**不抢已被步骤预留（RESERVED）的件**；置 `true` 才纳入候选 |

候选不足时抛 `1003005005 容器【{}】可用子实例不足：需要 {} 个，实际可用 {} 个`；
容器无子实例抛 `1003005006`。

### 6.5 事务边界

「改 `root_slot_id`」+「写流水」+「重算槽位状态」在**同一个 `@Transactional`** 内。
且 **先写流水再改位置**——这样幂等键冲突会 fail-fast，整个事务回滚，不会出现
「位置变了但没流水」或「流水写了位置没动」。

### 6.6 相关错误码

| 错误码 | 含义 |
|---|---|
| `1003004007` | 落位请使用上架/下架/转移接口，update 不接受槽位字段 |
| `1003005000` | 实例未落位，无法执行该操作 |
| `1003005001` | 实例已落位在槽位【{}】，请使用转移 |
| `1003005002` | 目标槽位与当前槽位相同，无需转移 |
| `1003005004` | 可消耗量不足：本次需要 {}，当前剩余 {} |
| `1003005005` | 容器可用子实例不足 |
| `1003005006` | 容器没有子实例，无法按容器消耗 |
| `1003005007` | 实例当前状态为【{}】，不允许消耗 |
| `1003005008` | 重复的操作请求（幂等键【{}】已处理过） |

---

## 七、前端实现（已落地）

### 7.1 槽位抽屉（`views/wms/space/index.vue`）操作区

```
┌─ 槽位详情 · Zone_Rack_Reagent_R1C1 ──────────┐
│  槽位类型   STORAGE                          │
│  使用状态   ● 占用                            │
│  已占用量   1                                │
│  ─────────────────────────────────────────    │
│  【当前占用】                                  │
│   INST-580689   96 孔板 / AVAILABLE           │
│  ─────────────────────────────────────────    │
│  【操作】                                     │
│   [ 下架 ]   [ 转移 ]      ← 占用时显示       │
│   [ 上架 ]                  ← 空闲时显示      │
│  ─────────────────────────────────────────    │
│  【本槽位最近流水】                            │
│   22:42  PUT_IN     INST-580689  → 入位       │
│   15:19  CREATE     INST-580689  → 建账即入位 │
└──────────────────────────────────────────────┘
```

- 抽屉宽度 380px → **460px**
- 空闲槽位：`上架` → 弹窗选「未落位的顶层实例」（`filterable` 可搜）
- 占用槽位：`下架` → 二次确认；`转移` → 选目标槽位（`transferTargets` 已过滤掉
  自身 / 停用 / 非 FREE / DEVICE 类槽位）
- 停用槽位：禁上架并给出提示
- 抽屉底部内嵌该槽位最近 10 条流水（`list-by-slot`）
- 操作成功后 `refreshAfterOperation()` 统一刷新槽位、占用实例、流水三块

### 7.2 独立「流水查询」菜单（`views/wms/movement/index.vue`）

> 用户口径：「**单开一个 且 在槽位那边也要有**」——两处都做了。

- 页面 `defineOptions({ name: 'WmsMovement' })`（与菜单 `component_name` 一致，保证 keep-alive）
- 搜索：流水类型 / 实例编码 / 内容物编码 / 区域 / 业务来源 / 操作时间范围
- 表格：操作时间 · 类型 tag · 实例编码（可点开轨迹）· 内容物 · 位置变化（from → to）·
  数量体积变化 · 状态变化 · 来源 · 操作人 · 详情
- 两个抽屉：**详情**（全字段 descriptions）+ **实例轨迹**（el-timeline）
- 枚举中文名来自 `api/wms/movement/index.ts` 的 `MOVEMENT_TYPE_LABEL` / `_TAG`，
  避免页面里硬编码

### 7.3 实例表单（`views/wms/instance/InstanceForm.vue`）

编辑态**禁用槽位下拉**，改为只读输入 + 提示：

> 落位改动请在「空间管理」页面对槽位执行上架 / 下架 / 转移，以便记录物料流水

这样 UI 与后端 `1003004007` 拦截形成前后呼应。

### 7.4 消耗接口一期**不接前端**

> 用户口径：「消耗的接口 目前前端还没接 **给其他模块用的**」

因此 `consume` / `consume-by-container` 只提供 API + 权限点，不做界面入口。

---

## 八、菜单与权限点（`sql/mysql/wms-menu-movement.sql`，已执行）

| menu id | 名称 | 类型 | 权限标识 |
|---|---|---|---|
| 12756 | 物料流水 | 菜单 `wms/movement/index` | — |
| 12757 | 流水查询 | 按钮 | `wms:movement:query` |
| 12758 | 物料上架 | 按钮 | `wms:material-instance:put-in` |
| 12759 | 物料下架 | 按钮 | `wms:material-instance:take-out` |
| 12760 | 物料转移 | 按钮 | `wms:material-instance:transfer` |
| 12761 | 物料消耗 | 按钮 | `wms:material-instance:consume` |

- 12758~12761 挂在**物料实例菜单（12751）**下
- 授权 `role_id=2`
- 脚本幂等（先 DELETE 再 INSERT）
- 执行后**必须清 Redis 权限缓存**：`permission_menu_ids:*` / `menu_role_ids:*` / `user_role_ids:*`

---

## 九、验证记录（临时第二实例 48081，不影响 IDE 调试进程）

| 场景 | 结果 |
|---|---|
| `consume-by-container` 领 4 根 | 返回 4 个子实例 id；再领 2 根正确接着挑 A5、A6 |
| 幂等（重复投 `demo-consume-002`） | `1003005008 重复的操作请求（幂等键【demo-consume-002#0】已处理过）` |
| 容器内状态 | 18 AVAILABLE + 6 USED，A1~A6 全部 `USED current_count=0` |
| 流水落库 | 6 条 CONSUME，`operation_id` 正确归组，`idempotent_key` 按明细派生 |
| `list-by-instance` | 返回完整轨迹（含 `movementTypeName`「消耗」、`containerTypeCode`、`contentDefCode`、`operator=1`、`operatorType=USER`） |
| 转移 R1C2→R3C1 | 成功，返回 `2103865798156787714` |
| 重复转移到同槽位 | `1003005002` |
| 已落位再 put-in | `1003005001` |
| 未落位 take-out | `1003005000` |
| put-in 回 R1C2 | 成功，位置复原 |
| `update` 试图改槽位 | `1003004007`（关键一致性约束生效） |

---

## 十、待确认 / 已知遗留

1. **`consume` 与 `consume-by-container` 的返回值语义不一致**
   - `consume` 返回 `List<Long>` = **流水 id**
   - `consume-by-container` 返回 `List<Long>` = **被消耗的实例 id**
   - 两者都是 `List<Long>`，调用方容易误用。建议二期统一为一个结果 VO
     （如 `{ movementIds, consumedInstanceIds }`），或至少明确文档约定。
2. **`RESERVE` / `RELEASE` 事件已定义但尚无写入方** —— 等调度域接入时使用。
3. **`STATUS_CHANGE` 事件已定义但当前只有 `CONSUME` 会带状态变化** ——
   纯手工改状态（`update` 改 `instanceStatus`）目前不走流水，二期视审计要求补。
4. **`ADJUST` 冲正无专用接口** —— 一期靠人工 SQL 或后续补接口。
5. **表里 `capacity`（槽位容量）仍是死字段** —— `uk_root_slot` 明确禁止一槽多实例，
   一期约定「1 个实例 = 1 个标准位」，二期跨位实例再引入 `capacity_units`。
