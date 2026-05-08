# DNAcloud

**Universal AI Agent DNA Capability Marketplace**

用户说出想要什么专家，DNAcloud 通过 OKX x402 购买并将对应的 Skills、Agents、MCP、Hooks、Rules 和 Tests 安装到 AI Agent 项目中。

---

## 项目定位

DNAcloud 解决 AI Agent 生态的三个断点：

- **用户**：不需要手动配置 Skill、MCP、Hook，只需说"我要一个交易大师"，DNAcloud 完成搜索、支付、安装全流程
- **创作者**：可以把专业能力打包成可安装、可计费的 DNA 包，通过平台自动结算收益
- **支付**：基于 OKX x402，AI Agent 和 CLI 可以自主按次购买能力包，不依赖传统账号订阅

当前支持 **Claude Code**，Cursor / Codex / Windsurf 兼容中。

---

## 整体架构

```
用户说"我要一个交易大师"
       │
       ▼
DNAcloud Skill (Claude Code 内)
  检测 OKX x402 支付凭证
       │
       ▼
dnacloud-market-researcher Agent
  搜索 Marketplace API
       │  ← GET /v1/dna/search
       ▼
Marketplace Server (Spring Boot)
  返回匹配 DNA 包列表
       │
用户确认购买
       │
       ▼
dnacloud CLI / PaymentClient
  GET /artifact → 402 Payment Required
  OKX x402 签名支付
  retry → 服务端 verify → 返回签名 artifact
       │
       ▼
dnacloud-installer Agent
  解包 → 签名校验 → 展示 install preview
  写入项目文件系统
       │
       ▼
dnacloud verify → active
```

---

## 仓库结构

```
DNA/
├── packages/                    # TypeScript 包（pnpm workspace）
│   ├── schema/                  # DNA 包 manifest schema 和类型定义
│   ├── validator/               # DNA 包结构校验器（本地 validate 用）
│   ├── cli/                     # dnacloud CLI（Node.js 18+）
│   │   ├── src/
│   │   │   ├── commands/        # init / install / verify / status / rollback / upload / creator
│   │   │   ├── installer/       # Installer、Verifier、Rollback、路径管理
│   │   │   └── marketplace/     # MarketplaceClient、PaymentClient（OKX x402）
│   │   └── bootstrap/           # dnacloud init 时写入项目的 Bootstrap 副本
│   └── mcp-server/              # DNAcloud MCP Server，暴露 marketplace 搜索工具给 Claude Code
│
├── server/                      # Marketplace 后端（Spring Boot 3 / Java 17 / Maven）
│   └── src/main/java/com/okg/dnacloud/
│       ├── controller/          # MarketplaceController、CreatorController
│       ├── service/             # MarketplaceService、ArtifactService、CreatorService
│       ├── payment/             # OkxX402Client（x402 支付验证）
│       ├── entity/              # JPA 实体（PackageVersion、PaymentReceipt、RevenueEntry 等）
│       ├── repository/          # Spring Data JPA 仓库
│       ├── model/               # API 请求/响应 DTO
│       └── config/              # WebConfig（CORS）
│
├── dna-packages/                # DNA 包源文件
│   ├── bootstrap/               # DNAcloud Bootstrap 插件（安装到用户 Claude Code 项目）
│   │   └── .claude/
│   │       ├── skills/dnacloud/ # DNAcloud 主 Skill（搜索、支付、安装引导）
│   │       ├── agents/          # dnacloud-installer、dnacloud-market-researcher
│   │       └── commands/        # /dna-install /dna-status /dna-upload /dna-earnings 等
│   └── trading-master-dna/     # Trading Master DNA 官方能力包
│       ├── manifest.json        # 包元信息（id、版本、价格、组件列表）
│       ├── install-plan.json    # 安装计划（文件映射）
│       ├── skills/              # trading-master Skill + 参考文档
│       ├── agents/              # market-analyst、portfolio-manager、risk-manager 等
│       ├── commands/            # /trade-plan /risk-check /order-preview 等
│       ├── mcp/                 # market-data、account-read、order-execution MCP 配置模板
│       ├── hooks/               # pre-tool-use-trade-guard（下单前风控拦截）
│       ├── rules/               # 机器规则 + 权限策略
│       └── tests/               # 安装后合规性测试
│
└── doc/                         # 文档和网站
    ├── website/index.html       # 项目官网（单文件）
    └── dnacloud_*.md            # 技术设计文档（v05 / v06）
```

---

## 快速开始

### 环境要求

| 工具 | 版本 |
|------|------|
| Node.js | ≥ 18 |
| pnpm | ≥ 9 |
| Java | 17（服务端） |
| Maven | 3.x（服务端） |

