# DNAcloud 生产部署指南

## 概览

DNAcloud 由两部分组成：
- **Server**：Spring Boot 3 / Java 17，提供 Marketplace API、Creator API、OKX x402 支付中间件
- **CLI**（`@dnacloud/cli`）：TypeScript/Node.js，提供 `dnacloud` 命令行工具

---

## 一、服务端部署

### 1.1 依赖要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 Zulu 17 或 Temurin 17 |
| MySQL / PostgreSQL | 8.0+ / 14+ | 生产环境替换默认 H2 |
| 磁盘 | 10GB+ | artifact 存储 |

### 1.2 构建

```bash
cd server
JAVA_HOME=/path/to/jdk17 mvn package -DskipTests
# 产物：target/dnacloud-server-1.0.0-SNAPSHOT.jar
```

### 1.3 必需环境变量

以下变量**必须在生产环境配置**，否则对应功能不可用：

```bash
# ---- 服务基础 ----
SERVER_PORT=8080
DNACLOUD_BASE_URL=https://api.yourdomain.com      # 下载链接中的公网地址，必须配置

# ---- 数据库（生产替换 H2）----
DB_URL=jdbc:mysql://your-db-host:3306/dnacloud?useSSL=true&serverTimezone=UTC
DB_DRIVER=com.mysql.cj.jdbc.Driver
DB_USERNAME=dnacloud
DB_PASSWORD=<strong-password>
JPA_DDL_AUTO=validate                              # 生产环境用 validate，禁止 update/create
JPA_DIALECT=org.hibernate.dialect.MySQLDialect

# ---- artifact 存储 ----
DNACLOUD_ARTIFACT_STORE=/data/dnacloud/artifacts   # 需要有读写权限的持久化目录

# ---- OKX x402 支付（买家支付验证）----
OKX_API_KEY=<your-okx-api-key>
OKX_SECRET_KEY=<your-okx-secret-key>
OKX_PASSPHRASE=<your-okx-passphrase>

# ---- 平台收款地址（接收买家付款）----
DNACLOUD_PAYMENT_ADDRESS=0x<platform-wallet-address>

# ---- Admin API 保护 ----
DNACLOUD_ADMIN_API_KEY=<random-secret-32chars+>    # 必须配置，否则 admin 端点拒绝服务

# ---- 平台签名密钥 ----
DNACLOUD_SIGNING_KEY=<random-secret-64chars+>      # 用于包签名，不配置则签名为 "unsigned"
```

### 1.4 可选环境变量

```bash
# ---- Creator 收益结算（链上转账）----
DNACLOUD_TREASURY_KEY=<treasury-wallet-private-key>   # 配置后 payout worker 将尝试链上转账
                                                        # 不配置则 payout 状态为 pending，需手动处理

# ---- CORS ----
DNACLOUD_CORS_ORIGINS=https://yourdomain.com,https://app.yourdomain.com

# ---- 平台费率（默认 20%）----
DNACLOUD_PLATFORM_FEE_RATE=0.20

# ---- 最小结算金额（单位：最小精度，默认 100000 = 0.1 USDG）----
DNACLOUD_MINIMUM_PAYOUT=100000

# ---- 本地测试模式（生产禁止开启）----
# DNACLOUD_LOCAL_TEST=false    # 默认 false，绝对不要在生产设置为 true
```

### 1.5 启动

```bash
java -jar target/dnacloud-server-1.0.0-SNAPSHOT.jar
```

推荐使用 systemd 管理进程：

```ini
[Unit]
Description=DNAcloud Server
After=network.target

[Service]
User=dnacloud
WorkingDirectory=/opt/dnacloud
EnvironmentFile=/opt/dnacloud/.env
ExecStart=/usr/bin/java -Xmx512m -jar /opt/dnacloud/dnacloud-server.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 1.6 H2 → MySQL 数据库迁移

> H2 仅用于开发/演示，**生产必须使用 MySQL 或 PostgreSQL**。

**步骤 1**：在 `pom.xml` 中添加 MySQL 驱动（已有 H2 scope=runtime，替换或并存）：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

**步骤 2**：设置 `JPA_DDL_AUTO=create` 首次启动（自动建表），然后改回 `validate`。

**步骤 3**：检查 `application.yml` 中的 `JPA_DIALECT` 环境变量已设置为 MySQL Dialect。

---

## 二、CLI 发布

### 2.1 构建

```bash
cd packages/cli
pnpm build
```

### 2.2 发布到 npm

```bash
npm publish --access public
# 包名：@dnacloud/cli
# 用户安装：npm install -g @dnacloud/cli
```

### 2.3 配置 marketplace 地址

CLI 默认连接 `http://localhost:8080`，用户可通过以下方式配置：

