# DNAcloud for Claude Code 产品方案 v0.5

更新时间：2026-05-07  
版本变化：将 **Trading Master DNA** 从“示例业务包”升级为 **一期正式官方能力包**。但一期不以盈利能力为目标，而以“用户能获得完整交易 agent 技能”作为目标。

---

## 1. 本版核心结论

DNAcloud Phase 1 要交付两件正式产品：

1. **DNAcloud Bootstrap for Claude Code**  
   用户安装后，Claude Code 可以搜索 DNA 市场、通过 OKX x402 支付、下载 DNA 包、展示安装预览、安装到当前项目并验证生效。

2. **Trading Master DNA 官方能力包**  
   用户说“我要一个交易大师”后，可以购买并安装该包。安装后，Claude Code 获得交易分析、资金管理、策略交易流程、风险控制、下单流程和复盘能力。

重要边界：

> Trading Master DNA 的一期目标不是让用户盈利，而是让用户的 Claude Code 获得可工作的交易 agent 能力。

---

## 2. 产品一句话

**DNAcloud 让用户用一句自然语言，把 Claude Code 初始化成某类专家 agent；一期官方包是 Trading Master DNA，目标是让用户获得交易相关 Skills、Agents、MCP、Hooks、规则和命令。**

---

## 3. 用户故事

用户已经安装 Claude Code，然后安装 DNAcloud Bootstrap：

```bash
dnacloud init
```

用户在 Claude Code 中说：

```text
我要一个交易大师，可以帮助我下单、资金管理、策略交易和风险控制。
```

DNAcloud Skill 触发后：

```text
1. 识别需求为 trading capability initialization
2. 搜索 DNAcloud Marketplace
3. 推荐 Trading Master DNA 官方包
4. 展示价格、能力、权限和安装影响
5. 用户通过 OKX x402 真实支付
6. 下载签名 DNA artifact
7. 展示安装预览
8. 安装到当前 Claude Code 项目
9. 运行 verify
10. 用户开始使用交易能力
```

---

## 4. Trading Master DNA 一期定位

### 4.1 它是什么

Trading Master DNA 是一个 Claude Code 专家能力包，包含：

- 交易分析 Skill；
- 资金管理 Skill；
- 风控检查 Skill；
- 订单预案 Skill；
- 交易复盘 Skill；
- 市场分析 subagent；
- 仓位管理 subagent；
- 风险经理 subagent；
- 执行检查 subagent；
- 交易日志 subagent；
- 市场数据 MCP 配置；
- 账户/仓位 MCP 配置；
- 下单执行 MCP 配置；
- PreToolUse 风控 hook；
- 机器规则；
- 安装验证测试；
- 常用 slash commands。

### 4.2 它不是什么

Trading Master DNA 不承诺：

- 保证盈利；
- 自动生成稳赚策略；
- 替用户承担交易风险；
- 托管用户私钥或交易所 API key；
- 在没有用户授权的情况下真实下单；
- 伪造行情、账户、订单或回测结果。

### 4.3 一期验收目标

Trading Master DNA 安装后，用户应该能做到：

```text
1. 让 Claude Code 按交易工作流分析市场；
2. 让 Claude Code 输出交易计划；
3. 让 Claude Code 计算仓位和风险；
4. 让 Claude Code 检查下单前条件；
5. 让 Claude Code 调用用户配置的真实市场数据 MCP；
6. 让 Claude Code 在用户配置真实下单 MCP 后，可以进入订单预览和授权下单流程；
7. 让 Claude Code 记录交易日志并做复盘。
```

盈亏、胜率、收益率、alpha 表现不作为一期验收指标。

---

## 5. DNAcloud Bootstrap 一期能力

Bootstrap 安装后提供：

```text
.claude/skills/dnacloud/
.claude/agents/dnacloud-installer.md
.claude/agents/dnacloud-market-researcher.md
.claude/commands/dna.md
.claude/commands/dna-install.md
.claude/commands/dna-status.md
.mcp.json 中的 dnacloud marketplace MCP
.dnacloud/config.json
.dnacloud/sources.json
.dnacloud/installed/
```

