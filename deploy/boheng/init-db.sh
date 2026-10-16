#!/usr/bin/env bash
# =============================================================================
# boheng 预发环境 · 数据库首次初始化脚本
# 用法：./init-db.sh
# 作用：把 sql/mysql 下的核心 SQL 按顺序传到目标并导入到 boheng-frame 库。
# 注意：仅首次部署执行一次；后续更新不要重复执行（会 DROP TABLE）。
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
SQL_DIR="$PROJECT_DIR/sql/mysql"

# ---------------------------- 加载 .env ----------------------------
if [[ ! -f "$SCRIPT_DIR/.env" ]]; then
  echo "❌ 缺少 $SCRIPT_DIR/.env，请先 cp .env.example .env 并填写 SSH 信息"
  exit 1
fi
set -a; source "$SCRIPT_DIR/.env"; set +a

REMOTE_DIR="${REMOTE_DIR:-/opt/boheng}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:?请在 .env 里填写 MYSQL_ROOT_PASSWORD}"

SSH_HOST="${SSH_HOST:?请在 .env 里填写 SSH_HOST}"
SSH_PORT="${SSH_PORT:-22}"
SSH_USER="${SSH_USER:-root}"

SSH_OPTS=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o ConnectTimeout=15 -p "$SSH_PORT")
SCP_OPTS=(-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o ConnectTimeout=15 -P "$SSH_PORT")
if [[ -n "${SSH_KEY:-}" ]]; then
  SSH_CMD=(ssh "${SSH_OPTS[@]}" -i "$SSH_KEY" "$SSH_USER@$SSH_HOST")
  SCP_CMD=(scp "${SCP_OPTS[@]}" -i "$SSH_KEY")
elif [[ -n "${SSH_PASSWORD:-}" ]]; then
  export SSHPASS="$SSH_PASSWORD"
  SSH_CMD=(sshpass -e ssh "${SSH_OPTS[@]}" "$SSH_USER@$SSH_HOST")
  SCP_CMD=(sshpass -e scp "${SCP_OPTS[@]}")
else
  echo "❌ .env 里必须填 SSH_KEY 或 SSH_PASSWORD 之一"
  exit 1
fi

# ---------------------------- 导入顺序（可按需调整） ----------------------------
# wms 的 menu/movement/slot-unique 已并入 wms-final.sql，故不单独执行
# device-v4-upgrade.sql / flow-v3-migrate.sql 是存量库升级脚本，首次部署不需要
SQLS=(
  ruoyi-vue-pro.sql
  quartz.sql
  wms-final.sql
  wms-dict.sql
  wms-sample-migrate.sql
  device.sql
  device-dict.sql
  device-menu.sql
  device-login.sql
  device-callback.sql
  device-runtime-menu.sql
  device-invoke-menu.sql
  device-simulate-menu.sql
  flow-definition.sql
  flow-execution.sql
  flow-menu.sql
  flow-execution-menu.sql
  flow-device-task.sql
  flow-preview-menu.sql
)

echo "======================================================"
echo " boheng 数据库初始化 -> $SSH_USER@$SSH_HOST"
echo " 目标库: boheng-frame"
echo "======================================================"

# ---------------------------- 传输 SQL ----------------------------
"${SSH_CMD[@]}" "mkdir -p $REMOTE_DIR/sql"
for f in "${SQLS[@]}"; do
  if [[ -f "$SQL_DIR/$f" ]]; then
    echo "▶ 传输 $f"
    "${SCP_CMD[@]}" "$SQL_DIR/$f" "$SSH_USER@$SSH_HOST:$REMOTE_DIR/sql/$f"
  else
    echo "⚠️  跳过（不存在）: $f"
  fi
done

# ---------------------------- 目标端导入 ----------------------------
echo "▶ 目标端依次导入 ..."
"${SSH_CMD[@]}" bash -s "$REMOTE_DIR" "$MYSQL_ROOT_PASSWORD" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
PASS="$2"
cd "$REMOTE_DIR/sql"
for f in ruoyi-vue-pro.sql quartz.sql wms-final.sql wms-dict.sql wms-sample-migrate.sql \
         device.sql device-dict.sql device-menu.sql device-login.sql device-callback.sql \
         device-runtime-menu.sql device-invoke-menu.sql device-simulate-menu.sql \
         flow-definition.sql flow-execution.sql flow-menu.sql flow-execution-menu.sql \
         flow-device-task.sql flow-preview-menu.sql; do
  if [[ -f "$f" ]]; then
    echo "  → 导入 $f"
    docker exec -i boheng.mysql mysql -uroot -p"$PASS" boheng-frame < "$f"
  fi
done
echo "✅ 数据库初始化完成"
REMOTE

echo ""
echo "======================================================"
echo " 数据库初始化完成，可执行 ./deploy.sh 启动应用"
echo "======================================================"
