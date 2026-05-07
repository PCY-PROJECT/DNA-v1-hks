# DNAcloud for Claude Code 技术方案 v0.6

更新时间：2026-05-07  
版本主题：Creator Upload & Revenue Settlement  
目标：在 v0.5 的购买、下载、安装闭环上，新增创作者上传 DNA 包、自动校验、上架、平台收款和异步创作者结算能力。

---

## 1. 技术目标

v0.6 要实现三个闭环：

```text
闭环 A：Creator Upload
创作者通过 DNAcloud Skill / CLI 上传 DNA 包 → 自动校验 → 平台签名 → 上架

闭环 B：Buyer Purchase
买家搜索创作者 DNA 包 → OKX x402 支付到平台公共账户 → 下载 → 安装 → verify

闭环 C：Revenue Settlement
购买成功 → 记录账本 → 计算创作者应收 → payout worker 异步打款到创作者收款地址
```

v0.6 不是内容质量优化阶段。不要花大量时间评估交易包是否盈利、法律包是否专业或电商包是否高转化。重点是基础设施真实可用。

---

## 2. 系统架构

```text
Client Side
  ├── DNAcloud Bootstrap Plugin for Claude Code
  ├── DNAcloud Skill
  ├── dnacloud CLI
  ├── Package Builder
  ├── Package Uploader
  ├── Package Installer
  └── Verifier

Server Side
  ├── Creator API
  ├── Package Upload API
  ├── Package Validator
  ├── Package Registry
  ├── Artifact Storage
  ├── Marketplace Search API
  ├── OKX x402 Seller Middleware
  ├── Payment Ledger
  ├── Payout Ledger
  ├── Payout Worker
  └── Signing Service

Treasury Side
  ├── Platform Public Receiving Account
  ├── Settlement Wallet
  └── Creator Payout Transfers
```

---

## 3. Claude Code 集成落点

DNAcloud Bootstrap 仍然安装到 Claude Code 项目或用户环境中：

```text
.claude/
  skills/
    dnacloud/
      SKILL.md
      references/
        upload-workflow.md
        install-policy.md
        payment-policy.md
  commands/
    dna.md
    dna-upload.md
    dna-status.md
    dna-earnings.md
  agents/
    dnacloud-installer.md
    dnacloud-uploader.md
  settings.local.json

.dnacloud/
  config.json
  trust.json
  cache/
  installed/
  creator/
```

v0.6 新增命令：

```bash
dnacloud upload <package.zip>
dnacloud creator login
dnacloud creator packages
dnacloud creator earnings
dnacloud creator payouts
dnacloud validate <package.zip>
```

在 Claude Code 中新增命令：

```text
/dna-upload
/dna-earnings
/dna-packages
```

---

## 4. DNA 包结构规范

v0.6 支持 zip 包。解压后必须满足：

```text
my-dna-package/
  manifest.json
  install-plan.json
  README.md
  package.sha256

  skills/
    <skill-name>/
      SKILL.md
      references/

  agents/
    <agent-name>.md

  commands/
    <command-name>.md

  mcp/
    <server-name>.mcp.json

  hooks/
    hooks.json

  rules/
    permissions.json
    machine-rules.json

  tests/
    conformance-tests.json
```

不是所有目录都必须存在，但至少需要一种 Claude Code 能力组件：

```text
skills/*/SKILL.md
agents/*.md
commands/*.md
mcp/*.json
hooks/hooks.json
```

---

## 5. manifest.json schema

```json
{
  "id": "conservative-trading-assistant",
  "name": "Conservative Trading Assistant",
  "version": "1.0.0",
  "description": "A conservative trading workflow pack for Claude Code.",
  "category": "trading",
  "tags": ["trading", "risk", "paper-trading"],
  "creator": {
    "display_name": "Henry",
    "wallet_address": "0xCreatorAddress"
  },
  "pricing": {
    "amount": "1.00",
    "currency": "USDG",
    "network": "eip155:196"
  },
  "payout": {
    "address": "0xCreatorAddress",
    "network": "eip155:196",
    "currency": "USDG"
  },
  "capabilities": {
    "skills": ["trading-master"],
    "agents": ["risk-manager"],
    "commands": ["trade-plan"],
    "mcp": ["market-data"],
    "hooks": ["pre-tool-use-trade-guard"]
  },
  "risk_level": "medium",
  "created_at": "2026-05-07T00:00:00Z"
}
```

