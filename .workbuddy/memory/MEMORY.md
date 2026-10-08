# boheng-boot-mini 项目长期记忆

## ⚠️ 工作目录已切换（2026-09-26）
- 项目已通过 `ProjectReactor` 改名为 **`boheng-frame`**，新工作目录：
  **`/Volumes/External_1TB/Projects/boheng-frame`**（包名 `cn.boheng.frame.*`）
- 原目录 `boheng-boot-mini`（`cn.boheng.frame`）保留，作为芋道原始基座，不再作为开发目录
- 后续开发都在 `boheng-frame` 下进行；本 MEMORY 文件仍随项目复制（`boheng-frame/.workbuddy/memory/` 已有同份）
- 改名器跑通时踩过的坑：输出目录若含 `boheng` 关键字会触发防呆退出，已改为写死
  `/Volumes/External_1TB/Projects/boheng-frame`

## 个人基座（伯珩，Boheng）
- 用户署名：**伯珩**（bó héng，拼音 Boheng），曾用名「一南」
- **基座命名**（`ProjectReactor.java` 已按此定制，以后所有派生项目都从这里派生，只改 New 变量）：
  - groupId = `cn.boheng`
  - artifactId = `boheng-frame`
  - 包名根 = `cn.boheng.frame`（`cn.boheng.frame.*`）
  - 系统标题 = `伯珩基础平台`
- 改名器路径（两份，内容一致、包名不同）：
  - 源码内：`boheng-server/src/test/java/cn.boheng.frame/ProjectReactor.java`（包 `cn.boheng.frame`，随项目一起被改名）
  - **存档（基座母版）**：`.workbuddy/tools/ProjectReactor.java`（包 `cn.boheng.frame`，脱离源码目录，是派生的权威起点）
  - 替换顺序固定：GROUP_ID → PACKAGE_NAME → ARTIFACT_ID（必须放最后，太短）→ 大写首字母 → TITLE
  - 白名单（不重写直接拷）：gif/jpg/svg/png/eot/woff2/ttf/woff/xdb
  - 派生新项目时改 main() 里 4 个 New 变量：groupIdNew / artifactIdNew / packageNameNew / titleNew

## 工程定位
- `boheng-boot-mini` = 芋道（ruoyi-vue-pro）精简版，包名根 `cn.boheng.frame`
- 当前工作分支：**`master-jdk17`**，tag `v2026.08(jdk17/21)`
  - 其他分支：`master`（jdk8/11）、`master-jdk25`
- **JDK 17 + Spring Boot 3.5.15**
- ⚠️ 命名空间用 **`jakarta.*`**（`jakarta.annotation.Resource`、`jakarta.validation.*`），不是 `javax.*`
  - 若切回 `master` 分支则要用 `javax.*`，写代码前先确认分支

## 构建
```bash
JAVA_HOME=/Users/yinan/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home \
  mvn -pl boheng-module-wms -am -DskipTests -Dmaven.test.skip=true compile
```
- Maven 3.9.12；**localRepository = `/Users/yinan/workspace/tools/apache-maven-3.9.12/repo`**（不是 `~/.m2/repository`）
- 本机无 JDK 8/11；可用 JDK 17 / 25 / 27，**必须用 17**
- ⚠️ **macOS BSD grep 不支持 BRE 的 `\|` 分支**：`grep "a\|b"` 会静默不匹配（假阴性）。
  校验文本一律用 `grep -nE "a|b"`

## 底座约定（写代码必须遵守）
- 实体基类：
  - 租户表 → `cn.boheng.frame.framework.tenant.core.db.TenantBaseDO`（含 tenantId），**不加** `@TenantIgnore`
  - 非租户表 → `BaseDO` + `@TenantIgnore`（boheng 自己的 ConfigDO/JobDO/FileDO 都这么写）
  - 判定逻辑在 `TenantDatabaseInterceptor.computeIgnoreTable()`
