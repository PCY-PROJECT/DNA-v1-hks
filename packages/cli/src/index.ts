#!/usr/bin/env node
import { program } from 'commander';
import { initCommand } from './commands/init.js';
import { installCommand } from './commands/install.js';
import { verifyCommand } from './commands/verify.js';
import { statusCommand } from './commands/status.js';
import { rollbackCommand } from './commands/rollback.js';

program
  .name('dnacloud')
  .description('DNAcloud CLI — install expert DNA capabilities into Claude Code')
  .version('1.0.0');

program
  .command('init')
  .description('Initialize DNAcloud Bootstrap in the current Claude Code project')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL', 'https://api.dnacloud.okg.com')
  .action(initCommand);

program
  .command('install <packageId>')
  .description('Install a DNA package from the marketplace')
  .option('--version <version>', 'Package version', 'latest')
  .option('--marketplace-url <url>', 'DNAcloud marketplace URL', 'https://api.dnacloud.okg.com')
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

program.parse();