Server 端发布时追加：

```json
{
  "published_at": "2026-05-07T00:00:00Z",
  "package_hash": "sha256:...",
  "validation_report_id": "val_...",
  "platform_signature": "0x...",
  "status": "published"
}
```

---

## 6. 收款地址验证

### 6.1 为什么需要

创作者上传包时提交收款地址。如果不验证，攻击者可以把别人的包或匿名包绑定到任意地址，引起结算争议。v0.6 要求创作者证明自己控制该收款地址。

### 6.2 流程

```text
1. Client 请求 upload nonce
2. Server 返回 nonce
3. Client 构造 challenge
4. Creator wallet 签名 challenge
5. Client 上传 package + signature
6. Server 验证签名地址等于 payout_address
```

Challenge 格式：

```text
dnacloud-upload:<nonce>:<package_hash>:<payout_address>:<timestamp>
```

验证通过后，`payout_address_verified = true`。

---

## 7. Upload API

### 7.1 创建上传会话

```http
POST /api/v1/creator/upload-session
Content-Type: application/json

{
  "package_id": "conservative-trading-assistant",
  "version": "1.0.0",
  "payout_address": "0xCreatorAddress",
  "network": "eip155:196"
}
```

返回：

```json
{
  "upload_session_id": "upl_123",
  "nonce": "random_nonce",
  "challenge": "dnacloud-upload:random_nonce:pending:0xCreatorAddress:2026-05-07T00:00:00Z"
}
```

### 7.2 上传包

```http
POST /api/v1/creator/packages/upload
Content-Type: multipart/form-data

package=@my-dna-package.zip
upload_session_id=upl_123
payout_signature=0xSignature
```

返回：

```json
{
  "package_id": "conservative-trading-assistant",
  "version": "1.0.0",
  "status": "published",
  "validation_result": "passed_with_warnings",
  "validation_report_url": "/api/v1/packages/conservative-trading-assistant/1.0.0/validation-report",
  "marketplace_url": "dnacloud://package/conservative-trading-assistant"
}
```

---

## 8. 自动校验服务

### 8.1 校验步骤

```text
1. zip 安全解压
   - 拒绝 path traversal，例如 ../
   - 拒绝绝对路径
   - 拒绝超大文件
   - 拒绝符号链接

2. manifest 校验
   - id/name/version/category/price/payout 必填
   - version 必须 semver
   - id 只能 lowercase + hyphen
   - payout network/currency 必须在平台支持列表

3. 文件类型 allowlist
   - .md
   - .json
   - .yaml/.yml
   - .txt
   - scripts 可后续版本开放，v0.6 默认拒绝可执行二进制

4. Claude Code 组件校验
   - skills/*/SKILL.md 存在且有可读描述
   - agents/*.md frontmatter 基本可解析
   - commands/*.md 有 description frontmatter
   - mcp/*.json 是合法 JSON
   - hooks/hooks.json 是合法 hook 配置

5. 敏感信息扫描
   - private key
   - seed phrase
   - API key 常见模式
   - JWT/token 常见模式
   - exchange secret key 常见模式

6. 危险行为扫描
   - rm -rf /
   - curl | sh
   - wget | sh
   - chmod 777
   - 读取 ~/.ssh、~/.aws、~/.config、wallet 文件夹
   - 引导用户粘贴私钥/助记词

7. 收款地址验证
   - 地址格式合法
   - 签名恢复地址等于 payout_address

8. 安装影响分析
   - 写入哪些 .claude 路径
   - 修改哪些 settings
   - 是否启用 hooks
   - 是否添加 MCP servers

9. conformance tests 基础校验
   - tests/conformance-tests.json 可解析
   - 至少包含一个 install_success 测试
```

### 8.2 校验输出

