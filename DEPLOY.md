# DNAcloud 上线操作手册

> 按顺序执行，每步完成后打勾。

---

## 全局 Checklist

```
基础设施
  [ ] 1. 构建 Server JAR
  [ ] 2. 上传并部署到服务器
  [ ] 3. 配置 .env（填写真实值）
  [ ] 4. 开放防火墙端口
  [ ] 5. 配置 Nginx + HTTPS

官方包上线
  [ ] 6. 上传 Trading Master DNA 到 marketplace

前端 CLI 发布
  [ ] 7. 发布 @dnacloud/mcp-server 到 npm
  [ ] 8. 发布 @dnacloud/cli 到 npm

验收测试
  [ ] 9. 服务端接口冒烟测试
  [ ] 10. CLI 端到端安装流程测试
  [ ] 11. OKX x402 真实支付测试
```

---

## 一、构建 Server JAR

在本地执行（需要 JDK 17）：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  mvn -f server/pom.xml package -DskipTests

# 产物
ls -lh server/target/dnacloud-server-1.0.0-SNAPSHOT.jar
```

---

## 二、上传并部署到服务器

### 2.1 上传文件

```bash
SERVER=root@163.7.3.34

scp server/target/dnacloud-server-1.0.0-SNAPSHOT.jar $SERVER:/opt/dnacloud/dnacloud-server.jar
scp .env $SERVER:/opt/dnacloud/.env
scp scripts/deploy.sh $SERVER:/tmp/deploy.sh
```

### 2.2 首次部署（在服务器上执行）

```bash
# 创建运行用户和目录
useradd -r -s /bin/false dnacloud 2>/dev/null || true
mkdir -p /opt/dnacloud/{artifacts,logs,data}
chown -R dnacloud:dnacloud /opt/dnacloud

# 安装 JDK 17（Ubuntu）
apt-get update && apt-get install -y openjdk-17-jre-headless

# 创建 systemd service
cat > /etc/systemd/system/dnacloud.service << 'EOF'
[Unit]
Description=DNAcloud Marketplace Server
After=network.target

[Service]
User=dnacloud
WorkingDirectory=/opt/dnacloud
EnvironmentFile=/opt/dnacloud/.env
ExecStart=/usr/bin/java -Xmx512m -jar /opt/dnacloud/dnacloud-server.jar
Restart=always
RestartSec=10
StandardOutput=append:/opt/dnacloud/logs/app.log
StandardError=append:/opt/dnacloud/logs/app.log

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable dnacloud
systemctl start dnacloud

# 检查是否启动成功
sleep 5 && curl -s http://localhost:8089/actuator/health
```

### 2.3 后续更新（只需两步）

```bash
scp server/target/dnacloud-server-1.0.0-SNAPSHOT.jar $SERVER:/opt/dnacloud/dnacloud-server.jar
ssh $SERVER "systemctl restart dnacloud && sleep 3 && curl -s http://localhost:8089/actuator/health"
```

---

## 三、配置 .env（填写真实值）

服务器上编辑 `/opt/dnacloud/.env`，以下是**完整的生产配置**：

```bash
# ─── 服务基础 ─────────────────────────────────────────────────────
SERVER_PORT=8089
# 公网地址，用于拼接 artifact 下载链接（必须与实际访问地址一致）
DNACLOUD_BASE_URL=https://api.dnacloud.okg.com

# ─── Artifact 存储 ────────────────────────────────────────────────
DNACLOUD_ARTIFACT_STORE=/opt/dnacloud/artifacts

# ─── OKX OnchainOS 支付凭证 ───────────────────────────────────────
# 来源：https://web3.okx.com/zh-hans/onchainos/dev-portal
# 注意：这是 OnchainOS 开发者门户的凭证，不是 OKX 交易所 API Key
OKX_API_KEY=<从 OnchainOS 门户获取>
OKX_SECRET_KEY=<从 OnchainOS 门户获取>
OKX_PASSPHRASE=<从 OnchainOS 门户获取>

# ─── 代币合约地址 ─────────────────────────────────────────────────
# XLayer mainnet USDT 合约地址
USDT_CONTRACT_ADDRESS=0x779ded0c9e1022225f8e0630b35a9b54be713736

# ─── 平台钱包 ─────────────────────────────────────────────────────
# 买家付款目标地址（平台收款钱包，EVM 地址）
DNACLOUD_PAYMENT_ADDRESS=0x<平台钱包地址>

# 平台出款私钥（可选，配置后自动链上转账给创作者）
# 不配置时 payout 保持 pending 状态，需手动处理
# DNACLOUD_TREASURY_KEY=0x<私钥>

# ─── 安全密钥 ─────────────────────────────────────────────────────
# 生成命令：openssl rand -hex 32
DNACLOUD_ADMIN_API_KEY=<32字节随机值>

