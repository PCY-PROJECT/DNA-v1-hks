# DNAcloud for Claude Code AI Code 工作流 v0.6

版本主题：Creator Upload & Revenue Settlement  
目标：在 v0.5 已完成购买/安装主流程后，新增创作者上传 DNA 包、自动审核、上架、买家购买后平台公共账户收款、异步打款给创作者收款地址。

---

## 0. 总原则

1. 不要改掉 v0.5 的核心购买安装主流程。
2. 本版本重点不是 Trading Master DNA 内容，而是 DNAcloud 基础服务。
3. 创作者上传路径必须从 Claude Code 用户视角可用：`dnacloud init` 后通过 Skill/CLI 上传。
4. 自动审核先做简单校验，但必须真实运行，不要只写提示词。
5. 买家支付必须走 OKX x402 真实支付流程，不要 mock payment。
6. 买家支付进入平台公共账户，不直接付给创作者。
7. 创作者收益通过 revenue ledger 记录，再由 payout worker 异步转给 DNA 包配置的收款地址。
8. Payout 不能 mock success；没有真实转账能力时必须显示 `payout_pending` 或 `payout_failed_config_missing`。
9. 所有金额使用最小单位存储，禁止浮点数。
10. 每个包版本不可覆盖，必须按 package hash 和 version 固定。

---

## 1. 开发顺序总览

```text
1. 扩展 DNA package schema
2. 实现本地 package validator
3. 实现 creator identity / payout address signature
4. 实现 upload API
5. 实现 artifact storage / registry
6. 实现 marketplace search 可见 creator 包
7. 扩展 OKX x402 purchase flow 支持 creator 包
8. 实现 payment ledger / revenue ledger
9. 实现 payout worker
10. 实现 Claude Code /dna-upload 和 CLI upload
11. 实现 creator earnings/payouts 查询
12. E2E：上传包 → 搜索 → 购买 → 安装 → 收入入账 → 异步结算
```

---

## 2. Milestone 1：Schema 扩展

### 目标

支持第三方 DNA 包上架。

### 要做

更新 schema：

```text
manifest.json
install-plan.json
validation-report.json
creator-profile.json
payment-receipt.json
revenue-entry.json
payout-batch.json
```

manifest 新增字段：

```text
creator.wallet_address
pricing.amount
pricing.currency
pricing.network
payout.address
payout.currency
payout.network
capabilities
risk_level
```

### 验收

```text
pnpm test schema
schema 能校验官方 Trading Master DNA
schema 能校验第三方 sample DNA
schema 能拒绝缺少 payout 的上传包
```

---

## 3. Milestone 2：本地 package validator

### 目标

创作者上传前和服务端接收后都能跑同一套基础校验。

### 要做

实现：

```text
packages/validator/src/validatePackage.ts
packages/validator/src/safeUnzip.ts
packages/validator/src/scanSecrets.ts
packages/validator/src/scanDangerousPatterns.ts
packages/validator/src/validateClaudeComponents.ts
```

校验项：

```text
zip path traversal
manifest schema
install-plan schema
file allowlist
secret scan
dangerous pattern scan
Claude Code skill/agent/command/mcp/hook 基础校验
payout address fields
price fields
```

### 验收

```text
dnacloud validate ./fixtures/valid-dna.zip => passed
dnacloud validate ./fixtures/secret-leak-dna.zip => failed
dnacloud validate ./fixtures/path-traversal.zip => failed
dnacloud validate ./fixtures/hooks-warning.zip => passed_with_warnings
```

---

## 4. Milestone 3：Creator identity 和收款地址签名

### 目标

创作者必须证明自己控制收款地址。

### 要做

实现：

```text
POST /api/v1/creator/upload-session
POST /api/v1/creator/verify-payout-address
```

CLI：

```bash
dnacloud creator login --wallet 0x...
dnacloud upload ./my.zip --payout-address 0x...
```

签名 challenge：

```text
dnacloud-upload:<nonce>:<package_hash>:<payout_address>:<timestamp>
```

### 验收

```text
正确地址签名 => verified
错误地址签名 => rejected
过期 nonce => rejected
重复 nonce => rejected
```

