# DNAcloud for Claude Code 技术方案 v0.5

更新时间：2026-05-07  
版本变化：Trading Master DNA 升级为一期正式官方包，但验收目标从盈利结果改为交易能力安装与可用性。

---

## 1. 技术目标

Phase 1 要实现两个完整闭环：

```text
闭环 A：DNAcloud Bootstrap 安装闭环
用户安装 Bootstrap → Claude Code 获得 DNAcloud 搜索、支付、下载、安装能力

闭环 B：Trading Master DNA 安装闭环
用户说“我要一个交易大师” → 支付 → 下载 → 安装 → verify → Claude Code 获得交易能力
```

---

## 2. 系统组件

```text
Client Side
  ├── DNAcloud Bootstrap Plugin
  ├── dnacloud CLI
  ├── dnacloud MCP Server
  ├── Package Installer
  ├── Verifier
  └── Trading Master DNA installed components

Server Side
  ├── Marketplace API
  ├── Package Registry
  ├── Artifact Storage
  ├── OKX x402 Seller Middleware
  ├── Signing Service
  └── Receipt Ledger
```

---

## 3. Trading Master DNA 包结构

```text
trading-master-dna/
  manifest.json
  install-plan.json
  signature.txt
  package.sha256

  skills/
    trading-master/
      SKILL.md
      references/
        trading-workflow.md
        market-analysis.md
        position-sizing.md
        risk-policy.md
        order-preview.md
        execution-policy.md
        trade-review.md

  agents/
    market-analyst.md
    portfolio-manager.md
    risk-manager.md
    execution-reviewer.md
    trade-journalist.md

  commands/
    trade-plan.md
    risk-check.md
    order-preview.md
    portfolio-status.md
    daily-trade-review.md

  mcp/
    market-data.mcp.json
    account-read.mcp.json
    order-execution.mcp.json

  hooks/
    pre-tool-use-trade-guard.json

  rules/
    machine-rules.json
    permissions.json
    trading-capabilities.json

  claude/
    CLAUDE.patch.md

  tests/
    conformance-tests.json
```

---

## 4. Trading Master DNA manifest 要点

```json
{
  "schemaVersion": "dnacloud.package.v1",
  "id": "trading-master-dna",
  "name": "Trading Master DNA",
  "version": "1.0.0",
  "domain": "trading",
  "packageType": "official-capability-pack",
  "objective": "install trading capabilities into Claude Code, not optimize profitability",
  "capabilities": [
    "market_analysis",
    "position_management",
    "strategy_workflow",
    "risk_control",
    "order_preview",
    "live_order_tool_integration",
    "trade_journal",
    "post_trade_review"
  ],
  "notGuaranteed": [
    "profitability",
    "win_rate",
    "investment_advice",
    "risk_free_trading"
  ],
  "components": {
    "skills": ["skills/trading-master/SKILL.md"],
    "agents": [
      "agents/market-analyst.md",
      "agents/portfolio-manager.md",
      "agents/risk-manager.md",
      "agents/execution-reviewer.md",
      "agents/trade-journalist.md"
    ],
    "commands": [
      "commands/trade-plan.md",
      "commands/risk-check.md",
      "commands/order-preview.md",
      "commands/portfolio-status.md",
      "commands/daily-trade-review.md"
    ],
    "mcp": [
      "mcp/market-data.mcp.json",
      "mcp/account-read.mcp.json",
      "mcp/order-execution.mcp.json"
    ],
    "hooks": ["hooks/pre-tool-use-trade-guard.json"],
    "rules": ["rules/machine-rules.json"]
  }
}
```

---

## 5. Trading Master DNA 的真实能力边界

### 5.1 必须支持

- 读取真实市场数据 MCP；
- 读取真实账户/仓位 MCP；
- 生成交易计划；
- 生成订单预览；
- 调用真实下单 MCP 的接口路径；
- 在 Claude Code Hooks 中加入 PreToolUse 交易检查；
- 安装后验证各组件存在；
- 缺失真实 MCP 凭据时明确提示用户配置，而不是生成假数据。

### 5.2 不需要优化

- 策略盈利性；
- 回测收益表现；
- 高频交易算法；
- 复杂 alpha 因子；
- 精细化策略参数。

### 5.3 不允许

- 伪造行情；
- 伪造订单；
- 伪造成交；
- 伪造账户余额；
- 在没有用户配置真实 MCP 时假装可下单。

---

## 6. MCP 配置原则

Trading Master DNA 可以安装 MCP 配置，但不应内置用户密钥。

`.mcp.json` 中只写 server 定义和环境变量引用：

