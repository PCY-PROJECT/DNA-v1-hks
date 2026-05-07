# DNAcloud 用户使用指南

版本：v0.6 | 更新：2026-05-07

---

## 概述

DNAcloud 是 Claude Code 的专家能力包市场。

- **买家（Buyer）**：购买 DNA 包，让 Claude Code 获得新的专家能力
- **卖家/创作者（Creator）**：上传自己制作的 DNA 包，获得买家支付的收益

---

## 前置要求

| 条件 | 说明 |
|------|------|
| Claude Code | 已安装并可正常使用 |
| Node.js 18+ | 运行 dnacloud CLI |
| EVM 钱包地址 | 卖家必须有，买家可选 |
| OKX API Key | 买家支付 OKX x402 所需 |

---

## Part 0：安装 dnacloud CLI

> 用户不需要 clone 仓库，不需要了解内部实现，只需要一行命令。

### 生产环境（推荐）

```bash
npm install -g @dnacloud/cli
```

安装后 `dnacloud` 命令全局可用。CLI 内已内置：
- Bootstrap skill/agent/command 文件
- 默认 marketplace URL：`https://api.dnacloud.okg.com`
- MCP server 通过 `npx -y @dnacloud/mcp-server` 按需启动

### 当前黑客松状态

CLI 尚未发布到 npm。本地使用方式：

```bash
git clone https://github.com/okg/dnacloud.git
cd dnacloud
pnpm install && pnpm -r build
npm install -g ./packages/cli

# 本地服务器，需先启动
cd server
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
  java -jar target/dnacloud-server-1.0.0-SNAPSHOT.jar &
```

之后所有 `dnacloud` 命令自动连接 `http://localhost:8080`（通过 `DNACLOUD_MARKETPLACE_URL` 环境变量覆盖）。

### 生产上线 checklist

| 项目 | 当前状态 | 上线要求 |
|------|----------|----------|
| `@dnacloud/cli` | 本地构建 | 发布到 npm |
| `@dnacloud/mcp-server` | 本地文件 | 发布到 npm |
| Bootstrap 文件 | CLI 包已内置 ✓ | — |
| 默认 marketplace URL | 已改为生产地址 ✓ | 服务器部署到云端 |
| Java server | `localhost:8080` | 部署到 `api.dnacloud.okg.com` |

---

## Part 1：买家 SOP

### 总流程

```
npm install -g @dnacloud/cli       ← 一次性
dnacloud init                       ← 每个 Claude Code 项目执行一次
→ 搜索包 / 说自然语言
→ OKX x402 支付
→ 安装到当前项目的 .claude/
→ dnacloud verify 通过
→ 使用能力命令
```

---

### 第一步：初始化 DNAcloud Bootstrap

**在你的 Claude Code 项目根目录执行**（每个项目执行一次）：

```bash
dnacloud init
```

初始化会做以下事情：
- 创建 `.dnacloud/` 配置目录
- 写入 `.mcp.json`，注册 `dnacloud-marketplace` MCP server
- 安装 DNAcloud Skill、Agent、Command 到 `.claude/`

完成后你会看到：

```
✓ DNAcloud Bootstrap 已安装到当前项目
  .dnacloud/config.json     — DNAcloud 配置
  .claude/skills/dnacloud/  — DNAcloud skill
  .claude/agents/           — DNAcloud installer agent
  .claude/commands/         — dna, dna-install, dna-status 命令
```

---

### 第二步：配置 OKX API（支付所需）

在项目 `.env` 文件中添加：

```bash
OKX_API_KEY=your_okx_api_key
OKX_SECRET_KEY=your_okx_secret_key
OKX_PASSPHRASE=your_okx_passphrase
```

> 如果没有 OKX API Key，可以先浏览和安装免费包，付费包会在下载时提示需要配置。

---

### 第三步：搜索 DNA 包

**方式 A：在 Claude Code 中自然语言触发**

直接对 Claude Code 说：

```
我要一个交易大师
搜索 DNA marketplace 里的交易类包
给我安装一个适合新手的加密交易助手
```

Claude Code 会自动搜索并展示结果。

**方式 B：CLI 命令**

```bash
# 搜索关键词
dnacloud search trading

# 查看包详情
dnacloud info trading-master-dna
```

搜索结果示例：

