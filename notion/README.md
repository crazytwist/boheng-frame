# 伯珩项目设计资料同步

本目录同步整理 Notion「伯珩」项目下的架构、领域设计和 Transport V1.0 资料。

## Notion 来源

- [伯珩](https://app.notion.com/p/40504aea1758813fa182c095b6b8c921?pvs=204)
- [V1.0](https://app.notion.com/p/40504aea1758812d84f3e7f23986d04d?pvs=204)
- [总体架构](https://app.notion.com/p/40504aea1758815895aec0cb0652ff84?pvs=204)
- [WMS 物料模块](https://app.notion.com/p/40504aea175881aa904ce7bc2781b111?pvs=204)
- [Device 设备模块](https://app.notion.com/p/40504aea175881918172c71092dc1665?pvs=204)
- [Flow 流程模块](https://app.notion.com/p/40504aea17588187a126d9621a98c32c?pvs=204)
- [Scheduler 调度模块](https://app.notion.com/p/40504aea175881b7b533f9dbed4a7094?pvs=204)
- [Data 数据模块](https://app.notion.com/p/40504aea175881d68358ccdc8b2285c1?pvs=204)
- [Workflow 展现模块](https://app.notion.com/p/40504aea175881a2905ff8404c257ecb?pvs=204)
- [Transport 运输域终版设计](https://app.notion.com/p/40504aea17588176be28c94a40e8f8b2?pvs=204)

## 本地文件映射

| 本地文件 | 对应内容 |
|---|---|
| `00-整体架构分析.md` | 总体架构与模块边界 |
| `01-WMS物料模块.md` | WMS 物料、槽位和流水设计 |
| `02-Device设备模块.md` | Device 设备、命令和状态设计 |
| `03-Flow流程模块.md` | Flow 流程定义与执行设计 |
| `04-Transport与资源调度设计.md` | Transport、资源和调度演进方案 |
| `05-Transport-V1-DDL.sql` | Transport V1.0 十张表 DDL |

仓库中的 `doc/` 和 `sql/mysql/` 保留为正式源码文档与数据库脚本；本目录用于集中查看和同步 Notion 设计资料。

同步时间：2026-10-27
