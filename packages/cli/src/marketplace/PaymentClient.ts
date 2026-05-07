import { createHmac } from 'node:crypto';
import type { PaymentChallenge } from './MarketplaceClient.js';

export interface OkxApiConfig {
  apiKey: string;
  secretKey: string;
  passphrase: string;
}

export class PaymentClient {
  constructor(private readonly config: OkxApiConfig) {}

  async signAndPay(challenge: PaymentChallenge): Promise<string> {
    this.validateConfig();
    this.validateChallenge(challenge);

    const timestamp = new Date().toISOString();
    const method = 'POST';
    const path = challenge.resource;
    const signedBody = JSON.stringify({
      nonce: challenge.nonce,
      amount: challenge.amount,
      currency: challenge.currency,
      network: challenge.network,
      payTo: challenge.payTo,
    });

    const signature = this.buildOkxSignature(timestamp, method, path, signedBody);

    const credential = Buffer.from(JSON.stringify({
      scheme: challenge.scheme,
      apiKey: this.config.apiKey,
      timestamp,
      signature,
      passphrase: this.config.passphrase,
      signedBody,
      nonce: challenge.nonce,
      amount: challenge.amount,
      currency: challenge.currency,
      network: challenge.network,
      payTo: challenge.payTo,
      resource: challenge.resource,
    })).toString('base64');

    return credential;
  }

  private validateConfig(): void {
    if (!this.config.apiKey) {
      throw new Error('OKX API key not configured. Set OKX_API_KEY environment variable.');
    }
    if (!this.config.secretKey) {
      throw new Error('OKX secret key not configured. Set OKX_SECRET_KEY environment variable.');
    }
    if (!this.config.passphrase) {
      throw new Error('OKX passphrase not configured. Set OKX_PASSPHRASE environment variable.');
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

  private buildOkxSignature(timestamp: string, method: string, path: string, body: string): string {
    const message = timestamp + method + path + body;
    return createHmac('sha256', this.config.secretKey)
      .update(message)
      .digest('base64');
  }
}
