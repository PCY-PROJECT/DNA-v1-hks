Display current portfolio positions and risk exposure.

Usage: /portfolio-status

Steps:
1. Spawn portfolio-manager agent
2. Fetch real positions from account-read MCP
3. Display positions table with unrealized PnL and risk %
4. Show total risk exposure vs limit

If account-read MCP is not configured: show setup instructions.
Never display fabricated balances.
