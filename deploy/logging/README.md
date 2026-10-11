# 轻量级日志收集（Loki + Alloy + Grafana）

单机版日志收集平台，专门为低配服务器（如 N100）设计，全栈内存占用约 300–500MB。

## 架构

```
Spring Boot 应用（boheng-frame）
        │  Logback 写文件 ~/logs/boheng-server.log（含 [tenant:1] 字段）
        ▼
Grafana Alloy（采集器）   —— tail 文件 + 提取 tenant 标签
        │  HTTP push
        ▼
Loki（存储，只索引标签）
        │  LogQL 查询
        ▼
Grafana（查询看板，已自动配好 Loki 数据源）
```

## 快速开始

```bash
cd deploy/logging

# 1. 改 .env 的 LOG_DIR，指向应用日志目录（N100 默认 /home/yinan/logs）
# 2. 启动
docker compose up -d

# 3. 查看状态
docker compose ps
```

- Grafana：http://<host>:3000 （admin / admin，见 .env）
- Loki HTTP：http://<host>:3100
- Alloy 调试 UI：http://<host>:12345

## 组件版本（2026-10 稳定版）

| 组件 | 镜像 | 端口 |
|---|---|---|
| Loki | grafana/loki:3.7.7 | 3100 |
| Grafana Alloy | grafana/alloy:v1.19.2 | 12345 |
| Grafana | grafana/grafana:12.4.1 | 3000 |

> Promtail 已于 2026-03 EOL，新装统一用 Alloy。

## Spring Boot 侧已做的改动

1. `TenantLogConverter`：从 `TenantContextHolder.getTenantId()` 读当前租户，日志里输出 `[tenant:1]`（非租户线程输出 `[tenant:]`）。
2. `logback-spring.xml`：注册 `%tenantId` 转换词，`FILE_LOG_PATTERN` 追加 `[tenant:%tenantId]`。
3. Alloy 用正则 `tenant:(?P<tenant>[0-9]+)` 把 tenantId 提取为 Loki 的 `tenant` 标签。

## LogQL 查询示例

```logql
# 某服务全部日志
{service="boheng-server"}

# 按租户隔离
{service="boheng-server", tenant="1"}

# 按级别 + 关键字
{service="boheng-server"} |= "ERROR" |~ "FlowExecution"

# 最近 5 分钟
{service="boheng-server"} | json
```

## 调优 / 说明

- **保留期**：默认 30 天（`config/loki/local-config.yaml` 的 `retention_period: 720h`）。
- **label 低基数原则**：只有 `service/env/tenant` 作 label，正文里的变量值用 `|=` / `|~` 过滤，不要新增高基数标签（否则流爆炸、Loki 变慢）。
- **加服务**：新服务日志也在同一目录时，可在 `config/alloy/config.alloy` 里加一个 targets 项并改 `service` 标签。
- **本地 Mac（OrbStack）**：`.env` 的 `LOG_DIR` 改成 `/Users/yinan/logs` 即可。