```json
{
  "result": "passed_with_warnings",
  "score": 82,
  "errors": [],
  "warnings": [
    {
      "code": "HOOKS_PRESENT",
      "message": "This package installs Claude Code hooks. Buyer confirmation will be required."
    }
  ],
  "capabilities": {
    "skills": 2,
    "agents": 3,
    "commands": 4,
    "mcp": 1,
    "hooks": 1
  }
}
```

---

## 9. Artifact Storage

发布成功后，每个版本不可变：

```text
s3://dnacloud-artifacts/packages/<package_id>/<version>/<package_hash>.zip
```

数据库记录：

```text
package_id
version
package_hash
artifact_url
manifest_json
validation_report_json
platform_signature
status
created_at
published_at
```

同一 `package_id + version` 不允许覆盖。创作者必须发布新 version。

---

## 10. Marketplace Search

v0.6 搜索不需要复杂推荐，只需要可搜索：

```http
GET /api/v1/marketplace/search?q=trading&category=trading
```

返回：

```json
{
  "items": [
    {
      "package_id": "conservative-trading-assistant",
      "name": "Conservative Trading Assistant",
      "version": "1.0.0",
      "creator": "0xCreatorAddress",
      "price": "1.00",
      "currency": "USDG",
      "validation_result": "passed_with_warnings",
      "risk_level": "medium"
    }
  ]
}
```

---

## 11. OKX x402 购买流

DNAcloud 是 Seller，买家是 Buyer，OKX Payment API / OnchainOS x402 是 Facilitator。买家请求付费资源时，服务端返回 402 payment requirements；买家签名付款后重试；服务端验证并返回资源。

### 11.1 下载请求

```http
GET /api/v1/packages/{package_id}/{version}/download
```

未付款时返回：

```http
402 Payment Required
Content-Type: application/json

{
  "accepts": [
    {
      "scheme": "exact",
      "network": "eip155:196",
      "asset": "USDG",
      "amount": "1000000",
      "payTo": "0xPlatformPublicAccount",
      "resource": "/api/v1/packages/conservative-trading-assistant/1.0.0/download"
    }
  ]
}
```

Client 使用 OKX x402 能力签名后重试：

```http
GET /api/v1/packages/{package_id}/{version}/download
X-PAYMENT: <signed-payment-credential>
```

服务端执行：

```text
verify payment
settle payment
record payment receipt
return artifact download URL or artifact bytes
```

### 11.2 收款账户

`payTo` 永远是平台公共账户，而不是创作者收款地址。创作者收款通过 payout worker 处理。

---

## 12. Payment Ledger

购买成功后写入 `payment_receipts`：

```sql
payment_id
buyer_address
package_id
package_version
creator_id
x402_network
x402_currency
gross_amount
platform_receiver_address
okx_verify_receipt
okx_settle_receipt
settlement_tx_hash
status
created_at
```

状态：

```text
created
verified
settled
failed
refunded_reserved
```

v0.6 可以先不做退款，但字段预留。

---

## 13. Revenue Ledger

每笔成功支付生成 revenue entry：

```sql
revenue_id
payment_id
package_id
package_version
creator_id
payout_address
network
currency
gross_amount
platform_fee_amount
creator_amount
status
created_at
```

状态：

```text
pending_payout
payout_processing
paid
payout_failed
held
```

计算：

```text
platform_fee_amount = gross_amount * fee_rate
creator_amount = gross_amount - platform_fee_amount
```

金额使用最小单位存储，不使用浮点数。

---

## 14. Payout Worker

### 14.1 Worker 流程

```text
1. 扫描 pending_payout
2. 按 creator payout_address + currency + network 聚合
3. 检查平台公共账户余额
4. 创建 payout batch
5. 发起链上转账
6. 记录 tx hash
7. 更新 revenue entry 为 paid
8. 失败则标记 payout_failed 并可重试
```

### 14.2 幂等要求

每个 payout batch 有唯一 `payout_batch_id`。

```text
同一 revenue_id 只能被一个 active batch 占用
链上 tx 发送前后都要记录状态
重试不能重复支付同一 revenue_id
```

### 14.3 Payout API

