# DNAcloud Whitepaper

版本：v0.1 Hackathon Edition  
项目：DNAcloud for Claude Code  
支付层：OKX Onchain OS Payment / x402

---

## 摘要

DNAcloud 是面向 Claude Code 的 AI Agent 能力包市场和安装器。用户可以用自然语言描述自己想要的专家能力，例如“我要一个交易大师”“我要一个电商老师”“我要一个合同审查律师”。DNAcloud 会检索市场中的 DNA 包，通过 OKX x402 完成支付，下载并校验签名包，然后把其中的 Skills、Subagents、MCP 配置、Hooks、Rules、Commands、Tests 安装到用户当前 Claude Code 项目中。

DNAcloud 同时支持创作者上传自己的 DNA 包并上架售卖。买家购买后，支付金额先进入平台公共账户，平台账本记录创作者应收，并由异步结算 worker 将资金打到该 DNA 包绑定的创作者收款地址。

项目的核心不是卖 prompt，也不是卖普通 Markdown，而是把专家能力打包成 Claude Code 可安装、可验证、可支付、可分发的能力单元。

---

## 1. 背景：Agent 会使用工具，但能力配置仍然困难

Claude Code 已经具备强大的可扩展能力。Claude Code 插件可以包含 Skills、Agents、Hooks、MCP servers 和 commands；Skills 可以将任务专用能力添加到 Claude Code；Subagents 可以拥有独立上下文和专门工具；Hooks 可以在工具调用前后执行命令；MCP 可以连接外部工具和服务。

但对普通用户来说，这些能力仍然太底层。一个小白想让 Claude Code 成为交易助手，并不是说一句“你是交易大师”就够了。他需要：

- 写交易 Skill；
- 配置行情和交易相关 MCP；
- 设置资金管理规则；
- 配置下单前 Hook；
- 写风险检查规则；
- 添加订单预览命令；
- 设计复盘流程；
- 运行安装后验证；
- 维护更新和回滚。

这是一整套 agent engineering 工作，不是普通用户能轻松完成的。

---

## 2. 问题定义

现有 AI Agent 生态有三个断点：

### 2.1 用户需求和可运行能力之间断裂

用户说“我要一个交易大师”，但 Claude Code 本身不会自动知道应该安装哪些 Skills、Agents、MCP、Hooks 和规则。

### 2.2 创作者能力和用户项目之间断裂

专家可以写教程、卖课程、分享 prompt，但很难把自己的专业能力变成用户 Claude Code 项目里可运行的结构化能力包。

### 2.3 Agent 原生支付缺失

传统支付适合人类订阅，不适合 agent 在请求资源时按次购买能力包、数据和服务。AI Agent 需要可以通过 HTTP 请求直接完成支付的机制。

---

## 3. 解决方案：DNAcloud

DNAcloud 提供三层能力：

1. DNAcloud Bootstrap：安装在用户 Claude Code 项目中的基础技能和 CLI，用于搜索、支付、下载、安装和验证 DNA 包。
2. DNAcloud Marketplace：托管和分发 DNA 包，支持创作者上传、自动审核、签名、上架和收益结算。
3. OKX x402 Payment：买家获取付费 DNA 包时使用的支付层。

DNAcloud 的目标是把复杂的 Claude Code 配置流程变成：

```text
用户说出需求
→ DNAcloud 搜索能力包
→ 用户支付
→ DNAcloud 安装能力包
→ verify 通过
→ Claude Code 获得专家能力
```

---

## 4. DNA 包定义

DNA 包是一个 Claude Code 能力包。它可以包含：

```text
manifest.json
install-plan.json
signature.txt
skills/
agents/
commands/
mcp/
hooks/
rules/
tests/
memory/
```

### 4.1 manifest.json

描述 DNA 包的身份、版本、创作者、价格、分类、有效期和兼容性。

### 4.2 skills/

提供 Claude Code 可调用的专业 Skill。例如 trading-master、contract-review、amazon-listing-optimizer。

### 4.3 agents/

提供专用 Subagents。例如 market-analyst、risk-manager、portfolio-manager。

### 4.4 commands/

提供 Slash commands。例如 /trade-plan、/risk-check、/daily-review。

### 4.5 mcp/

提供 MCP server 配置模板或安装说明。

### 4.6 hooks/

提供工具调用前后的约束逻辑，例如下单前风险检查。

### 4.7 rules/

提供机器可读规则，如风险上限、权限策略、安装约束。

### 4.8 tests/

提供安装后验证用例，证明 DNA 包已激活。

---

## 5. 买家体验

第一期 demo 以 Trading Master DNA 为例。

