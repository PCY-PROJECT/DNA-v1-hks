Check creator earnings and payout status for uploaded DNA packages.

Usage: /dna-earnings [wallet_address]

Steps:
1. Get wallet address from $ARGUMENTS or ask user
2. Run: dnacloud creator earnings <wallet_address>
3. Show earnings summary: total gross, platform fee, pending payout, paid payout
4. Optionally run: dnacloud creator payouts <wallet_address> to see settlement history
5. Optionally run: dnacloud creator packages <wallet_address> to see uploaded packages

Information shown:
- Total gross sales (in USDT)
- Platform fee (20%)
- Pending payout (not yet transferred)
- Paid payout (already transferred on-chain with tx hash)
- Per-package revenue breakdown
- Payout batch history with tx hashes

Note: Payouts are processed asynchronously by the platform payout worker.
If DNACLOUD_TREASURY_KEY is not configured on the server, payouts will remain in pending_payout state.
