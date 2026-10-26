# 物料域分析（WMS）

> 模块：`boheng-module-wms` + `boheng-module-wms-api`
> 定位：实验室里一切「实物」与「空间」的建模与记账。回答三个问题——**东西是什么**（物质）、**装在哪**（容器/位置）、**现在在哪**（落位/状态）。

---

## 一、一句话定位

物料域是「实验室物理世界」的数字孪生。它把「物质定义 → 实物实例 → 空间落位」拆成清晰的层级，核心信条是：

> **位置也是实例（Position is an Instance）**

一块 96 孔板 = 1 行载体实例 + 96 行孔实例；孔不是坐标，而是和板子同级的「实例」。这带来一个关键推论：**内容物 1:1 内联在实例表上，没有独立的「实例-内容物」关系表**。

---

## 二、子域清单（7 个概念）

| 概念 | 表 | 职责 | 一句话 |
|---|---|---|---|
| 区域 zone | `wms_zone_info` + `wms_zone_material_type` | 空间树（房间→料架→层） | 「货架第几层」 |
| 槽位 slot | `wms_slot_info` | 叶子空间位，实例的落位点 | 「货架第 3 格」 |
| 容器类型 container_type | `wms_container_type` | 容器物理形态 × 层级角色 | 「能装什么的容器」 |
| 内容物定义 content_def | `wms_content_def` | 纯物质定义，不含包装 | 「装的是什么东西」 |
| 物料实例 material_instance | `wms_material_instance` | 一件具体的实物（含容器/孔/载体） | 「这一件实物」 |
| 物料流水 movement | `wms_material_movement` | 只增不改的变更流水 | 「发生过什么变动」 |
| 样本 sample | `wms_sample_info` | 业务对象（详见《样本域》） | 「送检的业务样本」 |

> 物料域实际是 `content_def → material_instance` 的「物质→实物」两级，样本 `sample` 是夹在中间的业务层，单独成篇。

---

## 三、空间域：zone + slot

### 3.1 区域树

- `wms_zone_info` 是一棵**树**：`parent_zone_code` 指向父区域，`zone_code` 是区域编码。
- 区域物料类型：**独立表管关系、字典管值域** —— `wms_zone_material_type` 记录「这个区域允许放什么物料类型」，值域来自字典 `wms_material_type`（REAGENT/CONSUMABLE/SAMPLE/STANDARD/TOOL）。
- **继承规则**：子区域没配置物料类型时，沿 `parent_zone_code` 向上找第一个配置了 `temp_min/temp_max` 或物料类型的区域。
- 温区不落在 slot 上：按 `zone_code` 向上取第一个配置了温区的区域。

### 3.2 槽位（叶子）

槽位是空间树的**叶子**，也是实例的落位点。两个正交的状态：

| 状态字段 | 含义 | 取值 |
|---|---|---|
| `status` | 可用态 | 0 启用 / 1 停用 |
| `slot_status` | 使用态 | FREE / OCCUPIED / LOCKED / CHECKING |

关键约束：

- **slot 不存占用方**。「谁占用」的真相源是 `wms_material_instance.root_slot_id`，按它反查。slot 只留 `occupied_qty`（已占标准位数），`=0 ⟺ FREE`。
- **slot 不存锁**：`lock_source/ref_id/time/expire` 整组删除，锁归**调度域**。slot 只用 `LOCKED/CHECKING` 表达「暂不可用」，不带元信息。
- 设备位置：`device_code` + `device_position_no`（设备上第几个板位）。

---

## 四、实例域：container_type + content_def + material_instance

### 4.1 容器类型 container_type（16 张表中最复杂）

`category`（物理形态）× `hierarchy_role`（层级角色）是**两个正交维度**：

| hierarchy_role | 含义 | 容量字段 |
|---|---|---|
| CARRIER | 载体（如 96 孔板） | 无体积，填 `well_count/rows/cols` + `child_type_code` |
| CONTAINER | 容器（如试剂瓶） | `max_vol_ul` |
| WELL | 孔/格位（本身也是实例） | `max_vol_ul` |

- **嵌套合法性唯一依据** = `目标容器.child_type_code = 子实例的类型编码`（不只靠一个 nestable 布尔）。
- 机械字段：`size_x/y/z_mm`、`well_spacing_mm`（机械臂/多通道移液必需）。
- `device_labware_name` = Tecan i-control 板型映射点。

### 4.2 内容物定义 content_def（17）

**只描述「物质」，不描述「包装」**。所以「50mL 75% 乙醇瓶」= `container_type(BOTTLE_50ML) × content_def(ETHANOL-75PCT)` 的组合，而不是一条记录。

