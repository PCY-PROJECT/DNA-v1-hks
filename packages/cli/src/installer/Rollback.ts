import fs from 'node:fs';
import path from 'node:path';
import type { DnaLockFile, DnaInstallPlan } from '@dnacloud/schema';
import { DNACLOUD_DIR, LOCK_FILE } from './paths.js';

export class Rollback {
  private readonly projectRoot: string;

  constructor(projectRoot = process.cwd()) {
    this.projectRoot = projectRoot;
  }

  rollback(packageId: string): void {
    const lockPath = path.join(this.projectRoot, LOCK_FILE);
    if (!fs.existsSync(lockPath)) {
      throw new Error('No lock file found. Nothing to rollback.');
    }

    const lock = JSON.parse(fs.readFileSync(lockPath, 'utf-8')) as DnaLockFile;
    const entry = lock.installed[packageId];
    if (!entry) {
      throw new Error(`Package ${packageId} is not installed.`);
    }

    const installDir = path.join(this.projectRoot, DNACLOUD_DIR, 'installed', packageId, entry.version);
    const planPath = path.join(installDir, 'install-plan.json');
    if (!fs.existsSync(planPath)) {
      throw new Error(`Install plan not found at ${planPath}. Cannot rollback automatically.`);
    }

    const plan = JSON.parse(fs.readFileSync(planPath, 'utf-8')) as DnaInstallPlan;

    for (const step of plan.rollbackPlan) {
      const targetPath = path.join(this.projectRoot, step.path);
      if (step.type === 'delete' && fs.existsSync(targetPath)) {
        fs.rmSync(targetPath, { recursive: true, force: true });
      } else if (step.type === 'restore') {
        const snapshotPath = path.join(entry.snapshotDir, step.path);
        if (fs.existsSync(snapshotPath)) {
          fs.mkdirSync(path.dirname(targetPath), { recursive: true });
          fs.copyFileSync(snapshotPath, targetPath);
        }
      }
    }

    delete lock.installed[packageId];
    fs.writeFileSync(lockPath, JSON.stringify(lock, null, 2) + '\n');

    fs.rmSync(installDir, { recursive: true, force: true });
  }
}