```
1. Trading Master DNA Official
   Publisher: DNAcloud Official | verified official
   Price: 0.001 USDG (xlayer)

2. Conservative Trading Assistant
   Publisher: 0xd8da...6045 | auto-reviewed
   Price: 0.50 USDG (eip155:196)
   Validation: passed
```

---

### 第四步：安装 DNA 包

**方式 A：自然语言**

```
安装 trading-master-dna
我要购买 Trading Master DNA
```

**方式 B：CLI**

```bash
dnacloud install trading-master-dna
# 或指定版本
dnacloud install trading-master-dna --version 1.0.0
```

**安装预览**

安装前会展示：

```
即将安装：Trading Master DNA v1.0.0

将写入文件：
  .claude/skills/trading-master/SKILL.md
  .claude/agents/market-analyst.md
  .claude/agents/risk-manager.md
  .claude/commands/trade-plan.md
  .mcp.json (新增 market-data, order-execution)

价格：0.001 USDG (xlayer)
支付方式：OKX x402

确认安装？[y/N]
```

**OKX x402 支付流程**

1. 系统生成支付 challenge
2. 使用 OKX API Key/Secret 签名
3. 提交签名凭证
4. 服务端验证并下载 artifact
5. 安装到 `.claude/` 目录

> 支付进入 DNAcloud 平台公共账户，平台会在 48 小时内结算给创作者。

---

### 第五步：验证安装

```bash
dnacloud verify trading-master-dna
```

输出示例：

```
Package: trading-master-dna v1.0.0
Status: active ✓

  signature verified         ✓
  payment receipt found      ✓
  skills installed           ✓  (1 skill)
  agents installed           ✓  (5 agents)
  commands installed         ✓  (5 commands)
  mcp configured             ✓  (market-data, order-execution)
  hooks configured           ✓
  lock file updated          ✓
  rollback snapshot exists   ✓

  liveTradingReady: false
  → TRADING_API_KEY 未配置，运行命令查看配置说明
```

`liveTradingReady: false` 表示真实下单 MCP 未配置，**不代表安装失败**，分析和模拟功能可以正常使用。

---

### 第六步：使用已安装能力

安装完成后，可用命令：

| 命令 | 功能 |
|------|------|
| `/trade-plan BTC long 1000USDT 1%` | 生成完整交易计划 |
| `/risk-check` | 检查当前持仓风险 |
| `/order-preview` | 预览下单参数 |
| `/portfolio-status` | 查看仓位状态 |
| `/daily-trade-review` | 生成每日复盘 |

触发词示例：

```
分析一下 ETH 当前行情
我想做多 BTC，资金 500 USDT，帮我制定计划
检查一下我现在的风控情况
```

---

### 第七步：回滚（可选）

如果安装后不满意，可以回滚：

```bash
dnacloud rollback trading-master-dna
```

---

### 查看所有已安装包

```bash
dnacloud status
```

---

## Part 2：卖家（创作者）SOP

### 总流程

```
制作 DNA 包（zip）
→ dnacloud validate（本地校验）
→ dnacloud upload（上传+钱包签名）
→ 平台自动审核
→ 上架市场
→ 买家购买 → 收益进平台账本
→ 平台异步结算到你的收款地址
```

---

### 第一步：制作 DNA 包

DNA 包是一个 `.zip` 文件，解压后结构如下：

```
my-dna-package/
  manifest.json         ← 必须，包元信息
  install-plan.json     ← 必须，安装操作描述

  skills/
    <skill-name>/
      SKILL.md          ← Claude Code skill 定义

  agents/
    <agent-name>.md     ← Claude Code agent 定义

  commands/
    <command-name>.md   ← Claude Code 命令定义

  mcp/
    <server>.mcp.json   ← MCP server 配置（可选）

  hooks/
    hooks.json          ← PreToolUse hook（可选）

  rules/
    permissions.json    ← 权限声明

  tests/
    conformance-tests.json
```

**至少需要一种能力组件**：`skills/`、`agents/`、`commands/`、`mcp/` 任选一个。

---

### manifest.json 示例

