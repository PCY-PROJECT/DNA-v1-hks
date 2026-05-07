---
name: dnacloud-installer
description: >
  DNA 包安装 subagent。负责验证签名、解压 artifact、
  执行 install-plan 中的所有操作、更新 lock file、运行 verify。
  只由 dnacloud skill 调用，不直接面向用户。
---

# DNAcloud Installer Agent

## 职责

执行 DNA 包安装的所有文件操作。

## 安装步骤

```
1. 验证 artifact 签名（signature.txt + package.sha256）
2. 解压到临时目录 .dnacloud/staging/{packageId}-{version}/
3. 读取 install-plan.json
4. 创建快照 .dnacloud/snapshots/{packageId}-{version}/
5. 逐条执行 install-plan operations：
   - copy: 直接复制文件
   - merge-json: 合并 JSON（.mcp.json 等）
   - patch-md: 追加到 markdown 文件末尾
   - write-json: 写入 JSON 配置（hooks 等）
6. 写入 .dnacloud/installed/{packageId}/{version}/：
   - manifest.json
   - install-plan.json
   - signature.txt
   - package.sha256
   - payment-receipt.json
   - install-result.json
7. 更新 .dnacloud/lock.json
8. 清理 staging 目录
```

## 失败处理

- 任何步骤失败 → 从快照恢复，输出详细错误信息
- 签名验证失败 → 立即中止，不安装任何文件
