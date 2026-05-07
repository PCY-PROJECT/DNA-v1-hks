# 仓位计算参考

## 固定风险模型（默认）

```
单笔最大风险 = 账户净值 × 风险比例（默认 1%，最大 2%）

仓位大小 = 单笔最大风险 / (入场价 - 止损价)

示例：
  账户净值：10,000 USDT
  风险比例：1%
  单笔最大风险：100 USDT
  入场价：100 USDT
  止损价：98 USDT
  仓位大小：100 / (100 - 98) = 50 单位
```

## 输入要求

- `accountBalance`：来自 account-read MCP 真实余额，不得估算
- `riskPercent`：用户指定，默认 1%，不超过 2%
- `entryPrice`：来自 market-data MCP 真实价格或用户指定
- `stopLoss`：用户指定或技术分析得出

## 输出格式

```json
{
  "positionSize": "50",
  "unit": "token",
  "maxLoss": "100 USDT",
  "riskPercent": "1%",
  "entryPrice": "100",
  "stopLoss": "98",
  "riskRewardRatio": "1:2"
}
```
