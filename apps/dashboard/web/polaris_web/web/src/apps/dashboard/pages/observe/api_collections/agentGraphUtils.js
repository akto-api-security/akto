import { CustomersMinor, AutomationMajor, MagicMajor } from "@shopify/polaris-icons";
import MCPIcon from "@/assets/MCP_Icon.svg";
import WebhookIcon from "@/../public/webhooks_logo.svg";

export const getNodeCategoryFromType = (type) => {
  const typeLower = (type || '').toLowerCase();

  if (typeLower.includes('llm') || typeLower.includes('chat') || typeLower.includes('query')) {
    return { category: 'ai-model', type: 'LLM Call', description: 'AI Model Call' };
  }

  if (typeLower.includes('mcp') && typeLower.includes('tool')) {
    return { category: 'mcp', type: 'MCP Tool', description: 'MCP Client Tool' };
  }

  if (typeLower.includes('mcp')) {
    return { category: 'mcp', type: 'MCP Server', description: 'MCP Server' };
  }

  if (typeLower.includes('tool')) {
    return { category: 'ai-tool', type: 'AI Tool', description: 'AI Tool' };
  }

  if (typeLower.includes('webhook')) {
    return { category: 'webhook', type: 'Webhook Tool', description: 'Webhook Handler' };
  }

  return { category: 'internal', type: 'Internal Service', description: 'Internal Service' };
};

export const getComponentColors = (category) => {
  switch (category) {
    case 'internal':
      return { borderColor: '#3b82f6', backgroundColor: '#eff6ff' }; // Blue
    case 'agent':
      return { borderColor: '#f97316', backgroundColor: '#fff7ed' }; // Orange
    case 'ai-model':
      return { borderColor: '#ec4899', backgroundColor: '#fdf2f8' }; // Pink
    case 'mcp':
      return { borderColor: '#4cbebbff', backgroundColor: '#ecfdf5' }; // Yellow-Green
    case 'violation':
      return { borderColor: '#ef4444', backgroundColor: '#fef2f2' }; // Red - has violations
    case 'ai-tool':
      return { borderColor: '#8b5cf6', backgroundColor: '#f5f3ff' }; // Purple
    case 'webhook':
      return { borderColor: '#e91e63', backgroundColor: '#fce4ec' }; // Pink (webhook theme)
    case 'akto-hooks':
      return { borderColor: '#0ea5e9', backgroundColor: '#f0f9ff' }; // Sky blue - Akto proxy
    case 'arcade-mcp':
      return { borderColor: '#4cbebbff', backgroundColor: '#ecfdf5' }; // Same as mcp - teal/green
    case 'arcade-response':
      return { borderColor: '#10b981', backgroundColor: '#f0fdf4' }; // Green - success/response
    case 'arcade-tool-call':
    case 'vscode-tool-call':
      return { borderColor: '#8b5cf6', backgroundColor: '#f5f3ff' }; // Purple - tool call
    case 'vscode-hub':
      return { borderColor: '#3b82f6', backgroundColor: '#eff6ff' }; // Blue - VSCode hub
    default:
      return { borderColor: '#6b7280', backgroundColor: '#f9fafb' }; // Gray
  }
};

export const getComponentIcon = (category) => {
  switch (category) {
    case 'external':
      return CustomersMinor;
    case 'agent':
      return AutomationMajor;
    case 'ai-model':
      return MagicMajor;
    case "mcp":
    case 'arcade-mcp':
      return MCPIcon;
    case 'webhook':
      return WebhookIcon;
    case 'akto-hooks':
      return WebhookIcon;
    case 'arcade-response':
      return AutomationMajor;
    case 'vscode-hub':
      return AutomationMajor;
    case 'vscode-tool-call':
      return AutomationMajor;
    default:
      return CustomersMinor;
  }
};


