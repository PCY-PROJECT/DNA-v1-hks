import type { DnaSearchResult, DnaManifest } from '@dnacloud/schema';

export interface MarketplaceClientConfig {
  baseUrl: string;
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

  async getManifest(packageId: string, version = 'latest'): Promise<DnaManifest> {
    const url = `${this.config.baseUrl}/v1/dna/${packageId}`;
    const res = await fetch(url);
    if (res.status === 404) {
      throw new Error(`Package not found: ${packageId}`);
    }
    if (!res.ok) {
      throw new Error(`Failed to fetch package: ${res.status}`);
    }
    return res.json() as Promise<DnaManifest>;
  }

  async getArtifact(
    packageId: string,
    version: string,
    paymentCredential: string
  ): Promise<ArtifactDownloadResult> {
    const url = `${this.config.baseUrl}/v1/dna/${packageId}/versions/${version}/artifact`;
    const res = await fetch(url, {
      headers: { 'X-Payment-Credential': paymentCredential },
    });

    if (res.status === 402) {
      const body = (await res.json()) as { challenge: PaymentChallenge };
      return { type: 'payment_required', challenge: body.challenge };
    }

    if (!res.ok) {
      const body = (await res.json()) as { error: string };
      throw new Error(`Artifact download failed: ${body.error}`);
    }

    const data = (await res.json()) as ArtifactData;
    return { type: 'success', data };
  }
}

export interface PaymentChallenge {
  payTo: string;
  amount: string;
  currency: string;
  network: string;
  resource: string;
  nonce: string;
  expiresAt: number;
  scheme: string;
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
}

export type ArtifactDownloadResult =
  | { type: 'payment_required'; challenge: PaymentChallenge }
  | { type: 'success'; data: ArtifactData };
