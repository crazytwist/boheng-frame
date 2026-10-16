# boheng-frame 项目长期记忆

## 环境与构建
- 开发目录 `/Users/yinan/workspace/yinan/boheng/boheng-frame`（main 分支）；旧路径 `/Volumes/External_1TB/Projects/boheng-frame` 仅盘挂载时可用
- 前端在仓库内 `web/`（非独立仓库）；类型检查 `cd web && npm run ts:check`
- Maven `/Users/yinan/workspace/apache-maven-3.9.12/bin/mvn`（不在 PATH）；JDK17=`ms-17.0.17`（非 0.18）
- 构建：`JAVA_HOME=/Users/yinan/Library/Java/JavaVirtualMachines/ms-17.0.17/Contents/Home mvn -pl <module> -am -DskipTests compile`
- 单模块测试必须带 `-am`；指定 -Dtest 加 `-Dsurefire.failIfNoSpecifiedTests=false`
- macOS BSD grep 不支持 `\|` 分支，校验用 `grep -nE "a|b"`
- 工程 JDK17 + Spring Boot 3.5.15，命名空间 **jakarta.***（非 javax.*）

## 底座约定（写代码必守）
- 实体：租户表 `TenantBaseDO`（含 tenantId）；非租户表 `BaseDO`+`@TenantIgnore`（判定在 `TenantDatabaseInterceptor.computeIgnoreTable()`）
- 审计列固定：creator/create_time/updater/update_time/deleted bit(1) b'0'
- MP：logic-delete=1/0，id-type NONE；主键雪花显式 `@TableId(ASSIGN_ID)`
- **全库 0 个 UNIQUE**：普通索引 + Service 层唯一校验
- Mapper 继承 BaseMapperX + LambdaQueryWrapperX；VO 用 SaveReqVO/PageReqVO(extends PageParam)/RespVO
- 错误码段：infra 1-001 / system 1-002 / wms 1-003
- Controller：`/admin-api` 前缀框架加；app 侧类名加 **App 前缀**（避免同名 bean 撞车），admin 侧不变
- 模块装配：根 pom `<modules>` + boheng-server/pom.xml 依赖；扫描覆盖 `cn.boheng.frame.module`

## ★ 菜单 ID 段（写菜单 SQL 前必查 MAX(id)）
- WMS 物料空间目录 1348；WMS 菜单 1379-1421
- 设备域 12762-12797；流程域 12810-12833；样本域 12834-12838
- 教训：样本曾误用 12762-12766 撞设备域，已修

## ★ 本地 MySQL + SQL 执行约定
- 本地 MySQL=OrbStack `mysql-local`(8.0)，库 `boheng-frame`，root/root（非 root1234）
- 我直接执行 SQL（不再让用户跑）：`docker exec -i mysql-local mysql -uroot -proot --default-character-set=utf8mb4 -D boheng-frame < xxx.sql`
- 必带 `--default-character-set=utf8mb4`（否则中文乱码）；用户侧用 HexHub 连同一库

## 个人基座（伯珩）
- 署名伯珩（拼音 Boheng，曾用名一南）；groupId cn.boheng / artifactId boheng-frame / 包根 cn.boheng.frame / 标题 伯珩基础平台
- 改名器存档 `.workbuddy/tools/ProjectReactor.java`；派生只改 4 个 New 变量

## 模块现状
### Flow v3（能力×编排正交）
- flow_step=能力目录（step_type+type_config，控制类拒入）；flow_node 统一承载能力+控制类节点（route/loop/subflow/timer.wait，stepId 空）
- FlowDraftAssembler 是类型生效规则唯一实现；运行层五表+引擎（CAS/DAG/分支裁剪/循环展开/FANOUT 幂等/信号收件箱/Sweeper）
- 权限 12827-12831；SQL：flow-definition.sql / flow-v3-migrate.sql / flow-execution.sql
- 日志标准化：FlowLogContextHolder+FlowLogConverter（%flow 转换词，`[exe= node=]` 前缀）

### device.task 异步任务（拉取→执行→回报，external-task 模式）
- 表 flow_device_task；状态 PENDING/CLAIMED/DONE/FAILED/CANCELLED；CAS 认领防多机重复
- 认领：deviceCode 必传 × actionCode 可选，经 device-api listActionCodesByDevice 反查能力集匹配
- 接口 /app-api/flow/device-task claim/complete（@PermitAll）；admin /page
- 设备类型用 system_dict_type 字典（dict_type='device_type'，不建表），能力挂类型、实例继承
- 前端节点徽标 deviceTaskStatus（待领取/执行中/完成/失败/已取消）

