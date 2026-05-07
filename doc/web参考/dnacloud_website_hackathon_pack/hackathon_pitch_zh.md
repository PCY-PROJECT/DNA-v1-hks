# DNAcloud 黑客松 Pitch 材料

## 1. 30 秒一句话

DNAcloud 是 Claude Code 的专家能力包市场。用户说“我要一个交易大师”，DNAcloud 就会通过 OKX x402 购买并安装对应的 Skills、Agents、MCP、Hooks、Rules 和 Tests，让 Claude Code 当前项目获得真正可运行的专家能力。

---

## 2. 2 分钟 Pitch 脚本

大家好，我们的项目叫 DNAcloud。

今天很多人开始使用 Claude Code，但大多数人只把它当成代码助手。实际上，Claude Code 已经可以通过 Skills、Subagents、MCP、Hooks 和 commands 变成非常强大的 agent runtime。

问题是：普通用户不会配置这些能力。

比如一个小白说：“我想要一个交易大师，可以帮我下单、资金管理、策略交易和风险控制。”在没有 DNAcloud 之前，他需要自己写 Skill、配置 MCP、写 Hook、设置风险规则、设计复盘流程，还要测试是否安装成功。这不是普通用户能完成的。

DNAcloud 解决这个问题。

用户先安装 DNAcloud Bootstrap。之后他只要在 Claude Code 里说：“我要一个交易大师。”DNAcloud Skill 会搜索我们的 DNAcloud Marketplace，找到 Trading Master DNA。用户通过 OKX x402 支付后，DNAcloud 会下载签名能力包，并把其中的 Skills、Agents、MCP、Hooks、Rules、Commands 和 Tests 安装到当前 Claude Code 项目里。

安装完成后，Claude Code 就获得了交易相关的能力结构：交易分析、资金管理、策略流程、风险检查、订单预览、MCP 接入和复盘能力。我们不承诺盈利，我们解决的是“如何把专家能力安装进 agent”。

同时，DNAcloud 也支持创作者上传自己的 DNA 包。创作者提交能力包、价格和收款地址，平台自动校验后上架。买家购买后，支付先进入平台公共账户，平台再异步结算给该 DNA 包绑定的创作者地址。

OKX x402 是这个系统的支付层。它让 agent 或 CLI 在请求资源时可以直接收到 402 payment requirement，签名付款后获得 DNA 包，非常适合 AI Agent 购买能力、数据和工具。

我们的愿景是：让专家能力像 npm package 一样被安装，让每个人都能把 Claude Code 变成自己需要的专业 agent。

---

## 3. Demo 流程

### Step 1：初始化

```bash
npm install -g dnacloud
dnacloud init
```

### Step 2：用户自然语言需求

```text
我要一个交易大师，可以帮助我下单、资金管理、策略交易和风险控制。
```

### Step 3：DNAcloud 搜索市场

```text
Found Trading Master DNA
Price: 1 USDT
Payment: OKX x402
Includes: Skills, Agents, MCP, Hooks, Rules, Commands, Tests
```

### Step 4：支付并下载

```text
402 Payment Required
Signing payment credential...
Payment verified.
Downloading DNA package...
```

### Step 5：安装预览

```text
Will install:
- .claude/skills/trading-master
- .claude/agents/market-analyst.md
- .claude/agents/risk-manager.md
- .claude/commands/trade-plan.md
- .claude/commands/risk-check.md
- .mcp.json entries
- hooks/pre-trade-check
- .dnacloud/installed/trading-master
```

### Step 6：Verify

```text
Trading Master DNA active.
Skills installed: yes
Agents installed: yes
MCP config installed: yes
Hooks installed: yes
Tests passed: yes
```

### Step 7：创作者上传

```bash
dnacloud publish ./my-dna-package --price 1 --receiver 0xCreatorAddress
```

---

## 4. 评委 Q&A

### Q：这和 prompt 市场有什么区别？

Prompt 只是文本。DNA 包是 Claude Code 能力包，包含 Skills、Agents、MCP、Hooks、Rules、Commands、Tests 和安装计划。它不是给用户看的文章，而是会安装到项目里的运行结构。

### Q：为什么用 x402？

DNA 包是 agent 可以按需获取的付费资源。x402 允许客户端请求资源时收到 402 支付要求，然后签名付款并获得资源，非常适合 agent-native commerce。

### Q：为什么先做 Trading Master？

因为交易场景可以展示完整能力：数据、下单、风险、策略、资金管理、复盘、Hook、MCP。它是复杂度足够高的 demo，但我们的目标不是交易盈利，而是能力安装闭环。

### Q：创作者怎么赚钱？

创作者上传 DNA 包并绑定收款地址。买家支付进入平台公共账户，平台账本记录应收并异步结算给该 DNA 包的收款地址。

### Q：安全怎么处理？

第一期做简单自动审核、签名、安装预览、敏感信息扫描、危险命令扫描、verify 和 rollback。后续会升级成更完整的安全审核和信誉系统。
