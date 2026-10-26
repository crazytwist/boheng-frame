# 06｜Workflow / 呈现模块

## 模块定位

Workflow 是业务控制面和用户呈现入口，负责项目、实验、流程定义、参数、审批、执行状态、告警、结果和报告。

## 核心职责

- 创建项目和实验任务；
- 管理流程定义、版本和输入参数；
- 提交 Scheduler 调度请求；
- 启动、暂停、取消和查看实验执行；
- 汇总 Frame 的节点进度和 Transport 的运输结果；
- 展示 Data 资产、质量状态、分析结果和业务报告。

## 边界

```text
Workflow：定义做什么、为什么做、业务进展如何
Frame：怎样可靠执行流程节点
Scheduler：使用什么资源、何时执行
Data：实验数据如何成为可信资产
```

Workflow 不直接读取设备表、库位表或资源锁判断能否执行，所有执行许可必须通过 Scheduler/Frame/Transport 的正式接口。

## 端到端链路

```text
用户设计实验
    ↓
Workflow 保存流程版本和业务参数
    ↓
提交调度请求
    ↓
Scheduler 返回 Allocation Plan
    ↓
Frame 创建执行快照并运行
    ↓
Device / WMS / Transport 执行物理动作
    ↓
Data 返回资产引用
    ↓
Workflow 汇总进度、结果和报告
```

## V1.0 原则

先保持现有 Flow 能力作为 Frame 内运行模块；流程定义和运行实例边界清晰后，再逐步拆分 Workflow 控制面和 Frame 执行面。
