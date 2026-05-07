import chalk from 'chalk';
import { Verifier } from '../installer/Verifier.js';
import type { DnaVerifyResult } from '@dnacloud/schema';

export async function verifyCommand(packageId?: string): Promise<void> {
  const verifier = new Verifier(process.cwd());

  if (packageId) {
    const result = verifier.verify(packageId);
    printVerifyResult(result);
  } else {
    console.log(chalk.bold('\n运行 dnacloud verify（所有包）\n'));
    console.log(chalk.gray('请使用 dnacloud status 查看所有已安装包，或指定包 ID：'));
    console.log(chalk.gray('  dnacloud verify trading-master-dna\n'));
  }
}

export function printVerifyResult(result: DnaVerifyResult): void {
  console.log(chalk.bold(`\ndnacloud verify — ${result.package}\n`));

  console.log(`  状态:           ${badge(result.status)}`);
  console.log(`  版本:           ${result.version}`);
  console.log(`  签名验证:       ${tick(result.signatureVerified)}`);
  console.log(`  支付收据:       ${tick(result.paymentReceiptFound)}`);
  console.log(`  Skills:         ${tick(result.skillsInstalled)}`);
  console.log(`  Agents:         ${tick(result.agentsInstalled)}`);
  console.log(`  Commands:       ${tick(result.commandsInstalled)}`);
  console.log(`  MCP 配置:       ${tick(result.mcpConfigured)}`);
  console.log(`  Hooks:          ${tick(result.hooksConfigured)}`);
  console.log(`  Rules:          ${tick(result.rulesInstalled)}`);
  console.log(`  CLAUDE.md 补丁: ${tick(result.claudePatchApplied)}`);
  console.log(`  Lock 文件:      ${tick(result.lockFileUpdated)}`);
  console.log(`  回滚快照:       ${tick(result.rollbackSnapshotExists)}`);
  console.log(`  真实交易就绪:   ${tick(result.liveTradingReady)}`);

  if (result.missingUserConfig.length > 0) {
    console.log('\n' + chalk.yellow('缺少用户配置（真实交易需要）：'));
    for (const key of result.missingUserConfig) {
      console.log(`  ${chalk.yellow('!')} ${key}`);
    }
  }

  if (result.capabilitiesAvailable.length > 0) {
    console.log('\n' + chalk.green('可用能力：'));
    for (const cap of result.capabilitiesAvailable) {
      console.log(`  ${chalk.green('✓')} ${cap}`);
    }
  }

  console.log('');
}

function tick(v: boolean): string {
  return v ? chalk.green('✓') : chalk.red('✗');
}

function badge(status: string): string {
  if (status === 'active') return chalk.green(status);
  if (status === 'partial') return chalk.yellow(status);
  if (status === 'not-installed') return chalk.gray(status);
  return chalk.red(status);
}
