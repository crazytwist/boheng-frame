# Transport 运输域 V1.0

Transport 是面向“物料或载具转移”的场景模块。AGV 是 V1.0 的首个执行载体，但不作为领域名称；AGV 设备档案、厂商协议和设备命令由 Device 模块负责。

## 当前范围

- 运输需求、执行单、运输对象、停靠点和原子动作的跨模块契约；
- 通过 `TransportExecutor` 扩展 AGV、输送线、机械臂和人工等执行载体；
- V1.0 使用本地分配和本地资源锁，不依赖独立 Scheduler/Resource 服务；
- 后续通过 `AllocationService`、`ResourceLeaseService` 切换到 Scheduler/Resource。

## V1.0 实施顺序

1. 模块骨架与 API 契约（已完成）；
2. 状态枚举、领域表和迁移脚本；
3. 需求受理、幂等和执行单创建；
4. Stop/Operation 运行时状态机；
5. 本地执行器分配；
6. 本地资源锁、续租、过期回收和 fencing token；
7. Device 命令适配；
8. WMS 双边交接和位置更新；
9. 重试、补偿、事件和重启恢复；
10. 单元、集成和仿真测试。

详细设计与验收标准见项目文档：[虚拟资源与调度求解器设计备忘](../doc/08-虚拟资源与调度求解器设计备忘.md)。
