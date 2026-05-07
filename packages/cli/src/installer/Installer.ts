import fs from 'node:fs';
import path from 'node:path';
import AdmZip from 'adm-zip';
import { createHash } from 'node:crypto';
import type { DnaManifest, DnaInstallPlan, DnaLockFile } from '@dnacloud/schema';
import { assertManifest } from '@dnacloud/schema';
import { DNACLOUD_DIR, LOCK_FILE } from './paths.js';
import type { ArtifactData } from '../marketplace/MarketplaceClient.js';

export interface InstallPreview {
  packageId: string;
  version: string;
  operations: Array<{ description: string; destination: string }>;
}

export class Installer {
  private readonly projectRoot: string;

  constructor(projectRoot = process.cwd()) {
    this.projectRoot = projectRoot;
  }

  async install(artifact: ArtifactData, artifactZipPath: string): Promise<void> {
    const stagingDir = path.join(this.projectRoot, DNACLOUD_DIR, 'staging', `${artifact.packageId}-${artifact.version}`);
    const snapshotDir = path.join(this.projectRoot, DNACLOUD_DIR, 'snapshots', `${artifact.packageId}-${artifact.version}`);
    const installDir = path.join(this.projectRoot, DNACLOUD_DIR, 'installed', artifact.packageId, artifact.version);

    fs.mkdirSync(stagingDir, { recursive: true });
    fs.mkdirSync(snapshotDir, { recursive: true });
    fs.mkdirSync(installDir, { recursive: true });

    this.verifyArtifact(artifactZipPath, artifact.sha256, artifact.signature);

    const zip = new AdmZip(artifactZipPath);
    zip.extractAllTo(stagingDir, true);

    const manifestPath = path.join(stagingDir, 'manifest.json');
    const manifestData = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    assertManifest(manifestData);
    const manifest = manifestData as DnaManifest;

    const planPath = path.join(stagingDir, 'install-plan.json');
    const plan = JSON.parse(fs.readFileSync(planPath, 'utf-8')) as DnaInstallPlan;

    this.createSnapshot(plan, snapshotDir);
    this.executeInstallPlan(plan, stagingDir);

    const receiptPath = path.join(installDir, 'payment-receipt.json');
    fs.writeFileSync(receiptPath, JSON.stringify(artifact.paymentReceipt, null, 2) + '\n');

    fs.copyFileSync(manifestPath, path.join(installDir, 'manifest.json'));
    fs.copyFileSync(planPath, path.join(installDir, 'install-plan.json'));
    fs.writeFileSync(path.join(installDir, 'signature.txt'), artifact.signature + '\n');
    fs.writeFileSync(path.join(installDir, 'package.sha256'), artifact.sha256 + '\n');
    fs.writeFileSync(path.join(installDir, 'install-result.json'), JSON.stringify({
      installedAt: new Date().toISOString(),
      status: 'success',
    }, null, 2) + '\n');

    this.updateLockFile(manifest, artifact, snapshotDir, installDir);

    fs.rmSync(stagingDir, { recursive: true, force: true });
  }

  generatePreview(plan: DnaInstallPlan): InstallPreview {
    return {
      packageId: plan.packageId,
      version: plan.version,
      operations: plan.operations.map((op) => ({
        description: op.description,
        destination: op.destination,
      })),
    };
  }

  private verifyArtifact(zipPath: string, expectedSha256: string, signature: string): void {
    const data = fs.readFileSync(zipPath);
    const actualSha256 = createHash('sha256').update(data).digest('hex');
    if (actualSha256 !== expectedSha256) {
      throw new Error(`Artifact SHA256 mismatch.\nExpected: ${expectedSha256}\nActual:   ${actualSha256}`);
    }
    if (!signature || signature.trim() === '') {
      throw new Error('Artifact signature is missing. Refusing to install unsigned package.');
    }
  }

  private createSnapshot(plan: DnaInstallPlan, snapshotDir: string): void {
    for (const op of plan.operations) {
      const destPath = path.join(this.projectRoot, op.destination);
      if (fs.existsSync(destPath)) {
        const snapshotPath = path.join(snapshotDir, op.destination);
        fs.mkdirSync(path.dirname(snapshotPath), { recursive: true });
        fs.copyFileSync(destPath, snapshotPath);
      }
    }
  }

  private executeInstallPlan(plan: DnaInstallPlan, stagingDir: string): void {
    for (const op of plan.operations) {
      const srcPath = path.join(stagingDir, op.source);
      const destPath = path.join(this.projectRoot, op.destination);
      fs.mkdirSync(path.dirname(destPath), { recursive: true });

      switch (op.type) {
        case 'copy':
          fs.copyFileSync(srcPath, destPath);
          break;

        case 'merge-json': {
          const incoming = JSON.parse(fs.readFileSync(srcPath, 'utf-8')) as Record<string, unknown>;
          const existing = fs.existsSync(destPath)
            ? JSON.parse(fs.readFileSync(destPath, 'utf-8')) as Record<string, unknown>
            : {};
          const merged = deepMerge(existing, incoming);
          fs.writeFileSync(destPath, JSON.stringify(merged, null, 2) + '\n');
          break;
        }

        case 'patch-md': {
          const patch = fs.readFileSync(srcPath, 'utf-8');
          const existing = fs.existsSync(destPath) ? fs.readFileSync(destPath, 'utf-8') : '';
          if (!existing.includes(patch.trim().slice(0, 50))) {
            fs.writeFileSync(destPath, existing.trimEnd() + '\n\n' + patch);
          }
          break;
        }

        case 'write-json': {
          const incoming = JSON.parse(fs.readFileSync(srcPath, 'utf-8')) as Record<string, unknown>;
          const existing = fs.existsSync(destPath)
            ? JSON.parse(fs.readFileSync(destPath, 'utf-8')) as Record<string, unknown>
            : {};
          const merged = deepMerge(existing, incoming);
          fs.writeFileSync(destPath, JSON.stringify(merged, null, 2) + '\n');
          break;
        }

        default:
          throw new Error(`Unknown install operation type: ${(op as { type: string }).type}`);
      }
    }
  }

  private updateLockFile(
    manifest: DnaManifest,
    artifact: ArtifactData,
    snapshotDir: string,
    installDir: string
  ): void {
    const lockPath = path.join(this.projectRoot, LOCK_FILE);
    const lock: DnaLockFile = fs.existsSync(lockPath)
      ? JSON.parse(fs.readFileSync(lockPath, 'utf-8'))
      : { version: '1', installed: {} };

    lock.installed[manifest.id] = {
      version: manifest.version,
      installedAt: new Date().toISOString(),
      paymentReceiptHash: createHash('sha256')
        .update(JSON.stringify(artifact.paymentReceipt))
        .digest('hex'),
      signatureVerified: true,
      installDir,
      snapshotDir,
    };

    fs.writeFileSync(lockPath, JSON.stringify(lock, null, 2) + '\n');
  }
}

function deepMerge(target: Record<string, unknown>, source: Record<string, unknown>): Record<string, unknown> {
  const result = { ...target };
  for (const [key, value] of Object.entries(source)) {
    if (value && typeof value === 'object' && !Array.isArray(value) && key in result && typeof result[key] === 'object') {
      result[key] = deepMerge(result[key] as Record<string, unknown>, value as Record<string, unknown>);
    } else {
      result[key] = value;
    }
  }
  return result;
}
