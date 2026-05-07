---
name: dnacloud
description: >
  DNAcloud DNA 包搜索、购买、安装和发布能力。
  当用户表达想要某类专家能力、想安装某个 DNA 包、或想创建/发布 DNA 包时触发。
  触发词：我要一个[专家类型]、安装DNA、DNA market、dnacloud、
  我想要交易能力、给我安装、search DNA、buy DNA、install DNA,
  想要某种专家能力, 给 Claude 安装新能力, Claude 能不能帮我交易,
  我想发布DNA、我想创建DNA包、上传DNA、卖家、creator、我想赚取收益
---

# DNAcloud Skill

## 触发场景（买家）

- 用户说"我要一个交易大师"
- 用户说"帮我搜索可以交易的 DNA"
- 用户说"安装 Trading Master DNA"
- 用户说"我想给 Claude Code 安装新能力"
- 用户直接说出某类需求（如"我需要交易能力"）

## 触发场景（卖家）

- 用户说"我想发布一个 DNA 包"
- 用户说"我想创建一个专家 DNA"
- 用户说"怎么上传 DNA 到 marketplace"
- 用户说"我想查看我的收益"
- 用户说"我是 DNA 创作者"

## 执行流程（买家）

```
0. 支付环境检测（前置，必须通过才能继续）
   → 检查 .env 或环境变量中是否存在 OKX_API_KEY / OKX_SECRET_KEY / OKX_PASSPHRASE
   → 若缺失，进入【OKX x402 配置引导】流程（见下方），完成后再继续
   → 若已配置，直接进入步骤 1

1. 理解需求 → 识别用户想要的专家能力类型
2. 搜索市场 → 调用 dnacloud-marketplace MCP 搜索相关 DNA 包
3. 展示推荐 → 展示匹配的 DNA 包、价格、能力、权限影响
4. 用户确认 → 等待用户确认购买
5. OKX x402 支付 → 触发真实支付流程（不使用 mock）
6. 下载 artifact → 从 marketplace 下载签名包
7. 展示安装预览 → 列出将要安装的所有文件和修改
8. 用户确认安装 → 等待最终确认
9. 执行安装 → 调用 dnacloud-installer agent
10. 验证 → 运行 dnacloud verify
11. 完成 → 告知用户新能力已可用
```

## OKX x402 配置引导

**触发条件**：用户尝试购买 DNA 包时，检测到 OKX 凭证缺失。

**引导流程**：

```
检测到缺少 OKX x402 支付凭证。
购买 DNA 包需要通过 OKX OnchainOS 获取支付凭证。

⚠️ 注意：这不是 OKX 交易所的普通 API Key，
   而是 OKX OnchainOS 开发者门户专用凭证。

━━━━━━━━ 获取 OKX OnchainOS 凭证 ━━━━━━━━

步骤 1：打开 OKX OnchainOS 开发者门户
  → https://web3.okx.com/zh-hans/onchainos/dev-portal
  （不是 okx.com 的账户设置，请注意区别）

步骤 2：连接你的 EVM 钱包（MetaMask 等）
  - 点击"连接钱包"，选择你的 EVM 兼容钱包
  - 签名验证消息，证明钱包地址所有权
  - 无需充值，仅用于身份验证

步骤 3：创建 API Key
  - 在开发者门户创建 API Key
  - 保存三个凭证：API Key、Secret Key、Passphrase

步骤 4：写入项目 .env 文件
  OKX_API_KEY=你的 API Key
  OKX_SECRET_KEY=你的 Secret Key
  OKX_PASSPHRASE=你的 Passphrase

步骤 5：告诉我"配置完成了"，我会重新检测并继续安装。

⚠️ 安全提示：
  - 不要把 .env 文件提交到 Git（已在 .gitignore 中）
  - 不要把凭证内容告诉我，我只检测变量是否存在
  - 这套凭证仅用于 OKX OnchainOS x402 支付，与 OKX 交易账户分开

参考文档：
  https://web3.okx.com/zh-hans/onchainos/dev-docs/payments/overview

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**检测方式**：
```bash
# 检查 .env 文件是否包含必需的三个变量
grep -E "OKX_API_KEY|OKX_SECRET_KEY|OKX_PASSPHRASE" .env 2>/dev/null
```

检测到三个变量均存在（值非空）→ 通过，继续购买流程。
任意一个缺失 → 重新展示引导，等待用户完成配置。

## 执行流程（卖家）

识别到卖家意图时，引导至对应命令：

```
想创建新包    → /dna-create    （脚手架 + manifest 生成）
想上传/发布   → /dna-upload    （validate → 签名 → 上传）
想查看收益    → /dna-earnings  （收益账本 + 待结算金额）
想查看已上传  → /dna-packages  （包列表 + 状态）
```

卖家引导提示：
- 提醒 `objective` 只能描述"安装什么能力"，不能承诺盈利
- 提醒 MCP 配置中不能写入真实 API key，只用 `${ENV_VAR}` 占位
- 上传前必须先 validate（`dnacloud validate <zip>`）

## 展示格式

搜索结果展示：

```
━━━━━━━━ DNAcloud Marketplace ━━━━━━━━

找到 [N] 个匹配的 DNA 包：

📦 [Package Name] v[version]  ⭐ 官方包
   [description]
   价格: [amount] [currency]
   能力: [capability list]
   安装影响: [file list preview]

输入序号确认购买，或输入 0 取消。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## 工具调用

本 Skill 使用以下 MCP 工具：

- `mcp__dnacloud-marketplace__search` — 搜索 DNA 包
- `mcp__dnacloud-marketplace__get_package` — 获取包详情

安装由 `dnacloud-installer` agent 负责执行。

## 硬性约束

- 不使用 mock payment
- 支付未成功不安装
- 安装前必须展示预览
- 不接受未通过签名验证的 artifact
