-- =============================================================================
-- 【原始设计存档】Lab-WMS 一期 DDL（实验室仓储管理 · 空间 / 物料 / 台账 / 出入库）
-- 版本: v1.0    对应设计: docs/lab-wms-design.md (v1.1 + §4.5/§4.6)
-- 目标库: MySQL 8.0+（PostgreSQL 转换见文末映射表）
-- 约定:
--   1. 主键 id = BIGINT 雪花（应用侧生成，不用 AUTO_INCREMENT），便于跨底座迁移
--   2. 无 tenant_id（单体实验室平台）；如未来多租户，见设计 §11.2 演进路径
--   3. 表间关系仅用「编码/ID 引用」，不建外键（跨模块 device/flow 只认编码）
--   4. 审计四字段 create_id/create_time/update_id/update_time + 逻辑删除 del_flag
--   5. 时间统一 DATETIME（PG: TIMESTAMP），数值统一 DECIMAL(18,4)
-- 执行顺序: 空间 → 物料 → 台账 → 单据（依赖顺序，实际无 FK 约束，可并行）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. lab_zone_info  区域树（唯一空间主表：实验室/房间/功能区/温区/料架…全部节点）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_zone_info (
  id                BIGINT        NOT NULL,
  zone_code         VARCHAR(64)   NOT NULL COMMENT '区域编码(全局唯一)',
  parent_zone_code  VARCHAR(64)   NULL     COMMENT '父区域编码(NULL=根节点)',
  zone_name         VARCHAR(128)  NOT NULL COMMENT '区域名称',
  zone_type         VARCHAR(32)   NOT NULL COMMENT 'LAB实验室/ROOM房间/FUNCTION功能区/TEMP温控区/SAFETY安全区/RACK料架/BENCH台面/DEVICE设备工位/CUSTOM自定义(字典 lab_zone_type)',
  zone_level        INT           NOT NULL DEFAULT 1 COMMENT '树深度(根=1,冗余,便于范围查询)',
  temp_min          DECIMAL(5,2)  NULL     COMMENT '温区下限℃(沿树继承)',
  temp_max          DECIMAL(5,2)  NULL     COMMENT '温区上限℃(沿树继承)',
  biosafety_level   INT           NULL     COMMENT '生物安全等级(1-4,沿树继承)',
  rack_rows         INT           NULL     COMMENT '行数(RACK,批量生成槽位模板)',
  rack_cols         INT           NULL     COMMENT '列数(RACK,批量生成槽位模板)',
  slot_capacity     INT           NULL     COMMENT '每槽位标准容量(RACK 模板)',
  address           VARCHAR(255)  NULL     COMMENT '物理地址描述(LAB/ROOM)',
  sort_no           INT           NOT NULL DEFAULT 0,
  status            CHAR(1)       NOT NULL DEFAULT '1' COMMENT '1启用/0停用',
  description       VARCHAR(512)  NULL,
  del_flag          CHAR(1)       NOT NULL DEFAULT 'F' COMMENT 'F正常/T已删',
  create_id         BIGINT        NULL,
  create_time       DATETIME      NULL,
  update_id         BIGINT        NULL,
  update_time       DATETIME      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_zone_code (zone_code),
  KEY idx_parent (parent_zone_code),
  KEY idx_zone_type (zone_type)
) COMMENT '区域树(实验室/房间/功能区/温区/料架统一节点)';

