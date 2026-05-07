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
  | 'post_trade_review'
  | string;

export type DnaPackageStatus =
  | 'draft'
  | 'uploaded'
  | 'rejected'
  | 'published'
  | 'suspended'
  | 'deprecated';

export type ValidationResult = 'passed' | 'passed_with_warnings' | 'failed';

export type RevenueStatus =
  | 'pending_payout'
  | 'payout_processing'
  | 'paid'
  | 'payout_failed'
  | 'held';

export interface DnaCreator {
  display_name?: string;
  wallet_address: string;
}

export interface DnaPayout {
  address: string;
  network: string;
  currency: string;
}

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
  payout?: DnaPayout;
  creator?: DnaCreator;
  category?: string;
  tags?: string[];
  risk_level?: 'low' | 'medium' | 'high';
  components: DnaComponents;
  signature?: string;
  platform_signature?: string;
  package_hash?: string;
  published_at?: string;
  status?: DnaPackageStatus;
  validation_report_id?: string;
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
  creator?: DnaCreator;
  validation_result?: ValidationResult;
  risk_level?: string;
  status?: DnaPackageStatus;
}

export interface UploadSession {
  upload_session_id: string;
  nonce: string;
  challenge: string;
  package_id?: string;
  payout_address?: string;
  expires_at: string;
}

export interface UploadResult {
  package_id: string;
  version: string;
  status: DnaPackageStatus;
  validation_result: ValidationResult;
  validation_report?: ValidationReport;
  marketplace_url: string;
}

export interface ValidationReport {
  result: ValidationResult;
  score: number;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
  capabilities: {
    skills: number;
    agents: number;
    commands: number;
    mcp: number;
    hooks: number;
  };
}

export interface ValidationIssue {
  code: string;
  message: string;
  file?: string;
}

export interface RevenueEntry {
  revenue_id: string;
  payment_id: string;
  package_id: string;
  package_version: string;
  creator_id: string;
  payout_address: string;
  network: string;
  currency: string;
  gross_amount: string;
  platform_fee_amount: string;
  creator_amount: string;
  status: RevenueStatus;
  created_at: string;
}

export interface CreatorEarnings {
  total_gross: string;
  platform_fee: string;
  pending_payout: string;
  paid_payout: string;
  currency: string;
  network: string;
  payout_address: string;
  entries: RevenueEntry[];
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