# 生成命令：openssl rand -hex 64
DNACLOUD_SIGNING_KEY=<64字节随机值>

# ─── 业务参数 ─────────────────────────────────────────────────────
DNACLOUD_CORS_ORIGINS=https://dnacloud.okg.com
DNACLOUD_PLATFORM_FEE_RATE=0.20
DNACLOUD_MINIMUM_PAYOUT=100000

# ─── 生产必须保持注释 ─────────────────────────────────────────────
# DNACLOUD_LOCAL_TEST=false
```

修改 `.env` 后重启：

```bash
systemctl restart dnacloud
```

---

## 四、开放防火墙端口

在**云服务商控制台**的安全组/防火墙规则里添加：

| 端口 | 协议 | 用途 |
|------|------|------|
| 80   | TCP  | HTTP（Nginx，重定向到 443） |
| 443  | TCP  | HTTPS（Nginx 反代到 8089） |
| 8089 | TCP  | 可选，直接暴露（不推荐生产使用） |

---

## 五、配置 Nginx + HTTPS

### 5.1 安装 Nginx

```bash
apt-get install -y nginx certbot python3-certbot-nginx
```

### 5.2 配置反向代理

```bash
cat > /etc/nginx/sites-available/dnacloud << 'EOF'
server {
    listen 80;
    server_name api.dnacloud.okg.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name api.dnacloud.okg.com;

    ssl_certificate     /etc/letsencrypt/live/api.dnacloud.okg.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.dnacloud.okg.com/privkey.pem;

    client_max_body_size 60M;

    location / {
        proxy_pass         http://127.0.0.1:8089;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }
}
EOF

ln -sf /etc/nginx/sites-available/dnacloud /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

### 5.3 申请 SSL 证书

```bash
# 需要先把 api.dnacloud.okg.com 的 DNS A 记录指向服务器 IP
certbot --nginx -d api.dnacloud.okg.com
```

### 5.4 DNS 配置

在 DNS 控制台添加：

| 类型 | 主机名 | 值 | TTL |
|------|--------|-----|-----|
| A | api.dnacloud.okg.com | 163.7.3.34 | 600 |

---

## 六、上传 Trading Master DNA 官方包

### 6.1 打包

```bash
# 在本地 repo 中执行
cd dna-packages/trading-master-dna
zip -r ../../trading-master-dna-1.0.1.zip . -x "*.DS_Store"
cd ../..
```

### 6.2 验证包结构

```bash
node packages/cli/dist/index.js validate trading-master-dna-1.0.1.zip
```

期望输出：`✔ 包结构验证通过`

### 6.3 上传到 marketplace

```bash
node packages/cli/dist/index.js upload trading-master-dna-1.0.1.zip \
  --payout-address 0x<平台钱包地址> \
  --marketplace-url https://api.dnacloud.okg.com
```

### 6.4 验证上传成功

```bash
curl -s "https://api.dnacloud.okg.com/v1/dna/trading-master-dna" | python3 -m json.tool | grep '"version"'
```

---

## 七、发布 npm 包

### 7.1 构建 TypeScript 包

```bash
pnpm -r build
# 构建顺序：schema → validator → mcp-server → cli
```

### 7.2 发布 @dnacloud/mcp-server

```bash
cd packages/mcp-server
npm publish --access public
```

### 7.3 发布 @dnacloud/cli

```bash
cd packages/cli
# 确认 package.json 里 version 和默认 marketplace URL 正确
npm publish --access public
```

发布后，用户可以：

```bash
npm install -g @dnacloud/cli
# 或
npx @dnacloud/cli init
```

---

## 八、验收测试

### 8.1 服务端冒烟测试

```bash
BASE=https://api.dnacloud.okg.com

# 健康检查
curl -s $BASE/actuator/health
# 期望：{"status":"UP"}

# 搜索包
curl -s "$BASE/v1/dna/search?q=trading" | python3 -m json.tool | grep '"currency"'
# 期望："currency": "USDT"

# 获取包详情
curl -s "$BASE/v1/dna/trading-master-dna" | python3 -m json.tool | grep '"version"'

# 无支付请求 artifact → 应返回 402
curl -s -o /dev/null -w "%{http_code}" "$BASE/v1/dna/trading-master-dna/versions/1.0.1/artifact"
# 期望：402

# 解码 402 的支付要求
curl -s -I "$BASE/v1/dna/trading-master-dna/versions/1.0.1/artifact" \
  | grep X-PAYMENT-REQUIREMENT \
  | awk '{print $2}' \
  | base64 -d \
  | python3 -m json.tool
# 期望：包含 payTo 平台地址、asset USDT 合约、network eip155:196
```

### 8.2 CLI 端到端安装测试（本地测试模式）

```bash
# 新建测试目录
mkdir /tmp/test-install && cd /tmp/test-install

# init
node /path/to/DNA/packages/cli/dist/index.js init \
  --marketplace-url https://api.dnacloud.okg.com

# 查看生成的文件
find . -type f | sort

# install（本地测试模式跳过支付）
DNACLOUD_LOCAL_TEST=true \
  node /path/to/DNA/packages/cli/dist/index.js install trading-master-dna -y

# 期望最终输出：
#   ✔ 安装完成
#   状态: partial（liveTradingReady: ✗ 因未配置交易所 API key，这是预期行为）
```

### 8.3 真实 OKX x402 支付测试

> 需要买家已安装 OKX OnchainOS Payment Skill 且 Agentic Wallet 有 USDT 余额

1. 在装有 OKX Payment Skill 的 Claude Code 项目中执行 `dnacloud init`
2. 重启 Claude Code
3. 说："我要安装 Trading Master DNA"
4. 查看 Claude 是否触发 dnacloud skill → 搜索 → 展示包信息 → 发起 OKX 支付
5. 支付完成后验证：

```bash
dnacloud verify trading-master-dna
# 期望：signature verified, payment receipt found, skills/agents/commands 全部 ✓
```

---

## 九、OKX OnchainOS 账号配置指引

### 9.1 获取 API 凭证（服务端用）

1. 访问 [OKX OnchainOS 开发者门户](https://web3.okx.com/zh-hans/onchainos/dev-portal)
2. 注册/登录后创建应用
3. 获取 `API Key`、`Secret Key`、`Passphrase`
4. 填入服务器 `/opt/dnacloud/.env` 的 `OKX_API_KEY` / `OKX_SECRET_KEY` / `OKX_PASSPHRASE`

> ⚠️ 这是 OnchainOS 凭证，不是 OKX 交易所 API Key，两者不同。

### 9.2 买家侧：安装 OKX Payment Skill

买家在 Claude Code 中操作：

1. 访问 [Payment Skill 安装文档](https://web3.okx.com/zh-hans/onchainos/dev-docs/payments/payment-use-buyer)
2. 按文档安装 OKX OnchainOS Payment Skill
3. Skill 自动创建 Agentic Wallet（私钥在 TEE 内，无需导出）
4. 向 Agentic Wallet 充值 USDT（XLayer 网络）
5. 余额到账后，即可购买 DNA 包

---

## 十、运维操作

```bash
# 查看服务状态
systemctl status dnacloud

# 实时日志
journalctl -u dnacloud -f
# 或
tail -f /opt/dnacloud/logs/app.log

# 重启（修改 .env 后必须执行）
systemctl restart dnacloud

# 更新 jar
scp dnacloud-server-*.jar root@163.7.3.34:/opt/dnacloud/dnacloud-server.jar
ssh root@163.7.3.34 "systemctl restart dnacloud"

# 手动触发 payout（结算创作者收益）
curl -X POST https://api.dnacloud.okg.com/v1/creator/admin/payouts/run-once \
  -H "X-Admin-Api-Key: <DNACLOUD_ADMIN_API_KEY>"

# 查看某个创作者收益
curl "https://api.dnacloud.okg.com/v1/creator/earnings?wallet=0x<address>"
```

---

## 十一、已知限制

| 功能 | 状态 | 说明 |
|------|------|------|
| OKX x402 支付 | 代码已就绪 | 服务端调用 OKX Facilitator HTTP API，需 OKX OnchainOS 账号激活 |
| 链上自动 payout | 待实现 | `DNACLOUD_TREASURY_KEY` 配置后接口已预留，web3j 转账逻辑未实现 |
| 速率限制 | 未实现 | 建议在 Nginx 层添加 rate limiting |
| 认证系统 | 最简 | Creator 端点仅 payout_address 鉴权；Admin 端点有 API Key 保护 |
| H2 数据库 | 适合演示 | 生产建议迁移到 MySQL |

---

## 附：本地开发快速启动

```bash
# 1. 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  mvn -f server/pom.xml package -DskipTests
pnpm -r build

# 2. 启动服务端（本地测试模式）
export $(grep -v '^#' .env | grep -v '^$' | xargs)
DNACLOUD_LOCAL_TEST=true \
DNACLOUD_BASE_URL=http://localhost:8089 \
DNACLOUD_ARTIFACT_STORE=./server/test-artifacts \
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  java -jar server/target/dnacloud-server-1.0.0-SNAPSHOT.jar &

# 3. 健康检查
sleep 8 && curl -s http://localhost:8089/actuator/health

# 4. 测试完整安装流程
mkdir -p /tmp/test-project && cd /tmp/test-project
DNACLOUD_LOCAL_TEST=true \
  node /path/to/DNA/packages/cli/dist/index.js install trading-master-dna \
  --marketplace-url http://localhost:8089 -y
```
