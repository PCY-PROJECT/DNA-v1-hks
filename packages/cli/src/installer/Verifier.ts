import fs from 'node:fs';
import path from 'node:path';
import type { DnaVerifyResult, DnaLockFile } from '@dnacloud/schema';
import { DNACLOUD_DIR, LOCK_FILE } from './paths.js';

export class Verifier {
  private readonly projectRoot: string;

  constructor(projectRoot = process.cwd()) {
    this.projectRoot = projectRoot;
  }

  verify(packageId: string): DnaVerifyResult {
    const lockPath = path.join(this.projectRoot, LOCK_FILE);
    if (!fs.existsSync(lockPath)) {
      return this.notInstalled(packageId);
    }

    const lock = JSON.parse(fs.readFileSync(lockPath, 'utf-8')) as DnaLockFile;
    const entry = lock.installed[packageId];
    if (!entry) {
      return this.notInstalled(packageId);
    }

    const installDir = path.join(this.projectRoot, DNACLOUD_DIR, 'installed', packageId, entry.version);
    const claudeDir = path.join(this.projectRoot, '.claude');

    const signatureVerified = this.fileExists(installDir, 'signature.txt') && entry.signatureVerified;
    const paymentReceiptFound = this.fileExists(installDir, 'payment-receipt.json');

    const manifestPath = path.join(installDir, 'manifest.json');
    let manifest: { components?: Record<string, string[]> } = {};
    if (fs.existsSync(manifestPath)) {
      manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    }

    const skillsInstalled = this.checkComponents(manifest.components?.skills ?? [], claudeDir);
    const agentsInstalled = this.checkComponents(manifest.components?.agents ?? [], claudeDir);
    const commandsInstalled = this.checkComponents(manifest.components?.commands ?? [], claudeDir);
    const mcpConfigured = fs.existsSync(path.join(this.projectRoot, '.mcp.json'));
    const hooksConfigured = this.checkHooksConfig();
    const rulesInstalled = this.fileExists(path.join(this.projectRoot, DNACLOUD_DIR, 'installed', packageId, entry.version), 'machine-rules.json');
    const claudePatchApplied = this.checkClaudePatch(packageId);
    const lockFileUpdated = true;
    const rollbackSnapshotExists = fs.existsSync(
      path.join(this.projectRoot, DNACLOUD_DIR, 'snapshots', `${packageId}-${entry.version}`)
    );

    const missingUserConfig = this.detectMissingConfig();
    const liveTradingReady = missingUserConfig.length === 0;

    const allActive = signatureVerified && paymentReceiptFound && skillsInstalled &&
      agentsInstalled && commandsInstalled && mcpConfigured && hooksConfigured;

    return {
      package: packageId,
      version: entry.version,
      status: allActive ? 'active' : 'partial',
      signatureVerified,
      paymentReceiptFound,
      skillsInstalled,
      agentsInstalled,
      commandsInstalled,
      mcpConfigured,
      hooksConfigured,
      rulesInstalled,
      claudePatchApplied,
      lockFileUpdated,
      rollbackSnapshotExists,
      liveTradingReady,
      missingUserConfig,
      capabilitiesAvailable: allActive
        ? ['market_analysis_workflow', 'position_management_workflow', 'risk_check_workflow', 'order_preview_workflow', 'trade_review_workflow']
        : [],
    };
  }

  private notInstalled(packageId: string): DnaVerifyResult {
    return {
      package: packageId,
      version: 'not-installed',
      status: 'not-installed',
      signatureVerified: false,
      paymentReceiptFound: false,
      skillsInstalled: false,
      agentsInstalled: false,
      commandsInstalled: false,
      mcpConfigured: false,
      hooksConfigured: false,
      rulesInstalled: false,
      claudePatchApplied: false,
      lockFileUpdated: false,
      rollbackSnapshotExists: false,
      liveTradingReady: false,
      missingUserConfig: [],
      capabilitiesAvailable: [],
    };
  }

  private fileExists(dir: string, filename: string): boolean {
    return fs.existsSync(path.join(dir, filename));
  }

  private checkComponents(components: string[], baseDir: string): boolean {
    if (components.length === 0) return true;
    return components.every((c) => {
      const relativePath = c.replace(/^\.claude\//, '');
      return fs.existsSync(path.join(baseDir, relativePath));
    });
  }

  private checkHooksConfig(): boolean {
    const settingsPath = path.join(this.projectRoot, '.claude', 'settings.local.json');
    if (!fs.existsSync(settingsPath)) return false;
    const settings = JSON.parse(fs.readFileSync(settingsPath, 'utf-8')) as Record<string, unknown>;
    return !!(settings.hooks && (settings.hooks as Record<string, unknown>).PreToolUse);
  }

  private checkClaudePatch(packageId: string): boolean {
    const claudeMd = path.join(this.projectRoot, 'CLAUDE.md');
    if (!fs.existsSync(claudeMd)) return false;
    const content = fs.readFileSync(claudeMd, 'utf-8');
    return content.includes('Trading Master DNA');
  }

  private detectMissingConfig(): string[] {
    const required = ['TRADING_API_KEY', 'TRADING_API_SECRET', 'DNACLOUD_TRADING_VENUE'];
    return required.filter((key) => !process.env[key]);
  }
}
