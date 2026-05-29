import { mapLabel, getDashboardCategory } from "../../../../main/labelHelper";
const makeNode = (id, label, type, description, x, status, category) =>
    ({ id, label, type, description, x, y: 160, status, category });

const userNode      = ()      => makeNode('user',     'User',   'External', 'External user interacting with the AI agent', 100, 'connected', 'external');
const agentNode     = (label) => makeNode('ai-agent', label,   'AI Agent', 'Central AI agent processing requests',        400, 'active',    'agent');
const llmCallNode   = (label) => makeNode('llm-call', label,   'LLM Call', 'Large Language Model API call',               700, 'active',    'ai-model');
const mcpServerNode = (label) => makeNode('mcp',      label,   'MCP Server', 'MCP',                                       400, 'active',    'mcp');

const externalServerNode = (label, type) => {
    const apiServerType = mapLabel('Api', getDashboardCategory()) + ' Server';
    return {
        id: 'external',
        label,
        type: type || apiServerType,
        description: type ? mapLabel('Api', getDashboardCategory()) + ' server' : apiServerType.toLowerCase().replace('server', 'server'),
        x: 700, y: 160,
        status: 'active',
        category: 'external'
    };
};

// Layout builders
const llmAgentLayout = (agentLabel, llmLabel) => ({
    components: [userNode(), agentNode(agentLabel), llmCallNode(llmLabel)]
});

const mcpAgentLayout = (mcpLabel, externalLabel, externalType) => ({
    components: [
        { ...agentNode('Cursor'), x: 100 },
        mcpServerNode(mcpLabel),
        externalServerNode(externalLabel, externalType)
    ]
});

const agentDiscoveryData = {
    "1756781037":  llmAgentLayout('Copy AI',             'POST /v1/generate'),
    "1756780057":  llmAgentLayout('yodayo.com',          'POST /api/v1/taverns'),
    "1756780830":  llmAgentLayout('Jasper AI',           'POST /api/v1/completions'),
    "1756781639":  llmAgentLayout('Do not pay Agent',    'POST /v1/legal-assistant'),
    "1756779813":  llmAgentLayout('Janitor AI',          'POST /api/v1/chats/messages'),
    "1756799740":  llmAgentLayout('babylonhealth.com',   'POST /v1/ai-consultation'),
    "1755667479":  llmAgentLayout('Felo AI',             'POST /trail/v1/chat/completions'),
    "1756779490":  llmAgentLayout('Replicate',           'POST /v1/predictions'),
    "1756797279":  llmAgentLayout('Luma AI',             'POST /v1/financial-advisor'),
    "482256023":   llmAgentLayout('Chargebee Agent',     'POST /api/public/code_generation'),
    "1755603870":  llmAgentLayout('Lmaerna Agent',       'POST /api/stream/create-evaluation'),
    "1755630915":  llmAgentLayout('Perplexity',          'POST /rest/sse/perplexity_ask'),
    "1755631633":  llmAgentLayout('Agenta AI',           'POST /services/completion/test'),
    "-1839477461": llmAgentLayout('Copy AI',             'POST /n8n-agent/webhook/{webhook_id}'),
    "1756780970":  llmAgentLayout('Anthropic AI Agent',  'POST /v1/messages'),
    "1757935140":  llmAgentLayout('OpenAI Agent',        'POST /v1/chat/completions'),
    "1433259559":  llmAgentLayout('LangSmith Agent',     'POST /langchain/default/{trace_id}'),
    "1756779585":  llmAgentLayout('Cohere AI agent',     'POST /v1/chat'),
    "-1227024417": llmAgentLayout('Microsoft Co-Pilot',  'POST /copilot/conversation/{conversationId}'),

    "-1214040103": mcpAgentLayout('mcp.kite.trade',                'Kite API server'),
    "806508600":   mcpAgentLayout('mcp.vulnerable.io',             'Vulnerable API server'),
    "1498019923":  mcpAgentLayout('mcp-api.lambdatest.com',        'LambdaTest API server'),
    "1741353695":  mcpAgentLayout('mcp.squareup.com',              'Square API server'),
    "369380821":   mcpAgentLayout('mcp.testkg.com',                'Test API server'),
    "-134853966":  mcpAgentLayout('mcp.akto.io',                   'app.akto.io'),
    "-1091509450": mcpAgentLayout('playwright/mcp',                'Playwright API server'),
    "-1889683798": mcpAgentLayout('mcp-server-text-editor',        'Local file system',   'File system'),
    "-797611530":  mcpAgentLayout('vulnerable-mcp-kong.akto.io',   'Akto Kong API Server'),
    "197918427":   mcpAgentLayout('mcp.explorium.ai',              'Explorium API Server'),
    "-890278005": mcpAgentLayout('mcp-kong.akto.io',              'Akto Kong API Server'),
    "-1065831232": mcpAgentLayout('mcpservice-bzeaa6fme3czgch8.eastus-01.azurewebsites.net','Azure default mcp server'),
};

export const dummyCollections = new Set(Object.keys(agentDiscoveryData));

export default agentDiscoveryData;
