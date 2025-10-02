import { mapLabel, getDashboardCategory } from "../../../../main/labelHelper";

const agentDiscoveryData = {
    "1756781037": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Copy AI',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /v1/generate',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756780057": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'yodayo.com',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /api/v1/taverns',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756780830": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Jasper AI',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /api/v1/completions',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756781639": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Do not pay Agent',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /v1/legal-assistant',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756779813": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Janitor AI',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /api/v1/chats/messages',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756799740": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'babylonhealth.com',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /v1/ai-consultation',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1755667479": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Felo AI',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /trail/v1/chat/completions',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756779490": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Replicate',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /v1/predictions',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1756797279": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Luma AI',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /v1/financial-advisor',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "482256023": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Chargebee Agent',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /api/public/code_generation',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1755603870": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Lmaerna Agent',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /api/stream/create-evaluation',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1755630915": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Perplexity',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /rest/sse/perplexity_ask',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "1755631633": {
        components: [
            {
                id: 'user',
                label: 'User',
                type: 'External',
                description: 'External user interacting with the AI agent',
                x: 100,
                y: 160,
                status: 'connected',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Agenta AI',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 400,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'llm-call',
                label: 'POST /services/completion/test',
                type: 'LLM Call',
                description: 'Large Language Model API call',
                x: 700,
                y: 160,
                status: 'active',
                category: 'ai-model'
            }
        ]
    },
    "-1214040103": {
        components: [
            {
                id: 'external',
                label: 'Kite API server',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp.kite.trade',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "806508600": {
        components: [
            {
                id: 'external',
                label: 'Vulnerable API server',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp.vulnerable.io',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "1498019923": {
        components: [
            {
                id: 'external',
                label: 'LambdaTest API server',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp-api.lambdatest.com',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "1741353695": {
        components: [
            {
                id: 'external',
                label: 'Square API server',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp.squareup.com',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "369380821": {
        components: [
            {
                id: 'external',
                label: 'Test API server',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp.testkg.com',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "-134853966": {
        components: [
            {
                id: 'external',
                label: 'app.akto.io',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp.akto.io',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "-1091509450": {
        components: [
            {
                id: 'external',
                label: 'Playwright API server',
                type: mapLabel('Api', getDashboardCategory()) + ' Server',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'playwright/mcp',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
    "-1889683798": {
        components: [
            {
                id: 'external',
                label: 'Local file system',
                type: 'File system',
                description: mapLabel('Api', getDashboardCategory()) + ' server',
                x: 700,
                y: 160,
                status: 'active',
                category: 'external'
            },
            {
                id: 'ai-agent',
                label: 'Cursor',
                type: 'AI Agent',
                description: 'Central AI agent processing requests',
                x: 100,
                y: 160,
                status: 'active',
                category: 'agent'
            },
            {
                id: 'mcp',
                label: 'mcp-server-text-editor',
                type: 'MCP Server',
                description: 'MCP',
                x: 400,
                y: 160,
                status: 'active',
                category: 'mcp'
            }
        ]
    },
}

export default agentDiscoveryData;