```bash
dnacloud config set marketplace.url https://api.yourdomain.com
# 或在项目 .dnacloud/config.json 中设置
```

---

## 三、安全检查清单

### 必须完成

- [ ] `DNACLOUD_ADMIN_API_KEY` 已配置（32+ 字符随机字符串）
- [ ] `DNACLOUD_LOCAL_TEST` 为 `false`（默认值，确认未被覆盖）
- [ ] `H2_CONSOLE_ENABLED` 为 `false`（默认值）
- [ ] 生产数据库替换 H2，`JPA_DDL_AUTO=validate`
- [ ] `DNACLOUD_BASE_URL` 设置为公网地址（含 https）
- [ ] `OKX_SECRET_KEY` 已配置（否则所有付费包无法购买）
- [ ] `DNACLOUD_SIGNING_KEY` 已配置（否则包签名为 "unsigned"）
- [ ] `DNACLOUD_PAYMENT_ADDRESS` 已配置（否则无法接收付款）
- [ ] 服务运行在反向代理（Nginx/Caddy）后，外部仅暴露 443

### 建议完成

- [ ] 设置 `DNACLOUD_CORS_ORIGINS` 为实际域名
- [ ] 部署 TLS 证书（Let's Encrypt 或商业证书）
- [ ] 配置 artifact 存储目录的备份策略
- [ ] 配置 `DNACLOUD_TREASURY_KEY` 实现自动链上 payout

### 已知限制（Hackathon 阶段）

| 功能 | 状态 | 说明 |
|------|------|------|
| OKX x402 支付 | HMAC 本地验证 | 使用 OKX API 密钥做 HMAC 签名验证，不调用外部 OKX 支付接口 |
| 链上 payout | Stub | `DNACLOUD_TREASURY_KEY` 不配置时 payout 保持 pending 状态；配置后接口已预留但 web3j 转账未实现 |
| 速率限制 | 未实现 | 生产建议在 Nginx 层添加 rate limiting |
| 认证系统 | 无 | Creator 端口只有 payout_address 参数，无账号体系；admin 端口有 API Key 保护 |

---

## 四、API 端点参考

### Marketplace（公开）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/v1/dna/search?q=` | 搜索 DNA 包 |
| GET | `/v1/dna/{packageId}` | 获取包详情 |
| GET | `/v1/dna/{packageId}/versions/{version}/artifact` | 获取 artifact（触发支付） |
| GET | `/v1/dna/{packageId}/versions/{version}/download` | 下载 zip 文件 |

### Creator（公开，仅地址鉴权）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/creator/upload-session` | 创建上传会话 |
| POST | `/v1/creator/packages/upload` | 上传 DNA 包 |
| GET | `/v1/creator/packages?wallet=` | 查看已上传包 |
| GET | `/v1/creator/earnings?wallet=` | 查看收益账本 |
| GET | `/v1/creator/payouts?wallet=` | 查看结算记录 |

### Admin（需要 X-Admin-Api-Key Header）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/v1/creator/admin/payouts/run-once` | 手动触发 payout worker |

---

## 五、本地开发快速启动

```bash
# 1. 启动 server（H2 内存模式，跳过支付验证）
cd server
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  DNACLOUD_LOCAL_TEST=true \
  java -jar target/dnacloud-server-1.0.0-SNAPSHOT.jar

# 2. 初始化本地 DNA workspace
mkdir my-dna-workspace && cd my-dna-workspace
dnacloud init

# 3. 卖家：打包并上传
cd /path/to/your-dna-package
dnacloud validate package.zip
dnacloud upload package.zip --payout-address 0xYourAddress

# 4. 买家：搜索并安装（local-test 模式跳过支付）
dnacloud install trading-master-dna

# 5. 验证安装
dnacloud verify trading-master-dna
```