```json
{
  "schemaVersion": "dnacloud.package.v1",
  "id": "my-trading-assistant",
  "name": "My Trading Assistant",
  "version": "1.0.0",
  "category": "trading",
  "description": "一个帮助新手进行加密货币分析的助手。不保证盈利。",
  "price": {
    "amount": "1.00",
    "currency": "USDG",
    "network": "eip155:196"
  },
  "payout": {
    "address": "0xYourWalletAddress",
    "network": "eip155:196",
    "currency": "USDG"
  },
  "creator": {
    "display_name": "Your Name",
    "wallet_address": "0xYourWalletAddress"
  },
  "risk_level": "medium",
  "capabilities": ["market_analysis"],
  "notGuaranteed": ["profitability", "investment_advice"]
}
```

**关键字段说明：**

| 字段 | 要求 |
|------|------|
| `id` | 全小写，只含字母、数字和 `-`，全市场唯一 |
| `version` | 语义化版本，如 `1.0.0` |
| `price.amount` | 价格字符串，如 `"1.00"` |
| `payout.address` | 你的 EVM 收款地址，必须是你控制的钱包 |
| `payout.network` | 支持：`eip155:196`（XLayer）、`xlayer`、`mantle` |
| `payout.currency` | 支持：`USDG`、`USDT`、`USDC` |

---

### install-plan.json 示例

```json
{
  "packageId": "my-trading-assistant",
  "version": "1.0.0",
  "targetDir": ".claude",
  "operations": [
    {
      "type": "copy",
      "source": "skills/trading/SKILL.md",
      "destination": ".claude/skills/trading/SKILL.md",
      "description": "安装交易 skill"
    },
    {
      "type": "copy",
      "source": "commands/trade-plan.md",
      "destination": ".claude/commands/trade-plan.md",
      "description": "安装 /trade-plan 命令"
    }
  ],
  "rollbackPlan": [
    { "type": "delete", "path": ".claude/skills/trading" },
    { "type": "delete", "path": ".claude/commands/trade-plan.md" }
  ]
}
```

---

### 第二步：本地校验

打包前先在本地校验：

```bash
dnacloud validate ./my-dna-package.zip
```

输出示例（通过）：

```
✔ 校验通过 (score: 100)
  Capabilities: Skills: 1 | Agents: 2 | Commands: 3 | MCP: 0 | Hooks: 0
```

输出示例（失败）：

```
✖ 校验失败 (score: 60)

✗ 错误：
  [MISSING_PAYOUT_ADDRESS] manifest.payout.address is required
  [DANGER_CURL_PIPE_SH] curl | sh pattern detected (skills/setup/SKILL.md)

⚠ 警告：
  [HOOKS_PRESENT] This package installs hooks. Buyer confirmation will be required.
```

**自动检查项包括：**

- zip 路径穿越攻击
- manifest 格式合规性
- 版本号合法性（semver）
- payout 地址和网络支持范围
- 文件类型白名单（只允许 .md / .json / .yaml / .txt 等）
- 私钥、助记词、API Key 扫描
- 危险命令模式扫描（`curl|sh`、`wget|sh`、`chmod 777` 等）
- 能力组件完整性

---

### 第三步：上传到 DNAcloud

#### 方式 A：Claude Code 自然语言

```
我要上传一个 DNA 包
```

Claude Code 会引导：
1. 输入包文件路径
2. 输入收款地址
3. 本地校验
4. 生成 challenge 供你签名
5. 上传并返回市场链接

#### 方式 B：CLI 命令

```bash
dnacloud upload ./my-dna-package.zip \
  --payout-address 0xYourWalletAddress \
  --price 1.00 \
  --currency USDG \
  --category trading
```

**签名步骤**

上传时系统会输出一个 challenge 字符串，需要用你的钱包私钥签名（EIP-191 个人签名）：

```
Challenge: dnacloud-upload:abc123:sha256hash:0xYourAddress:1778143487
```

使用工具签名：

```bash
# 使用 cast（Foundry）
cast sign --private-key $WALLET_PRIVATE_KEY "dnacloud-upload:..."

# 使用 ethers.js
const sig = await wallet.signMessage("dnacloud-upload:...");
```

将签名粘贴回 CLI 后，上传完成。

**上传结果示例：**

```
✓ DNA 包上传成功！

  Package ID:        my-trading-assistant
  Status:            published
  Validation:        passed
  Marketplace URL:   dnacloud://package/my-trading-assistant
```

---

### 第四步：确认上架状态

