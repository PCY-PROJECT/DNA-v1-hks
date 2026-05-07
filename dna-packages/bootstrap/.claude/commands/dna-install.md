Install a DNA package by ID from the DNAcloud Marketplace.

Usage: /dna-install <packageId> [version]

Example: /dna-install trading-master-dna
Example: /dna-install trading-master-dna 1.0.0

Steps:
1. Fetch package manifest from marketplace
2. Show package details: capabilities, price, permissions, files to be installed
3. Ask user to confirm purchase
4. Process OKX x402 payment (requires OKX credentials configured)
5. Download signed artifact from marketplace
6. Verify artifact signature and SHA256
7. Show detailed install preview
8. Ask user to confirm installation
9. Spawn dnacloud-installer agent to execute install
10. Run verification and show results

Fails explicitly if:
- Package not found
- OKX credentials not configured
- Payment verification fails
- Signature check fails
