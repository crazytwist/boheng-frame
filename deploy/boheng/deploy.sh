#!/usr/bin/env bash
# =============================================================================
# boheng 预发环境一键发布脚本
# 用法：
#   ./deploy.sh                 # 更新发布（复用已有 jar/dist，仅重建镜像并部署）
#   ./deploy.sh --build         # 完整构建：mvn package + pnpm build + 镜像 + 部署
#   ./deploy.sh --with-mw       # 连同 MySQL/Redis 镜像一起打包传输（目标离线时用）
#   ./deploy.sh --remote-build  # 只传 jar/dist，由目标服务器原生构建镜像
#                               # 适用于：本机拉不到基础镜像，或本机架构≠目标架构时
#   ./deploy.sh --migrate-only  # 只跑 SQL 增量迁移（不动代码/镜像/容器）
#   ./deploy.sh --skip-migrate  # 发布代码但跳过 SQL 迁移
#
# 前提：
#   1. 先 cp .env.example .env 并填好 SSH_HOST/SSH_PORT/SSH_USER/SSH_KEY 或 SSH_PASSWORD
#   2. 本地已装：Docker、JDK17、Maven、Node/pnpm（--build 时才需要 Maven/Node）
#   3. 目标服务器已装 Docker（及 docker compose 插件）
#
# 数据库迁移（schema history）：
#   自动扫描 sql/mysql/*-migrate.sql，远程写入 _boheng_migrations 表追踪已应用版本。
#   首次跑会预置 wms-sample-migrate.sql / flow-v3-migrate.sql 为「已应用」，
#   因为它们是 init-db.sh 的初始化脚本，或 v2→v3 历史升级脚本。
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# ---------------------------- 参数 ----------------------------
BUILD=0
WITH_MW=0
REMOTE_BUILD=0
MIGRATE_ONLY=0
SKIP_MIGRATE=0
for arg in "$@"; do
  case "$arg" in
    --build) BUILD=1 ;;
    --with-mw) WITH_MW=1 ;;
    --remote-build) REMOTE_BUILD=1 ;;
    --migrate-only) MIGRATE_ONLY=1 ;;
    --skip-migrate) SKIP_MIGRATE=1 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# 互斥：--migrate-only 与 --build/--with-mw/--remote-build/--skip-migrate
if [[ "$MIGRATE_ONLY" == "1" ]]; then
  if [[ "$BUILD" == "1" || "$WITH_MW" == "1" || "$REMOTE_BUILD" == "1" || "$SKIP_MIGRATE" == "1" ]]; then
    echo "❌ --migrate-only 不能与 --build / --with-mw / --remote-build / --skip-migrate 同时使用"
    exit 1
  fi
fi

# 互斥：--remote-build 模式下镜像由服务器自己构建，不再需要本机打包中间件镜像
if [[ "$REMOTE_BUILD" == "1" && "$WITH_MW" == "1" ]]; then
  echo "❌ --remote-build 与 --with-mw 不能同时使用（远程构建时中间件镜像由服务器自行拉取）"
  exit 1
fi

# ---------------------------- 加载 .env ----------------------------
if [[ ! -f "$SCRIPT_DIR/.env" ]]; then
  echo "❌ 缺少 $SCRIPT_DIR/.env"
  echo "   请先执行: cp $SCRIPT_DIR/.env.example $SCRIPT_DIR/.env 并填写 SSH 信息"
  exit 1
fi
set -a; source "$SCRIPT_DIR/.env"; set +a

REMOTE_DIR="${REMOTE_DIR:-/home/megarobo/boheng}"
# 与 .env 的 28 段公网端口对齐（用户实际配置）
PORT_WEB="${PORT_WEB:-28081}"
PORT_SERVER="${PORT_SERVER:-28088}"
PORT_MYSQL="${PORT_MYSQL:-28086}"
PORT_REDIS="${PORT_REDIS:-28082}"

# ---------------------------- SSH 封装 ----------------------------
SSH_HOST="${SSH_HOST:?请在 .env 里填写 SSH_HOST}"
SSH_PORT="${SSH_PORT:-22}"
SSH_USER="${SSH_USER:-root}"