```bash
dnacloud creator packages 0xYourWalletAddress
```

输出：

```
  ● My Trading Assistant v1.0.0 — published — 1.00 USDG
```

包状态说明：

| 状态 | 含义 |
|------|------|
| `published` | 已上架，可被搜索购买 |
| `rejected` | 校验失败，不可购买（查看校验报告修复后重新上传新版本）|
| `suspended` | 平台暂停展示（联系平台）|

---

### 第五步：查看收益

每次有买家购买后，平台自动记录收益。

```bash
dnacloud creator earnings 0xYourWalletAddress
```

输出示例：

```
📊 创作者收益报告

  Payout Address:   0xYourWalletAddress
  Currency:         USDG (eip155:196)

  Total Gross Sales:   5.000000 USDG
  Platform Fee:       -1.000000 USDG   (20%)
  Pending Payout:      4.000000 USDG
  Paid Payout:         0.000000 USDG

  Recent Entries:
    ● my-trading-assistant — 0.800000 USDG — pending_payout
    ● my-trading-assistant — 0.800000 USDG — pending_payout
    ...
```

---

### 第六步：查看结算记录

平台 payout worker 周期性把收益转到你的收款地址。

```bash
dnacloud creator payouts 0xYourWalletAddress
```

输出示例：

```
💸 结算记录

  ● batch-uuid-xxx
    Amount: 4.000000 USDG
    Status: paid
    Tx:     0xabc123...def456
    Date:   2026-05-08T10:00:00Z
```

**结算说明：**

| 状态 | 含义 |
|------|------|
| `pending_payout` | 已产生收益，等待批量结算 |
| `payout_processing` | 结算中 |
| `paid` | 已转账，有 tx hash 可查 |
| `payout_failed` | 转账失败，平台会重试 |

> 当前版本：如果服务器未配置 `DNACLOUD_TREASURY_KEY`，结算将保持 `pending_payout` 状态，不会自动转账。联系平台运营配置结算能力。

---

### 版本更新

不能覆盖已发布版本，必须新增 version：

1. 修改 `manifest.json` 中的 `version`（如 `1.0.1`）
2. 重新打包
3. 重新 validate + upload

---

## Part 3：禁止事项

**买家**
- 不要分享购买凭证给他人
- 不要尝试绕过支付下载包

**卖家**
- 不要在包中内嵌私钥、助记词、API Key
- 不要包含 `curl|sh`、`wget|sh` 等远程执行命令
- 不要声称盈利保证、投资建议
- 不要填写不属于你的收款地址（签名验证会失败）
- 不要覆盖已发布版本，必须 bump version

---

## Part 4：常见问题

**Q：搜索不到包？**
确认 `dnacloud init` 已在当前项目执行，`.mcp.json` 已注册 `dnacloud-marketplace`。

**Q：支付失败？**
检查 `.env` 中 `OKX_API_KEY`、`OKX_SECRET_KEY`、`OKX_PASSPHRASE` 是否正确配置。

**Q：verify 显示 `liveTradingReady: false`？**
这不是安装失败。需要配置对应 MCP 的真实凭据（如 `TRADING_API_KEY`）才能启用真实下单能力，分析功能已可正常使用。

**Q：上传包显示 `rejected`？**
运行 `dnacloud validate ./my-package.zip` 查看详细错误，修复后修改版本号重新上传。

**Q：收益一直是 `pending_payout`？**
当前版本的 payout 自动转账需要服务器配置 `DNACLOUD_TREASURY_KEY`，如果是本地开发环境则结算停在 pending 是正常的。

---

## 快速参考卡

```bash
# 买家
dnacloud init                          # 初始化
dnacloud install <package-id>          # 安装包
dnacloud verify <package-id>           # 验证
dnacloud status                        # 查看所有已装包
dnacloud rollback <package-id>         # 回滚

# 卖家
dnacloud validate ./my-package.zip     # 本地校验
dnacloud upload ./my-package.zip \
  --payout-address 0x...               # 上传
dnacloud creator packages 0x...        # 查看包列表
dnacloud creator earnings 0x...        # 查看收益
dnacloud creator payouts 0x...         # 查看结算

# Claude Code 命令
/dna-upload                            # 引导上传流程
/dna-earnings                          # 查看收益
/dna-packages                          # 查看包列表
```