export const calculateGraphLayout = (totalTargets, containerHeight = 400) => {
  // More compact spacing
  const baseSpacing = totalTargets <= 3 ? 120 : totalTargets <= 6 ? 100 : 90;
  const nodeHeight = 80;

  const totalTargetHeight = totalTargets * baseSpacing;

  const startY = Math.max(40, (containerHeight - totalTargetHeight) / 2);

  const sourceY = startY + (totalTargetHeight / 2) - nodeHeight;

  return {
    baseSpacing,
    nodeHeight,
    totalTargetHeight,
    startY,
    sourceY
  };
};


export const getNodeXPosition = (category) => {
  // More compact positioning
  if (category === 'ai-model') {
    return 400;
  } else if (category === 'mcp') {
    return 550;
  } else if (['ai-tool', 'webhook', 'internal'].includes(category)) {
    return 700;
  }
  return 400;
};


// Build the fixed 5-node linear arcade graph:
// AI Agent → Akto Hooks (req) → MCP Server → Akto Hooks (resp) → Response
// onNodeClick is passed in from the component so it stays framework-agnostic here.
export const buildArcadeGraph = ({ agentName, mcpServers, onNodeClick }) => {
  const centerY = 160;
  const nodeSpacingX = 300;
  const startX = 40;

  const makeNode = (id, x, component) => ({
    id,
    type: 'agentNode',
    position: { x, y: centerY },
    draggable: false,
    data: { component: { id, ...component }, onNodeClick },
  });

  const nodes = [
    makeNode('arcade-agent', startX, {
      label: agentName,
      type: 'AI Agent',
      category: 'agent',
      description: `AI Agent: ${agentName}`,
      status: 'active',
    }),
    makeNode('arcade-hooks-req', startX + nodeSpacingX, {
      label: 'Akto Hooks',
      type: 'Proxy (Request)',
      category: 'akto-hooks',
      description: 'Akto intercepts outgoing tool call requests, providing visibility and security before they reach MCP servers.',
      status: 'active',
    }),
    makeNode('arcade-mcp', startX + nodeSpacingX * 2, {
      label: 'ARCADE registry',
      type: 'MCP Server',
      category: 'arcade-mcp',
      description: `MCP servers discovered via arcade.dev: ${mcpServers.join(', ') || 'None'}`,
      status: 'connected',
      mcpServers,
      showBoundary: true,
      boundaryColor: '#4cbebbff',
      boundaryBg: 'rgba(76, 190, 187, 0.05)',
    }),
    makeNode('arcade-tool-call', startX + nodeSpacingX * 3, {
      label: 'Tool Call',
      type: 'Tool Call',
      category: 'arcade-tool-call',
      description: 'Final tool call response returned to the AI agent after passing through Akto proxy.',
      status: 'connected',
    }),
    makeNode('arcade-hooks-resp', startX + nodeSpacingX * 4, {
      label: 'Akto Evaluation response',
      type: 'Proxy (Response)',
      category: 'akto-hooks',
      description: 'Akto intercepts incoming tool call responses, providing visibility and security before they reach the agent.',
      status: 'active',
    }),
  ];

  const edges = [
    { id: 'ae-1', source: 'arcade-agent',     target: 'arcade-hooks-req',  type: 'agentEdge',  data: { edgeParam: 'tool call' } },
    { id: 'ae-2', source: 'arcade-hooks-req',  target: 'arcade-mcp',        type: 'agentEdge',  data: { edgeParam: 'Evaluated call' } },
    { id: 'ae-3', source: 'arcade-mcp',        target: 'arcade-tool-call',  type: 'agentEdge',  data: { edgeParam: 'Make call' } },
    { id: 'ae-4', source: 'arcade-tool-call',  target: 'arcade-hooks-resp', type: 'agentEdge',  data: { edgeParam: 'Tool response' } }
  ];

  return { nodes, edges };
};