# 注意：ssh 用 -p 指定端口，scp 用 -P（大写）指定端口，二者不可混用
SSH_OPTS=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o ConnectTimeout=15 -p "$SSH_PORT")
SCP_OPTS=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o ConnectTimeout=15 -P "$SSH_PORT")
if [[ -n "${SSH_KEY:-}" ]]; then
  SSH_CMD=(ssh "${SSH_OPTS[@]}" -i "$SSH_KEY" "$SSH_USER@$SSH_HOST")
  SCP_CMD=(scp "${SCP_OPTS[@]}" -i "$SSH_KEY")
elif [[ -n "${SSH_PASSWORD:-}" ]]; then
  if ! command -v sshpass >/dev/null 2>&1; then
    echo "❌ 使用了 SSH_PASSWORD 但本机没有 sshpass。请改用 SSH_KEY，或执行: brew install sshpass"
    exit 1
  fi
  export SSHPASS="$SSH_PASSWORD"
  SSH_CMD=(sshpass -e ssh "${SSH_OPTS[@]}" "$SSH_USER@$SSH_HOST")
  SCP_CMD=(sshpass -e scp "${SCP_OPTS[@]}")
else
  echo "❌ .env 里必须填 SSH_KEY 或 SSH_PASSWORD 之一"
  exit 1
fi