- 审计字段（表列名固定）：`creator` varchar(64) / `create_time` datetime / `updater` varchar(64) / `update_time` datetime / `deleted` bit(1) 默认 b'0'
- MP 全局：`logic-delete-value: 1`，`logic-not-delete-value: 0`，`id-type: NONE`
- **主键**：要雪花就显式写 `@TableId(type = IdType.ASSIGN_ID)`（MySQL 下默认会走 AUTO）
- **唯一键**：整个 boheng DDL 里 0 个 UNIQUE —— 一律用普通索引 + Service 层唯一性校验
- Mapper：继承 `BaseMapperX`，条件用 `LambdaQueryWrapperX`，分页 `selectPage(reqVO, wrapper)`
- VO：`SaveReqVO` / `PageReqVO extends PageParam`（含 `LocalDateTime[] createTime` + `@DateTimeFormat`）/ `RespVO`
- 工具：`BeanUtils.toBean`、`exception(ErrorCode)`、`VisibleForTesting`
- 错误码段位：infra `1-001-000-000`，system `1-002-000-000`，**wms `1-003-000-000`**
- 控制器：`/admin-api` 前缀由框架加；`@PreAuthorize("@ss.hasPermission('模块:业务:动作')")`
- 模块装配：模块 pom 加进根 `pom.xml` 的 `<modules>` + `boheng-server/pom.xml` 依赖；
  组件扫描覆盖 `cn.boheng.frame.module`，Mapper 由 `@MapperScan` 自动扫描

## WMS 模块（已落地）
- 模块 `boheng-module-wms`，包 `cn.boheng.frame.module.wms`
- **18 张表** `wms_*`，DDL 在 `sql/mysql/wms.sql`（**v1.4**）
- Java 已生成的 domain（121 文件）：zone / slot / material / batch / inventory / order
  - ⚠️ **Java 侧欠三轮账**，停在 v1.2 字段：`wms_codegen.py` 未加实例域表；
    代码里还有已删的 `wms_instance_content`、`occupiedMaterial*`、`lock*`
  - ⚠️ **代码里不能留 `wms_instance_content`**（v1.4 已删除该表）
- **空间域**（v1.1 立，v1.3 字段收敛）：`wms_zone_info` 树 + `wms_zone_material_type` 关联表 + `wms_slot_info`
  - 区域物料类型：**独立表管关系，字典管值域**（`wms_material_type`）；子区域未配置则沿 parent_zone_code 继承
  - slot 双状态正交：`status`(可用态 0启用/1停用) 与 `slot_status`(使用态 FREE/OCCUPIED/LOCKED/CHECKING)
  - **slot 不存占用方**（v1.3）：「谁占用」真相源 = `wms_material_instance.root_slot_id`，按它反查；
    slot 只留 `occupied_qty`（已占标准位数，与 slot_status 同源，=0 ⟺ FREE）
  - **slot 不存锁**（v1.3）：`lock_source/ref_id/time/expire` 整组删除，锁归**调度域**；
    slot 只用 `slot_status` 的 LOCKED/CHECKING 表达「暂不可用」，不带元信息
  - 设备位置：`device_code` + `device_position_no`（v1.3 由 `device_slot_no` 更名）
  - 温区不落 slot：按 `zone_code` 向上取第一个配置了 temp_min/temp_max 的区域