-- -----------------------------------------------------------------------------
-- 2. lab_slot_info  槽位（叶子实体：货位档案 + 占用/锁定运行态）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_slot_info (
  id             BIGINT       NOT NULL,
  slot_code      VARCHAR(64)  NOT NULL COMMENT '槽位编码(全局唯一)',
  zone_code      VARCHAR(64)  NOT NULL COMMENT '挂载区域编码(唯一从属边, 父节点须 mountable 且为叶子)',
  slot_type      VARCHAR(32)  NOT NULL COMMENT 'STORAGE存储/DEVICE设备器位/BUFFER暂存/WASTE废弃(字典 lab_slot_type)',
  slot_status    VARCHAR(32)  NOT NULL DEFAULT 'EMPTY' COMMENT 'EMPTY空闲/OCCUPIED占用/LOCKED锁定/DISABLED停用/CHECKING盘点中',
  capacity       INT          NOT NULL DEFAULT 1 COMMENT '容量(标准位)',
  unique_batch   CHAR(1)      NOT NULL DEFAULT 'F' COMMENT 'T=一位一批(样本位/器位)',
  row_no         INT          NULL COMMENT '行号',
  col_no         INT          NULL COMMENT '列号',
  layer_no       INT          NULL COMMENT '层号',
  device_code    VARCHAR(64)  NULL COMMENT '关联设备编码(DEVICE 必填, module-device)',
  device_slot_no VARCHAR(64)  NULL COMMENT '设备侧器位号(如 Tecan 堆栈第3位)',
  temp_min       DECIMAL(5,2) NULL COMMENT '温区下限(可空, 覆盖 zone 继承值)',
  temp_max       DECIMAL(5,2) NULL COMMENT '温区上限(可空, 覆盖 zone 继承值)',
  lock_source    VARCHAR(32)  NULL COMMENT '锁来源 TASK调度/MANUAL人工/CHECK盘点',
  lock_ref_id    BIGINT       NULL COMMENT '锁引用ID(任务ID/盘点单ID)',
  lock_time      DATETIME     NULL COMMENT '加锁时间',
  lock_expire    DATETIME     NULL COMMENT '锁过期时间(定时任务释放)',
  version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
  status         CHAR(1)      NOT NULL DEFAULT '1' COMMENT '1启用/0停用(区别于占用态)',
  description    VARCHAR(512) NULL,
  del_flag       CHAR(1)      NOT NULL DEFAULT 'F',
  create_id      BIGINT       NULL,
  create_time    DATETIME     NULL,
  update_id      BIGINT       NULL,
  update_time    DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot_code (slot_code),
  KEY idx_zone (zone_code),
  KEY idx_device (device_code, device_slot_no),
  KEY idx_slot_status (slot_status),
  KEY idx_lock_expire (lock_expire)
) COMMENT '槽位(树叶子: 货位档案 + 占用锁定)';

-- -----------------------------------------------------------------------------
-- 3. lab_material_info  物料主数据
-- -----------------------------------------------------------------------------
CREATE TABLE lab_material_info (
  id               BIGINT        NOT NULL,
  material_code    VARCHAR(64)   NOT NULL COMMENT '物料编码',
  material_name    VARCHAR(128)  NOT NULL COMMENT '物料名称',
  material_type    VARCHAR(32)   NOT NULL COMMENT 'REAGENT试剂/CONSUMABLE耗材/SAMPLE样本/STANDARD标准品/TOOL工具(字典)',
  category_id     BIGINT        NULL     COMMENT '分类ID(一期平铺, 二期改树)',
  spec             VARCHAR(128)  NULL     COMMENT '规格(500mL/100T)',
  unit             VARCHAR(32)   NOT NULL COMMENT '库存单位(字典)',
  storage_cond     VARCHAR(32)   NULL     COMMENT '存储条件 RT/C2_8/F20/Ultra80/FROZTHAW(字典, 与 zone 继承温区做匹配校验)',
  require_batch    CHAR(1)       NOT NULL DEFAULT 'T' COMMENT '是否批次管理',
  fefo             CHAR(1)       NOT NULL DEFAULT 'T' COMMENT '是否 FEFO 出库',
  open_expiry_days INT           NULL     COMMENT '开瓶效期(天)',
  hazard_flag      CHAR(1)       NOT NULL DEFAULT 'F' COMMENT '危化品标记',
  cas_no           VARCHAR(64)   NULL     COMMENT 'CAS 号(试剂)',
  manufacturer     VARCHAR(128)  NULL     COMMENT '生产厂商',
  status           CHAR(1)       NOT NULL DEFAULT '1',
  description      VARCHAR(1024) NULL,
  del_flag         CHAR(1)       NOT NULL DEFAULT 'F',
  create_id        BIGINT        NULL,
  create_time      DATETIME      NULL,
  update_id        BIGINT        NULL,
  update_time      DATETIME      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_code (material_code),
  KEY idx_material_type (material_type),
  KEY idx_material_name (material_name)
) COMMENT '物料主数据';

