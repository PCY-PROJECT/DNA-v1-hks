import fs from 'node:fs';
import path from 'node:path';
import semver from 'semver';
import type { ValidationReport, ValidationIssue, DnaManifest } from '@dnacloud/schema';
import { scanContent } from './scanSecrets.js';

const ALLOWED_EXTENSIONS = new Set(['.md', '.json', '.yaml', '.yml', '.txt', '.png', '.svg']);

const SUPPORTED_NETWORKS = new Set(['eip155:196', 'xlayer', 'eip155:5000', 'mantle']);
const SUPPORTED_CURRENCIES = new Set(['USDG', 'USDT', 'USDC']);

export interface ValidateOptions {
  requirePayout?: boolean;
}

export function validateExtractedPackage(
  extractDir: string,
  opts: ValidateOptions = {}
): ValidationReport {
  const errors: ValidationIssue[] = [];
  const warnings: ValidationIssue[] = [];

  // 1. manifest.json
  const manifestPath = path.join(extractDir, 'manifest.json');
  if (!fs.existsSync(manifestPath)) {
    errors.push({ code: 'MISSING_MANIFEST', message: 'manifest.json is required' });
    return { result: 'failed', score: 0, errors, warnings, capabilities: { skills: 0, agents: 0, commands: 0, mcp: 0, hooks: 0 } };
  }

  let manifest: DnaManifest;
  try {
    manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8')) as DnaManifest;
  } catch {
    errors.push({ code: 'INVALID_MANIFEST_JSON', message: 'manifest.json is not valid JSON' });
    return { result: 'failed', score: 0, errors, warnings, capabilities: { skills: 0, agents: 0, commands: 0, mcp: 0, hooks: 0 } };
  }

  // Required fields
  if (!manifest.id || !/^[a-z0-9-]+$/.test(manifest.id)) {
    errors.push({ code: 'INVALID_ID', message: 'manifest.id must be lowercase alphanumeric with hyphens', file: 'manifest.json' });
  }
  if (!manifest.name) {
    errors.push({ code: 'MISSING_NAME', message: 'manifest.name is required', file: 'manifest.json' });
  }
  if (!manifest.version || !semver.valid(manifest.version)) {
    errors.push({ code: 'INVALID_VERSION', message: 'manifest.version must be valid semver', file: 'manifest.json' });
  }
  if (!manifest.price?.amount || !manifest.price?.currency || !manifest.price?.network) {
    errors.push({ code: 'MISSING_PRICE', message: 'manifest.price.amount/currency/network are required', file: 'manifest.json' });
  }

  // Payout validation (required for uploads)
  if (opts.requirePayout) {
    if (!manifest.payout?.address) {
      errors.push({ code: 'MISSING_PAYOUT_ADDRESS', message: 'manifest.payout.address is required for uploads', file: 'manifest.json' });
    }
    if (!manifest.payout?.network || !SUPPORTED_NETWORKS.has(manifest.payout.network)) {
      errors.push({ code: 'UNSUPPORTED_PAYOUT_NETWORK', message: `payout.network must be one of: ${[...SUPPORTED_NETWORKS].join(', ')}`, file: 'manifest.json' });
    }
    if (!manifest.payout?.currency || !SUPPORTED_CURRENCIES.has(manifest.payout.currency)) {
      errors.push({ code: 'UNSUPPORTED_PAYOUT_CURRENCY', message: `payout.currency must be one of: ${[...SUPPORTED_CURRENCIES].join(', ')}`, file: 'manifest.json' });
    }
  }

  // 2. install-plan.json
  const installPlanPath = path.join(extractDir, 'install-plan.json');
  if (!fs.existsSync(installPlanPath)) {
    errors.push({ code: 'MISSING_INSTALL_PLAN', message: 'install-plan.json is required' });
  }

  // 3. Capability components — at least one required
  const capabilities = { skills: 0, agents: 0, commands: 0, mcp: 0, hooks: 0 };
  const skillsDir = path.join(extractDir, 'skills');
  if (fs.existsSync(skillsDir)) {
    for (const entry of fs.readdirSync(skillsDir, { withFileTypes: true })) {
      if (entry.isDirectory()) {
        const skillMd = path.join(skillsDir, entry.name, 'SKILL.md');
        if (fs.existsSync(skillMd)) capabilities.skills++;
        else warnings.push({ code: 'MISSING_SKILL_MD', message: `skills/${entry.name}/SKILL.md not found`, file: `skills/${entry.name}` });
      }
    }
  }

  const agentsDir = path.join(extractDir, 'agents');
  if (fs.existsSync(agentsDir)) {
    capabilities.agents = fs.readdirSync(agentsDir).filter(f => f.endsWith('.md')).length;
  }

  const commandsDir = path.join(extractDir, 'commands');
  if (fs.existsSync(commandsDir)) {
    capabilities.commands = fs.readdirSync(commandsDir).filter(f => f.endsWith('.md')).length;
  }

  const mcpDir = path.join(extractDir, 'mcp');
  if (fs.existsSync(mcpDir)) {
    capabilities.mcp = fs.readdirSync(mcpDir).filter(f => f.endsWith('.json')).length;
  }

  const hooksFile = path.join(extractDir, 'hooks', 'hooks.json');
  if (fs.existsSync(hooksFile)) {
    capabilities.hooks = 1;
    warnings.push({ code: 'HOOKS_PRESENT', message: 'This package installs Claude Code hooks. Buyer confirmation will be required.' });
  }

  const totalComponents = capabilities.skills + capabilities.agents + capabilities.commands + capabilities.mcp + capabilities.hooks;
  if (totalComponents === 0) {
    warnings.push({ code: 'LOW_CAPABILITY', message: 'No skills/agents/commands/mcp/hooks found. This package has limited runtime effect.' });
  }

  // 4. File allowlist + secret scan
  scanDirectory(extractDir, extractDir, errors, warnings);

  // 5. Score calculation
  const baseScore = 100;
  const errorPenalty = errors.length * 20;
  const warnPenalty = warnings.length * 5;
  const score = Math.max(0, Math.min(100, baseScore - errorPenalty - warnPenalty));

  const result = errors.length > 0 ? 'failed'
    : warnings.length > 0 ? 'passed_with_warnings'
    : 'passed';

  return { result, score, errors, warnings, capabilities };
}

function scanDirectory(
  dir: string,
  baseDir: string,
  errors: ValidationIssue[],
  warnings: ValidationIssue[],
): void {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    const relPath = path.relative(baseDir, fullPath);

    if (entry.isDirectory()) {
      scanDirectory(fullPath, baseDir, errors, warnings);
    } else {
      const ext = path.extname(entry.name).toLowerCase();
      if (!ALLOWED_EXTENSIONS.has(ext)) {
        errors.push({ code: 'DISALLOWED_FILE_TYPE', message: `File type not allowed: ${relPath}`, file: relPath });
        continue;
      }

      try {
        const content = fs.readFileSync(fullPath, 'utf-8');
        const { secretIssues, dangerIssues } = scanContent(content, relPath);
        errors.push(...secretIssues.map(i => ({ ...i, code: 'SECRET_' + i.code })));
        errors.push(...dangerIssues);
      } catch {
        // binary file or read error — skip content scan
      }
    }
  }
}
