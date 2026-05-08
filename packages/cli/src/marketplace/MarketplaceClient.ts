import type { DnaSearchResult, DnaManifest } from '@dnacloud/schema';

export interface MarketplaceClientConfig {
  baseUrl: string;
}

// Standard x402 PaymentRequirement (from X-PAYMENT-REQUIREMENT header)
export interface PaymentRequirement {
  scheme: string;
  network: string;
  maxAmountRequired: string;
  resource: string;
  description: string;
  mimeType: string;
  payTo: string;
  maxTimeoutSeconds: number;
  asset: string;
  extra?: {
    name?: string;
    version?: string;
    [key: string]: unknown;
  };
}

export interface X402Response {
  x402Version: number;
  accepts: PaymentRequirement[];
  error?: string;
}

export class MarketplaceClient {
  constructor(private readonly config: MarketplaceClientConfig) {}

  async search(query: string): Promise<DnaSearchResult[]> {
    const url = `${this.config.baseUrl}/v1/dna/search?q=${encodeURIComponent(query)}`;
    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(`Marketplace search failed: ${res.status} ${res.statusText}`);
    }
    return res.json() as Promise<DnaSearchResult[]>;
  }

  async getManifest(packageId: string): Promise<DnaManifest> {
    const url = `${this.config.baseUrl}/v1/dna/${packageId}`;
    const res = await fetch(url);
    if (res.status === 404) throw new Error(`Package not found: ${packageId}`);
    if (!res.ok) throw new Error(`Failed to fetch package: ${res.status}`);
    return res.json() as Promise<DnaManifest>;
  }

  /**
   * First call — no payment header.
   * Returns the x402 requirement from the server's 402 response.
   */
  async requestArtifact(
    packageId: string,
    version: string
  ): Promise<{ type: 'payment_required'; requirement: PaymentRequirement }
            | { type: 'success'; data: ArtifactData }> {
    const url = `${this.config.baseUrl}/v1/dna/${packageId}/versions/${version}/artifact`;
    const res = await fetch(url);

    if (res.status === 402) {
      const requirementHeader = res.headers.get('X-PAYMENT-REQUIREMENT');
      if (!requirementHeader) {
        throw new Error('Server returned 402 but missing X-PAYMENT-REQUIREMENT header');
      }
      const x402: X402Response = JSON.parse(
        Buffer.from(requirementHeader, 'base64').toString('utf8')
      );
      const requirement = x402.accepts?.[0];
      if (!requirement) throw new Error('No accepted payment scheme in 402 response');
      return { type: 'payment_required', requirement };
    }

    if (!res.ok) {
      const body = await res.json() as { error?: string };
      throw new Error(`Artifact request failed: ${body.error ?? res.statusText}`);
    }
    return { type: 'success', data: await res.json() as ArtifactData };
  }

  /**
   * Retry with signed X-PAYMENT header after signing the EIP-3009 authorization.
   */
  async getArtifactWithPayment(
    packageId: string,
    version: string,
    paymentHeader: string
  ): Promise<ArtifactData> {
    const url = `${this.config.baseUrl}/v1/dna/${packageId}/versions/${version}/artifact`;
    const res = await fetch(url, {
      headers: { 'X-PAYMENT': paymentHeader },
    });

    if (res.status === 402) {
      const body = await res.json() as { error?: string; message?: string };
      throw new Error(`Payment rejected: ${body.message ?? body.error ?? 'unknown'}`);
    }
    if (!res.ok) {
      const body = await res.json() as { error?: string };
      throw new Error(`Artifact download failed: ${body.error ?? res.statusText}`);
    }

    const paymentResponse = res.headers.get('X-PAYMENT-RESPONSE');
    const data = await res.json() as ArtifactData;
    if (paymentResponse) {
      data.paymentResponse = JSON.parse(
        Buffer.from(paymentResponse, 'base64').toString('utf8')
      );
    }
    return data;
  }
}

export interface ArtifactData {
  packageId: string;
  version: string;
  downloadUrl: string;
  signature: string;
  sha256: string;
  paymentReceipt: {
    txHash: string;
    payer: string;
    amount: string;
    currency: string;
    network: string;
    verifiedAt: string;
    settlementRef: string;
  };
  paymentResponse?: unknown;
}
