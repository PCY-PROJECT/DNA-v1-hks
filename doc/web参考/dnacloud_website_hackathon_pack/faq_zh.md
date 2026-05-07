# DNAcloud FAQ

## DNAcloud 是什么？

DNAcloud 是面向 Claude Code 的专家能力包市场和安装器。用户可以用自然语言搜索和购买专家 DNA 包，并把其中的 Skills、Agents、MCP、Hooks、Rules、Commands 和 Tests 安装到当前 Claude Code 项目里。

## DNA 包是什么？

DNA 包是一个可安装的 Claude Code 能力包。它不是 prompt，也不只是 Markdown。它可以包含 Skill、Subagent、MCP 配置、Hook、命令、机器规则、测试和安装计划。

## 为什么不直接卖 prompt？

因为 prompt 只改变对话风格，DNA 包会改变 Claude Code 项目的能力结构。安装后，用户可以获得新的命令、新的 agent、新的 workflow、新的 MCP 配置和新的验证测试。

## 为什么使用 OKX x402？

x402 让客户端请求资源时可以收到 HTTP 402 payment requirement，签名付款后获得资源。它适合 AI Agent 和 CLI 按次购买能力包、数据和 API 服务。

## Trading Master DNA 会赚钱吗？

不承诺盈利。它的目标是提供交易相关能力结构：交易分析、资金管理、策略流程、风险检查、订单预览、MCP 接入和复盘能力。真实交易风险由用户控制。

## 创作者如何赚钱？

创作者上传 DNA 包并绑定收款地址。买家购买后，付款先进入平台公共账户，平台记录账本，并异步结算给该 DNA 包绑定的收款地址。

## 平台如何审核 DNA 包？

第一期采用简单自动审核，包括 manifest 校验、包结构校验、敏感信息扫描、危险命令扫描、收款地址校验和基础安装测试。

## 用户安装后如何确认生效？

DNAcloud 会运行 verify 和 conformance tests，确认 Skills、Agents、MCP、Hooks、Rules 和 Commands 是否安装成功。

## 如果安装出错怎么办？

DNAcloud 会保留安装记录和 rollback 信息。用户可以执行回滚命令恢复安装前状态。
