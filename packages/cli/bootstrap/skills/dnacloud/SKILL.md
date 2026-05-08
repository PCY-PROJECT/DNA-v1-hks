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
   → 检查是否已安装 OKX OnchainOS Payment Skill（Agentic Wallet）
   → 若未安装，进入【OKX Payment Skill 配置引导】流程（见下方），完成后再继续
   → 若已安装，直接进入步骤 1

1. 理解需求 → 识别用户想要的专家能力类型
2. 搜索市场 → 调用 dnacloud-marketplace MCP 搜索相关 DNA 包
3. 展示推荐 → 展示匹配的 DNA 包、价格、能力、权限影响
4. 用户确认 → 等待用户确认购买
5. OKX x402 支付 → 向 marketplace 请求 artifact，服务端返回 402
   OKX Payment Skill 自动检测 402，Agentic Wallet 签名，重放请求完成支付
6. 下载 artifact → 服务端验证支付后返回签名包
7. 展示安装预览 → 列出将要安装的所有文件和修改
8. 用户确认安装 → 等待最终确认
9. 执行安装 → 调用 dnacloud-installer agent
10. 验证 → 运行 dnacloud verify
11. 完成 → 告知用户新能力已可用
```

## OKX Payment Skill 配置引导

**触发条件**：用户尝试购买 DNA 包时，检测到 OKX Payment Skill 未安装。

**引导流程**：

```
购买 DNA 包需要 OKX OnchainOS Payment Skill 完成链上支付。
Payment Skill 提供 Agentic Wallet，自动处理 HTTP 402 支付请求，无需手动管理私钥。

━━━━━━━━ 安装 OKX Payment Skill ━━━━━━━━

步骤 1：按照官方文档安装 Payment Skill
  → https://web3.okx.com/zh-hans/onchainos/dev-docs/payments/payment-use-buyer
  安装后，Skill 会自动配置 Agentic Wallet（私钥由 TEE 保管，无需导出）

步骤 2：为 Agentic Wallet 充值 USDT
  - 在 OKX 购买 USDT，转入 X Layer 网络
  - 或通过 OKX Wallet 跨链桥转入
  - Trading Master DNA 售价：0.001 USDT

步骤 3：告诉我"配置完成了"，我会重新检测并继续安装。

⚠️ 说明：
  - Agentic Wallet 私钥在 TEE 内生成，不可导出，用户无需管理私钥
  - 支付由 Payment Skill 全自动完成，无需手动操作
  - 参考：https://web3.okx.com/zh-hans/onchainos/dev-docs/payments/overview

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**检测方式**：询问用户"是否已安装 OKX OnchainOS Payment Skill 并配置了 Agentic Wallet？"
- 用户确认已安装 → 通过，继续购买流程
- 用户未安装 → 展示引导，等待完成配置

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
