# DNAcloud 官网设计与信息架构

版本：v0.1 Hackathon Edition  
项目定位：DNAcloud for Claude Code，基于 OKX x402 的 AI Agent 能力包市场与安装器。

---

## 1. 官网目标

官网不是普通介绍页，而是黑客松评委和早期用户理解项目的入口。它需要在 30 秒内讲清楚：

1. DNAcloud 是什么；
2. 为什么 Claude Code 用户需要它；
3. OKX x402 在项目里解决了什么问题；
4. 买家如何购买并安装 DNA 包；
5. 创作者如何上传 DNA 包并获得收益；
6. 第一版 demo 能跑通什么。

一句话定位：

> DNAcloud is an agent capability marketplace for Claude Code. Users describe what expert agent they want, pay through OKX x402, and install Skills, Agents, MCP, Hooks, Rules, and Tests into their current Claude Code project.

中文定位：

> DNAcloud 是面向 Claude Code 的 AI Agent 能力包市场。用户说出想要什么专家，DNAcloud 就通过 OKX x402 购买并安装对应的 Skill、Agent、MCP、Hook、规则和测试。

---

## 2. 首页结构

### Section 1：Hero

标题：

> Turn Claude Code into any expert agent in minutes.

中文副标题：

> 用 OKX x402 购买专家 DNA 包，一键把 Claude Code 初始化成交易助手、电商教练、合同审查员或任意专业 Agent。

主按钮：

- Try Demo
- Read Whitepaper
- Upload DNA Package

核心标签：

- Built for Claude Code
- Powered by OKX x402
- Skills + Agents + MCP + Hooks
- Creator Marketplace

---

### Section 2：Problem

标题：

> Claude Code is powerful, but setup is hard.

内容：

小白想让 Claude Code 成为“交易大师”或“电商老师”，不能只靠一句 prompt。他需要配置 Skills、Subagents、MCP、Hooks、规则、记忆、测试和权限。这是一整套 agent engineering，而不是简单提问。

痛点卡片：

1. 用户不会写 Skill；
2. 用户不会配置 MCP；
3. 用户不知道怎么设置 Hook 和风控；
4. 用户不知道专家工作流应该长什么样；
5. 创作者也没有简单方式把自己的 agent 能力出售给 Claude Code 用户。

---

### Section 3：Solution

标题：

> DNAcloud packages expert capability into installable DNA.

解释：

DNA 包不是普通文档，而是一个可安装的 Claude Code 能力包，包含：

- Skills
- Subagents
- Slash commands
- MCP configuration
- Hooks
- Rules
- Tests
- Memory / CLAUDE.md patch
- Manifest and signature

---

### Section 4：Buyer Flow

标题：

> From one sentence to installed expert agent.

流程：

1. 用户安装 DNAcloud Bootstrap；
2. 用户在 Claude Code 里说：“我要一个交易大师”；
3. DNAcloud Skill 搜索市场；
4. 用户选择 Trading Master DNA；
5. 用户通过 OKX x402 支付；
6. DNAcloud 下载并校验 DNA 包；
7. 安装到当前 Claude Code 项目；
8. 运行 verify；
9. Claude Code 获得交易相关能力。

---

### Section 5：Creator Flow

标题：

> Anyone can sell expert DNA.

流程：

1. 创作者运行 `dnacloud init`；
2. 准备 DNA 包；
3. 通过 DNAcloud Skill/CLI 上传；
4. 填写价格、分类、简介、收款地址；
5. 平台自动校验包结构和简单安全风险；
6. 审核通过后上架；
7. 买家购买后，资金进入平台公共账户；
8. 平台异步结算给 DNA 包绑定的收款地址。

---

### Section 6：Payment Architecture

标题：

> Agent-native payment with OKX x402.

解释：

DNAcloud 使用 OKX Onchain OS Payment / x402 作为买家获取 DNA 包的支付层。买家请求付费资源，服务端返回 402 payment requirement，客户端签名并重试请求，服务端验证支付后返回 DNA 包。

