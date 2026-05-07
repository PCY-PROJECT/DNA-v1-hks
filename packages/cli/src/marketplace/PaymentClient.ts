import type { PaymentChallenge } from './MarketplaceClient.js';

export interface OkxX402Config {
  walletAddress: string;
  privateKey?: string;
}

export class PaymentClient {
  constructor(private readonly config: OkxX402Config) {}

  async signAndPay(challenge: PaymentChallenge): Promise<string> {
    this.validateConfig();
    this.validateChallenge(challenge);

    const payload = {
      scheme: challenge.scheme,
      payTo: challenge.payTo,
      amount: challenge.amount,
      currency: challenge.currency,
      network: challenge.network,
      resource: challenge.resource,
      nonce: challenge.nonce,
      payer: this.config.walletAddress,
      timestamp: Date.now(),
    };

    const signature = await this.signPayload(payload);

    const credential = Buffer.from(
      JSON.stringify({ ...payload, signature })
    ).toString('base64');

    return credential;
  }

  private validateConfig(): void {
    if (!this.config.walletAddress) {
      throw new Error(
        'OKX wallet address not configured.\n' +
        'Set DNACLOUD_WALLET_ADDRESS environment variable.'
      );
    }
    if (!this.config.privateKey) {
      throw new Error(
        'OKX private key not configured.\n' +
        'Set DNACLOUD_PRIVATE_KEY environment variable.\n' +
        'Never hardcode private keys in code or config files.'
      );
    }
  }

  private validateChallenge(challenge: PaymentChallenge): void {
    const now = Math.floor(Date.now() / 1000);
    if (challenge.expiresAt < now) {
      throw new Error('Payment challenge has expired. Please retry the download.');
    }
    if (challenge.scheme !== 'okx-x402') {
      throw new Error(`Unsupported payment scheme: ${challenge.scheme}`);
    }
  }

  private async signPayload(payload: object): Promise<string> {
    const message = JSON.stringify(payload);
    const encoder = new TextEncoder();
    const data = encoder.encode(message);

    const keyData = Buffer.from(this.config.privateKey!.replace('0x', ''), 'hex');
    const cryptoKey = await crypto.subtle.importKey(
      'raw',
      keyData,
      { name: 'HMAC', hash: 'SHA-256' },
      false,
      ['sign']
    );

    const sig = await crypto.subtle.sign('HMAC', cryptoKey, data);
    return Buffer.from(sig).toString('hex');
  }
}
