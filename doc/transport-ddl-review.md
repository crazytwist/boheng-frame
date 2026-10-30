# Transport DDL 整合与关系审查

## 1. 结论先行

当前仓库里存在两套运输执行模型：

1. `transport-v1.sql`：完整 Transport 领域模型。
2. `agv-transport-single.sql`：早期单 AGV P0 任务队列模型。

两套模型都描述了“运输任务”和“槽位预占”，如果同时启用，会产生两个事实源：

- `transport_order` 与 `agv_transport_task` 都可能表示当前任务；
- `transport_local_lock`、`transport_lock_ref` 与 `agv_slot_hold` 都可能表示槽位锁；
- Flow 关联既可能落在 `transport_order`，也可能落在 `agv_transport_task`。

建议本期统一以 `transport_*` 为主模型，`agv_transport_task` 和 `agv_slot_hold` 作为迁移前兼容表，不再新增业务逻辑。

## 2. 表的职责分层

### 静态配置

| 表 | 职责 | 是否承载运行时数据 |
| --- | --- | --- |
| `transport_route_config` | 路线、区域、动作能力键、流程模板 | 否 |

路线表只记录：

- 起点/终点站点和区域；
- `AGV#move`、`AGV#grab`、`AGV#place` 等动作能力键；
- AGV 任务类型、托板类型、路线装载能力；
- 门禁动作能力键；
- 绑定的流程编码和版本。

实际的 WMS 槽位、实际 AGV、运输状态、锁和命令均不写入该表。

### 业务与执行主表

| 表 | 职责 | 关键关系 |
| --- | --- | --- |
| `transport_request` | 外部提交的运输需求 | 一个需求可以有多个运输方案/运输单 |
| `transport_order` | 一次实际执行的运输单 | 隶属于一个需求，保存路线快照和执行器快照 |
| `transport_item` | 运输单中的物料/载具明细 | 隶属于运输单，关联 WMS 物料实例 |
| `transport_stop` | 运输单的有序停靠点 | 隶属于运输单 |
| `transport_operation` | 停靠点下的原子操作 | 隶属于停靠点，可关联物料和 Device 命令 |

### 交接与审计

| 表 | 职责 |
| --- | --- |
| `transport_handover_session` | 交出方和接收方的双边交接状态 |
| `transport_handover_evidence` | 传感器、扫码、视觉、设备回调等交接证据 |
| `transport_event` | 运输状态和领域事件流水，只增不改 |

### 资源锁

| 表 | 职责 |
| --- | --- |
| `transport_local_lock` | V1 本地资源互斥锁，当前真实锁状态 |
| `transport_lock_ref` | 将业务对象与锁/租约关联，记录锁的持有范围 |

WMS 的位置状态仍以 WMS 为事实源。Transport 只保存位置快照和锁关联，不替代 WMS 的库存、槽位和移动流水。

## 3. ER 图