### 构建所有 TypeScript 包

```bash
pnpm install
pnpm build          # 构建 schema、validator、cli、mcp-server
```

### 构建并启动服务端

```bash
cd server
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  mvn package -DskipTests

JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  java -jar target/dnacloud-server-1.0.0-SNAPSHOT.jar
```

服务默认监听 `http://localhost:8080`，使用 H2 内存/文件数据库（`./data/dnacloud.mv.db`）。

### 初始化 DNAcloud Bootstrap

```bash
# 全局安装 CLI（开发时用本地构建版本）
node packages/cli/dist/index.js init

# 或发布后
npm install -g @dnacloud/cli
dnacloud init
```

`dnacloud init` 会将以下内容写入当前 Claude Code 项目：

| 类型 | 文件 | 说明 |
|------|------|------|
| Skill | `.claude/skills/dnacloud/SKILL.md` | 核心搜索/购买/安装引导 |
| Agent | `.claude/agents/dnacloud-installer.md` | 负责解包和安装 |
| Agent | `.claude/agents/dnacloud-market-researcher.md` | 负责市场搜索 |
| Command | `.claude/commands/dna-install.md` | `/dna-install` |
| Command | `.claude/commands/dna-status.md` | `/dna-status` |
| Command | `.claude/commands/dna-upload.md` | `/dna-upload` |
| Command | `.claude/commands/dna-earnings.md` | `/dna-earnings` |
| Command | `.claude/commands/dna-packages.md` | `/dna-packages` |
| Command | `.claude/commands/dna-create.md` | `/dna-create` |
| Command | `.claude/commands/dna.md` | `/dna`（帮助入口） |

### 购买和安装 Trading Master DNA

```bash
# 前提：配置 OKX OnchainOS 支付凭证（见下方）
dnacloud install trading-master-dna

# 或直接在 Claude Code 中说：
# "我要一个交易大师"
```

### 验证安装状态

```bash
dnacloud verify trading-master-dna
dnacloud status
```

---

## OKX x402 支付凭证配置

购买 DNA 包需要 OKX OnchainOS 开发者凭证（**非 OKX 交易所普通 API Key**）：

1. 打开 [OKX OnchainOS 开发者门户](https://web3.okx.com/zh-hans/onchainos/dev-portal)
2. 连接 EVM 钱包（MetaMask 等），签名验证地址所有权，无需充值
3. 在开发者门户创建 API Key，保存三个凭证
4. 写入项目 `.env`：

```env
OKX_API_KEY=your_api_key
OKX_SECRET_KEY=your_secret_key
OKX_PASSPHRASE=your_passphrase
```

未配置时直接对 Claude Code 说"我要安装 DNA"，Skill 会自动引导完成配置。

---

## 服务端 API

| 端点 | 说明 |
|------|------|
| `GET /v1/dna/search?q=` | 搜索 DNA 包 |
| `GET /v1/dna/{id}` | 获取包详情 |
| `GET /v1/dna/{id}/versions/{ver}/artifact` | 获取 artifact（需 x402 支付） |
| `POST /v1/creator/packages/upload` | 上传 DNA 包 |
| `GET /v1/creator/packages?wallet=` | 查询创作者包列表 |
| `GET /v1/creator/earnings?wallet=` | 查询收益账本 |
| `GET /v1/creator/payouts?wallet=` | 查询结算记录 |
| `POST /v1/creator/admin/payouts/run-once` | 触发 payout worker（需 Admin Key） |

完整部署配置见 [DEPLOY.md](./DEPLOY.md)。

---

## Creator 工作流

```bash
# 本地校验包结构
dnacloud validate ./my-dna-package.zip

# 上传到市场
dnacloud upload ./my-dna-package.zip --payout-address 0x...

# 查看收益
dnacloud creator earnings <wallet>
dnacloud creator payouts <wallet>
```

---

## DNA 包格式

每个 DNA 包是一个 zip，包含：

```
manifest.json        # 必填：包身份、版本、价格、组件列表
install-plan.json    # 必填：文件安装映射
signature.txt        # 平台签名
skills/              # Claude Code Skill
agents/              # Subagent 定义
commands/            # Slash Commands
mcp/                 # MCP Server 配置模板（只含环境变量引用，不含真实 key）
hooks/               # PreToolUse / PostToolUse Hook
rules/               # 机器规则
tests/               # 安装后验证用例
```

---

## 硬性约束

- 不使用 mock payment，支付失败不安装
- MCP 配置只写 `${ENV_VAR}` 占位，不硬编码真实 key
- 不伪造行情、账户余额、成交结果
- Trading Master DNA 不承诺盈利
