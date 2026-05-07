import chalk from 'chalk';
import ora from 'ora';
import fs from 'node:fs';
import path from 'node:path';
import { DNACLOUD_DIR } from '../installer/paths.js';
import type { CreatorEarnings } from '@dnacloud/schema';

function getBaseUrl(cwd: string, override?: string): string {
  const configPath = path.join(cwd, DNACLOUD_DIR, 'config.json');
  if (override) return override;
  if (fs.existsSync(configPath)) {
    const config = JSON.parse(fs.readFileSync(configPath, 'utf-8')) as { marketplaceUrl?: string };
    return config.marketplaceUrl ?? 'http://localhost:8080';
  }
  return 'http://localhost:8080';
}

export async function creatorEarnings(walletAddress: string, opts: { marketplaceUrl?: string } = {}): Promise<void> {
  const spin = ora('查询创作者收益...').start();
  const baseUrl = getBaseUrl(process.cwd(), opts.marketplaceUrl);

  try {
    const res = await fetch(`${baseUrl}/v1/creator/earnings?wallet=${walletAddress}`);
    if (!res.ok) {
      spin.fail(`查询失败: ${res.status}`);
      return;
    }
    const data = await res.json() as CreatorEarnings;
    spin.succeed('收益查询成功');

    console.log('');
    console.log(chalk.bold('📊 创作者收益报告'));
    console.log('');
    console.log(chalk.gray(`  Payout Address:   ${data.payout_address}`));
    console.log(chalk.gray(`  Currency:         ${data.currency} (${data.network})`));
    console.log('');
    console.log(`  Total Gross Sales:   ${chalk.white(formatAmount(data.total_gross))} ${data.currency}`);
    console.log(`  Platform Fee:        ${chalk.red('-' + formatAmount(data.platform_fee))} ${data.currency}`);
    console.log(`  Pending Payout:      ${chalk.yellow(formatAmount(data.pending_payout))} ${data.currency}`);
    console.log(`  Paid Payout:         ${chalk.green(formatAmount(data.paid_payout))} ${data.currency}`);

    if (data.entries?.length > 0) {
      console.log('');
      console.log(chalk.bold('  Recent Entries:'));
      for (const entry of data.entries.slice(0, 10)) {
        const statusColor = entry.status === 'paid' ? chalk.green
          : entry.status === 'pending_payout' ? chalk.yellow
          : chalk.red;
        console.log(`    ${statusColor('●')} ${entry.package_id} — ${formatAmount(entry.creator_amount)} ${data.currency} — ${statusColor(entry.status)}`);
      }
    }
  } catch (e) {
    spin.fail(`连接失败: ${e instanceof Error ? e.message : e}`);
  }
}

export async function creatorPayouts(walletAddress: string, opts: { marketplaceUrl?: string } = {}): Promise<void> {
  const spin = ora('查询结算记录...').start();
  const baseUrl = getBaseUrl(process.cwd(), opts.marketplaceUrl);

  try {
    const res = await fetch(`${baseUrl}/v1/creator/payouts?wallet=${walletAddress}`);
    if (!res.ok) {
      spin.fail(`查询失败: ${res.status}`);
      return;
    }
    const data = await res.json() as { batches: Array<{ id: string; total_amount: string; currency: string; tx_hash?: string; status: string; created_at: string }> };
    spin.succeed('结算记录查询成功');

    console.log('');
    console.log(chalk.bold('💸 结算记录'));
    if (!data.batches?.length) {
      console.log(chalk.gray('  暂无结算记录'));
      return;
    }
    for (const batch of data.batches) {
      const statusColor = batch.status === 'paid' ? chalk.green
        : batch.status === 'payout_processing' ? chalk.blue
        : batch.status === 'payout_failed' ? chalk.red
        : chalk.yellow;
      console.log('');
      console.log(`  ${statusColor('●')} ${batch.id}`);
      console.log(chalk.gray(`    Amount: ${formatAmount(batch.total_amount)} ${batch.currency}`));
      console.log(chalk.gray(`    Status: ${statusColor(batch.status)}`));
      if (batch.tx_hash) console.log(chalk.gray(`    Tx:     ${batch.tx_hash}`));
      console.log(chalk.gray(`    Date:   ${batch.created_at}`));
    }
  } catch (e) {
    spin.fail(`连接失败: ${e instanceof Error ? e.message : e}`);
  }
}

export async function creatorPackages(walletAddress: string, opts: { marketplaceUrl?: string } = {}): Promise<void> {
  const spin = ora('查询已上传包...').start();
  const baseUrl = getBaseUrl(process.cwd(), opts.marketplaceUrl);

  try {
    const res = await fetch(`${baseUrl}/v1/creator/packages?wallet=${walletAddress}`);
    if (!res.ok) {
      spin.fail(`查询失败: ${res.status}`);
      return;
    }
    const data = await res.json() as { packages: Array<{ id: string; name: string; version: string; status: string; validation_result: string; price: string; currency: string }> };
    spin.succeed('已上传包列表');

    console.log('');
    if (!data.packages?.length) {
      console.log(chalk.gray('  暂无上传包'));
      return;
    }
    for (const pkg of data.packages) {
      const statusColor = pkg.status === 'published' ? chalk.green
        : pkg.status === 'rejected' ? chalk.red
        : chalk.yellow;
      console.log(`  ${statusColor('●')} ${pkg.name} v${pkg.version} — ${statusColor(pkg.status)} — ${pkg.price} ${pkg.currency}`);
    }
  } catch (e) {
    spin.fail(`连接失败: ${e instanceof Error ? e.message : e}`);
  }
}

function formatAmount(minimalUnits: string): string {
  if (!minimalUnits) return '0.00';
  const n = BigInt(minimalUnits);
  const whole = n / 1_000_000n;
  const frac = n % 1_000_000n;
  return `${whole}.${frac.toString().padStart(6, '0')}`;
}