```json
{
  "mcpServers": {
    "market-data": {
      "command": "dnacloud-market-mcp",
      "args": ["--provider", "${DNACLOUD_MARKET_PROVIDER}"],
      "env": {
        "API_KEY": "${MARKET_DATA_API_KEY}"
      }
    },
    "order-execution": {
      "command": "dnacloud-order-mcp",
      "args": ["--venue", "${DNACLOUD_TRADING_VENUE}"],
      "env": {
        "API_KEY": "${TRADING_API_KEY}",
        "API_SECRET": "${TRADING_API_SECRET}"
      }
    }
  }
}
```

如果用户没有配置真实环境变量，交易能力应该进入“待配置”状态，而不是 mock。

---

## 7. PreToolUse Hook

Trading Master DNA 可以安装 PreToolUse hook，用于订单相关工具调用前展示风险检查结果。

Hook 关注：

```text
mcp__order-execution__place_order
mcp__order-execution__cancel_order
mcp__order-execution__modify_order
mcp__wallet__swap
mcp__wallet__approve
```

Hook 行为：

```text
1. 读取 order 参数
2. 检查是否存在安装的 Trading Master DNA rules
3. 检查用户是否配置交易场景
4. 检查是否需要二次确认
5. 输出 allow / deny / ask
```

注意：DNAcloud 基础服务不替用户承担交易风险。Hook 只是把交易能力包中的流程和用户配置的规则接入 Claude Code。

---

## 8. 安装验证标准

`dnacloud verify` 对 Trading Master DNA 的验收不看盈利，看能力完整性。

示例输出：

```json
{
  "package": "trading-master-dna",
  "version": "1.0.0",
  "status": "active",
  "signatureVerified": true,
  "paymentReceiptFound": true,
  "skillsInstalled": true,
  "agentsInstalled": true,
  "commandsInstalled": true,
  "mcpConfigured": true,
  "hooksConfigured": true,
  "claudePatchApplied": true,
  "liveTradingReady": false,
  "missingUserConfig": [
    "TRADING_API_KEY",
    "TRADING_API_SECRET",
    "DNACLOUD_TRADING_VENUE"
  ],
  "capabilitiesAvailable": [
    "market_analysis_workflow",
    "position_management_workflow",
    "risk_check_workflow",
    "order_preview_workflow",
    "trade_review_workflow"
  ]
}
```

`liveTradingReady=false` 不代表安装失败，只代表用户尚未配置真实下单凭据。

---

## 9. OKX x402 支付闭环

Trading Master DNA 是正式商品，下载 artifact 必须通过 OKX x402 支付。

流程：

```text
GET /v1/dna/trading-master-dna/versions/1.0.0/artifact
→ 402 Payment Required
→ client signs OKX x402 payment
→ retry request with payment credential
→ server verifies with OKX
→ server settles with OKX
→ returns signed artifact
```

服务端不允许：

- verify 失败仍返回 artifact；
- 缺少 OKX env 仍返回 artifact；
- 用 mock receipt 代替真实 receipt。

---

## 10. 安装流程

```text
1. 用户需求触发 dnacloud skill
2. marketplace 返回 Trading Master DNA
3. 用户确认购买
4. OKX x402 支付
5. artifact 下载
6. verify signature/hash
7. unpack to staging
8. generate install preview
9. create project snapshot
10. apply install-plan
11. update .dnacloud/lock.json
12. run dnacloud verify
13. mark active
```

---

## 11. 用户上传 DNA 扩展

虽然 Trading Master DNA 是一期正式官方包，系统仍要保留来源抽象：

```ts
interface DnaSource {
  id: string;
  type: 'marketplace' | 'local-upload' | 'git' | 'enterprise';
  search(query: SearchQuery): Promise<DnaSearchResult[]>;
  getManifest(ref: DnaRef): Promise<DnaManifest>;
  acquire(ref: DnaRef, payment?: PaymentContext): Promise<DnaArtifact>;
}
```

Phase 1：

```text
MarketplaceSource: implemented
LocalUploadSource: interface reserved
GitSource: interface reserved
EnterpriseSource: interface reserved
```

---

## 12. Definition of Done

一期完成必须满足：

- [ ] DNAcloud Bootstrap 能安装到 Claude Code 项目；
- [ ] 用户说“我要一个交易大师”能触发 DNAcloud Skill；
- [ ] Marketplace 返回 Trading Master DNA 官方包；
- [ ] 用户通过 OKX x402 真实支付；
- [ ] 服务端返回签名 Trading Master DNA artifact；
- [ ] Installer 展示安装预览；
- [ ] Installer 写入 Skills、Agents、Commands、MCP、Hooks、Rules；
- [ ] `dnacloud verify` 显示 active；
- [ ] Claude Code 能使用交易分析、资金管理、风险检查、订单预览、复盘能力；
- [ ] 缺少真实交易凭据时不伪造下单能力；
- [ ] 配置真实下单 MCP 后，系统可进入真实下单授权流程。
