# 04｜Scheduler 调度模块

## 模块定位

Scheduler 是资源决策面，负责回答“何时执行、使用什么资源、采用什么顺序”。它不直接驱动设备，也不维护 WMS 或 Device 的业务主数据。

## V1.0 边界

当前 Transport V1.0 使用本地分配器和本地锁；Scheduler 先保留契约，不作为运行前置依赖。

未来 Scheduler 内部分为：

- Resource Core：资源注册、能力、容量、互斥、租约、续租和释放；
- Planner/Solver：候选过滤、约束求解、评分、排程和动态重排。

## 调度链路

```text
Workflow / Transport 提交调度请求
        ↓
候选资源过滤
        ↓
硬约束校验
        ↓
软目标评分
        ↓
生成 Allocation Plan
        ↓
Resource Lease 原子占用
        ↓
交给 Frame / Transport 执行
```

## 关键原则

- Solver 只提出方案，Lease 才是资源占用事实。
- 资源使用 `owner_module + owner_type + owner_id` 弱引用业务对象。
- 支持 `OFF / ADVISORY / ENFORCED` 渐进接入。
- 先使用规则过滤与评分，数据稳定后再引入复杂求解器。
- 方案必须保存输入快照、约束、评分、淘汰原因和版本，保证可解释和可回放。

## 后续阶段

V1.1 建设统一 Resource Lease；V1.2 建设多车分配、任务排序和简单插单；V2.0 扩展时间窗、路权、充电和动态重规划。
