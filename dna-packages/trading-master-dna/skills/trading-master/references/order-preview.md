# 订单预览流程参考

## 订单预览格式

在调用 order-execution MCP 之前，必须展示订单预览并等待用户确认。

```
━━━━━━━━━━ 订单预览 ━━━━━━━━━━

类型:    [市价单 / 限价单]
方向:    [买入 / 卖出]
资产:    [symbol]
数量:    [amount] [unit]
价格:    [price 或 MARKET]
止损:    [stopLoss]
止盈:    [takeProfit]

预计成本:    [cost] USDT
最大损失:    [maxLoss] USDT（账户 [riskPercent]%）
风险回报比:  [ratio]

交易场所:  [venue]（来自 order-execution MCP 配置）

⚠️  Trading Master DNA 不承诺此交易盈利。
⚠️  确认后将调用真实下单接口。

输入 CONFIRM 确认下单，输入其他内容取消。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## 规则

1. 必须展示预览后等待确认，不得自动下单
2. 用户输入非 `CONFIRM` 的任何内容均视为取消
3. 确认超时（5分钟）自动取消
4. 取消后不调用 order-execution MCP
