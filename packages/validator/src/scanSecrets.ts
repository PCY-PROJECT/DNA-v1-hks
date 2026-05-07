import type { ValidationIssue } from '@dnacloud/schema';

const SECRET_PATTERNS: Array<{ pattern: RegExp; code: string; message: string }> = [
  {
    pattern: /private[_\s]?key\s*[:=]\s*["']?[a-fA-F0-9]{64}/i,
    code: 'SECRET_PRIVATE_KEY',
    message: 'Possible private key detected',
  },
  {
    // Match only when followed by an actual value (not an env var reference like ${...})
    pattern: /(?:mnemonic|seed[_\s]?phrase|passphrase)\s*[:=]\s*["']?(?!\$\{)[a-zA-Z0-9 ]{8,}/i,
    code: 'SECRET_MNEMONIC_HINT',
    message: 'Possible seed phrase or mnemonic reference detected',
  },
  {
    // Must contain actual base64 chars (+/) to avoid matching EVM addresses/hashes
    pattern: /[a-zA-Z0-9+/]{40,}={1,2}|(?:[a-zA-Z0-9]{10,}[+/][a-zA-Z0-9+/]{10,}={0,2})/,
    code: 'SECRET_BASE64_BLOB',
    message: 'Large base64 blob detected — may contain secrets',
  },
  {
    pattern: /sk[-_][a-zA-Z0-9]{20,}/,
    code: 'SECRET_API_KEY_SK',
    message: 'API secret key pattern (sk-...) detected',
  },
  {
    pattern: /eyJ[a-zA-Z0-9_-]{20,}\.[a-zA-Z0-9_-]{20,}\./,
    code: 'SECRET_JWT',
    message: 'JWT token detected',
  },
];

const DANGEROUS_PATTERNS: Array<{ pattern: RegExp; code: string; message: string }> = [
  {
    pattern: /rm\s+-rf\s+\//,
    code: 'DANGER_RM_RF_ROOT',
    message: 'Dangerous rm -rf / command detected',
  },
  {
    pattern: /curl[^|]+\|\s*(ba)?sh/,
    code: 'DANGER_CURL_PIPE_SH',
    message: 'curl | sh pattern detected — remote code execution risk',
  },
  {
    pattern: /wget[^|]+\|\s*(ba)?sh/,
    code: 'DANGER_WGET_PIPE_SH',
    message: 'wget | sh pattern detected — remote code execution risk',
  },
  {
    pattern: /chmod\s+777/,
    code: 'DANGER_CHMOD_777',
    message: 'chmod 777 detected — unsafe permissions',
  },
  {
    pattern: /~\/(\.ssh|\.aws|\.config|\.kube|\.gnupg)/,
    code: 'DANGER_CREDENTIAL_PATH',
    message: 'Access to credential directories detected',
  },
  {
    pattern: /(paste|enter|provide|share)\s+(your\s+)?(private\s+key|seed\s+phrase|mnemonic|secret)/i,
    code: 'DANGER_PHISHING',
    message: 'Possible prompt injection requesting secrets',
  },
];

export interface ScanResult {
  secretIssues: ValidationIssue[];
  dangerIssues: ValidationIssue[];
}

export function scanContent(content: string, filePath: string): ScanResult {
  const secretIssues: ValidationIssue[] = [];
  const dangerIssues: ValidationIssue[] = [];

  for (const { pattern, code, message } of SECRET_PATTERNS) {
    if (pattern.test(content)) {
      secretIssues.push({ code, message, file: filePath });
    }
  }

  for (const { pattern, code, message } of DANGEROUS_PATTERNS) {
    if (pattern.test(content)) {
      dangerIssues.push({ code, message, file: filePath });
    }
  }

  return { secretIssues, dangerIssues };
}