-- -----------------------------------------------------------------------------
-- 4. lab_material_batch  批次
-- -----------------------------------------------------------------------------
CREATE TABLE lab_material_batch (
  id                 BIGINT        NOT NULL,
  material_id        BIGINT        NOT NULL,
  batch_no           VARCHAR(64)   NOT NULL COMMENT '批号/LOT',
  batch_status       VARCHAR(32)   NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/FROZEN冻结隔离/DEPLETED耗尽/EXPIRED过期',
  produce_date       DATE          NULL     COMMENT '生产日期',
  expiry_date        DATE          NULL     COMMENT '失效日期(FEFO 依据)',
  opened_time        DATETIME      NULL     COMMENT '开瓶时间',
  opened_expiry_date DATE          NULL     COMMENT '开瓶失效日期(开瓶后 FEFO 取更早者)',
  supplier_name      VARCHAR(128)  NULL,
  price              DECIMAL(18,4) NULL,
  sample_ref         VARCHAR(64)   NULL     COMMENT '样本编码(SAMPLE 类型: 物理样本唯一标识)',
  barcode            VARCHAR(128)  NULL     COMMENT '条码(开瓶标签/样本管条码)',
  remark             VARCHAR(512)  NULL,
  del_flag           CHAR(1)       NOT NULL DEFAULT 'F',
  create_id          BIGINT        NULL,
  create_time        DATETIME      NULL,
  update_id          BIGINT        NULL,
  update_time        DATETIME      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_batch (material_id, batch_no),
  KEY idx_expiry (expiry_date),
  KEY idx_opened_expiry (opened_expiry_date),
  KEY idx_barcode (barcode)
) COMMENT '物料批次';

-- -----------------------------------------------------------------------------
-- 5. lab_inventory  库存台账（聚合键: slot × batch，唯一库存事实源）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_inventory (
  id           BIGINT        NOT NULL,
  slot_id      BIGINT        NOT NULL,
  slot_code    VARCHAR(64)   NOT NULL COMMENT '冗余, 编码寻址',
  material_id  BIGINT        NOT NULL,
  batch_id     BIGINT        NOT NULL,
  qty          DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '现存数量(>0; 归零即删除该行)',
  version      INT           NOT NULL DEFAULT 0 COMMENT '乐观锁',
  del_flag     CHAR(1)       NOT NULL DEFAULT 'F',
  create_id    BIGINT        NULL,
  create_time  DATETIME      NULL,
  update_id    BIGINT        NULL,
  update_time  DATETIME      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_slot_batch (slot_id, batch_id),
  KEY idx_material (material_id),
  KEY idx_batch (batch_id),
  KEY idx_slot_code (slot_code)
) COMMENT '库存台账(slot × batch)';

-- -----------------------------------------------------------------------------
-- 6. lab_inventory_history  库存流水（只增不改，正反向可追溯）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_inventory_history (
  id              BIGINT        NOT NULL,
  slot_id         BIGINT        NOT NULL,
  slot_code       VARCHAR(64)   NOT NULL,
  material_id     BIGINT        NOT NULL,
  batch_id        BIGINT        NOT NULL,
  change_type     VARCHAR(32)   NOT NULL COMMENT 'IN入库/OUT出库/MOVE_IN移入/MOVE_OUT移出/CHECK_ADJ盘点调整/INIT建账',
  before_qty      DECIMAL(18,4) NOT NULL COMMENT '变更前数量',
  change_qty      DECIMAL(18,4) NOT NULL COMMENT '变更量(正负)',
  after_qty       DECIMAL(18,4) NOT NULL COMMENT '变更后数量',
  order_type      VARCHAR(32)   NULL     COMMENT '关联单据类型 IN/OUT/MOVE/CHECK',
  order_id        BIGINT        NULL,
  order_detail_id BIGINT        NULL COMMENT '幂等键来源(明细维度)',
  task_id         BIGINT        NULL COMMENT '关联实验任务(module-task)',
  operator_type   VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT 'USER/DEVICE/AUTO',
  operator_ref    VARCHAR(64)   NULL     COMMENT '设备编码/定时任务标识',
  remark          VARCHAR(512)  NULL,
  create_id       BIGINT        NULL,
  create_time     DATETIME      NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_detail_change (order_detail_id, change_type),
  KEY idx_slot (slot_id, create_time),
  KEY idx_batch (batch_id, create_time),
  KEY idx_order (order_id),
  KEY idx_create_time (create_time)
) COMMENT '库存变更流水(只增不改)';

-- -----------------------------------------------------------------------------
-- 7. lab_in_order  入库单
-- -----------------------------------------------------------------------------
CREATE TABLE lab_in_order (
  id            BIGINT       NOT NULL,
  order_no      VARCHAR(64)  NOT NULL COMMENT '单号 RK202609250001',
  order_type    VARCHAR(32)  NOT NULL COMMENT 'PURCHASE采购/RETURN归还/RECYCLE回收/INIT建账',
  order_status  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/EXECUTING/COMPLETED/CANCELLED',
  source_order  VARCHAR(64)  NULL     COMMENT '来源单号(采购单/任务ID)',
  applicant_id  BIGINT       NULL     COMMENT '申请人',
  execute_time  DATETIME     NULL COMMENT '开始执行时间',
  complete_time DATETIME     NULL COMMENT '完成时间',
  remark        VARCHAR(512) NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT 'F',
  create_id     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_id     BIGINT       NULL,
  update_time   DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_status (order_status),
  KEY idx_create_time (create_time)
) COMMENT '入库单';