Bootstrap 的职责：

- 理解用户要安装什么专家能力；
- 搜索 DNAcloud Marketplace；
- 处理 OKX x402 购买；
- 下载签名 DNA artifact；
- 校验 artifact；
- 生成 install preview；
- 写入当前 Claude Code 项目；
- 运行 status / verify / rollback。

---

## 6. OKX x402 支付要求

Phase 1 使用 OKX Onchain OS Payment / x402。

要求：

- 不使用 mock payment；
- 用户购买 Trading Master DNA 时走真实 x402 支付；
- 服务端未收到有效支付凭证时返回 HTTP 402；
- 客户端根据 OKX payment requirements 完成签名支付；
- 服务端调用 OKX verify / settle 后才返回 DNA artifact；
- 缺少 OKX 配置时必须明确失败，不允许假装支付成功。

---

## 7. Trading Master DNA 购买后安装结果

安装后项目结构示例：

```text
project-root/
  CLAUDE.md
  .mcp.json
  .claude/
    skills/
      trading-master/
        SKILL.md
        references/
          trading-workflow.md
          position-sizing.md
          risk-policy.md
          order-preview.md
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
    settings.local.json
  .dnacloud/
    installed/
      trading-master-dna/
        1.0.0/
          manifest.json
          install-plan.json
          signature.txt
          package.sha256
          payment-receipt.json
          install-result.json
```

---

## 8. 用户使用体验

安装后用户可以直接说：

```text
帮我分析今天 BTC 和 ETH 的交易机会。
```

Claude Code 应该调用 Trading Master DNA 的交易分析流程。

用户可以说：

```text
帮我制定一个 1000 USDT 的 ETH 交易计划。
```

Claude Code 应该输出：

```text
市场判断
策略假设
仓位建议
止损条件
止盈条件
风险暴露
不应该交易的条件
需要用户补充的信息
```

用户可以说：

```text
按照刚才的计划生成订单预案。
```

Claude Code 应该进入订单预览流程，而不是直接盲目下单。

如果用户已经配置真实下单 MCP，并确认授权，DNA 可以引导 Claude Code 调用真实下单工具。DNAcloud 基础服务只负责安装和权限透明化，不替用户判断是否应承担交易风险。

---

## 9. 正式包 vs 盈利策略

Trading Master DNA 是正式包，但它的“正式”体现在：

```text
结构完整
安装可靠
能力可触发
MCP 可接入
Hooks 可配置
规则可读取
流程可执行
状态可验证
```

不是体现在：

```text
收益率高
胜率高
回测漂亮
预测准确
保证盈利
```

一期不要花费大量时间优化策略盈利性。策略内容以基础、通用、可解释、可替换为主。

---

## 10. 未来扩展：用户上传 DNA

虽然 Phase 1 主路径是 DNAcloud Marketplace，但架构必须保留用户自主上传路径：

```bash
dnacloud install ./trading-master-custom.zip
```

未来来源：

```text
MarketplaceSource
LocalUploadSource
GitSource
EnterpriseSource
CreatorUploadSource
```

Phase 1 可以只实现 MarketplaceSource，但 validator、installer、verify、rollback 应该对来源无感。

---

## 11. MVP 成功标准

Phase 1 成功标准：

1. 用户安装 DNAcloud Bootstrap；
2. 用户说“我要一个交易大师”；
3. 系统推荐 Trading Master DNA 官方包；
4. 用户通过 OKX x402 完成真实支付；
5. 系统下载签名 DNA 包；
6. 用户看到安装预览；
7. 系统安装 Skills、Agents、Commands、MCP、Hooks、Rules；
8. `dnacloud verify` 显示 active；
9. 用户能在 Claude Code 中触发交易分析、资金管理、风险检查、订单预览、复盘等能力；
10. 若用户配置真实交易 MCP，系统可以进入真实下单授权流程。
