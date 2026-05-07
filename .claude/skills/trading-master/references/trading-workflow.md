# 交易工作流参考

## 标准交易工作流

```
阶段 1：市场侦察
  → 读取真实行情（market-data MCP）
  → 识别趋势、支撑/阻力、成交量
  → 确定市场状态（趋势/震荡/突破）

阶段 2：账户确认
  → 读取真实仓位（account-read MCP）
  → 确认可用资金
  → 检查当前持仓风险暴露

阶段 3：计划制定
  → 根据市场状态选择策略
  → 计算仓位（见 position-sizing.md）
  → 设定止损、止盈
  → 生成完整交易计划

阶段 4：风控检查
  → 调用 risk-manager subagent
  → 检查单笔风险 ≤ 账户 2%
  → 检查总风险暴露
  → 确认不违反机器规则

阶段 5：订单预览
  → 生成订单预案（见 order-preview.md）
  → 展示给用户确认
  → 等待用户明确授权

阶段 6：执行
  → 用户授权后调用 order-execution MCP
  → 记录下单结果

阶段 7：复盘（日终）
  → 调用 trade-journalist subagent
  → 记录决策过程、结果、改进点
```

## 数据要求

所有阶段必须使用真实 MCP 数据。缺失配置时：

```
market-data MCP 未配置 → 提示用户配置，停止分析
account-read MCP 未配置 → 提示用户配置，不生成假余额
order-execution MCP 未配置 → 只能做计划，不能真实下单
```
