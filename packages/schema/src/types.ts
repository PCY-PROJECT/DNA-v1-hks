export type DnaSourceType = 'marketplace' | 'local-upload' | 'git' | 'enterprise';
export type DnaPackageType = 'official-capability-pack' | 'community-pack' | 'personal-pack';
export type DnaCapability =
  | 'market_analysis'
  | 'position_management'
  | 'strategy_workflow'
  | 'risk_control'
  | 'order_preview'
  | 'live_order_tool_integration'
  | 'trade_journal'
  | 'post_trade_review';

export interface DnaManifest {
  schemaVersion: 'dnacloud.package.v1';
  id: string;
  name: string;
  version: string;
  domain: string;
  packageType: DnaPackageType;
  objective: string;
  capabilities: DnaCapability[];
  notGuaranteed: string[];
  price: DnaPrice;
  components: DnaComponents;
  signature?: string;
}

export interface DnaPrice {
  amount: string;
  currency: string;
  network: string;
}

export interface DnaComponents {
  skills: string[];
  agents: string[];
  commands: string[];
  mcp: string[];
  hooks: string[];
  rules: string[];
  claude?: string[];
  tests?: string[];
}

export interface DnaInstallPlan {
  packageId: string;
  version: string;
  targetDir: string;
  operations: DnaInstallOperation[];
  rollbackPlan: DnaRollbackStep[];
}

export interface DnaInstallOperation {
  type: 'copy' | 'merge-json' | 'patch-md' | 'write-json';
  source: string;
  destination: string;
  description: string;
}

export interface DnaRollbackStep {
  type: 'delete' | 'restore';
  path: string;
}

export interface DnaVerifyResult {
  package: string;
  version: string;
  status: 'active' | 'partial' | 'failed' | 'not-installed';
  signatureVerified: boolean;
  paymentReceiptFound: boolean;
  skillsInstalled: boolean;
  agentsInstalled: boolean;
  commandsInstalled: boolean;
  mcpConfigured: boolean;
  hooksConfigured: boolean;
  rulesInstalled: boolean;
  claudePatchApplied: boolean;
  lockFileUpdated: boolean;
  rollbackSnapshotExists: boolean;
  liveTradingReady: boolean;
  missingUserConfig: string[];
  capabilitiesAvailable: string[];
}

export interface DnaLockFile {
  version: '1';
  installed: Record<string, DnaInstalledEntry>;
}

export interface DnaInstalledEntry {
  version: string;
  installedAt: string;
  paymentReceiptHash: string;
  signatureVerified: boolean;
  installDir: string;
  snapshotDir: string;
}

export interface DnaSearchResult {
  id: string;
  name: string;
  version: string;
  domain: string;
  description: string;
  price: DnaPrice;
  capabilities: DnaCapability[];
  packageType: DnaPackageType;
}

export interface DnaRef {
  id: string;
  version: string;
  source: DnaSourceType;
}

export interface PaymentContext {
  network: string;
  payer: string;
  receiptCredential: string;
}

export interface DnaArtifact {
  manifest: DnaManifest;
  packageData: Buffer;
  signature: string;
  sha256: string;
  paymentReceipt: PaymentReceipt;
}

export interface PaymentReceipt {
  txHash: string;
  payer: string;
  amount: string;
  currency: string;
  network: string;
  verifiedAt: string;
  settlementRef: string;
}
