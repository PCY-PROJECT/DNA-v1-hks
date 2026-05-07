List DNA packages uploaded by a creator and their status.

Usage: /dna-packages [wallet_address]

Steps:
1. Get wallet address from $ARGUMENTS or ask user
2. Run: dnacloud creator packages <wallet_address>
3. Show list of uploaded packages with:
   - Package ID and version
   - Status (published / rejected / suspended)
   - Validation result (passed / passed_with_warnings / failed)
   - Price and currency
4. For any rejected packages, suggest checking validation report

Creator package statuses:
- draft: Not yet uploaded
- uploaded: Awaiting validation
- rejected: Validation failed — package not available for purchase
- published: Available in the marketplace
- suspended: Temporarily hidden by platform
- deprecated: Creator marked as deprecated
