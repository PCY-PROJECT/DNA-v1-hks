Upload a DNA package to the DNAcloud marketplace.

Usage: /dna-upload [package_path]

This command guides creators through the complete upload flow:
1. Validate the package structure locally
2. Request an upload session from DNAcloud
3. Generate the payout address challenge for signing
4. Upload the package with the wallet signature
5. Show validation report and marketplace URL

Steps:
1. Ask for the package zip path (or use $ARGUMENTS if provided)
2. Run: dnacloud validate <package_path>
3. If validation fails, show errors and stop
4. Ask for creator payout wallet address
5. Ask for price (if not in manifest)
6. Run: dnacloud upload <package_path> --payout-address <address>
7. The CLI will print the challenge string for wallet signing
8. After user provides signature, upload completes
9. Show the package URL and status

Requirements:
- Package must be a .zip file with valid manifest.json
- Creator must have an EVM wallet address for payout
- DNAcloud Bootstrap must be initialized (dnacloud init)

If upload is rejected: show the validation errors clearly and suggest fixes.
If upload passes with warnings: show warnings so creator knows what buyers will see.