- `content_type` = REAGENT / STANDARD / BUFFER / SAMPLE / WASTE / MEDIA / SOLVENT / OTHER
- `storage_cond` 用枚举（RT / C2_8 / F20 / Ultra80 / FROZTHAW），为了能和 zone 温区**程序化匹配**
- **两个效期并存**：`shelf_life_days`（入库起算）+ `open_life_days`（开封起算）
- 溯源字段：`hazard_level` / `cas_no` / `catalog_no` / `supplier` / `require_batch` / `fefo`

### 4.3 物料实例 material_instance（18）

实例承载三件事：**容器（这一件）**、**内联的内容物（装了什么）**、**树结构（在哪个位置）**。

**内联内容物组**（不再有独立内容物表）：
```
content_def_id / content_def_code   ← 装的是什么物质
content_type                        ← 快照（CARRIER 为空；空容器为 EMPTY）
sample_id                           ← 关联业务样本（试剂/空容器为 NULL）
current_vol_ul / current_count      ← 当前量
```

> ⚠️ 重要差异：`batch_no`（内部批次）与 `lot_no`（厂商批号）在 v1.4 原设计里是分开的，但**当前代码的 `MaterialInstanceDO` 已简化，暂未落这两个字段**，从样本生成实例时也暂不带批次。这是待补项。

**六态**（`instance_status`，与样本八态正交）：
```
AVAILABLE → RESERVED → IN_USE → USED
                              ↘ EXPIRED（效期到）
                              ↘ DISCARDED（终态，废弃）
```
- `RESERVED` = 步骤已申请未取用（调度依赖）
- **没有 `status` 启用位**，终态是 DISCARDED 而不是「停用」

**树结构**（实例是一棵 16 层上限的树）：
```
parent_instance_id  + parent_position_code   ← 边（A1 孔 = 板子的 A1 位置）
instance_path                                ← 物化路径（前缀索引 64）
root_instance_id                             ← 根实例（整树冗余）
root_slot_id                                 ← 落位槽位（仅顶层实例有值，可空）
```

- ⚠️ **移动整树必须用路径前缀 UPDATE**（root_slot_id / root_instance_id / path / depth 是整树冗余），禁止单改某一层；跨父搬迁要同事务重写 path 与 depth。
- **不冗余 `zone_code`**（区域属槽位，整盒移动会变），按区查走 `root_slot_code`。

**两套位置别混**（全文最关键的一张对比）：

| 位置概念 | 含义 | 谁有值 |
|---|---|---|
| `root_slot_id` | 空间位置（货架第 3 格） | **只有顶层实例** |
| `parent_position_code` | 容器内位置（板子 A1 孔） | **子实例自身的位置码** |

> 一块 96 孔板占 1 个 slot；内部 96 孔是子实例，不落 slot 表。
> 一期约定 **1 个实例 = 1 个标准位**（供 `slot.occupied_qty` 累加），跨位实例二期再引入 `capacity_units`。

---

## 五、流水域：movement（只增不改）

`wms_material_movement` 记录物料的一切变动，**只 insert 不 update/delete**，错了写一条 `ADJUST` 反向冲正。

九种流水类型：

| 类型 | 含义 |
|---|---|
| CREATE | 建账 |
| PUT_IN / TAKE_OUT | 上架 / 下架 |
| MOVE | 转移 |
| CONSUME | 消耗 |
| STATUS_CHANGE | 状态变更 |
| RESERVE / RELEASE | 预留 / 释放预留 |
| ADJUST | 冲正 |

- **流水主体是实例**（位置即实例 → 上下架/转移本质是 `root_slot_id` 的三次变更）。
- 位置字段成对记录 `from → to`：`fromSlotId/fromSlotCode/fromZoneCode` → `toSlotId/...`。
- 数量/体积记录**变更前后 + 变更量**（`beforeQty/changeQty/afterQty`、`beforeVolUl/changeVolUl/afterVolUl`）。
- 幂等：`idempotentKey`（外部调用防重复过账，为空不参与唯一约束）。
- ⚠️ `recordMovement` 必须与业务变更**同一事务**，否则「位置变了但没流水」。

---

## 六、核心设计原则（速查）

1. **位置即实例**：孔/格位也是实例，空孔也建行（`content_type = EMPTY`）。
2. **内容物内联**：没有独立实例-内容物表。
3. **职责三分**：容器类型管「能装什么」/ 内容物定义管「物质是什么」/ 实例管「这一件实物」。
4. **slot 不存占用方/锁**：占用真相源 = `material_instance.root_slot_id`；锁归调度域。
5. **两套位置正交**：slot（空间）vs parent_position_code（容器内）。
6. **实例六态 × 样本八态正交**：实例由物理操作驱动，样本由流程驱动。
7. **流水只增不改**：审计级，冲正走 ADJUST。
8. **唯一键一律普通索引 + Service 校验**（整个 wms DDL 0 个 UNIQUE，逻辑删除兼容用「生成列 + 唯一索引」）。
