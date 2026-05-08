#!/usr/bin/env bash
# DNAcloud Server — 一键部署脚本（Ubuntu 20.04 / 22.04 / 24.04）
# 用法：sudo bash deploy.sh [--domain api.yourdomain.com] [--jar /path/to/app.jar]
# 执行前请先复制并填好 .env.example -> .env，脚本会自动加载

set -euo pipefail

# ─── 可配置参数 ────────────────────────────────────────────────────────────────
APP_USER="dnacloud"
APP_DIR="/opt/dnacloud"
JAR_NAME="dnacloud-server.jar"
SERVICE_NAME="dnacloud"
JAVA_VERSION="17"
DOMAIN=""          # 留空则跳过 Nginx 配置
JAR_SRC=""         # 留空则尝试从 ./server/target/ 复制

# 解析命令行参数
while [[ $# -gt 0 ]]; do
  case "$1" in
    --domain) DOMAIN="$2"; shift 2 ;;
    --jar)    JAR_SRC="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

# ─── 颜色输出 ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ─── 权限检查 ──────────────────────────────────────────────────────────────────
[[ $EUID -ne 0 ]] && error "请用 sudo 执行此脚本"

# ─── 加载 .env ─────────────────────────────────────────────────────────────────
ENV_FILE="$(dirname "$0")/../.env"
if [[ -f "$ENV_FILE" ]]; then
  info "加载 .env: $ENV_FILE"
  set -a; source "$ENV_FILE"; set +a
else
  warn ".env 文件不存在，将使用环境变量。建议先复制 .env.example -> .env 并填写配置。"
fi

# ─── Step 1: 安装 Java 17 ──────────────────────────────────────────────────────
info "Step 1/7: 检查 Java $JAVA_VERSION ..."
if java -version 2>&1 | grep -q "version \"$JAVA_VERSION"; then
  info "Java $JAVA_VERSION 已安装，跳过"
else
  info "安装 OpenJDK $JAVA_VERSION ..."
  apt-get update -qq
  apt-get install -y -qq openjdk-${JAVA_VERSION}-jre-headless
  java -version
fi

# ─── Step 2: 创建系统用户和目录 ────────────────────────────────────────────────
info "Step 2/7: 创建用户 $APP_USER 和目录 $APP_DIR ..."
if ! id "$APP_USER" &>/dev/null; then
  useradd --system --no-create-home --shell /bin/false "$APP_USER"
  info "用户 $APP_USER 已创建"
fi

mkdir -p "$APP_DIR"/{artifacts,data,logs}
chown -R "$APP_USER":"$APP_USER" "$APP_DIR"
chmod 750 "$APP_DIR"

# ─── Step 3: 复制 jar ──────────────────────────────────────────────────────────
info "Step 3/7: 部署 jar 文件 ..."
if [[ -z "$JAR_SRC" ]]; then
  # 自动查找 server/target 下的 jar
  JAR_SRC=$(find "$(dirname "$0")/../server/target" -name "dnacloud-server-*.jar" \
            ! -name "*sources*" 2>/dev/null | head -1)
fi
[[ -z "$JAR_SRC" ]] && error "找不到 jar 文件，请先运行 'cd server && mvn package -DskipTests'，或用 --jar 参数指定路径"
[[ ! -f "$JAR_SRC" ]] && error "指定的 jar 不存在: $JAR_SRC"

cp "$JAR_SRC" "$APP_DIR/$JAR_NAME"
chown "$APP_USER":"$APP_USER" "$APP_DIR/$JAR_NAME"
info "jar 已复制到 $APP_DIR/$JAR_NAME"

# ─── Step 4: 写入 .env（若 $APP_DIR/.env 不存在）──────────────────────────────
info "Step 4/7: 检查应用配置 ..."
if [[ ! -f "$APP_DIR/.env" ]]; then
  warn "$APP_DIR/.env 不存在，从模板创建（请手动填写各项配置）"
  cat > "$APP_DIR/.env" <<'ENVEOF'
# ========== DNAcloud Server 环境变量 ==========
# 请填写所有 <REPLACE_ME> 项后重启服务

SERVER_PORT=8080

# 数据库（默认 H2 文件模式，生产建议换 MySQL）
# DB_URL=jdbc:mysql://127.0.0.1:3306/dnacloud?useSSL=true&serverTimezone=UTC&characterEncoding=utf8
# DB_DRIVER=com.mysql.cj.jdbc.Driver
# DB_USERNAME=dnacloud
# DB_PASSWORD=<REPLACE_ME>
# JPA_DDL_AUTO=validate
# JPA_DIALECT=org.hibernate.dialect.MySQLDialect

# artifact 存储
DNACLOUD_ARTIFACT_STORE=/opt/dnacloud/artifacts

# 公网地址（下载链接前缀，必须填写）
DNACLOUD_BASE_URL=https://<REPLACE_ME>

# OKX OnchainOS 支付凭证（购买 DNA 包时验证用）
OKX_API_KEY=<REPLACE_ME>
OKX_SECRET_KEY=<REPLACE_ME>
OKX_PASSPHRASE=<REPLACE_ME>

# 平台收款地址
DNACLOUD_PAYMENT_ADDRESS=0x<REPLACE_ME>

