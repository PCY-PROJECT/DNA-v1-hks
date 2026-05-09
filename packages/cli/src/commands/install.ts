import chalk from 'chalk';
import ora from 'ora';
import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import { MarketplaceClient } from '../marketplace/MarketplaceClient.js';
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

  const version = options.version === 'latest' ? manifest.version : options.version;

  spin.start('请求 artifact...');
  let artifactData;

  // 从环境中获取 X-PAYMENT（由 OKX Payment Skill 注入）
  const xPayment = process.env.X_PAYMENT_CREDENTIAL ?? '';

  if (!xPayment) {
    const probe = await marketplaceClient.requestArtifact(packageId, version);
    if (probe.type === 'success') {
      spin.succeed('无需支付，artifact 已获取');
      artifactData = probe.data;
    } else {
      spin.stop();
      const req = probe.requirement;
      console.log('\n' + chalk.bold('💳 需要支付：'));
      console.log(`  金额:   ${req.maxAmountRequired} ${req.extra?.name ?? req.asset}`);
      console.log(`  网络:   ${req.network}`);
      console.log(`  收款方: ${req.payTo}`);
      console.log(`\n${chalk.yellow('支付方式 A — OKX OnchainOS CLI（推荐）：')}`);
      console.log(`  onchainos payment x402-pay --accepts '${JSON.stringify(req)}' --key $YOUR_WALLET_KEY`);
      console.log(`  # 拿到 base64 payload 后重新运行：`);
      console.log(chalk.cyan(`  X_PAYMENT_CREDENTIAL=<base64_payload> dnacloud install ${packageId} --yes`));
      console.log(`\n${chalk.yellow('支付方式 B — Claude Code Agent（需已安装 OKX Payment Skill）：')}`);
      console.log(`  在 Claude Code 中说"我要安装 ${packageId}"，Agent 会自动完成支付并注入凭证\n`);
      process.exit(1);
    }
  } else {
    spin.text = 'OKX x402 支付凭证验证中...';
    try {
      artifactData = await marketplaceClient.getArtifactWithPayment(packageId, version, xPayment);
      const txHash = artifactData.paymentReceipt?.txHash;
      if (txHash) {
        spin.succeed(`支付已确认  txHash: ${chalk.green(txHash)}`);
      } else {
        spin.succeed('支付已确认（链上结算延迟，txHash 将在 30-60s 后可查）');
      }
    } catch (err) {
      spin.fail(`支付验证失败: ${(err as Error).message}`);
      process.exit(1);
    }
  }
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
