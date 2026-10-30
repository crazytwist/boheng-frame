# Transport 关系与流程图

## 一、Transport ER 图

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
        varchar warehouse_load_action_key
        varchar flow_definition_key
        tinyint enabled
    }

    transport_request {
        bigint id PK
        varchar request_no UK
        varchar request_type
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
        int current_stop_seq
    }

    transport_item {
        bigint id PK
        bigint order_id FK
        bigint material_instance_id
        bigint source_slot_id
        bigint target_slot_id
        bigint current_slot_id
        varchar status
        int sequence_no
    }

    transport_stop {
        bigint id PK
        bigint order_id FK
        int stop_seq
        varchar stop_type
        bigint transfer_slot_id
        varchar location_code
        varchar status
    }

    transport_operation {
        bigint id PK
        bigint order_id FK
        bigint stop_id FK
        bigint item_id FK
        bigint device_command_id
        varchar operation_type
        varchar executor_type
        varchar status
    }

    transport_handover_session {
        bigint id PK
        bigint order_id FK
        bigint stop_id FK
        bigint operation_id FK
        bigint item_id FK
        bigint wms_movement_id
        varchar handover_type
        varchar status
    }

    transport_handover_evidence {
        bigint id PK
        bigint session_id FK
        bigint device_command_id
        varchar evidence_type
        json evidence_payload
    }

    transport_lock_ref {
        bigint id PK
        bigint order_id FK
        bigint stop_id FK
        bigint operation_id FK
        varchar lock_ref
        varchar resource_type
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
        bigint request_id
        bigint order_id
        bigint stop_id
        bigint operation_id
        bigint item_id
        bigint handover_id
        varchar event_type
    }

    transport_route_config ||--o{ transport_order : "route snapshot"
    transport_request ||--o{ transport_order : "creates"
    transport_order ||--|{ transport_item : "contains"
    transport_order ||--|{ transport_stop : "has"
    transport_stop ||--o{ transport_operation : "executes"
    transport_item ||--o{ transport_operation : "carried by"
    transport_order ||--o{ transport_handover_session : "owns"
    transport_stop ||--o{ transport_handover_session : "occurs at"
    transport_operation ||--o{ transport_handover_session : "confirms"
    transport_item ||--o{ transport_handover_session : "transfers"
    transport_handover_session ||--o{ transport_handover_evidence : "has evidence"
    transport_order ||--o{ transport_lock_ref : "references"
    transport_lock_ref }o--|| transport_local_lock : "points to"
    transport_order ||--o{ transport_event : "emits"
```

## 二、Transport 执行流程图

```mermaid
flowchart TD
    A[提交运输需求] --> B[读取 transport_route_config]
    B --> C[解析起点/终点区域和 action_code_key]
    C --> D[创建 transport_request]
    D --> E[创建 transport_order]
    E --> F[WMS 查询起点区域可用出料位]
    F --> G[WMS 查询终点区域可用接收位]
    G --> H[创建 transport_item]
    H --> I[创建 PICKUP / DROPOFF transport_stop]
    I --> J[预约 WMS 槽位]
    J --> K[创建 transport_local_lock]
    K --> L[生成 transport_operation]
    L --> M[AGV move 到起点]
    M --> N[Warehouse 出料]
    N --> O[AGV grab]
    O --> P[释放起点出料位]
    P --> Q{AGV 是否满载或物料是否完成}
    Q -->|否| F
    Q -->|是| R[AGV move 到终点]
    R --> S[AGV place]
    S --> T[创建 handover_session]
    T --> U[收集交接证据]
    U --> V{交接是否成功}
    V -->|否| W[失败/人工介入/补偿]
    V -->|是| X[WMS 创建移动流水]
    X --> Y[更新 transport_item 和 transport_order]
    Y --> Z[写入 transport_event]
```

## 三、Transport 模块关系图

```mermaid
flowchart LR
    R[transport_route_config\n静态路线配置]
    TR[TransportRouteResolver\n路线解析]
    WMS[WMS API\n区域/槽位/移动流水]
    DEV[Device API\nAGV/Warehouse命令]
    FLOW[Flow Engine\n流程执行]
    REQ[transport_request\n运输需求]
    ORD[transport_order\n运输单]
    ITEM[transport_item\n物料明细]
    STOP[transport_stop\n停靠点]
    OP[transport_operation\n原子动作]
    LOCK[transport_local_lock\n本地资源锁]
    REF[transport_lock_ref\n锁关联]
    HAND[transport_handover_session\n交接会话]
    EVID[transport_handover_evidence\n交接证据]
    EVENT[transport_event\n事件流水]

    R --> TR
    TR --> REQ
    TR --> ORD
    WMS -->|查询/预约槽位| ORD
    WMS -->|更新位置| HAND
    DEV -->|执行设备命令| OP
    FLOW -->|驱动节点| OP
    REQ --> ORD
    ORD --> ITEM
    ORD --> STOP
    STOP --> OP
    ITEM --> OP
    ORD --> LOCK
    LOCK --> REF
    OP --> HAND
    HAND --> EVID
    ORD --> EVENT
    OP --> EVENT
    HAND --> EVENT
```

## 四、Transport 表的核心职责

```text
transport_route_config
    只保存静态路线规划

transport_request
    接收一次运输需求

transport_order
    承载一次可执行运输

transport_item
    承载运输的多批次物料

transport_stop
    描述起点、终点及中间停靠点

transport_operation
    描述 move、grab、place、warehouse transport 等原子动作

transport_local_lock
    记录 AGV、出料位、接收位等资源锁

transport_handover_session
    管理交出方和接收方确认

transport_handover_evidence
    保存扫码、传感器和设备回调证据

transport_event
    保存运输过程中的状态事件
```
