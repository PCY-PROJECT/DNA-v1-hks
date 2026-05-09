#!/usr/bin/env node
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { CallToolRequestSchema, ListToolsRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { z } from 'zod';

const MARKETPLACE_URL = process.env.DNACLOUD_MARKETPLACE_URL ?? 'https://finderfund.cn/dna/api';

const server = new Server(
  { name: 'dnacloud-marketplace', version: '1.0.0' },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: 'search',
      description: 'Search the DNAcloud Marketplace for DNA capability packages',
      inputSchema: {
        type: 'object',
        properties: {
          query: {
            type: 'string',
            description: 'Search query (e.g. "trading", "market analysis", "risk management")',
          },
        },
        required: ['query'],
      },
    },
    {
      name: 'get_package',
      description: 'Get detailed information about a specific DNA package',
      inputSchema: {
        type: 'object',
        properties: {
          packageId: { type: 'string', description: 'The DNA package ID (e.g. "trading-master-dna")' },
        },
        required: ['packageId'],
      },
    },
    {
      name: 'get_install_preview',
      description: 'Get the install preview (list of files that will be added) for a DNA package',
      inputSchema: {
        type: 'object',
        properties: {
          packageId: { type: 'string' },
          version: { type: 'string', default: 'latest' },
        },
        required: ['packageId'],
      },
    },
  ],
}));

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  if (name === 'search') {
    const { query } = z.object({ query: z.string() }).parse(args);
    const res = await fetch(`${MARKETPLACE_URL}/v1/dna/search?q=${encodeURIComponent(query)}`);
    if (!res.ok) {
      return { content: [{ type: 'text', text: `Marketplace search failed: ${res.status}` }], isError: true };
    }
    const packages = await res.json() as unknown[];
    if (!Array.isArray(packages) || packages.length === 0) {
      return { content: [{ type: 'text', text: `No DNA packages found for query: "${query}"` }] };
    }
    const text = packages.map((p: unknown) => formatPackageSummary(p as Record<string, unknown>)).join('\n\n');
    return { content: [{ type: 'text', text }] };
  }

  if (name === 'get_package') {
    const { packageId } = z.object({ packageId: z.string() }).parse(args);
    const res = await fetch(`${MARKETPLACE_URL}/v1/dna/${encodeURIComponent(packageId)}`);
    if (res.status === 404) {
      return { content: [{ type: 'text', text: `Package not found: ${packageId}` }], isError: true };
    }
    if (!res.ok) {
      return { content: [{ type: 'text', text: `Failed to fetch package: ${res.status}` }], isError: true };
    }
    const pkg = await res.json() as Record<string, unknown>;
    return { content: [{ type: 'text', text: JSON.stringify(pkg, null, 2) }] };
  }

  if (name === 'get_install_preview') {
    const { packageId } = z.object({ packageId: z.string() }).parse(args);
    const text = [
      `Install Preview for ${packageId}:`,
      `Run: dnacloud install ${packageId}`,
      `This will add Skills, Agents, Commands, MCP configs, and Hooks to your project.`,
      `See manifest for full details. Use dnacloud verify ${packageId} after installation.`,
    ].join('\n');
    return { content: [{ type: 'text', text }] };
  }

  return { content: [{ type: 'text', text: `Unknown tool: ${name}` }], isError: true };
});

function formatPackageSummary(p: Record<string, unknown>): string {
  const price = (p.price as Record<string, string>) ?? {};
  const capabilities = (p.capabilities as string[]) ?? [];
  return [
    `📦 ${p.name} v${p.version}  [${p.packageType}]`,
    `   ID: ${p.id}`,
    `   ${p.description}`,
    `   价格: ${price.amount} ${price.currency} (${price.network})`,
    `   能力: ${capabilities.join(', ')}`,
  ].join('\n');
}

const transport = new StdioServerTransport();
await server.connect(transport);
