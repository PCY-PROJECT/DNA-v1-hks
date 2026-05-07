---
name: dnacloud
description: >
  DNAcloud DNA 包搜索、购买和安装能力。
  当用户表达想要某类专家能力、想安装某个 DNA 包时触发。
  触发词：我要一个[专家类型]、安装DNA、DNA market、dnacloud、
  我想要交易能力、给我安装、search DNA、buy DNA、install DNA,
  想要某种专家能力, 给 Claude 安装新能力, Claude 能不能帮我交易
---

# DNAcloud Skill

## 触发场景

- 用户说"我要一个交易大师"
- 用户说"帮我搜索可以交易的 DNA"
- 用户说"安装 Trading Master DNA"
- 用户说"我想给 Claude Code 安装新能力"
- 用户直接说出某类需求（如"我需要交易能力"）

## 执行流程

```
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
