Generate a complete trading plan for the given asset and parameters.

Usage: /trade-plan [symbol] [direction] [amount] [risk%]

Example: /trade-plan BTC long 1000USDT 1%

Steps:
1. Spawn market-analyst agent to fetch real market data
2. Spawn portfolio-manager agent to check current positions
3. Calculate position size using position-sizing reference
4. Generate full trading plan in standard format
5. Spawn risk-manager agent to validate the plan
6. If ALLOW: show order preview and wait for user confirmation
7. If DENY: show denial reason and stop

If market-data MCP is not configured: show configuration instructions and stop.
If account-read MCP is not configured: show configuration instructions, proceed with plan only (no real execution).