```http
GET /api/v1/creator/payouts
GET /api/v1/creator/earnings
```

返回：

```json
{
  "pending": "9600000",
  "paid": "0",
  "currency": "USDG",
  "network": "eip155:196",
  "payout_address": "0xCreatorAddress",
  "entries": [
    {
      "package_id": "conservative-trading-assistant",
      "gross_amount": "1000000",
      "creator_amount": "800000",
      "status": "pending_payout"
    }
  ]
}
```

---

## 15. 数据库表

```sql
creators
  id
  wallet_address
  display_name
  created_at

creator_payout_addresses
  id
  creator_id
  address
  network
  currency
  verified_signature
  verified_at
  status

packages
  id
  current_version
  creator_id
  name
  category
  status
  created_at

package_versions
  id
  package_id
  version
  package_hash
  manifest_json
  artifact_url
  validation_report_json
  platform_signature
  price_amount
  price_currency
  price_network
  payout_address_id
  status
  published_at

payment_receipts
  id
  buyer_address
  package_id
  package_version_id
  gross_amount
  currency
  network
  platform_receiver_address
  okx_receipt_json
  tx_hash
  status
  created_at

revenue_entries
  id
  payment_receipt_id
  package_id
  package_version_id
  creator_id
  payout_address
  gross_amount
  platform_fee_amount
  creator_amount
  currency
  network
  status
  created_at

payout_batches
  id
  creator_id
  payout_address
  currency
  network
  total_amount
  tx_hash
  status
  created_at
  completed_at
```

---

## 16. 安全策略

### 16.1 上传安全

```text
限制包大小
安全解压
禁止路径穿越
禁止二进制执行文件
扫描敏感信息
扫描危险命令
收款地址签名验证
同版本不可覆盖
平台签名发布
```

### 16.2 安装安全

买家安装前必须看到：

```text
将写入哪些文件
将安装哪些 Skills
将安装哪些 Agents
将添加哪些 MCP servers
将添加哪些 Hooks
自动审核报告
风险警告
```

高风险组件默认需要确认：

```text
hooks
mcp servers
settings.local.json modifications
order-execution 类工具
shell command hooks
```

### 16.3 结算安全

```text
所有资金先进入平台公共账户
creator payout address 必须签名验证
payout worker 幂等
payout 失败可重试
payout 记录 tx hash
金额用最小单位
```

---

## 17. E2E 测试场景

### 17.1 创作者上传

```text
Given: 一个有效 DNA zip 包和创作者收款地址
When: 创作者运行 dnacloud upload
Then: 平台校验通过并发布 package
And: 搜索 API 可以搜到该包
```

### 17.2 买家购买安装

```text
Given: package 已发布
When: 买家搜索并购买
Then: OKX x402 支付成功
And: 返回签名 artifact
And: installer 安装到 Claude Code 项目
And: verify 通过
```

### 17.3 收益结算

```text
Given: 买家支付成功
When: payout worker 运行
Then: revenue entry 从 pending_payout 变为 paid
And: payout batch 有 tx hash
And: 创作者 earnings 显示已支付
```

---

## 18. v0.6 不做

```text
不做复杂人工审核后台
不做大模型内容质量评分
不做复杂推荐排序
不做创作者主页 UI
不做退款和争议处理
不做链上 split payment 合约
不做直接付款给创作者
不做复杂税务系统
```

---

## 19. 实现优先级

```text
P0
- package schema
- upload CLI
- upload API
- validation service
- artifact storage
- package registry
- search API
- OKX x402 download payment
- payment ledger
- revenue ledger

P1
- payout worker
- creator earnings API
- Claude Code /dna-upload command
- install preview for creator packages
- validation report UI/text output

P2
- warning badges
- package suspension
- creator package list
- payout retry dashboard
```

---

## 20. 关键开发原则

```text
不要 mock payment success
不要 mock payout success
可以使用 OKX 支持的开发/测试网络或小额真实支付，但不能假装支付成功
所有支付、下载授权和结算状态都必须来自真实 receipt 或真实链上 tx
没有真实支付凭据时，必须让流程失败并提示配置
```
