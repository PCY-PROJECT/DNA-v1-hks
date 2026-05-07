Search the DNAcloud Marketplace and install DNA capability packages.

Usage: /dna [query]

Examples:
  /dna                        — show installed packages and status
  /dna trading                — search for trading-related DNA packages
  /dna install trading-master-dna  — install a specific package by ID

Steps:
1. If no query: run dnacloud status and show installed packages
2. If query provided: spawn dnacloud-market-researcher agent to search
3. Show results and wait for user to select a package
4. Show install preview (files that will be added/modified)
5. Wait for user confirmation
6. Process OKX x402 payment (real payment, no mock)
7. Download and verify artifact signature
8. Spawn dnacloud-installer agent to install
9. Run dnacloud verify to confirm installation