### 样本域（WMS 内 sample 子域）
- 三层：wms_content_def → wms_sample_info → wms_material_instance.sample_id
- 八态 SampleStatusEnum（REGISTERED/LOADED/IN_PROCESS/SPLIT/COMPLETED/ARCHIVED/REJECTED/LOST）与实例六态正交
- 契约层 boheng-module-wms-api（SampleApi + SampleStatus），flow 只依赖契约层
- 谱系五件套：parentSampleId/rootSampleId/generation/deriveType(ORIGINAL/SPLIT/ALIQUOT/MERGE/ADD_REAGENT)
- SQL wms-sample-migrate.sql（增量幂等，菜单 12834-12838）

### WMS（18 表，DDL v1.4）
- 空间域：wms_zone_info 树 + wms_zone_material_type + wms_slot_info；slot 不存占用方/锁（真相源=instance.root_slot_id）
- 实例域三表：container_type/content_def/material_instance；**位置也是实例**，内容物 1:1 内联
- 实例六态 AVAILABLE/RESERVED/IN_USE/USED/EXPIRED/DISCARDED
- ⚠️ Java 侧欠账停在 v1.2 字段（wms_codegen.py 未加实例域表），代码不能留 wms_instance_content
- 代码生成器 .workbuddy/tools/wms_codegen.py；原设计存档 .workbuddy/reference/

## 部署（预发 大兴）
- deploy/boheng/：4 容器 boheng.web / boheng.server / boheng.mysql / boheng.redis，端口走 .env 变量 PORT_WEB/SERVER/MYSQL/REDIS
- **28 段公网端口**（用户 .env）：PORT_WEB=28081 / PORT_SERVER=28088 / PORT_MYSQL=28086 / PORT_REDIS=28082
- 目标 123.59.127.6:32223 (megarobo)，REMOTE_DIR=/home/megarobo/boheng；跨架构 buildx --platform linux/amd64
- ★ 目标 CPU 是 Intel Core 2 Duo T7700（缺 POPCNT，仅 **x86-64-v1**）→ MySQL 锁 `mysql:8.0.32`（OL8.7+glibc2.28），不能用 mysql:8.0 最新 tag（OL9 要 x86-64-v2 会崩溃循环）
- ★ 预发是**共享多租户机**：SSH 登录的是内网 10.10.2.100（无公网 IP），123.59.127.6 是公网网关
- ★ 容器端口暴露规则：仅 web 对外暴露 28081:80，server/mysql/redis 默认不暴露公网（docker-compose.yml 里 ports 段默认注释）；后端 API 走 Nginx 反代 `/admin-api/`、`/app-api/` → boheng.server:48080
- ★ 服务器侧公网**只开放 28081 一个端口**；28088/28086/28082（server/mysql/redis 的公网映射）服务器侧未开，公网探测均 Connection refused（正常）
- ★ 构建坑：WorkBuddy 注入 `NODE_OPTIONS=--require=node-language-shim.cjs`，长构建大量 `write EPIPE` 失败 → 构建前 `unset NODE_OPTIONS`
- ★ 镜像坑：`docker create ... sleep 1` 后 commit 会继承 `sleep 1` 为 CMD，容器秒退崩溃循环 → create 时用 `nginx -g 'daemon off;'` 或 commit 加 `--change 'CMD [...]'`
- ★ 部署坑：`docker compose up -d` 在 image 没变时不重建容器，**端口变更不会刷新** → 改 yml 的 ports 后必须 `--force-recreate web`（症状：`docker compose config` 是 28081，但容器仍跑在老端口 48080）
- 验证：admin/admin123 登录（userId=1 超管）→ wms sample 3/container-type 2/material-instance 53；device/info 11/action 13/device-task 10；flow/definition 4，全部 code:0
- banner「[WMS - 已禁用]」是芋道基座 BannerApplicationRunner 对原版 cn.iocoder.yudao 包类存在性检查的**误报**（我们的 WMS 在 cn.boheng.frame.module.wms 包，类名对不上），实际路由全注册
