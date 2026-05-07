import chalk from 'chalk';
import ora from 'ora';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { MarketplaceClient } from '../marketplace/MarketplaceClient.js';
import { PaymentClient } from '../marketplace/PaymentClient.js';
import { Installer } from '../installer/Installer.js';
import { Verifier } from '../installer/Verifier.js';

interface InstallOptions {
  version: string;
  marketplaceUrl: string;
  yes?: boolean;
}

export async function installCommand(packageId: string, options: InstallOptions): Promise<void> {
  console.log(chalk.bold(`\nDNAcloud — 安装 ${packageId}\n`));

  const marketplaceClient = new MarketplaceClient({ baseUrl: options.marketplaceUrl });
  const spin = ora(`从 marketplace 获取 ${packageId} 信息...`).start();

  let manifest;
  try {
    manifest = await marketplaceClient.getManifest(packageId);
    spin.succeed(`找到: ${manifest.name} v${manifest.version}`);
  } catch (err) {
    spin.fail(`获取包信息失败: ${(err as Error).message}`);
    process.exit(1);
  }

  console.log('\n' + chalk.bold('包信息：'));
  console.log(`  名称:     ${manifest.name}`);
  console.log(`  版本:     ${manifest.version}`);
  console.log(`  类型:     ${manifest.packageType}`);
  console.log(`  目标:     ${manifest.objective}`);
  console.log(`  价格:     ${manifest.price.amount} ${manifest.price.currency} (${manifest.price.network})`);
  console.log(`  能力:     ${manifest.capabilities.join(', ')}`);
  console.log(`  不承诺:   ${manifest.notGuaranteed.join(', ')}`);

  const confirmed = options.yes || await confirm('\n确认购买并安装？(y/N) ');
  if (!confirmed) {
    console.log(chalk.yellow('已取消。'));
    process.exit(0);
  }

  const apiKey = process.env.OKX_API_KEY;
  const secretKey = process.env.OKX_SECRET_KEY;
  const passphrase = process.env.OKX_PASSPHRASE;

  if (!apiKey || !secretKey || !passphrase) {
    console.error(chalk.red('\n❌ OKX x402 支付配置缺失'));
    console.error('请设置以下环境变量（或在 .env 文件中配置）：');
    console.error('  OKX_API_KEY    — OKX API Key');
    console.error('  OKX_SECRET_KEY — OKX Secret Key');
    console.error('  OKX_PASSPHRASE — OKX Passphrase');
    process.exit(1);
  }

  const paymentClient = new PaymentClient({ apiKey, secretKey, passphrase });
  const version = options.version === 'latest' ? manifest.version : options.version;

  spin.start('发起 OKX x402 支付请求...');

  const firstAttempt = await marketplaceClient.getArtifact(packageId, version, '');
  if (firstAttempt.type !== 'payment_required') {
    spin.fail('预期收到 402 挑战，但服务器未返回支付要求。');
    process.exit(1);
  }

  spin.text = '签名 OKX x402 支付...';
  let credential: string;
  try {
    credential = await paymentClient.signAndPay(firstAttempt.challenge);
    spin.succeed('支付凭证已生成');
  } catch (err) {
    spin.fail(`支付失败: ${(err as Error).message}`);
    process.exit(1);
  }

  spin.start('提交支付并下载签名 artifact...');
  const result = await marketplaceClient.getArtifact(packageId, version, credential);
  if (result.type === 'payment_required') {
    spin.fail('支付验证失败，服务器拒绝了支付凭证。');
    process.exit(1);
  }

  spin.succeed('Artifact 下载成功，支付收据已保存');

  const artifactData = result.data;
  const tmpZip = path.join(process.cwd(), '.dnacloud', 'staging', `${packageId}-${version}.zip`);
  fs.mkdirSync(path.dirname(tmpZip), { recursive: true });
  const zipResponse = await fetch(artifactData.downloadUrl);
  const buffer = Buffer.from(await zipResponse.arrayBuffer());
  fs.writeFileSync(tmpZip, buffer);

  const installer = new Installer(process.cwd());

  const planPath = path.join(process.cwd(), '.dnacloud', 'staging', 'install-plan.json');
  if (fs.existsSync(planPath)) {
    const plan = JSON.parse(fs.readFileSync(planPath, 'utf-8'));
    const preview = installer.generatePreview(plan);
    console.log('\n' + chalk.bold('安装预览（将写入以下文件）：'));
    for (const op of preview.operations) {
      console.log(`  ${chalk.green('+')} ${op.destination}  ${chalk.gray(op.description)}`);
    }
  }

  const confirmInstall = options.yes || await confirm('\n确认安装到当前项目？(y/N) ');
  if (!confirmInstall) {
    fs.rmSync(tmpZip, { force: true });
    console.log(chalk.yellow('已取消。Artifact 已清理。'));
    process.exit(0);
  }

  spin.start('安装中...');
  try {
    await installer.install(artifactData, tmpZip);
    spin.succeed('安装完成');
  } catch (err) {
    spin.fail(`安装失败: ${(err as Error).message}`);
    process.exit(1);
  }

  spin.start('验证安装...');
  const verifier = new Verifier(process.cwd());
  const verifyResult = verifier.verify(packageId);

  if (verifyResult.status === 'active') {
    spin.succeed(`验证通过 — 状态: ${chalk.green('active')}`);
  } else {
    spin.warn(`验证结果: ${chalk.yellow(verifyResult.status)}`);
  }

  console.log('\n' + chalk.bold('安装结果：'));
  console.log(`  状态:           ${badge(verifyResult.status)}`);
  console.log(`  Skills:         ${tick(verifyResult.skillsInstalled)}`);
  console.log(`  Agents:         ${tick(verifyResult.agentsInstalled)}`);
  console.log(`  Commands:       ${tick(verifyResult.commandsInstalled)}`);
  console.log(`  MCP 配置:       ${tick(verifyResult.mcpConfigured)}`);
  console.log(`  Hooks 配置:     ${tick(verifyResult.hooksConfigured)}`);
  console.log(`  真实交易就绪:   ${tick(verifyResult.liveTradingReady)}`);

  if (verifyResult.missingUserConfig.length > 0) {
    console.log('\n' + chalk.yellow('⚠️  需要配置以下环境变量才能进行真实交易：'));
    for (const key of verifyResult.missingUserConfig) {
      console.log(`  export ${key}=<your-value>`);
    }
  }

  if (verifyResult.capabilitiesAvailable.length > 0) {
    console.log('\n' + chalk.green('✓') + ' 现在你可以在 Claude Code 中使用：');
    console.log('  /trade-plan       制定交易计划');
    console.log('  /risk-check       风险检查');
    console.log('  /order-preview    订单预览');
    console.log('  /portfolio-status 查看持仓');
    console.log('  /daily-trade-review 日终复盘\n');
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

function tick(v: boolean): string {
  return v ? chalk.green('✓') : chalk.red('✗');
}

function badge(status: string): string {
  if (status === 'active') return chalk.green(status);
  if (status === 'partial') return chalk.yellow(status);
  return chalk.red(status);
}
