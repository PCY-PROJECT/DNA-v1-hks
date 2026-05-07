import { Ajv } from 'ajv';
import type { DnaManifest } from './types.js';

const ajv = new Ajv({ allErrors: true });

const manifestSchema = {
  type: 'object',
  required: ['schemaVersion', 'id', 'name', 'version', 'domain', 'packageType', 'objective', 'capabilities', 'price', 'components'],
  properties: {
    schemaVersion: { type: 'string', const: 'dnacloud.package.v1' },
    id: { type: 'string', minLength: 1 },
    name: { type: 'string', minLength: 1 },
    version: { type: 'string', pattern: '^\\d+\\.\\d+\\.\\d+$' },
    domain: { type: 'string', minLength: 1 },
    packageType: { type: 'string', enum: ['official-capability-pack', 'community-pack', 'personal-pack'] },
    objective: { type: 'string', minLength: 1 },
    capabilities: { type: 'array', items: { type: 'string' }, minItems: 1 },
    notGuaranteed: { type: 'array', items: { type: 'string' } },
    price: {
      type: 'object',
      required: ['amount', 'currency', 'network'],
      properties: {
        amount: { type: 'string' },
        currency: { type: 'string' },
        network: { type: 'string' },
      },
    },
    components: {
      type: 'object',
      required: ['skills', 'agents', 'commands', 'mcp', 'hooks', 'rules'],
      properties: {
        skills: { type: 'array', items: { type: 'string' } },
        agents: { type: 'array', items: { type: 'string' } },
        commands: { type: 'array', items: { type: 'string' } },
        mcp: { type: 'array', items: { type: 'string' } },
        hooks: { type: 'array', items: { type: 'string' } },
        rules: { type: 'array', items: { type: 'string' } },
      },
    },
  },
  additionalProperties: true,
};

const validateManifestFn = ajv.compile(manifestSchema);

export function validateManifest(data: unknown): { valid: boolean; errors: string[] } {
  const valid = validateManifestFn(data);
  if (valid) return { valid: true, errors: [] };
  const errors = (validateManifestFn.errors ?? []).map(
    (e: { instancePath: string; message?: string }) => `${e.instancePath || '(root)'} ${e.message}`
  );
  return { valid: false, errors };
}

export function assertManifest(data: unknown): asserts data is DnaManifest {
  const result = validateManifest(data);
  if (!result.valid) {
    throw new Error(`Invalid DNA manifest:\n${result.errors.join('\n')}`);
  }
}
