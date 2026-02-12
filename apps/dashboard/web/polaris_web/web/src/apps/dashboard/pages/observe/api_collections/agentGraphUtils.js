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
    case 'ai-tool':
      return { borderColor: '#8b5cf6', backgroundColor: '#f5f3ff' }; // Purple
    case 'webhook':
      return { borderColor: '#e91e63', backgroundColor: '#fce4ec' }; // Pink (webhook theme)
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
      return MCPIcon;
    case 'webhook':
      return WebhookIcon;
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
