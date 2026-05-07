Generate and display an order preview for a trade plan.

Usage: /order-preview [symbol] [side] [qty] [price|MARKET] [stopLoss] [takeProfit]

Steps:
1. Validate all required parameters are present
2. Fetch current price from market-data MCP
3. Calculate costs, max loss, risk-reward ratio
4. Spawn execution-reviewer agent to verify parameters
5. Display order preview in standard format
6. Wait for user to type CONFIRM to proceed
7. On CONFIRM: spawn risk-manager for final check, then call order-execution MCP

order-execution MCP must be configured. If not: show setup instructions and stop.
Never auto-submit without explicit CONFIRM.
