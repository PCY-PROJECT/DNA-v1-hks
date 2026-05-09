import chalk from 'chalk';
import ora from 'ora';
import fs from 'node:fs';
import path from 'node:path';
import { DNACLOUD_DIR, CLAUDE_DIR } from '../installer/paths.js';

interface InitOptions {
  marketplaceUrl: string;
}

export async function initCommand(options: InitOptions): Promise<void> {
  console.log(chalk.bold('\nDNAcloud Bootstrap 初始化\n'));

  const cwd = process.cwd();

  // 如果已有 config.json，且未显式指定 --marketplace-url，则沿用已有配置的 URL
  const existingConfigPath = path.join(cwd, DNACLOUD_DIR, 'config.json');
  if (fs.existsSync(existingConfigPath)) {
    try {
      const existing = JSON.parse(fs.readFileSync(existingConfigPath, 'utf-8')) as { marketplaceUrl?: string };
      if (existing.marketplaceUrl && options.marketplaceUrl === process.env.DNACLOUD_MARKETPLACE_URL) {
        options = { ...options, marketplaceUrl: existing.marketplaceUrl };
      }
    } catch {}
  }

  const spin = ora('检查 Claude Code 项目结构...').start();

  const claudeDir = path.join(cwd, CLAUDE_DIR);
  const dnaDir = path.join(cwd, DNACLOUD_DIR);

  if (!fs.existsSync(claudeDir)) {
    spin.warn('.claude/ 目录不存在，将创建');
    fs.mkdirSync(claudeDir, { recursive: true });
  }

  spin.text = '创建 DNAcloud 目录结构...';
  const dirs = [
    path.join(dnaDir),
    path.join(dnaDir, 'installed'),
    path.join(dnaDir, 'snapshots'),
    path.join(claudeDir, 'skills', 'dnacloud'),
    path.join(claudeDir, 'agents'),
    path.join(claudeDir, 'commands'),
  ];
  for (const d of dirs) {
    fs.mkdirSync(d, { recursive: true });
  }

  spin.text = '注册 dnacloud-marketplace MCP server...';
  const mcpJsonPath = path.join(cwd, '.mcp.json');
  const mcpConfig = fs.existsSync(mcpJsonPath)
    ? JSON.parse(fs.readFileSync(mcpJsonPath, 'utf-8')) as Record<string, unknown>
    : {};
  const servers = (mcpConfig.mcpServers as Record<string, unknown>) ?? {};

  // 优先使用本地构建的 MCP server（本地开发/hackathon 场景）
  const localMcpPath = path.resolve(import.meta.dirname, '../../../mcp-server/dist/index.js');
  const mcpEntry = fs.existsSync(localMcpPath)
    ? { command: 'node', args: [localMcpPath], env: { DNACLOUD_MARKETPLACE_URL: options.marketplaceUrl } }
    : { command: 'npx', args: ['-y', '@dnacloud/mcp-server'], env: { DNACLOUD_MARKETPLACE_URL: options.marketplaceUrl } };

  servers['dnacloud-marketplace'] = mcpEntry;
  mcpConfig.mcpServers = servers;
  fs.writeFileSync(mcpJsonPath, JSON.stringify(mcpConfig, null, 2) + '\n');

  spin.text = '写入 DNAcloud 配置...';
  const config = {
    version: '1',
    marketplaceUrl: options.marketplaceUrl,
    initializedAt: new Date().toISOString(),
  };
  fs.writeFileSync(
    path.join(dnaDir, 'config.json'),
    JSON.stringify(config, null, 2) + '\n'
  );

  const sources = {
    version: '1',
    sources: [
      { id: 'marketplace', type: 'marketplace', url: options.marketplaceUrl, enabled: true },
    ],
  };
  fs.writeFileSync(
    path.join(dnaDir, 'sources.json'),
    JSON.stringify(sources, null, 2) + '\n'
  );

  const lockFile = { version: '1', installed: {} };
  const lockPath = path.join(dnaDir, 'lock.json');
  if (!fs.existsSync(lockPath)) {
    fs.writeFileSync(lockPath, JSON.stringify(lockFile, null, 2) + '\n');
  }

  spin.text = '安装 Bootstrap skill 文件...';
  // 优先顺序：
  // 1. npm 包内置 bootstrap/（生产：npm install -g @dnacloud/cli）
  // 2. 本地 repo 的 dna-packages/bootstrap/.claude（开发模式）
  const npmBuiltinBootstrap = path.resolve(import.meta.dirname, '../../bootstrap');
  const repoBootstrap = path.resolve(import.meta.dirname, '../../../../dna-packages/bootstrap/.claude');
  const bootstrapSrc = fs.existsSync(npmBuiltinBootstrap)
    ? npmBuiltinBootstrap
    : fs.existsSync(repoBootstrap)
    ? repoBootstrap
    : null;

  if (bootstrapSrc) {
    copyDir(bootstrapSrc, claudeDir);
  } else {
    spin.warn('Bootstrap 文件未找到，跳过 skill/command 安装。请确认 CLI 已正确安装。');
  }

  spin.succeed('DNAcloud Bootstrap 初始化完成！');

  console.log('\n' + chalk.green('✓') + ' DNAcloud Bootstrap 已安装到当前项目');
  console.log(chalk.gray('  .dnacloud/config.json     — DNAcloud 配置'));
  console.log(chalk.gray('  .claude/skills/dnacloud/  — DNAcloud skill'));
  console.log(chalk.gray('  .claude/agents/           — DNAcloud installer agent'));
  console.log(chalk.gray('  .claude/commands/         — dna, dna-install, dna-status 命令'));
  console.log('\n' + chalk.bold('现在你可以在 Claude Code 中说：'));
  console.log(chalk.cyan('  "我要一个交易大师"'));
  console.log(chalk.cyan('  "搜索 DNA marketplace"'));
  console.log(chalk.cyan('  "/dna trading"') + '\n');
}

function copyDir(src: string, dest: string): void {
  const entries = fs.readdirSync(src, { withFileTypes: true });
  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);
    if (entry.isDirectory()) {
      fs.mkdirSync(destPath, { recursive: true });
      copyDir(srcPath, destPath);
    } else {
      fs.copyFileSync(srcPath, destPath);
    }
  }
}