---

## 5. Milestone 4：Upload API

### 目标

创作者能上传 zip 包，服务端自动校验并发布。

### 要做

实现：

```text
POST /api/v1/creator/packages/upload
GET /api/v1/creator/packages
GET /api/v1/packages/:id/:version/validation-report
```

服务端流程：

```text
receive multipart zip
compute sha256
safe unzip
validate package
verify payout signature
store artifact
write package_versions
sign package metadata
publish to marketplace index
return status
```

### 验收

```text
上传有效包 => status published
上传失败包 => status rejected + report
同 package_id 同 version 重复上传 => rejected
同 package_id 新 version => accepted
```

---

## 6. Milestone 5：Artifact Storage 和 Registry

### 目标

每个发布版本不可变、可下载、可签名验证。

### 要做

存储路径：

```text
artifacts/packages/<package_id>/<version>/<sha256>.zip
```

数据库：

```text
packages
package_versions
validation_reports
```

平台签名：

```text
sign(package_id + version + package_hash + validation_report_hash)
```

### 验收

```text
下载 artifact 后 sha256 与 package_versions.package_hash 一致
平台签名可验证
已发布版本不能覆盖
```

---

## 7. Milestone 6：Marketplace Search 接入 creator 包

### 目标

买家可以搜索到用户上传的 DNA 包。

### 要做

更新：

```text
GET /api/v1/marketplace/search
GET /api/v1/packages/:id
```

返回字段包括：

```text
package_id
name
version
creator
price
currency
network
validation_result
risk_level
capabilities
```

### 验收

```text
上传后 5 秒内可搜索
搜索 trading 返回官方包和创作者包
详情页包含 validation report summary
```

---

## 8. Milestone 7：OKX x402 purchase flow 支持 creator 包

### 目标

买家购买创作者包，资金仍进入平台公共账户。

### 要做

更新下载 API：

```text
GET /api/v1/packages/:id/:version/download
```

未付款返回 402 payment requirements：

```text
payTo = PLATFORM_PUBLIC_ACCOUNT
amount = package price in minimal units
network/currency = package pricing
resource = package download URL
```

付款后：

```text
verify OKX x402 payment
settle OKX x402 payment
record payment receipt
return artifact
```

### 验收

```text
未付款 => 402
付款凭证无效 => 402/403
付款成功 => 200 + artifact
receipt ledger 有记录
```

---

## 9. Milestone 8：Payment Ledger 和 Revenue Ledger

### 目标

每笔购买都能计算平台收入和创作者应收。

### 要做

表：

```text
payment_receipts
revenue_entries
```

逻辑：

```text
on payment settled:
  create payment_receipt
  get package_version.creator_id
  get payout_address
  platform_fee = gross * fee_rate
  creator_amount = gross - platform_fee
  create revenue_entry(status=pending_payout)
```

### 验收

```text
购买 1.00 USDG
fee_rate 20%
payment_receipt.gross_amount = 1000000
revenue.creator_amount = 800000
revenue.platform_fee_amount = 200000
revenue.status = pending_payout
```

---

## 10. Milestone 9：Payout Worker

### 目标

公共账户异步把资金打给 DNA 包对应创作者收款地址。

### 要做

实现：

```text
workers/payoutWorker.ts
POST /api/v1/admin/payouts/run-once
GET /api/v1/creator/payouts
GET /api/v1/creator/earnings
```

Worker 逻辑：

```text
load pending_payout entries
batch by creator + payout_address + currency + network
lock entries
check treasury balance
create payout_batch
send onchain transfer from platform public account to payout_address
record tx_hash
mark entries paid
```

### 验收

```text
pending_payout entries 被 worker 处理
成功转账后状态变 paid
payout_batch 记录 tx hash
worker 重试不会重复支付
余额不足时状态保持 pending 或 payout_failed_insufficient_balance
```

---

## 11. Milestone 10：Claude Code Skill / Command 上传体验

### 目标

用户不是必须懂 CLI，也可以在 Claude Code 内通过 DNAcloud Skill 上传。

### 要做

更新：

