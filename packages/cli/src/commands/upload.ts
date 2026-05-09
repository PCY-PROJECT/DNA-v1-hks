import chalk from 'chalk';
import ora from 'ora';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { createReadStream } from 'node:fs';
import { DNACLOUD_DIR } from '../installer/paths.js';
import type { UploadSession, UploadResult, ValidationReport } from '@dnacloud/schema';

interface UploadOptions {
  payoutAddress: string;
  price?: string;
  currency?: string;
  category?: string;
  marketplaceUrl?: string;
}

export async function uploadCommand(packagePath: string, options: UploadOptions): Promise<void> {
  const spin = ora('准备上传 DNA 包...').start();

  const absPath = path.resolve(packagePath);
  if (!fs.existsSync(absPath)) {
    spin.fail(`包文件不存在: ${absPath}`);
    process.exit(1);
  }

  const cwd = process.cwd();
  const configPath = path.join(cwd, DNACLOUD_DIR, 'config.json');
  if (!fs.existsSync(configPath)) {
    spin.fail(`未初始化 DNAcloud，请先在目标目录运行: dnacloud init\n  当前目录: ${cwd}`);
    process.exit(1);
  }

  const config = JSON.parse(fs.readFileSync(configPath, 'utf-8')) as { marketplaceUrl?: string };
  const baseUrl = options.marketplaceUrl ?? config.marketplaceUrl ?? 'http://localhost:8080';

  // 计算包 hash
  spin.text = '计算包 SHA256...';
  const packageHash = await computeFileSha256(absPath);

  // 请求上传会话
  spin.text = '请求上传会话...';
  let session: UploadSession;
  try {
    const res = await fetch(`${baseUrl}/v1/creator/upload-session`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        payout_address: options.payoutAddress,
        package_hash: packageHash,
      }),
    });
    if (!res.ok) {
      const err = await res.text();
      spin.fail(`创建上传会话失败: ${res.status} ${err}`);
      if (res.status === 405) {
        console.log(chalk.yellow('  提示：URL 路径可能缺少 /api，完整地址示例：https://finderfund.cn/dna/api'));
      }
      process.exit(1);
    }
    session = await res.json() as UploadSession;
  } catch (e) {
    spin.fail(`无法连接到 DNAcloud 服务器: ${e instanceof Error ? e.message : e}`);
    process.exit(1);
  }

  spin.succeed(`上传会话已创建，收款地址: ${options.payoutAddress}`);

  const uploadSpin = ora('上传 DNA 包到 DNAcloud...').start();

  try {
    const formData = new FormData();
    const fileContent = fs.readFileSync(absPath);
    formData.append('package', new Blob([fileContent], { type: 'application/zip' }), path.basename(absPath));
    formData.append('upload_session_id', session.upload_session_id);
    formData.append('payout_signature', 'none');
    if (options.price) formData.append('price', options.price);
    if (options.currency) formData.append('currency', options.currency);
    if (options.category) formData.append('category', options.category);

    const res = await fetch(`${baseUrl}/v1/creator/packages/upload`, {
      method: 'POST',
      body: formData,
    });

    const result = await res.json() as UploadResult;

    if (!res.ok) {
      uploadSpin.fail(`上传失败: ${res.status} — ${(result as any).error ?? JSON.stringify(result)}`);
      printValidationReport(result.validation_report);
      process.exit(1);
    }

    if (result.validation_result === 'failed') {
      uploadSpin.fail(`校验失败: ${result.status}`);
      printValidationReport(result.validation_report);
      process.exit(1);
    }

    uploadSpin.succeed('DNA 包上传成功！');
    console.log('');
    console.log(chalk.green('✓') + ' 包已发布到 DNAcloud');
    console.log(chalk.gray(`  Package ID:        ${result.package_id}`));
    console.log(chalk.gray(`  Status:            ${result.status}`));
    console.log(chalk.gray(`  Validation:        ${result.validation_result}`));
    console.log(chalk.gray(`  Marketplace URL:   ${result.marketplace_url}`));

    if (result.validation_report?.warnings?.length) {
      console.log('');
      console.log(chalk.yellow('⚠️  校验警告：'));
      for (const w of result.validation_report.warnings) {
        console.log(chalk.yellow(`  [${w.code}] ${w.message}`));
      }
    }
  } catch (e) {
    uploadSpin.fail(`上传异常: ${e instanceof Error ? e.message : e}`);
    process.exit(1);
  }
}

export async function validateLocalPackage(packagePath: string): Promise<void> {
  const spin = ora('验证 DNA 包结构...').start();

  const absPath = path.resolve(packagePath);
  if (!fs.existsSync(absPath)) {
    spin.fail(`包文件不存在: ${absPath}`);
    process.exit(1);
  }

  try {
    // 本地解压并验证
    const { validateExtractedPackage } = await import('@dnacloud/validator');
    const tmpDir = `/tmp/dnacloud-validate-${Date.now()}`;
    fs.mkdirSync(tmpDir, { recursive: true });

    // 解压 zip
    const { execSync } = await import('node:child_process');
    execSync(`unzip -q "${absPath}" -d "${tmpDir}"`, { stdio: 'pipe' });

    const report = validateExtractedPackage(tmpDir, { requirePayout: true });

    // 清理
    fs.rmSync(tmpDir, { recursive: true, force: true });

    if (report.result === 'failed') {
      spin.fail(`校验失败 (score: ${report.score})`);
      const hasMissingManifest = report.errors.some(e => e.code === 'MISSING_MANIFEST');
      if (hasMissingManifest) {
        console.log('');
        console.log(chalk.yellow('提示：manifest.json 必须位于 zip 根目录，请使用以下方式打包：'));
        console.log(chalk.cyan('  cd <package-dir> && zip -r ../<package>.zip .'));
        console.log(chalk.red('  ✗ 错误方式：zip -r <package>.zip <package-dir>/'));
      }
    } else if (report.result === 'passed_with_warnings') {
      spin.warn(`校验通过（有警告）(score: ${report.score})`);
    } else {
      spin.succeed(`校验通过 (score: ${report.score})`);
    }

    printValidationReport(report);
  } catch (e) {
    spin.fail(`校验异常: ${e instanceof Error ? e.message : e}`);
    process.exit(1);
  }
}

function printValidationReport(report?: ValidationReport): void {
  if (!report) return;

  if (report.errors.length > 0) {
    console.log('');
    console.log(chalk.red('✗ 错误：'));
    for (const err of report.errors) {
      console.log(chalk.red(`  [${err.code}] ${err.message}${err.file ? ` (${err.file})` : ''}`));
    }
  }

  if (report.warnings.length > 0) {
    console.log('');
    console.log(chalk.yellow('⚠ 警告：'));
    for (const w of report.warnings) {
      console.log(chalk.yellow(`  [${w.code}] ${w.message}${w.file ? ` (${w.file})` : ''}`));
    }
  }

  if (report.capabilities) {
    console.log('');
    console.log(chalk.gray('  Capabilities:'));
    console.log(chalk.gray(`    Skills: ${report.capabilities.skills} | Agents: ${report.capabilities.agents} | Commands: ${report.capabilities.commands} | MCP: ${report.capabilities.mcp} | Hooks: ${report.capabilities.hooks}`));
  }
}

async function computeFileSha256(filePath: string): Promise<string> {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = createReadStream(filePath);
    stream.on('data', chunk => hash.update(chunk));
    stream.on('end', () => resolve(hash.digest('hex')));
    stream.on('error', reject);
  });
}