-- -----------------------------------------------------------------------------
-- 8. lab_in_order_detail  入库单明细（逐条过账单元）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_in_order_detail (
  id               BIGINT        NOT NULL,
  order_id         BIGINT        NOT NULL,
  material_id      BIGINT        NOT NULL,
  batch_id         BIGINT        NULL     COMMENT '执行时生成/绑定',
  plan_qty         DECIMAL(18,4) NOT NULL COMMENT '计划数量',
  executed_qty     DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '已执行数量',
  target_slot_id   BIGINT        NULL COMMENT '建议/实际入库槽位',
  target_slot_code VARCHAR(64)   NULL,
  detail_status    VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTING/DONE/SKIPPED',
  execute_time     DATETIME      NULL,
  remark           VARCHAR(512)  NULL,
  del_flag         CHAR(1)       NOT NULL DEFAULT 'F',
  create_id        BIGINT        NULL,
  create_time      DATETIME      NULL,
  update_id        BIGINT        NULL,
  update_time      DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_target_slot (target_slot_id),
  KEY idx_detail_status (detail_status)
) COMMENT '入库单明细';

-- -----------------------------------------------------------------------------
-- 9. lab_out_order  出库单
-- -----------------------------------------------------------------------------
CREATE TABLE lab_out_order (
  id            BIGINT       NOT NULL,
  order_no      VARCHAR(64)  NOT NULL COMMENT '单号 CK202609250001',
  order_type    VARCHAR(32)  NOT NULL COMMENT 'USE领用/EXPERIMENT实验消耗/SCRAP报废',
  order_status  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  task_id       BIGINT       NULL COMMENT '关联实验任务(module-task)',
  applicant_id  BIGINT       NULL,
  execute_time  DATETIME     NULL,
  complete_time DATETIME     NULL,
  remark        VARCHAR(512) NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT 'F',
  create_id     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_id     BIGINT       NULL,
  update_time   DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_status (order_status),
  KEY idx_task (task_id)
) COMMENT '出库单';

-- -----------------------------------------------------------------------------
-- 10. lab_out_order_detail  出库单明细（FEFO 推荐源 + 逐条过账）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_out_order_detail (
  id               BIGINT        NOT NULL,
  order_id         BIGINT        NOT NULL,
  material_id      BIGINT        NOT NULL,
  batch_id         BIGINT        NULL     COMMENT '实际出库批次(FEFO 推荐, 执行时复核)',
  plan_qty         DECIMAL(18,4) NOT NULL,
  executed_qty     DECIMAL(18,4) NOT NULL DEFAULT 0,
  source_slot_id   BIGINT        NULL     COMMENT '出库槽位',
  source_slot_code VARCHAR(64)   NULL,
  detail_status    VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTING/DONE/SKIPPED',
  execute_time     DATETIME      NULL,
  remark           VARCHAR(512)  NULL,
  del_flag         CHAR(1)       NOT NULL DEFAULT 'F',
  create_id        BIGINT        NULL,
  create_time      DATETIME      NULL,
  update_id        BIGINT        NULL,
  update_time      DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_source_slot (source_slot_id),
  KEY idx_detail_status (detail_status)
) COMMENT '出库单明细';

-- -----------------------------------------------------------------------------
-- 11. lab_move_order  移库单（二期启用，一期建表备用）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_move_order (
  id            BIGINT       NOT NULL,
  order_no      VARCHAR(64)  NOT NULL COMMENT '单号 YK202609250001',
  order_status  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  task_id       BIGINT       NULL COMMENT '关联实验任务',
  device_code   VARCHAR(64)  NULL COMMENT '涉及设备编码(DEVICE_IN/OUT 时)',
  applicant_id  BIGINT       NULL,
  execute_time  DATETIME     NULL,
  complete_time DATETIME     NULL,
  remark        VARCHAR(512) NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT 'F',
  create_id     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_id     BIGINT       NULL,
  update_time   DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_status (order_status),
  KEY idx_task (task_id)
) COMMENT '移库单';

