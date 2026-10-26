# boheng 预发环境部署

> 本目录是 boheng（伯珩基础平台）预发环境的整套 Docker 部署资产。一套脚本把「前端 + 后端 + MySQL + Redis」发布到预发服务器。

## 一、容器命名规则

所有容器统一 `boheng.<组件>` 前缀：

| 容器名 | 组件 | 镜像 |
|---|---|---|
| `boheng.web` | 前端（Nginx：静态托管 + `/admin-api` 反代） | `boheng/web:latest` |
| `boheng.server` | 后端（Spring Boot，JDK17） | `boheng/server:latest` |
| `boheng.mysql` | MySQL 8.0 | `mysql:8.0` |
| `boheng.redis` | Redis 7 | `redis:7` |

配套资源命名：网络 `boheng-net`；数据卷 `boheng-mysql-data`、`boheng-redis-data`。

## 二、端口规则（28 段公网端口）

`.env` 里集中配置，**默认仅 `boheng.web` 对外暴露** —— 后端通过 Nginx 反代 `/admin-api` 访问，MySQL/Redis 只走内部 `boheng-net`：

| 容器 | 公网端口（默认） | 容器端口 | 对外 |
|---|---|---|---|
| `boheng.web` | **28081** | 80 | ✅ **唯一公开入口**（前后端一体） |
| `boheng.server` | （默认关闭） | 48080 | ⚠️ 仅调试（默认走 Nginx 反代） |
| `boheng.mysql` | （默认关闭） | 3306 | ⚠️ 仅调试 |
| `boheng.redis` | （默认关闭） | 6379 | ⚠️ 仅调试 |

> - `PORT_WEB / PORT_SERVER / PORT_MYSQL / PORT_REDIS` 在 `.env` 里覆盖。
> - 调试用：解开 `server/mysql/redis` 服务的 `ports:` 注释即可让公网直连后端 API / MySQL / Redis。
> - 服务器侧只对公网开放 **28081**，所有前端访问与 API 调用都走这一个端口（Nginx 把 `/admin-api/、/app-api/` 反代到内网 boheng.server:48088）。

## 三、目录结构

```
deploy/boheng/
├── docker-compose.yml    整套编排（4 容器）
├── Dockerfile.server     后端镜像（temurin:17-jre）
├── Dockerfile.web        前端镜像（nginx:alpine）
├── nginx.conf            前端 Nginx 配置（反代 /admin-api）
├── .env.example          配置模板（SSH 目标 + 端口 + 密码）
├── deploy.sh             一键发布脚本
├── init-db.sh            数据库首次初始化脚本
└── mysql-init/           首次启动自动执行的 SQL（可选，放 .sql 文件）
```

## 四、使用步骤

### 1. 首次配置

```bash
cd deploy/boheng
cp .env.example .env
# 编辑 .env，填写目标服务器 SSH 信息：
#   SSH_HOST / SSH_PORT / SSH_USER / SSH_KEY（或 SSH_PASSWORD）
#   MYSQL_ROOT_PASSWORD（数据库 root 密码）
```

### 2. 首次部署（建库 + 起服务）

```bash
./init-db.sh     # 建库并导入全部 SQL（仅首次执行一次）
./deploy.sh --build   # 完整构建 + 发布
```

### 3. 日常更新（改动代码后重新发布）

```bash
./deploy.sh            # 复用已有 jar/dist，重建镜像并滚动更新；自动应用 SQL 增量迁移
./deploy.sh --build    # 代码有变更时，强制 mvn + pnpm 重新构建
./deploy.sh --skip-migrate  # 只发代码，跳过数据库迁移
./deploy.sh --migrate-only  # 只跑 SQL 增量（不动代码/镜像/容器）
```

### 4. 数据库迁移（schema history）

`deploy.sh` 每次发布都会扫描 `sql/mysql/*-migrate.sql` 并自动应用到远端 MySQL。
迁移追踪表 `boheng-frame._boheng_migrations` 记录已应用版本，重复运行不会重放。

- 文件名加 `-migrate.sql` 后缀的才会被自动扫到（按文件名排序）
- 写新迁移时请使用 `information_schema` 检查列/表是否已存在，保持幂等
- 历史脚本 `wms-sample-migrate.sql` / `flow-v3-migrate.sql` 首次运行会预置为「已应用」，
  因为它们由 `init-db.sh` 或 v2→v3 升级流程执行过

### 5. 离线目标（目标机拉不到 Docker Hub 时）

```bash
./deploy.sh --with-mw  # 连同 mysql/redis 镜像一起打包传输
```

## 五、访问地址

```
前端入口:  http://<SSH_HOST>:28081          ← 唯一公网端口
后端 API:  http://<SSH_HOST>:28081/admin-api  ← 同端口，由前端 Nginx 反代
```

> 服务器侧只开 **28081** 一个公网端口。docker compose 里 web 容器端口映射 `${PORT_WEB:-28081}:80`，后端通过 Nginx 反代 `/admin-api/、/app-api/` 访问 `boheng.server:48088`。**改 web 端口必须同步通知服务器侧开端口**，否则会立刻掉线。

## 六、说明

- **数据库**：库名 `boheng-frame`（含连字符，SQL 里已用反引号）。`init-db.sh` 用推荐顺序导入：`ruoyi-vue-pro.sql`（基座）→ `wms-final.sql`（wms，含菜单/字典/流水/唯一索引，无需再跑 wms-menu/wms-movement 等已并入脚本）→ `wms-sample-menu-fix.sql` → device 系列 → flow 系列。
- **后端配置**：用 `SPRING_PROFILES_ACTIVE=local`，数据源/Redis 的 host 由 compose 环境变量覆盖为容器名（`boheng.mysql` / `boheng.redis`）。
- **JDK**：后端镜像是 `eclipse-temurin:17-jre`（项目 JDK17，与旧 Dockerfile 的 21 不一致，已纠正）。
- **脚本前提**：本机装 Docker；`--build` 时还需 JDK17 + Maven + Node/pnpm；目标机装 Docker（含 compose 插件）。