强调：

- 不需要传统账号订阅；
- 适合 AI Agent 按次购买能力包或数据；
- 支持未来的高频能力调用和微支付；
- 平台使用公共账户收款，再异步结算给创作者。

---

### Section 7：Demo Scenario

标题：

> Hackathon demo: Install a Trading Master DNA.

Demo 脚本：

```bash
npm install -g dnacloud
dnacloud init
```

用户在 Claude Code 里说：

```text
我要一个交易大师，可以帮助我下单、资金管理、策略交易和风险控制。
```

DNAcloud 返回：

```text
Found: Trading Master DNA
Includes:
- trading-master skill
- market-analyst agent
- risk-manager agent
- order-preview command
- MCP config
- pre-trade hook
- risk rules
- conformance tests
Price: 1 USDT via OKX x402
```

用户支付后：

```text
Trading Master DNA installed successfully.
Status: active
Live trading: requires user-controlled MCP and approval
```

---

### Section 8：Whitepaper CTA

按钮：

- Read whitepaper
- View architecture
- Start building DNA

---

## 3. 视觉设计建议

### 品牌关键词

- Agent-native
- Programmable capability
- Marketplace
- Trust and installation
- OKX x402 payment
- Claude Code extension

### 色彩

建议使用深色科技风，但避免纯 Web3 土味。

- Background: `#070A12`
- Card: `#111827`
- Border: `#263244`
- Primary neon: `#66F2C2`
- Secondary blue: `#6EA8FF`
- Accent purple: `#B794F4`
- Text primary: `#F8FAFC`
- Text secondary: `#A7B0C0`

### 字体

- 英文标题：Inter / Space Grotesk
- 正文：Inter / system-ui
- 代码：JetBrains Mono / Menlo

### 组件

- Hero gradient blob
- Terminal demo panel
- Capability package card
- Flow timeline
- Architecture diagram
- Creator payout card
- Whitepaper content tabs

---

## 4. 首页首屏文案

```text
DNAcloud
The capability marketplace for Claude Code agents.

Say what expert you want. Pay with OKX x402. Install Skills, Agents, MCP, Hooks, Rules, and Tests into your Claude Code project.

From “I want a trading master” to a working Claude Code capability pack.
```

中文：

```text
DNAcloud
Claude Code 的专家能力包市场。

用户只需要说出想要什么专家，DNAcloud 就会通过 OKX x402 购买并安装对应的 Skill、Agent、MCP、Hook、规则和测试。

从“我要一个交易大师”到可运行的 Claude Code 能力包。
```

---

## 5. 黑客松评委最关心的问题

### Q1：你们到底做了什么？

我们做了一个 Claude Code 的能力包市场和安装器。它让用户通过自然语言搜索、购买、安装专家 DNA 包；也让创作者上传自己的 DNA 包并通过 OKX x402 获得收益。

### Q2：为什么需要 x402？

因为 DNA 包本质上是 agent 可调用、可安装、可更新的数字资源。x402 让 agent/CLI 可以在请求资源时直接完成按次付费，不需要传统账号、订阅或支付网关。

### Q3：为什么不只是卖 prompt？

DNA 包不是 prompt。它包含 Skills、Subagents、MCP 配置、Hooks、规则、测试和安装计划。它安装后会改变 Claude Code 当前项目的能力结构。

### Q4：为什么从交易大师开始？

交易大师是一个复杂但容易理解的 demo。它能展示 Skills、Agents、MCP、Hooks、Rules、Tests、x402 支付和安装验证的完整链路。我们不承诺盈利，目标是证明能力安装和市场交易闭环。

### Q5：未来怎么扩展？

从官方 Trading Master DNA 开始，扩展到用户上传 DNA 包，再扩展到法律、电商、投研、客服、营销、代码审查等垂直领域。
