Guide the user through creating a new DNA package from scratch.

Usage: /dna-create [package-name]

## Step-by-step flow

1. **Ask for basic info** (if not in $ARGUMENTS):
   - Package ID (lowercase, hyphens only, e.g. `my-expert-dna`)
   - Package name (display name)
   - Domain / expertise area
   - Target price (in USDG)
   - Creator payout wallet address (EVM, 0x...)

2. **Scaffold the package directory structure**:

```
<package-id>/
  manifest.json          ← required: package metadata
  install-plan.json      ← required: what gets installed where
  skills/<package-id>/
    SKILL.md             ← skill trigger + execution flow
  agents/                ← optional: specialized sub-agents
  commands/              ← optional: /slash commands
  mcp/                   ← optional: MCP server configs (no real keys)
  hooks/                 ← optional: pre/post tool hooks
  rules/                 ← optional: permissions + machine rules
  claude/
    CLAUDE.patch.md      ← optional: CLAUDE.md additions
```

3. **Generate `manifest.json`** with all required fields:
   - `schemaVersion`: `"dnacloud.package.v1"`
   - `id`, `name`, `version`, `domain`, `packageType`
   - `objective`: what this DNA installs, NOT what it promises to achieve
   - `capabilities[]`: machine-readable capability identifiers
   - `notGuaranteed[]`: be honest about limitations
   - `price`: `{ amount, currency: "USDG", network: "xlayer" }`
   - `payout`: `{ address: <wallet>, currency: "USDG", network: "xlayer" }`
   - `components`: list every file that will be installed

4. **Generate `install-plan.json`** mapping source → destination:
   - skills → `.claude/skills/<id>/`
   - agents → `.claude/agents/`
   - commands → `.claude/commands/`
   - mcp → merged into `.mcp.json`
   - hooks → merged into `.claude/settings.json`

5. **Write a SKILL.md** with:
   - Frontmatter: `name`, `description`, trigger phrases
   - Trigger scenarios (natural language → skill activation)
   - Execution flow (numbered steps)
   - Hard constraints (what this skill must NOT do)

6. **Remind the creator**:
   - Never hardcode API keys or secrets — use `${ENV_VAR}` references
   - `objective` describes what gets installed, not profit guarantees
   - Run `dnacloud validate <package-dir>.zip` before uploading
   - Pack with: `cd <package-id> && zip -r ../<package-id>-<version>.zip . -x "*.DS_Store"`

7. **Next steps after scaffolding**:
   ```
   dnacloud validate <package-id>-1.0.0.zip   # check structure
   dnacloud upload   <package-id>-1.0.0.zip --payout-address <wallet>
   ```

## Hard rules for generated content
- `objective` must start with "install ... capabilities" — never promise ROI or outcomes
- MCP env values must be `"${VAR_NAME}"` — never real credentials
- `notGuaranteed` must be honest and present
