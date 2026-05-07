Run risk control check on a proposed trade or current portfolio.

Usage: /risk-check [optional: symbol qty entryPrice stopLoss]

Steps:
1. If parameters provided: check proposed trade against risk-policy rules
2. If no parameters: check current portfolio total risk exposure
3. Spawn risk-manager agent to execute the check
4. Output ALLOW / DENY with detailed reasoning

Always uses real account data from account-read MCP.
