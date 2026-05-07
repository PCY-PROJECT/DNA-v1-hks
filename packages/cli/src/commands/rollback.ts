import chalk from 'chalk';
import ora from 'ora';
import readline from 'node:readline';
import { Rollback } from '../installer/Rollback.js';

interface RollbackOptions {
  version?: string;
}

export async function rollbackCommand(packageId: string, options: RollbackOptions): Promise<void> {
  console.log(chalk.bold(`\ndnacloud rollback — ${packageId}\n`));

  console.log(chalk.yellow('⚠️  警告：此操作将从当前项目中移除该 DNA 包的所有文件。'));

  const confirmed = await confirm('确认回滚？(y/N) ');
  if (!confirmed) {
    console.log(chalk.gray('已取消。'));
    process.exit(0);
  }

  const spin = ora(`正在回滚 ${packageId}...`).start();
  const rollback = new Rollback(process.cwd());

  try {
    rollback.rollback(packageId);
    spin.succeed(`${packageId} 已成功回滚`);
    console.log(chalk.green('\n✓ 回滚完成。相关文件已移除，lock.json 已更新。\n'));
  } catch (err) {
    spin.fail(`回滚失败: ${(err as Error).message}`);
    process.exit(1);
  }
}

async function confirm(question: string): Promise<boolean> {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise((resolve) => {
    rl.question(question, (answer) => {
      rl.close();
      resolve(answer.toLowerCase() === 'y' || answer.toLowerCase() === 'yes');
    });
  });
}
