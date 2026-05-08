#!/usr/bin/env bash
# DNAcloud — MySQL 8 安装和初始化脚本（Ubuntu 20.04/22.04/24.04）
# 用法：sudo bash setup-mysql.sh
# 执行后会创建数据库 dnacloud 和用户 dnacloud，并打印连接信息

set -euo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

[[ $EUID -ne 0 ]] && error "请用 sudo 执行此脚本"

# 生成随机密码（32位）
DB_PASS=$(tr -dc 'A-Za-z0-9!@#$%^&*' < /dev/urandom | head -c 32 || true)

# ─── 安装 MySQL ───────────────────────────────────────────────────────────────
if command -v mysql &>/dev/null; then
  info "MySQL 已安装，跳过安装步骤"
else
  info "安装 MySQL 8 ..."
  apt-get update -qq
  apt-get install -y -qq mysql-server
  systemctl enable mysql
  systemctl start mysql
  info "MySQL 安装完成"
fi

# ─── 创建数据库和用户 ─────────────────────────────────────────────────────────
info "创建数据库 dnacloud 和用户 dnacloud ..."
mysql -u root <<SQLEOF
CREATE DATABASE IF NOT EXISTS dnacloud
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'dnacloud'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON dnacloud.* TO 'dnacloud'@'localhost';
FLUSH PRIVILEGES;
SQLEOF

info "数据库初始化完成"

# ─── 输出连接信息 ─────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  MySQL 配置完成，请将以下内容写入 /opt/dnacloud/.env${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "DB_URL=jdbc:mysql://127.0.0.1:3306/dnacloud?useSSL=false&serverTimezone=UTC&characterEncoding=utf8mb4"
echo "DB_DRIVER=com.mysql.cj.jdbc.Driver"
echo "DB_USERNAME=dnacloud"
echo "DB_PASSWORD=${DB_PASS}"
echo "JPA_DDL_AUTO=create"
echo "JPA_DIALECT=org.hibernate.dialect.MySQLDialect"
echo ""
warn "首次启动用 JPA_DDL_AUTO=create（自动建表），启动成功后改为 validate"
warn "请妥善保存上面的密码，此脚本不会再次显示"
echo ""
