import chalk from 'chalk';
import fs from 'node:fs';
import path from 'node:path';
import { Verifier } from '../installer/Verifier.js';
import { LOCK_FILE } from '../installer/paths.js';
import type { DnaLockFile } from '@dnacloud/schema';

export async function statusCommand(): Promise<void> {
  console.log(chalk.bold('\ndnacloud status\n'));

  const lockPath = path.join(process.cwd(), LOCK_FILE);
  if (!fs.existsSync(lockPath)) {
    console.log(chalk.gray('未找到安装记录。运行 dnacloud init 初始化，或 dnacloud install <packageId> 安装包。\n'));
    return;
  }

  const lock = JSON.parse(fs.readFileSync(lockPath, 'utf-8')) as DnaLockFile;
  const installed = Object.keys(lock.installed);

  if (installed.length === 0) {
    console.log(chalk.gray('未安装任何 DNA 包。运行 dnacloud install <packageId> 安装。\n'));
    return;
  }

  const verifier = new Verifier(process.cwd());

  console.log(`已安装 ${installed.length} 个 DNA 包：\n`);
  for (const packageId of installed) {
    const entry = lock.installed[packageId];
    const result = verifier.verify(packageId);

    const statusBadge = result.status === 'active'
      ? chalk.green('● active')
      : result.status === 'partial'
      ? chalk.yellow('◐ partial')
      : chalk.red('✗ failed');

    const liveTrading = result.liveTradingReady
      ? chalk.green('真实交易: 就绪')
      : chalk.yellow(`真实交易: 未就绪 (${result.missingUserConfig.length} 项配置缺失)`);

    console.log(`  ${statusBadge}  ${chalk.bold(packageId)} v${entry.version}`);
    console.log(`              安装时间: ${entry.installedAt}`);
    console.log(`              ${liveTrading}`);

    if (result.missingUserConfig.length > 0) {
      console.log(`              缺失: ${result.missingUserConfig.join(', ')}`);
    }
    console.log('');
  }

  console.log(chalk.gray('运行 dnacloud verify <packageId> 查看详细验证结果。'));
}
