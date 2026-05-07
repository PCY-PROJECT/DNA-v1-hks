# DNAcloud 技术架构说明

## 1. 系统组件

```text
Claude Code Project
  ├── DNAcloud Bootstrap Skill
  ├── DNAcloud CLI
  ├── Installed DNA Packages
  └── Verification Records

DNAcloud Cloud
  ├── Marketplace API
  ├── Package Registry
  ├── Package Validator
  ├── Signing Service
  ├── OKX x402 Payment Integration
  ├── Creator Upload API
  ├── Revenue Ledger
  └── Async Payout Worker
```

## 2. 买家下载流程

```mermaid
sequenceDiagram
  participant U as User
  participant CC as Claude Code + DNAcloud Skill
  participant API as DNAcloud Marketplace API
  participant X as OKX x402 Payment
  participant FS as Local Project

  U->>CC: 我要一个交易大师
  CC->>API: Search DNA packages
  API-->>CC: Trading Master DNA metadata
  U->>CC: Install selected package
  CC->>API: GET /packages/{id}/download
  API-->>CC: 402 Payment Required
  CC->>X: Sign and submit payment credential
  X-->>API: Payment verified
  API-->>CC: Signed DNA package
  CC->>CC: Verify signature and manifest
  CC->>U: Show install preview
  U->>CC: Confirm install
  CC->>FS: Install Skills/Agents/MCP/Hooks/Rules/Tests
  CC->>CC: Run conformance tests
  CC-->>U: DNA package active
```

## 3. 创作者上传流程

```mermaid
sequenceDiagram
  participant C as Creator
  participant CLI as DNAcloud CLI / Skill
  participant API as Creator Upload API
  participant V as Validator
  participant S as Signing Service
  participant R as Registry

  C->>CLI: dnacloud publish ./package --receiver 0x...
  CLI->>API: Upload package and metadata
  API->>V: Run automated review
  V-->>API: Review result
  API->>S: Sign package if valid
  S-->>API: Platform signature
  API->>R: Publish package
  R-->>CLI: Package listed
```

## 4. 支付与结算流程

```mermaid
flowchart TD
  A[Buyer requests paid DNA] --> B[DNAcloud returns 402 payment requirement]
  B --> C[Buyer signs payment via OKX x402]
  C --> D[DNAcloud verifies payment]
  D --> E[DNA package delivered]
  D --> F[Revenue ledger records gross amount]
  F --> G[Platform fee calculated]
  G --> H[Creator receivable created]
  H --> I[Async payout worker]
  I --> J[Transfer to creator receiver address]
  J --> K[Payout record updated]
```

## 5. DNA 包结构

```text
my-dna-package/
  manifest.json
  install-plan.json
  signature.txt
  skills/
  agents/
  commands/
  mcp/
  hooks/
  rules/
  tests/
  memory/
```

## 6. 第一阶段 API

```text
GET  /api/packages/search
GET  /api/packages/:id/manifest
GET  /api/packages/:id/download       # protected by OKX x402
POST /api/creator/packages/upload
POST /api/creator/packages/:id/publish
GET  /api/creator/packages/:id/status
GET  /api/ledger/creator/:address
POST /api/admin/payouts/run           # worker/internal
```

## 7. 本地安装路径

```text
.claude/
  skills/
  agents/
  commands/
  settings.local.json

.mcp.json

.dnacloud/
  config.json
  installed/
    <package-id>/
      manifest.json
      install-record.json
      verify-result.json
      rollback.json
```
