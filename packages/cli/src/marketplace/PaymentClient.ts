/**
 * Payment is handled by the OKX OnchainOS Payment Skill installed in Claude Code.
 *
 * When the server returns HTTP 402 + X-PAYMENT-REQUIREMENT, the OKX Payment Skill
 * (running inside the user's Claude Code environment) automatically:
 *   1. Detects the 402 response
 *   2. Signs EIP-3009 TransferWithAuthorization via the user's Agentic Wallet (TEE)
 *   3. Retries the request with X-PAYMENT header
 *
 * This file is kept for CLI standalone use only (dnacloud install outside Claude Code).
 * In that case the user must provide BUYER_WALLET_PRIVATE_KEY and use an EIP-3009
 * compatible wallet — but the primary flow is via OKX Payment Skill.
 */

export function isPaymentSkillFlow(): boolean {
  // When running inside Claude Code with OKX Payment Skill, payment is automatic.
  // CLI standalone mode requires wallet config.
  return process.env.OKX_PAYMENT_SKILL === 'true';
}

export function requiresWalletConfig(): boolean {
  return !process.env.BUYER_WALLET_PRIVATE_KEY && !isPaymentSkillFlow();
}

export function getWalletConfigError(): string {
  return [
    '❌ 支付方式未配置',
    '',
    '在 Claude Code 中使用（推荐）：',
    '  安装 OKX OnchainOS Payment Skill，Skill 会自动处理支付',
    '  参考：https://web3.okx.com/zh-hans/onchainos/dev-docs/payments/payment-use-buyer',
    '',
    '在 CLI 中独立使用：',
    '  BUYER_WALLET_PRIVATE_KEY=0x...  （需要 XLayer 上的 USDT 余额）',
  ].join('\n');
}
