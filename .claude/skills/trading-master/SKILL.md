---
name: trading-master
description: >
  专业交易工作流能力。当用户请求以下内容时触发：交易分析、市场分析、制定交易计划、
  资金管理、仓位计算、风控检查、下单、订单预览、止损止盈设置、复盘。
  触发词：交易计划、帮我分析、我要下单、资金管理、风控、止损、止盈、复盘、
  trade plan, market analysis, position sizing, risk check, order preview, trade review.
  安装来源：Trading Master DNA v1.0.0 官方包（DNAcloud Marketplace）。
---

# Trading Master 技能

## 重要边界

**Trading Master DNA 不承诺盈利，不生成虚假数据。**

- 如果用户未配置真实市场数据 MCP → 提示 `market data provider not configured`，不生成假价格
- 如果用户未配置真实账户 MCP → 提示 `account provider not configured`，不生成假余额
- 如果用户未配置真实下单 MCP → 提示 `order execution provider not configured`，不假装下单
- 不承诺收益率、胜率、保证盈利

## 触发场景

触发本技能当用户说以下任意内容：

- 帮我分析 [资产] 的交易机会
- 帮我制定交易计划
- 我要买/卖 [资产]
- 帮我计算仓位
- 风控检查
- 生成订单预案
- 交易复盘

## 执行流程

```
1. 识别：市场、资产、方向（多/空）、金额、风险偏好
2. 补问：缺少交易场所或账户信息时先补问
3. 市场数据：调用 market-data MCP 获取真实行情
4. 账户状态：调用 account-read MCP 获取真实仓位和余额
5. 交易计划：输出标准格式的交易计划
6. 仓位计算：根据风险偏好计算建议仓位
7. 风控检查：调用风险经理 subagent 执行检查
8. 订单预览：生成订单预案，等待用户确认
9. 下单授权：用户明确授权后才调用 order-execution MCP
10. 交易日志：记录交易决策和结果
```

## 交易计划输出格式

```
## 交易计划

**资产**: [symbol]
**方向**: [多/空]
**当前价格**: [来自真实 MCP，如未配置则提示]

**市场判断**
[基于真实行情数据的分析，2-3 句话]

**策略假设**
[触发条件和假设前提]

**仓位建议**
- 建议仓位: [金额] / [账户余额百分比]
- 入场价格: [价格区间]

**止损条件**
- 止损价: [价格]
- 最大损失: [金额]

**止盈条件**
- 第一目标: [价格]
- 第二目标: [价格（可选）]

**风险暴露**
- 最大亏损: [金额]
- 风险回报比: [比值]

**不应该交易的条件**
[明确列出会取消该交易的信号]

**需要用户补充的信息**
[如有缺口，列出]
```

## 引用文档

参考以下文档执行详细逻辑：

- `.claude/skills/trading-master/references/trading-workflow.md` — 完整交易工作流
- `.claude/skills/trading-master/references/position-sizing.md` — 仓位计算方法
- `.claude/skills/trading-master/references/risk-policy.md` — 风控策略
- `.claude/skills/trading-master/references/order-preview.md` — 订单预览流程
- `.claude/skills/trading-master/references/trade-review.md` — 复盘流程