```mermaid
erDiagram
    transport_route_config {
        bigint id PK
        varchar route_code UK
        varchar source_station_code
        varchar source_area_code
        varchar target_station_code
        varchar target_area_code
        varchar move_action_key
        varchar grab_action_key
        varchar place_action_key
        varchar flow_definition_key
    }

    transport_request {
        bigint id PK
        varchar request_no UK
        varchar source_type
        varchar source_ref
        varchar status
        bigint active_order_id
    }

    transport_order {
        bigint id PK
        bigint request_id FK
        varchar order_no UK
        varchar primary_executor_code
        json route_plan_json
        varchar status
    }

    transport_item {
        bigint id PK
        bigint order_id FK
        bigint material_instance_id REF
        bigint source_slot_id REF
        bigint target_slot_id REF
        varchar status
    }

    transport_stop {
        bigint id PK
        bigint order_id FK
        int stop_seq
        varchar stop_type
        bigint transfer_slot_id REF
        varchar status
    }

    transport_operation {
        bigint id PK
        bigint order_id FK
        bigint stop_id FK
        bigint item_id FK
        bigint device_command_id REF
        varchar operation_type
        varchar status
    }

    transport_handover_session {
        bigint id PK
        bigint order_id FK
        bigint stop_id FK
        bigint operation_id FK
        bigint item_id FK
        bigint wms_movement_id REF
        varchar status
    }

    transport_handover_evidence {
        bigint id PK
        bigint session_id FK
        bigint device_command_id REF
        varchar evidence_type
        json evidence_payload
    }

    transport_lock_ref {
        bigint id PK
        bigint order_id FK
        bigint stop_id FK
        bigint operation_id FK
        varchar lock_ref
        varchar resource_key
        varchar status
    }

    transport_local_lock {
        bigint id PK
        bigint order_id FK
        varchar resource_type
        varchar resource_key
        varchar lock_token UK
        varchar status
    }

    transport_event {
        bigint id PK
        bigint request_id REF
        bigint order_id REF
        bigint stop_id REF
        bigint operation_id REF
        bigint item_id REF
        bigint handover_id REF
        varchar event_type
    }

    transport_route_config ||--o{ transport_order : "route snapshot, logical reference"
    transport_request ||--o{ transport_order : creates
    transport_order ||--|{ transport_item : contains
    transport_order ||--|{ transport_stop : has
    transport_stop ||--o{ transport_operation : executes
    transport_item ||--o{ transport_operation : carried_by
    transport_order ||--o{ transport_handover_session : owns
    transport_stop ||--o{ transport_handover_session : occurs_at
    transport_operation ||--o{ transport_handover_session : confirms
    transport_item ||--o{ transport_handover_session : transfers
    transport_handover_session ||--o{ transport_handover_evidence : proves
    transport_order ||--o{ transport_lock_ref : holds
    transport_lock_ref }o--|| transport_local_lock : references
    transport_order ||--o{ transport_event : emits
    transport_operation ||--o{ transport_event : emits
    transport_handover_session ||--o{ transport_event : emits
```

## 4. 运行时主链路

```text
routeCode
  ↓ 只读
transport_route_config
  ↓ 解析起点/终点区域和 action_code_key
transport_request
  ↓ 创建执行方案
transport_order
  ↓ 从 WMS 查询实际槽位，写入快照
transport_item + transport_stop
  ↓ 生成原子操作
transport_operation
  ↓ 预约出料位/AGV/设备接口
transport_local_lock + transport_lock_ref
  ↓
Device command / WMS 交接
  ↓
transport_handover_session + transport_handover_evidence
  ↓
WMS movement 更新位置
  ↓
transport_event 记录全链路事件
```

## 5. 当前 P0 表的处理建议

### `agv_transport_task`

它把以下信息混在一张表里：

- 运输任务；
- 物料和源/目标槽位；
- Flow 执行引用；
- AGV 固定设备编码。

这些职责已经分别由 `transport_order`、`transport_item`、`transport_stop`、`transport_operation` 覆盖。

建议：

1. 不再作为新流程的主表；
2. 保留用于旧接口或数据迁移；
3. 最终将旧任务映射为 `transport_request → transport_order → transport_item`。

### `agv_slot_hold`

它与 `transport_local_lock` 的职责重复，且只支持 `task_id`。

建议统一为：

```text
transport_local_lock.resource_type = TRANSFER_SLOT
transport_local_lock.resource_key  = WMS槽位编码
transport_lock_ref.scope_type      = STOP 或 OPERATION
```

WMS 槽位本身的 `FREE/LOCKED/OCCUPIED` 仍由 WMS 管理，Transport 锁表只记录 Transport 侧的预约归属和租约。

## 6. 本期建议保留的 DDL 文件

```text
sql/mysql/transport-v1.sql
  运输域主模型

sql/mysql/transport-route-config.sql
  静态路线配置

sql/mysql/agv-transport-single.sql
  仅作为历史 P0 兼容表，暂不继续扩展
```

本期不要把两份任务表合并成一张“超级表”。正确做法是统一领域主表，旧表通过迁移或兼容适配逐步退出。

## 7. 需要重点确认的业务决策

1. `transport_route_config` 是否只保存路线规划，不保存具体 AGV 和具体 WMS 槽位。
2. `transport_order` 是否作为唯一的运行时运输单事实源。
3. `transport_local_lock` 是否作为 V1 唯一的本地锁事实源。
4. WMS 位置是否始终通过区域查询，Transport 只保存运行时快照。
5. Flow 执行引用统一落在 `transport_order`，还是还需要兼容 `agv_transport_task`。