- **实例域**（v1.4 **按原设计重写**）：三张表 = **容器类型 / 内容物定义 / 物料实例**
  - 原设计存档：`.workbuddy/reference/lab-instance-domain-ddl-original.sql`
    （`lab_container_type` / `lab_material_def` / `lab_material_instance`，含完整映射说明）
  - ★★ **核心：位置也是实例**。`hierarchy_role = WELL` 的孔/格位**也是实例**，不是坐标；
    因此内容物 **1:1 内联**在实例表上，**没有独立的实例内容物表**（v1.2 的
    `wms_instance_content` 已在 v1.4 删除）
    - 一块 96 孔板 = 1 行载体实例 + 96 行孔实例；空孔也建行，`content_type = EMPTY`
    - `content_type = EMPTY`（能装但现在是空的）≠ `NULL`（CARRIER，不装东西）
    - 代价：行数膨胀（一块板 97 行）
  - 职责三分：容器类型管「能装什么」/ 内容物定义管「物质是什么」/ 实例管「这一件实物」
  - **16 `wms_container_type`**（32 列）：`category` 物理形态 × `hierarchy_role` 层级角色是
    **两个正交维度**；容量只给 CONTAINER/WELL（`max_vol_ul`），CARRIER 留空并填
    `well_count/rows/cols` + `child_type_code`
    - **嵌套合法性唯一依据 = 目标容器.child_type_code = 子实例的类型编码**（不只靠 nestable）
    - 机械字段：`size_x/y/z_mm`、`well_spacing_mm`（机械臂/多通道移液必需）
    - `device_labware_name` = Tecan i-control 板型映射点
  - **17 `wms_content_def`**（24 列）：只描述「物质」不描述「包装」
    - 「50mL 75% 乙醇瓶」= container_type(BOTTLE_50ML) × content_def(ETHANOL-75PCT)
    - `content_type` = REAGENT/STANDARD/BUFFER/SAMPLE/WASTE/MEDIA/SOLVENT/OTHER
    - `storage_cond` 用枚举（RT/C2_8/F20/Ultra80/FROZTHAW）以便与 zone 温区**程序化匹配**
    - `shelf_life_days`（入库起算）+ `open_life_days`（开封起算）**两个效期并存**
    - `hazard_level` / `cas_no` / `catalog_no` / `supplier` / `require_batch` / `fefo`
  - **18 `wms_material_instance`**（36 列）：
    - 内联内容物组：`content_def_id|code` / `content_type` / `batch_no` / `lot_no` /
      `current_vol_ul` / `current_count` / `concentration`
    - **`batch_no`（内部批次）与 `lot_no`（厂商批号）必须分开**
    - `instance_status` **六态** = AVAILABLE/RESERVED/IN_USE/USED/EXPIRED/DISCARDED
      （`RESERVED` = 步骤已申请未取用，调度依赖）
    - 日期三件套 `received_at` / `opened_at` / `expired_at`
    - 溯源 `source_execution_id` / `source_node_id`（varchar 64，跨模块只认编码）
    - **无 `status` 启用位**（终态是 DISCARDED 不是停用）
    - 树：边 = `parent_instance_id` + `parent_position_code`；`instance_path` 物化路径
      （前缀索引 64）；`root_slot_id` 可空（未落位）；`depth` 上限 16
    - ⚠️ **移动整树必须用路径前缀 UPDATE**（root_slot_id/root_instance_id/path/depth 是整树冗余），
      禁止绕过它单改某一层；跨父搬迁要同事务重写 path 与 depth
    - **不冗余 `zone_code`**（区域属槽位，整盒移动会变），按区查走 root_slot_code
  - **两套位置别混**：`slot` = 空间位置（架子第 3 格，**只有顶层实例有值**）；
    `parent_position_code` = 容器内位置（板子 A1 孔，**本身就是子实例的位置码**）。
    一块 96 孔板占 1 个 slot；内部 96 孔是子实例，不落 slot 表
  - 一期约定 **1 个实例 = 1 个标准位**（供 slot.occupied_qty 累加），跨位实例二期再引入 capacity_units
- 代码生成器：`.workbuddy/tools/wms_codegen.py`（改 TABLES 元数据可整套重生成；**尚未加实例域表**）
- 原设计存档（两份）：
  - `.workbuddy/reference/lab-wms-ddl-v1.0-original.sql`（lab_ 前缀、14 表；无容器模型）
  - `.workbuddy/reference/lab-instance-domain-ddl-original.sql`（实例域三表 + 映射说明）
- 字典脚本：`sql/mysql/wms-dict.sql`（`wms_material_type`：REAGENT/CONSUMABLE/SAMPLE/STANDARD/TOOL）

## WMS 待定项（v1.4 文末已列，后续轮次决策）
- `wms_material_info`：**彻底退场**，还是收敛为「采购 / 出入库记账单元」（N:1 指向 content_def）
- `wms_material_batch`：批次已在实例上（batch_no/lot_no/received_at/opened_at/expired_at），
  本表是否还有必要
- ⚠️ `wms_zone_material_type.material_type` 的字典 `wms_material_type`
  与 `wms_content_def.content_type` **值域高度重合，必须统一**，否则出现两套物料类型值域
- `wms_inventory` 聚合键 `slot × batch` 与「位置即实例」的关系需重新推导

