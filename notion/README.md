# 伯珩项目设计资料同步

本目录同步整理 Notion「伯珩」项目下的架构、领域设计和 Transport V1.0 资料。

## Notion 来源

- [智慧实验室（自动化调度）](https://app.notion.com/p/4055050e7ea38050b39ee1e2e419f7f8?pvs=204)
- [总体架构](https://app.notion.com/p/4055050e7ea381c7b29ee82296aa9787?pvs=204)
- [WMS 物料模块](https://app.notion.com/p/4055050e7ea38195bdc3fa3925cbb75a?pvs=204)
- [Device 设备模块](https://app.notion.com/p/4055050e7ea38170877fcd598037d9f0?pvs=204)
- [Flow 流程模块](https://app.notion.com/p/4055050e7ea3817ba3cafea179e677e0?pvs=204)
- [Scheduler 调度模块](https://app.notion.com/p/4055050e7ea38118bebdc3e428f6ab81?pvs=204)
- [Transport 与资源调度设计（补充）](https://app.notion.com/p/4055050e7ea381b28ea7ca0b962340fa?pvs=204)
- [Data 数据模块](https://app.notion.com/p/4055050e7ea3813a9fbcf96ed38b0ae0?pvs=204)
- [Workflow 呈现模块](https://app.notion.com/p/4055050e7ea3812885ffef685ff4ef13?pvs=204)
- [Transport 运输域终版设计](https://app.notion.com/p/4055050e7ea3811e8568fd60b3ec0596?pvs=204)

## 本地文件映射

| 本地文件 | 对应内容 |
|---|---|
| `00-整体架构分析.md` | 总体架构与模块边界 |
| `01-WMS物料模块.md` | WMS 物料、槽位和流水设计 |
| `02-Device设备模块.md` | Device 设备、命令和状态设计 |
| `03-Flow流程模块.md` | Flow 流程定义与执行设计 |
| `04-Scheduler调度模块.md` | Scheduler、Resource 和求解器设计 |
| `05-Data数据模块.md` | 数据采集、治理、处理和资产设计 |
| `06-Workflow呈现模块.md` | Workflow、流程定义和业务呈现设计 |
| `07-Transport运输域终版设计.md` | Transport 运输域终版设计 |
| `04-Transport与资源调度设计.md` | Transport 早期讨论稿（补充材料） |
| `05-Transport-V1-DDL.sql` | Transport V1.0 十张表 DDL |

仓库中的 `doc/` 和 `sql/mysql/` 保留为正式源码文档与数据库脚本；本目录用于集中查看和同步 Notion 设计资料。

同步时间：2026-10-27