用户安装 DNAcloud Bootstrap：

```bash
npm install -g dnacloud
dnacloud init
```

然后在 Claude Code 中说：

```text
我要一个交易大师，可以帮助我下单、资金管理、策略交易和风险控制。
```

DNAcloud Skill 识别需求，搜索 Marketplace，返回 Trading Master DNA。用户确认后通过 OKX x402 支付。支付完成后，DNAcloud 下载签名 DNA 包，展示安装预览，然后安装到当前 Claude Code 项目：

```text
.claude/skills/trading-master/
.claude/agents/market-analyst.md
.claude/agents/risk-manager.md
.claude/commands/trade-plan.md
.claude/commands/risk-check.md
.mcp.json
.dnacloud/installed/trading-master/
```

安装完成后运行 verify，确认该能力包 active。

Trading Master DNA 的目标不是保证盈利，而是让用户获得完整交易技能结构：交易分析、资金管理、策略流程、风险检查、订单预览、MCP 接入和复盘能力。

---

## 6. 创作者体验

创作者同样先运行：

```bash
dnacloud init
```

然后使用 DNAcloud Skill 或 CLI 上传 DNA 包：

```bash
dnacloud publish ./my-dna-package \
  --price 1 \
  --token USDT \
  --receiver 0xCreatorAddress \
  --category trading
```

平台会进行自动校验：

- manifest 是否完整；
- 文件结构是否符合规范；
- 是否包含危险脚本；
- 是否包含明显的私钥、助记词、API key；
- 收款地址格式是否正确；
- 是否能通过基础安装测试。

校验通过后，平台签名并上架。买家购买后，付款进入平台公共账户，平台异步结算到该 DNA 包绑定的收款地址。

---

## 7. 支付设计：OKX x402

DNAcloud 使用 OKX Onchain OS Payment / x402 完成买家获取 DNA 包的支付流程。

标准流程：

```text
1. Buyer requests DNA resource.
2. Seller returns 402 Payment Required with amount, token, network, destination.
3. Buyer signs and retries request with payment credential.
4. Seller verifies and returns the DNA package.
```

DNAcloud 当前采用平台公共账户收款模式：

```text
买家支付 → 平台公共账户 → 平台账本记录 → 异步结算给创作者地址
```

这样可以先保证交付链路简单稳定，同时为后续 escrow、链上分账、订阅、批量支付保留扩展空间。

---

## 8. 安全设计

DNAcloud 不应该让用户盲目安装第三方内容。第一期自动审核虽然简单，但必须包含基础安全机制：

1. 包结构校验；
2. manifest schema 校验；
3. 禁止明显危险命令；
4. 检测私钥、助记词、API key；
5. 安装预览；
6. 签名校验；
7. 安装后 verify；
8. rollback；
9. 创作者收款地址绑定；
10. 平台账本和 payout 记录。

---

## 9. 第一阶段范围

第一阶段重点不是做一个盈利交易机器人，也不是做完整内容市场，而是建立完整基础服务主流程：

- DNAcloud Bootstrap；
- Marketplace search；
- OKX x402 支付；
- DNA 包下载；
- 签名校验；
- Claude Code 安装；
- verify；
- 创作者上传；
- 自动审核；
- 上架；
- 平台公共账户收款；
- 异步结算到创作者地址。

Trading Master DNA 是官方示例包，用于证明复杂能力包可以被购买和安装。它只需要提供交易能力结构，不需要证明盈利。

---

## 10. 未来路线图

### Phase 1：Hackathon MVP

- Bootstrap install
- Trading Master DNA official package
- OKX x402 purchase
- DNA install and verify
- Creator upload
- Simple automated review
- Async payout

### Phase 2：Marketplace Growth

- Creator dashboard
- Public package pages
- Ratings and reviews
- Version update and rollback
- Featured packages
- Better security scanner

### Phase 3：Agent Economy Layer

- Batch payment
- Pay-per-call DNA APIs
- Real-time data DNA
- Creator subscriptions
- Escrow and dispute flow
- Cross-client support beyond Claude Code

---

## 11. 结论

DNAcloud 的愿景是成为 AI Agent 能力分发层。

用户不应该学习复杂的 Claude Code 配置才能获得专家 agent。创作者也不应该只能卖教程和 prompt。DNAcloud 让专家能力变成可安装、可支付、可验证、可分发的 DNA 包。

在黑客松版本中，我们聚焦 Claude Code 和 OKX x402，打通从用户需求、能力包搜索、链上支付、下载安装、验证激活，到创作者上传和异步结算的完整闭环。
