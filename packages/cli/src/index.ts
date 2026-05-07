#!/usr/bin/env node
import { program } from 'commander';
import { initCommand } from './commands/init.js';
import { installCommand } from './commands/install.js';
import { verifyCommand } from './commands/verify.js';
import { statusCommand } from './commands/status.js';
import { rollbackCommand } from './commands/rollback.js';
import { uploadCommand, validateLocalPackage } from './commands/upload.js';
import { creatorEarnings, creatorPayouts, creatorPackages } from './commands/creator.js';

program
  .name('dnacloud')
  .description('DNAcloud CLI — install expert DNA capabilities into Claude Code')
  .version('1.0.0');

const DEFAULT_MARKETPLACE_URL = process.env.DNACLOUD_MARKETPLACE_URL ?? 'https://api.dnacloud.okg.com';

program
  .command('init')
  .description('Initialize DNAcloud Bootstrap in the current Claude Code project')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL', DEFAULT_MARKETPLACE_URL)
  .action(initCommand);

program
  .command('install <packageId>')
  .description('Install a DNA package from the marketplace')
  .option('--version <version>', 'Package version', 'latest')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL', 'https://api.dnacloud.okg.com')
  .option('-y, --yes', 'Skip confirmation prompts')
  .action(installCommand);

program
  .command('verify [packageId]')
  .description('Verify installed DNA packages')
  .action(verifyCommand);

program
  .command('status')
  .description('Show status of all installed DNA packages')
  .action(statusCommand);

program
  .command('rollback <packageId>')
  .description('Rollback an installed DNA package')
  .option('--version <version>', 'Version to rollback')
  .action(rollbackCommand);

program
  .command('validate <packagePath>')
  .description('Validate a DNA package zip locally before uploading')
  .action(validateLocalPackage);

program
  .command('upload <packagePath>')
  .description('Upload a DNA package to DNAcloud marketplace')
  .requiredOption('--payout-address <address>', 'Creator payout wallet address (must match package manifest)')
  .option('--price <amount>', 'Package price (overrides manifest)')
  .option('--currency <currency>', 'Payment currency (default: USDG)')
  .option('--category <category>', 'Package category')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL')
  .action(uploadCommand);

const creator = program.command('creator').description('Creator account management');

creator
  .command('earnings <walletAddress>')
  .description('Show creator earnings and revenue entries')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL')
  .action(creatorEarnings);

creator
  .command('payouts <walletAddress>')
  .description('Show payout batches and settlement status')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL')
  .action(creatorPayouts);

creator
  .command('packages <walletAddress>')
  .description('List packages uploaded by this creator')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL')
  .action(creatorPackages);

program.parse();
