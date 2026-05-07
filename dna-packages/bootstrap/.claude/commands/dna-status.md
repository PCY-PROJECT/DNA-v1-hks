Show the status of all installed DNA packages in this project.

Usage: /dna-status [packageId]

With no arguments: show all installed packages summary.
With packageId: show detailed verification result for that package.

Output shows:
- Package ID, version, installed date
- Component status: skills, agents, commands, mcp, hooks, rules
- Signature verification status
- Payment receipt status
- liveTradingReady flag and missing user config
- Available capabilities

Runs dnacloud verify internally to get fresh status.