```text
.claude/skills/dnacloud/SKILL.md
.claude/skills/dnacloud/references/upload-workflow.md
.claude/commands/dna-upload.md
.claude/commands/dna-earnings.md
.claude/agents/dnacloud-uploader.md
```

用户说：

```text
我要上传自己的 DNA 包
```

Skill 应该引导：

```text
包路径是什么？
收款地址是什么？
价格是多少？
币种是什么？
分类是什么？
是否开始本地校验？
是否上传？
```

### 验收

```text
Claude Code 内自然语言触发上传流程
能调用 dnacloud validate
能调用 dnacloud upload
返回 marketplace package id
```

---

## 12. Milestone 11：Creator earnings / payouts 查询

### 目标

创作者能看到自己的收入和结算状态。

### 要做

CLI：

```bash
dnacloud creator earnings
dnacloud creator payouts
dnacloud creator packages
```

Claude commands：

```text
/dna-earnings
/dna-packages
```

### 验收

```text
创作者能看到 gross sales
能看到 platform fee
能看到 pending payout
能看到 paid payout
能看到 payout tx hash
```

---

## 13. Milestone 12：E2E 真实流程

### 目标

跑通完整真实应用场景。

### 流程

```text
1. Creator 使用 dnacloud init
2. Creator 准备 valid DNA zip
3. Creator 使用钱包签名 payout challenge
4. Creator 上传 DNA 包
5. Package 自动校验并 published
6. Buyer 搜索该包
7. Buyer 通过 OKX x402 支付给平台公共账户
8. Buyer 下载 artifact
9. Buyer 安装到 Claude Code 项目
10. verify 通过
11. payment ledger 有 settled 记录
12. revenue ledger 有 pending_payout
13. payout worker 转账给 creator payout address
14. creator earnings 显示 paid
```

### 禁止

```text
禁止用 mock payment 标记成功
禁止用 mock payout 标记成功
禁止伪造 tx hash
禁止伪造 receipt
```

如果真实支付环境未配置，测试应该失败并提示：

```text
OKX x402 credentials or payment environment is not configured.
```

---

## 14. 开发文件建议

```text
apps/api/src/routes/creator.ts
apps/api/src/routes/marketplace.ts
apps/api/src/routes/download.ts
apps/api/src/routes/payouts.ts

packages/schema/src/dnaPackage.ts
packages/validator/src/index.ts
packages/payments-okx/src/x402Seller.ts
packages/payments-okx/src/verify.ts
packages/payments-okx/src/settle.ts
packages/ledger/src/paymentLedger.ts
packages/ledger/src/revenueLedger.ts
packages/payout/src/payoutWorker.ts
packages/cli/src/commands/upload.ts
packages/cli/src/commands/creator.ts
packages/claude-bootstrap/skills/dnacloud/SKILL.md
packages/claude-bootstrap/commands/dna-upload.md
```

---

## 15. 验收 Checklist

### Upload

```text
[ ] dnacloud validate 有效包通过
[ ] dnacloud validate 危险包失败
[ ] dnacloud upload 需要 payout address
[ ] payout address 必须签名验证
[ ] upload 成功后 artifact 入库
[ ] package 可搜索
```

### Purchase

```text
[ ] 买家请求 download 未付款返回 402
[ ] OKX x402 付款后返回 artifact
[ ] artifact hash 可验证
[ ] installer 可安装创作者包
[ ] verify 可通过
```

### Settlement

```text
[ ] payment_receipt 记录真实 receipt
[ ] revenue_entry 计算平台费和创作者应收
[ ] payout worker 创建 batch
[ ] 转账成功后记录真实 tx hash
[ ] 创作者可查 earnings
```

---

## 16. 交付边界

v0.6 完成后，我们应该可以对外说：

```text
DNAcloud 已支持创作者上传自己的 Claude Code 能力包。
上传包会自动审核，审核通过后上架。
买家通过 OKX x402 支付购买。
平台公共账户统一收款。
创作者收益会根据账本异步结算到其收款地址。
```

不能说：

```text
所有包都经过人工专家审核
平台保证包内容质量
平台保证交易类包盈利
结算实时到账
平台支持退款争议处理
```