// Build hub-and-spoke VSCode graph: User → Agent (center) → LLM, Tool call (right) | Proxy/Hooks (below)
// Center label from sourceService; guardrail node varies by tags: mcp-server→Proxy, browser-llm→Proxy, gen-ai/ai-agent→Hooks.
export const buildVSCodeGraph = ({ onNodeClick, agentLabel = 'VSCode', hasMcpServer, hasBrowserLlm, hasGenAiOrAiAgent }) => {
  const guardrailType = hasMcpServer ? 'Proxy (MCP)' : hasGenAiOrAiAgent ? 'Hooks' : hasBrowserLlm ? 'Proxy' : 'Hooks';
  const leftX = 40;
  const centerX = 280;
  const rightX = 620;
  const centerY = 140;
  const guardrailY = centerY + 200;
  const rightSpacing = 120;
  const rightStartY = 80;

  const makeNode = (id, x, y, component) => ({
    id,
    type: 'agentNode',
    position: { x, y },
    draggable: false,
    data: { component: { id, ...component }, onNodeClick },
  });

  const nodes = [
    makeNode('vscode-user', leftX, centerY, {
      label: 'User',
      type: '',
      category: 'internal',
      description: 'User initiates prompts and receives responses from the agent.',
      status: 'active',
    }),
    makeNode('vscode-hub', centerX, centerY, {
      label: agentLabel,
      type: 'AI Agent',
      category: 'vscode-hub',
      description: 'VS Code with GitHub Copilot - orchestrates LLM calls, guardrail checks, and tool execution.',
      status: 'active',
      showBoundary: true,
      boundaryColor: '#3b82f6',
      boundaryBg: 'rgba(59, 130, 246, 0.05)',
    }),
    makeNode('vscode-guardrail', centerX, guardrailY, {
      label: 'Guardrail service',
      type: guardrailType,
      category: 'akto-hooks',
      description: hasMcpServer
        ? 'Akto proxy validates MCP tool calls for security and policy compliance.'
        : hasGenAiOrAiAgent
          ? 'Akto hooks validate prompts and tool calls for gen-ai/ai-agent flows.'
          : hasBrowserLlm
            ? 'Akto proxy validates browser LLM traffic for security and policy compliance.'
            : 'Akto guardrails validate prompts and tool calls for security and policy compliance.',
      status: 'active',
    }),
    makeNode('vscode-llm', rightX, rightStartY, {
      label: 'LLM',
      type: 'LLM Call',
      category: 'ai-model',
      description: 'Large Language Model - processes prompts and generates responses.',
      status: 'connected',
    }),
    makeNode('vscode-tool-call', rightX, rightStartY + rightSpacing, {
      label: 'Tool call',
      type: 'Tool Call',
      category: 'vscode-tool-call',
      description: 'External tool execution - terminal, file edits, and other agent tools.',
      status: 'connected',
    }),
  ];

  const edges = [
    { id: 've-1', source: 'vscode-user', target: 'vscode-hub', type: 'agentEdge', data: { edgeParam: 'prompt' } },
    { id: 've-2', source: 'vscode-hub', target: 'vscode-llm', type: 'agentEdge', data: { edgeParam: 'valid llm call' } },
    { id: 've-3', source: 'vscode-hub', target: 'vscode-guardrail', type: 'agentEdge', data: { edgeParam: 'validate' }, sourceHandle: 'bottom', targetHandle: 'top' },
    { id: 've-4', source: 'vscode-hub', target: 'vscode-tool-call', type: 'agentEdge', data: { edgeParam: 'valid tool call' } },
  ];

  return { nodes, edges };
};

export const CATEGORY_ORDER = ['ai-model', 'mcp', 'ai-tool', 'webhook', 'internal'];

export const sortCategories = (categories) => {
  return categories.sort((a, b) => {
    const indexA = CATEGORY_ORDER.indexOf(a);
    const indexB = CATEGORY_ORDER.indexOf(b);
    if (indexA === -1 && indexB === -1) return 0;
    if (indexA === -1) return 1;
    if (indexB === -1) return -1;
    return indexA - indexB;
  });
};
