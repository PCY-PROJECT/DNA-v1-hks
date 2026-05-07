Generate end-of-day trade review and journal summary.

Usage: /daily-trade-review [optional: date YYYY-MM-DD, defaults to today]

Steps:
1. Read trade journal from .dnacloud/trade-journal/YYYY-MM-DD.json
2. Fetch actual PnL from account-read MCP
3. Spawn trade-journalist agent to compile review
4. Output review in standard format including:
   - Trade count and PnL summary (real data only)
   - Per-trade decision quality assessment
   - Risk rule compliance check
   - Improvement notes for next session

All PnL figures must come from account-read MCP. Never fabricate results.
