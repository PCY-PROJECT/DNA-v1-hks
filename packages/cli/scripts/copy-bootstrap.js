#!/usr/bin/env node
// 把 dna-packages/bootstrap/.claude 打包进 CLI npm 包的 bootstrap/ 目录
// 这样 npm install -g @dnacloud/cli 后，init 命令可以直接读取内置文件

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const cliRoot = path.resolve(__dirname, '..');
const repoRoot = path.resolve(cliRoot, '../..');

const bootstrapSrc = path.join(repoRoot, 'dna-packages', 'bootstrap', '.claude');
const bootstrapDest = path.join(cliRoot, 'bootstrap');

if (!fs.existsSync(bootstrapSrc)) {
  console.warn(`[copy-bootstrap] source not found: ${bootstrapSrc}`);
  console.warn('[copy-bootstrap] bootstrap/ will be empty — init will skip file installation');
  fs.mkdirSync(bootstrapDest, { recursive: true });
  process.exit(0);
}

copyDir(bootstrapSrc, bootstrapDest);
console.log(`[copy-bootstrap] copied ${bootstrapSrc} → ${bootstrapDest}`);

function copyDir(src, dest) {
  fs.mkdirSync(dest, { recursive: true });
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    const s = path.join(src, entry.name);
    const d = path.join(dest, entry.name);
    if (entry.isDirectory()) copyDir(s, d);
    else fs.copyFileSync(s, d);
  }
}