# Admin API 保护（32+ 字符随机字符串）
DNACLOUD_ADMIN_API_KEY=<REPLACE_ME>

# 包签名密钥（64+ 字符随机字符串）
DNACLOUD_SIGNING_KEY=<REPLACE_ME>

# CORS（前端域名，多个用逗号分隔）
DNACLOUD_CORS_ORIGINS=https://<REPLACE_ME>

# 平台费率（默认 20%）
DNACLOUD_PLATFORM_FEE_RATE=0.20

# 创作者 payout 最小金额（单位最小精度，默认 0.1 USDG）
DNACLOUD_MINIMUM_PAYOUT=100000

# 链上自动 payout（可选，不配置则 payout 保持 pending）
# DNACLOUD_TREASURY_KEY=<REPLACE_ME>

# 本地测试模式（生产必须保持 false）
DNACLOUD_LOCAL_TEST=false
ENVEOF
  chmod 600 "$APP_DIR/.env"
  chown "$APP_USER":"$APP_USER" "$APP_DIR/.env"
  warn "请编辑 $APP_DIR/.env 填写配置后再启动服务：nano $APP_DIR/.env"
else
  info "$APP_DIR/.env 已存在，跳过创建"
fi

# ─── Step 5: 安装 systemd service ─────────────────────────────────────────────
info "Step 5/7: 配置 systemd 服务 ..."
cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<SVCEOF
[Unit]
Description=DNAcloud Server
Documentation=https://github.com/your-org/dnacloud
After=network.target
After=mysql.service

[Service]
Type=simple
User=${APP_USER}
Group=${APP_USER}
WorkingDirectory=${APP_DIR}
EnvironmentFile=${APP_DIR}/.env
ExecStart=/usr/bin/java \\
  -Xmx512m -Xms256m \\
  -XX:+UseG1GC \\
  -Djava.security.egd=file:/dev/./urandom \\
  -jar ${APP_DIR}/${JAR_NAME}
StandardOutput=append:${APP_DIR}/logs/app.log
StandardError=append:${APP_DIR}/logs/app.log
Restart=always
RestartSec=10
TimeoutStartSec=60
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
SVCEOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
info "systemd 服务已注册：$SERVICE_NAME"

# ─── Step 6: Nginx 反向代理（可选）───────────────────────────────────────────
if [[ -n "$DOMAIN" ]]; then
  info "Step 6/7: 配置 Nginx 反向代理 ($DOMAIN) ..."
  if ! command -v nginx &>/dev/null; then
    apt-get install -y -qq nginx
  fi

  NGINX_CONF="/etc/nginx/sites-available/dnacloud"
  cat > "$NGINX_CONF" <<NGINXEOF
upstream dnacloud_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name ${DOMAIN};

    # 上传 DNA 包最大 50MB
    client_max_body_size 50M;

    location / {
        proxy_pass         http://dnacloud_backend;
        proxy_http_version 1.1;
        proxy_set_header   Host              \$host;
        proxy_set_header   X-Real-IP         \$remote_addr;
        proxy_set_header   X-Forwarded-For   \$proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto \$scheme;
        proxy_set_header   Connection        "";
        proxy_read_timeout 60s;
    }

    # 健康检查不记日志
    location /actuator/health {
        proxy_pass http://dnacloud_backend;
        access_log off;
    }
}
NGINXEOF

  ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/dnacloud
  nginx -t && systemctl reload nginx
  info "Nginx 配置完成。HTTPS 证书请运行：certbot --nginx -d $DOMAIN"
else
  info "Step 6/7: 跳过 Nginx（未传入 --domain）"
fi

# ─── Step 7: 启动服务 ─────────────────────────────────────────────────────────
info "Step 7/7: 启动 $SERVICE_NAME ..."

# 检查 .env 是否还有未替换的占位符
if grep -q "<REPLACE_ME>" "$APP_DIR/.env" 2>/dev/null; then
  warn "检测到 .env 中仍有 <REPLACE_ME> 未填写"
  warn "请先编辑：nano $APP_DIR/.env"
  warn "然后手动启动：systemctl start $SERVICE_NAME"
else
  systemctl start "$SERVICE_NAME"
  sleep 3
  if systemctl is-active --quiet "$SERVICE_NAME"; then
    info "✓ 服务启动成功"
    systemctl status "$SERVICE_NAME" --no-pager -l
  else
    error "服务启动失败，查看日志：journalctl -u $SERVICE_NAME -n 50"
  fi
fi

echo ""
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${GREEN}  DNAcloud 部署完成${NC}"
echo -e "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo "  应用目录    : $APP_DIR"
echo "  环境变量    : $APP_DIR/.env"
echo "  日志        : $APP_DIR/logs/app.log"
echo "  Artifact 库 : $APP_DIR/artifacts"
echo ""
echo "  常用命令："
echo "    systemctl status  $SERVICE_NAME   # 查看状态"
echo "    systemctl restart $SERVICE_NAME   # 重启"
echo "    systemctl stop    $SERVICE_NAME   # 停止"
echo "    journalctl -u     $SERVICE_NAME -f   # 实时日志"
echo "    tail -f $APP_DIR/logs/app.log        # 应用日志"
[[ -n "$DOMAIN" ]] && echo "" && echo "  接口地址    : http://$DOMAIN (HTTP，请配置 HTTPS)"
echo ""
