# DNAcloud for Claude Code AI Code 工作流 v0.5

版本变化：Trading Master DNA 是一期正式官方包，但 AI Code 不需要花大量时间优化交易盈利能力。开发目标是让用户购买后获得可用的交易能力。

---

## 0. 总原则

1. Trading Master DNA 是正式包，不是随便的 demo。
2. Trading Master DNA 的验收目标是能力可用，不是盈利。
3. 不要写复杂策略优化，不要做收益承诺。
4. 不要使用 mock payment。
5. 不要使用 mock 行情、mock 账户、mock 下单结果。
6. 如果缺少真实 MCP 凭据，必须提示用户配置，不要伪造数据。
7. DNAcloud 基础服务是重点：搜索、支付、下载、安装、验证。
8. Trading Master DNA 内容只做到“完整交易工作流能力包”。

---

## 1. 开发顺序

```text
1. DNA schema / validator
2. DNAcloud Bootstrap plugin
3. dnacloud CLI
4. Marketplace API
5. OKX x402 payment middleware
6. Marketplace client + payment client
7. Trading Master DNA official package
8. Claude project installer
9. verify / status / rollback
10. E2E: 我要一个交易大师
```

---

## 2. Trading Master DNA 开发范围

### 必须写

```text
skills/trading-master/SKILL.md
skills/trading-master/references/trading-workflow.md
skills/trading-master/references/position-sizing.md
skills/trading-master/references/risk-policy.md
skills/trading-master/references/order-preview.md
skills/trading-master/references/trade-review.md

agents/market-analyst.md
agents/portfolio-manager.md
agents/risk-manager.md
agents/execution-reviewer.md
agents/trade-journalist.md

commands/trade-plan.md
commands/risk-check.md
commands/order-preview.md
commands/portfolio-status.md
commands/daily-trade-review.md

mcp/market-data.mcp.json
mcp/account-read.mcp.json
mcp/order-execution.mcp.json

hooks/pre-tool-use-trade-guard.json
rules/machine-rules.json
rules/permissions.json
```

### 不要写

```text
复杂盈利策略
高频交易系统
收益回测美化
保证盈利文案
AI 自动稳赚逻辑
模拟成交结果
伪造行情数据
```

---

## 3. Trading Master DNA 的 SKILL.md 要求

`SKILL.md` 应该让 Claude Code 在以下场景触发：

```text
用户请求交易分析
用户请求资金管理
用户请求制定交易计划
用户请求下单
用户请求风控检查
用户请求复盘
```

技能行为：

```text
1. 识别交易市场、资产、方向、金额、风险偏好；
2. 如果缺少交易场所或账户信息，先补问；
3. 调用市场数据 MCP 获取真实数据；
4. 调用账户 MCP 获取真实仓位；
5. 输出交易计划；
6. 进入订单预览；
7. 调用风险检查；
8. 用户授权后才调用下单 MCP；
9. 下单后记录交易日志；
10. 支持日终复盘。
```

---

## 4. MCP 配置要求

MCP config 只写 server 定义和环境变量引用。

不允许：

```text
写入真实 API key
写入真实私钥
写入默认交易账户
内置 fake provider
```

允许：

```text
引用用户环境变量
安装官方 MCP server 命令
提示用户配置真实 provider
```

---

## 5. PreToolUse Hook 要求

Hook 不负责盈利判断，只负责交易前能力流程接入。

行为：

```text
如果工具是下单/撤单/修改订单/链上 swap/approval：
  - 检查 Trading Master DNA 是否 active
  - 检查用户配置是否完整
  - 检查是否经过 order-preview
  - 检查是否需要用户确认
  - 输出 allow / deny / ask
```

---

## 6. Verify 要求

`dnacloud verify` 对 Trading Master DNA 必须检查：

```text
signature verified
payment receipt found
skills installed
agents installed
commands installed
mcp configured
hooks configured
rules installed
CLAUDE.md patch applied
lock file updated
rollback snapshot exists
```

不要检查：

```text
收益率
胜率
策略准确性
未来价格预测能力
```

---

## 7. E2E 场景

### 场景：我要一个交易大师

步骤：

```text
1. 用户在 Claude Code 里说：我要一个交易大师
2. DNAcloud Skill 触发
3. Search marketplace
4. 返回 Trading Master DNA
5. 用户确认购买
6. OKX x402 支付
7. 下载 signed artifact
8. 安装预览
9. 用户确认安装
10. 写入 Claude Code 项目
11. dnacloud verify
12. 用户使用 /trade-plan
```

验收：

```text
Claude Code 能识别交易请求
Claude Code 能调用 Trading Master workflow
Claude Code 能提示用户配置真实 MCP
Claude Code 能输出订单预案
Claude Code 能进入真实下单授权流程
```

---

## 8. 真实数据原则

如果用户没有配置真实市场数据 MCP：

```text
提示：market data provider not configured
不要生成假的价格
```

如果用户没有配置真实账户 MCP：

```text
提示：account provider not configured
不要生成假的余额
```

如果用户没有配置真实下单 MCP：

```text
提示：order execution provider not configured
不要假装下单
```

如果用户已配置真实 MCP：

```text
按 MCP 返回的真实数据工作
```

---

## 9. OKX x402 开发要求

和 v0.4 相同：

```text
不要 mock payment
缺少 OKX env 就 fail
verify 失败不返回 artifact
settle 失败不返回 artifact
保存真实 receipt
```

---

## 10. Definition of Done

完成时必须能演示：

```text
全新 Claude Code 项目
→ dnacloud init
→ 用户说“我要一个交易大师”
→ OKX x402 真实购买 Trading Master DNA
→ 安装完成
→ verify active
→ /trade-plan 可用
→ /risk-check 可用
→ /order-preview 可用
→ 缺少真实 MCP 时提示配置
→ 配置真实 MCP 后可进入下单授权流程
```