-- -----------------------------------------------------------------------------
-- 12. lab_move_order_detail  移库单明细
-- -----------------------------------------------------------------------------
CREATE TABLE lab_move_order_detail (
  id             BIGINT        NOT NULL,
  order_id       BIGINT        NOT NULL,
  material_id    BIGINT        NOT NULL,
  batch_id       BIGINT        NULL,
  qty            DECIMAL(18,4) NOT NULL,
  from_slot_id   BIGINT        NULL,
  from_slot_code VARCHAR(64)   NULL,
  to_slot_id     BIGINT        NULL,
  to_slot_code   VARCHAR(64)   NULL,
  move_reason    VARCHAR(32)   NOT NULL DEFAULT 'RELOCATE' COMMENT 'RELOCATE整理/TASK_RETURN任务回库/DEVICE_IN设备进板/DEVICE_OUT设备出板/TEMP_OUT临时取出',
  detail_status  VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/EXECUTING/DONE/SKIPPED',
  execute_time   DATETIME      NULL,
  remark         VARCHAR(512)  NULL,
  del_flag       CHAR(1)       NOT NULL DEFAULT 'F',
  create_id      BIGINT        NULL,
  create_time    DATETIME      NULL,
  update_id      BIGINT        NULL,
  update_time    DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_from_slot (from_slot_id),
  KEY idx_to_slot (to_slot_id),
  KEY idx_detail_status (detail_status)
) COMMENT '移库单明细';

-- -----------------------------------------------------------------------------
-- 13. lab_check_order  盘点单（二期启用，一期建表备用）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_check_order (
  id            BIGINT       NOT NULL,
  order_no      VARCHAR(64)  NOT NULL COMMENT '单号 PD202609250001',
  check_scope   VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT 'FULL全部/ZONE按区域/RACK按料架',
  scope_code    VARCHAR(64)  NULL     COMMENT '范围区域编码(FULL 时为空)',
  order_status  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CHECKING/CONFIRMED/CANCELLED',
  check_time    DATETIME     NULL     COMMENT '盘点时间',
  diff_count    INT          NOT NULL DEFAULT 0 COMMENT '差异条数(冗余)',
  applicant_id  BIGINT       NULL,
  complete_time DATETIME     NULL,
  remark        VARCHAR(512) NULL,
  del_flag      CHAR(1)      NOT NULL DEFAULT 'F',
  create_id     BIGINT       NULL,
  create_time   DATETIME     NULL,
  update_id     BIGINT       NULL,
  update_time   DATETIME     NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_order_status (order_status),
  KEY idx_scope (check_scope, scope_code)
) COMMENT '盘点单';

-- -----------------------------------------------------------------------------
-- 14. lab_check_order_detail  盘点单明细（差异确认后生成 CHECK_ADJ 流水）
-- -----------------------------------------------------------------------------
CREATE TABLE lab_check_order_detail (
  id            BIGINT        NOT NULL,
  order_id      BIGINT        NOT NULL,
  slot_id       BIGINT        NOT NULL,
  slot_code     VARCHAR(64)   NOT NULL,
  material_id   BIGINT        NULL,
  batch_id      BIGINT        NULL,
  book_qty      DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '账面数量',
  actual_qty    DECIMAL(18,4) NULL     COMMENT '实盘数量(NULL=未盘)',
  diff_qty      DECIMAL(18,4) NULL     COMMENT '差异=actual-book',
  handle_type   VARCHAR(32)   NULL     COMMENT 'ADJUST调整/IGNORE忽略',
  detail_status VARCHAR(32)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COUNTED/CONFIRMED',
  remark        VARCHAR(512)  NULL,
  del_flag      CHAR(1)       NOT NULL DEFAULT 'F',
  create_id     BIGINT        NULL,
  create_time   DATETIME      NULL,
  update_id     BIGINT        NULL,
  update_time   DATETIME      NULL,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_slot (slot_id),
  KEY idx_detail_status (detail_status)
) COMMENT '盘点单明细';

-- =============================================================================
-- PostgreSQL 转换映射（Liquibase 用 property 变量实现双库）
--   DATETIME                 -> TIMESTAMP
--   DATE                     -> DATE
--   DECIMAL(18,4)            -> NUMERIC(18,4)
--   CHAR(1)                  -> CHAR(1)
--   UNIQUE KEY uk_x (a,b)    -> CONSTRAINT uk_x UNIQUE (a,b)
--   KEY idx_x (a)            -> 独立 CREATE INDEX idx_x ON t (a);
--   COMMENT '...' (列内)     -> COMMENT ON COLUMN t.c IS '...';
--   COMMENT '...' (表尾)     -> COMMENT ON TABLE t IS '...';
--   MySQL 的 <table> COMMENT   -> 无对应语法, 拆成 COMMENT ON
-- 约束：不使用 MySQL 专有类型/函数（enum、GROUP_CONCAT、IFNULL 等），SQL 层保持两库通用
-- =============================================================================
