Upload a DNA package to the DNAcloud marketplace.

Usage: /dna-upload [package_path]

This command guides creators through the complete upload flow:
1. Validate the package structure locally
2. Upload the package with payout address
3. Show validation report and marketplace URL

Steps:
1. Ask for the package zip path (or use $ARGUMENTS if provided)
2. Run: dnacloud validate <package_path>
3. If validation fails, show errors and stop
4. Ask for creator payout wallet address (EVM address for receiving earnings)
5. Ask for price (if not in manifest)
6. Run: dnacloud upload <package_path> --payout-address <address>
7. Upload completes automatically — no wallet signing required
8. Show the package URL and status

Requirements:
- Package must be a .zip file with valid manifest.json
- Creator must provide an EVM wallet address for payout
- DNAcloud Bootstrap must be initialized (dnacloud init)

If upload is rejected: show the validation errors clearly and suggest fixes.
If upload passes with warnings: show warnings so creator knows what buyers will see.