# ==================== 数据库迁移函数 ====================
# 把 sql/mysql/*-migrate.sql scp 到远端，remote 通过 _boheng_migrations 表去重。
# 首次跑预置 wms-sample-migrate.sql / flow-v3-migrate.sql 为「已应用」，
# 因为它们是 init-db.sh 的初始化脚本，或 v2→v3 历史升级脚本。
apply_migrations() {
  if [[ "$SKIP_MIGRATE" == "1" ]]; then
    echo "⏭  跳过数据库迁移（--skip-migrate）"
    return 0
  fi

  # 收集本地待迁移文件
  local -a files=()
  for f in "$SQL_DIR"/*-migrate.sql; do
    [[ -f "$f" ]] && files+=("$(basename "$f")")
  done
  if [[ ${#files[@]} == 0 ]]; then
    echo "⏭  sql/mysql 下没有 *-migrate.sql，跳过"
    return 0
  fi
  IFS=$'\n' files=($(printf '%s\n' "${files[@]}" | sort)) ; unset IFS

  echo "======================================================"
  echo " 数据库迁移（schema history）"
  echo "   候选: ${files[*]}"
  echo "======================================================"

  # 先把候选文件 scp 到远端（apply 步骤需要远端路径）
  "${SSH_CMD[@]}" "mkdir -p '$REMOTE_SQL_DIR'"
  for f in "${files[@]}"; do
    echo "  ▶ 传输 $f"
    "${SCP_CMD[@]}" "$SQL_DIR/$f" "$SSH_USER@$SSH_HOST:$REMOTE_SQL_DIR/$f"
  done

  # 把候选列表写成本地文件，scp 到远端后远程直接 while read
  # 走文件而不是命令行数组：避免 NUL/换行通过 SSH 命令行参数传递的转义噩梦
  local CAND_LOCAL="$SCRIPT_DIR/.candidates.txt"
  printf '%s\n' "${files[@]}" > "$CAND_LOCAL"
  "${SCP_CMD[@]}" "$CAND_LOCAL" "$SSH_USER@$SSH_HOST:/tmp/boheng-migrate-candidates.txt"
  rm -f "$CAND_LOCAL"

  "${SSH_CMD[@]}" bash -s "$REMOTE_SQL_DIR" "$MYSQL_ROOT_PASSWORD" <<'REMOTE'
set -euo pipefail
REMOTE_SQL_DIR="$1"; shift
MYSQL_PWD="$1"; shift

# 历史预置：之前已通过 init-db.sh 或手工执行过的迁移
PRESEED=(
  "wms-sample-migrate.sql"
  "flow-v3-migrate.sql"
)

CAND_FILE="/tmp/boheng-migrate-candidates.txt"

# 等待 MySQL 容器就绪（最多 30s）
for i in {1..30}; do
  if docker exec boheng.mysql mysql -uroot -p"$MYSQL_PWD" -e 'SELECT 1' >/dev/null 2>&1; then
    break
  fi
  echo "  等待 boheng.mysql 就绪 ($i/30) ..."
  sleep 1
done

# 建迁移历史表
docker exec -i boheng.mysql mysql -uroot -p"$MYSQL_PWD" boheng-frame <<'SQL'
CREATE TABLE IF NOT EXISTS `_boheng_migrations` (
  `version`    varchar(128) NOT NULL COMMENT '迁移文件名（含 .sql）',
  `description` varchar(255) NULL     COMMENT '简短说明',
  `applied_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '应用时间',
  PRIMARY KEY (`version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据库迁移历史（由 deploy.sh 维护）';
SQL

# 首次：表为空时预置历史脚本为「已应用」
COUNT=$(docker exec boheng.mysql mysql -uroot -p"$MYSQL_PWD" -N -s -e \
  "SELECT COUNT(*) FROM \`boheng-frame\`._boheng_migrations")
if [[ "$COUNT" == "0" ]]; then
  echo "  首次运行：预置历史脚本为「已应用」"
  for v in "${PRESEED[@]}"; do
    docker exec -i boheng.mysql mysql -uroot -p"$MYSQL_PWD" boheng-frame <<SQL
INSERT IGNORE INTO \`_boheng_migrations\` (\`version\`, \`description\`)
VALUES ('$v', '历史预置：之前已通过 init-db.sh 或手工执行');
SQL
  done
fi

# 按序应用未跑过的迁移
APPLIED=0
SKIPPED=0
while IFS= read -r filename; do
  [[ -z "$filename" ]] && continue
  HAS=$(docker exec boheng.mysql mysql -uroot -p"$MYSQL_PWD" -N -s -e \
    "SELECT COUNT(*) FROM \`boheng-frame\`._boheng_migrations WHERE version='$filename'")
  if [[ "$HAS" != "0" ]]; then
    echo "  ✓ 跳过（已应用）: $filename"
    SKIPPED=$((SKIPPED+1))
    continue
  fi

  REMOTE_PATH="$REMOTE_SQL_DIR/$filename"
  if [[ ! -f "$REMOTE_PATH" ]]; then
    echo "  ⚠️  远端不存在: $filename（传输步骤未成功）"
    continue
  fi

  echo "  ▶ 应用: $filename"
  docker exec -i boheng.mysql mysql -uroot -p"$MYSQL_PWD" --default-character-set=utf8mb4 boheng-frame \
    < "$REMOTE_PATH"
  docker exec -i boheng.mysql mysql -uroot -p"$MYSQL_PWD" boheng-frame <<SQL
INSERT INTO \`_boheng_migrations\` (\`version\`, \`description\`)
VALUES ('$filename', 'deployed via deploy.sh');
SQL
  APPLIED=$((APPLIED+1))
done < "$CAND_FILE"

echo "  应用: $APPLIED 个 · 跳过: $SKIPPED 个"
rm -f "$CAND_FILE"
REMOTE
}

echo "======================================================"
echo " boheng 预发发布"
echo "   目标: $SSH_USER@$SSH_HOST:$SSH_PORT  ->  $REMOTE_DIR"
echo "   端口: web=$PORT_WEB server=$PORT_SERVER mysql=$PORT_MYSQL redis=$PORT_REDIS"
echo "======================================================"

# 数据库迁移在 apply_migrations() 函数里定义；以下是它在主流程里的位置：
# - --migrate-only：单独跑迁移，跳过构建/镜像/容器
# - 默认：构建+部署容器之后调用，把增量 SQL 应用到远端 MySQL
SQL_DIR="$PROJECT_DIR/sql/mysql"
REMOTE_SQL_DIR="$REMOTE_DIR/sql"

# 仅跑迁移：跳过构建/镜像/容器，直接进入迁移段
if [[ "$MIGRATE_ONLY" == "1" ]]; then
  apply_migrations
  exit 0
fi

# ---------------------------- 1. 构建后端 jar ----------------------------
JAR="$PROJECT_DIR/boheng-server/target/boheng-server.jar"
if [[ "$BUILD" == "1" || ! -f "$JAR" ]]; then
  echo "▶ 构建后端 jar ..."
  JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo)}"
  ( cd "$PROJECT_DIR" && mvn -pl boheng-server -am -DskipTests -Dmaven.test.skip=true package -q )
  [[ -f "$JAR" ]] || { echo "❌ 后端 jar 构建失败"; exit 1; }
else
  echo "▶ 复用已有后端 jar（--build 可强制重建）"
fi

# ---------------------------- 2. 构建前端 dist ----------------------------
# build:prod 按 .env.prod 的 VITE_OUT_DIR 输出到 dist-prod
DIST="$PROJECT_DIR/web/dist-prod"
if [[ "$BUILD" == "1" || ! -d "$DIST" ]]; then
  echo "▶ 构建前端 dist ..."
  ( cd "$PROJECT_DIR/web" && pnpm install --frozen-lockfile && pnpm build:prod )
  [[ -d "$DIST" ]] || { echo "❌ 前端构建失败"; exit 1; }
else
  echo "▶ 复用已有前端 dist（--build 可强制重建）"
fi

# ---------------------------- 3. 构建 Docker 镜像 ----------------------------
if [[ "$REMOTE_BUILD" == "1" ]]; then
  # 服务器原生构建：本机只产出架构无关的 jar/dist，镜像交由目标机 build。
  # 适用场景：本机拉不到基础镜像（Docker Hub 不通），或本机架构 ≠ 目标架构。
  echo "▶ [remote-build] 准备远端构建上下文 ..."
  "${SSH_CMD[@]}" "mkdir -p '$REMOTE_DIR/server-ctx/target' '$REMOTE_DIR/web-ctx'"

  echo "  ▶ 传输后端 jar（$(du -h "$JAR" | cut -f1)）..."
  "${SCP_CMD[@]}" "$JAR" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/server-ctx/target/boheng-server.jar"
  "${SCP_CMD[@]}" "$SCRIPT_DIR/Dockerfile.server" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/server-ctx/Dockerfile"

  # 前端 dist 文件多，先本地打 tar 再传，避免逐文件 scp 的开销
  echo "  ▶ 传输前端 dist（$(du -sh "$DIST" | cut -f1 | tr -d ' ')）..."
  DIST_TAR="$SCRIPT_DIR/.dist-prod.tar.gz"
  tar -C "$DIST" -czf "$DIST_TAR" .
  "${SCP_CMD[@]}" "$DIST_TAR" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/web-ctx/dist.tar.gz"
  rm -f "$DIST_TAR"
  "${SCP_CMD[@]}" "$SCRIPT_DIR/nginx.conf" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/web-ctx/nginx.conf"
  "${SCP_CMD[@]}" "$SCRIPT_DIR/Dockerfile.web" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/web-ctx/Dockerfile"
  echo "  ✓ 构建上下文已就绪（镜像将在目标机构建）"
else
  # 目标服务器是 x86_64，本地是 arm64，必须跨架构构建 linux/amd64
  PLATFORM="linux/amd64"
  echo "▶ 构建后端镜像 boheng/server:latest ($PLATFORM) ..."
  docker buildx build --platform "$PLATFORM" -f "$SCRIPT_DIR/Dockerfile.server" -t boheng/server:latest --load "$PROJECT_DIR/boheng-server"

  echo "▶ 构建前端镜像 boheng/web:latest ($PLATFORM) ..."
  WEB_CTX="$SCRIPT_DIR/.build-web"
  rm -rf "$WEB_CTX"; mkdir -p "$WEB_CTX"
  cp -r "$DIST" "$WEB_CTX/dist"
  cp "$SCRIPT_DIR/nginx.conf" "$WEB_CTX/nginx.conf"
  cp "$SCRIPT_DIR/Dockerfile.web" "$WEB_CTX/Dockerfile"
  docker buildx build --platform "$PLATFORM" -t boheng/web:latest --load "$WEB_CTX"

  # ---------------------------- 4. 打包镜像 ----------------------------
  echo "▶ 打包镜像 tar ..."
  TAR="$SCRIPT_DIR/boheng-images.tar"
  docker save boheng/server:latest boheng/web:latest -o "$TAR"

  if [[ "$WITH_MW" == "1" ]]; then
    echo "▶ 追加打包 MySQL/Redis 镜像 ..."
    # mysql 锁定 8.0.32（OL8/x86-64-v1），目标机老 CPU 跑不了 OL9 基座的 mysql:8.0 最新 tag
    docker pull --platform "$PLATFORM" -q mysql:8.0.32 redis:7-alpine
    docker save boheng/server:latest boheng/web:latest mysql:8.0.32 redis:7-alpine -o "$TAR"
  fi
fi

# ---------------------------- 5. 传输到目标 ----------------------------
echo "▶ 传输到目标服务器 ..."
# remote-build 模式下镜像在目标机构建，不需要传 tar
if [[ "$REMOTE_BUILD" != "1" ]]; then
  "${SCP_CMD[@]}" "$TAR" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/boheng-images.tar"
fi
"${SCP_CMD[@]}" "$SCRIPT_DIR/docker-compose.yml" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/docker-compose.yml"
"${SCP_CMD[@]}" "$SCRIPT_DIR/.env" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/.env"

# ---------------------------- 6. 目标端加载并启动 ----------------------------
echo "▶ 目标端加载镜像并启动 ..."
# 显式传 REMOTE_DIR 给远端：单引号 heredoc 不做本地变量展开，
# 远端脚本只能通过 $1 拿到 REMOTE_DIR
"${SSH_CMD[@]}" bash -s "$REMOTE_DIR" "$REMOTE_BUILD" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"; shift
REMOTE_BUILD="$1"; shift
cd "$REMOTE_DIR"

# 镜像来源二选一：本机 buildx 构建则 load tar；remote-build 则由目标机原生构建
if [[ "$REMOTE_BUILD" == "1" ]]; then
  echo "▶ 目标机构建后端镜像 boheng/server:latest ..."
  docker build -t boheng/server:latest server-ctx
  echo "▶ 目标机构建前端镜像 boheng/web:latest ..."
  mkdir -p web-ctx/dist
  tar -xzf web-ctx/dist.tar.gz -C web-ctx/dist
  rm -f web-ctx/dist.tar.gz
  docker build -t boheng/web:latest web-ctx
else
  docker load < boheng-images.tar
fi

# 生成 .env 供 compose 读取（去除 SSH 段，保留端口/密码）
{
  grep -E "^(PORT_|MYSQL_)" .env || true
} > .compose.env

# 兼容 docker compose / docker-compose
COMPOSE="docker compose"
docker compose version >/dev/null 2>&1 || COMPOSE="docker-compose"

# 启动（注意：server/mysql/redis 的 ports 段已在 docker-compose.yml 中默认注释掉，
# 这里传 PORT_* 仅作 fallback；公网仅 web 28081 可达，server/mysql/redis 走 boheng-net 内部互访）
# 注意：加 --force-recreate web 是为了确保端口映射按当前 yml 生效 —— 否则 image 没变时
# 容器端口会"卡"在老值（典型症状：yml 改成 28081 但容器仍跑在 48080）。
PORT_WEB="${PORT_WEB:-28081}" PORT_SERVER="${PORT_SERVER:-28088}" \
PORT_MYSQL="${PORT_MYSQL:-28086}" PORT_REDIS="${PORT_REDIS:-28082}" \
MYSQL_ROOT_PASSWORD="$(grep '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)" \
MYSQL_ROOT_USER="$(grep '^MYSQL_ROOT_USER=' .env | cut -d= -f2- | sed 's/^root$//;s/.*/root/')" \
  $COMPOSE up -d --remove-orphans --force-recreate web

echo "✅ 目标端容器已启动"
$COMPOSE ps
REMOTE

# 数据库迁移（容器起来后跑）
# 注意：必须放在 heredoc 外面调用 —— apply_migrations 是本地函数，
# 而 heredoc 里跑的是远端 bash 进程，看不到本地函数定义。
# 函数内部自己有 SSH 调用，会去远端执行 MySQL 迁移逻辑。
apply_migrations

if [[ "$REMOTE_BUILD" != "1" ]]; then
  rm -f "$TAR"
  rm -rf "$WEB_CTX"
fi

echo ""
echo "======================================================"
echo " 发布完成"
echo "   前端入口:   http://$SSH_HOST:$PORT_WEB   (公网端口)"
echo "   后端 API:   http://$SSH_HOST:$PORT_WEB/admin-api   (前端 Nginx 反代)"
echo "   MySQL:      内部访问 (boheng-net → boheng.mysql:3306)"
echo "   Redis:      内部访问 (boheng-net → boheng.redis:6379)"
echo "   查看日志:   ssh ... docker logs -f boheng.server"
echo "======================================================"
