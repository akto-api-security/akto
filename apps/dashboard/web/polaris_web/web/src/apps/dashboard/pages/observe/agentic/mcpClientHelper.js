const TYPE_TAG_KEYS = { MCP_SERVER: 'mcp-server', GEN_AI: 'gen-ai', BROWSER_LLM: 'browser-llm' };
const ASSET_TAG_KEYS = { MCP_CLIENT: 'mcp-client', AI_AGENT: 'ai-agent', BROWSER_LLM_AGENT: 'browser-llm-agent' };
const CLIENT_TYPES = { LLM: 'LLM', AI_AGENT: 'AI Agent', MCP_SERVER: 'MCP Server' };
const TYPE_TAG_TO_DISPLAY = {
    [TYPE_TAG_KEYS.MCP_SERVER]: CLIENT_TYPES.MCP_SERVER,
    [TYPE_TAG_KEYS.GEN_AI]: CLIENT_TYPES.AI_AGENT,
    [TYPE_TAG_KEYS.BROWSER_LLM]: CLIENT_TYPES.LLM,
};

// Known clients: keyword -> { displayName, domain }
const KNOWN_CLIENTS = {
    claude: { displayName: 'Claude', domain: 'claude.ai' },
    chatgpt: { displayName: 'ChatGPT', domain: 'openai.com' },
    openai: { displayName: 'OpenAI', domain: 'openai.com' },
    gemini: { displayName: 'Gemini', domain: 'gemini.google.com' },
    copilot: { displayName: 'Copilot', domain: 'copilot.microsoft.com' },
    cursor: { displayName: 'Cursor', domain: 'cursor.com' },
    grok: { displayName: 'Grok', domain: 'x.ai' },
    cody: { displayName: 'Cody', domain: 'sourcegraph.com' },
    windsurf: { displayName: 'Windsurf', domain: 'codeium.com' },
    codeium: { displayName: 'Codeium', domain: 'codeium.com' },
    tabnine: { displayName: 'Tabnine', domain: 'tabnine.com' },
    github: { displayName: 'GitHub', domain: 'github.com' },
    vscode: { displayName: 'VS Code', domain: 'code.visualstudio.com' },
    slack: { displayName: 'Slack', domain: 'slack.com' },
    notion: { displayName: 'Notion', domain: 'notion.so' },
    figma: { displayName: 'Figma', domain: 'figma.com' },
    stripe: { displayName: 'Stripe', domain: 'stripe.com' },
    aws: { displayName: 'AWS', domain: 'aws.amazon.com' },
    azure: { displayName: 'Azure', domain: 'azure.microsoft.com' },
    google: { displayName: 'Google', domain: 'google.com' },
    antigravity: { displayName: 'Antigravity', domain: 'antigravity.google' },
};

const findClientInfo = (tagValue) => {
    if (!tagValue) return null;
    const lower = tagValue.toLowerCase();
    const parts = lower.split(/[-_\s]+/);
    
    for (const part of parts) {
        if (KNOWN_CLIENTS[part]) return { ...KNOWN_CLIENTS[part], keyword: part };
    }
    for (const [key, info] of Object.entries(KNOWN_CLIENTS)) {
        if (lower.includes(key)) return { ...info, keyword: key };
    }
    return null;
};

const formatDisplayName = (tagValue) => {
    if (!tagValue) return 'Unknown';
    const info = findClientInfo(tagValue);
    if (!info) {
        return tagValue.split(/[-_\s]+/).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
    }
    const idx = tagValue.toLowerCase().indexOf(info.keyword);
    const before = tagValue.substring(0, idx).split(/[-_\s]+/).filter(w => w).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
    const after = tagValue.substring(idx + info.keyword.length).split(/[-_\s]+/).filter(w => w).map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()).join(' ');
    return [before, info.displayName, after].filter(p => p).join(' ');
};

const getDomainForFavicon = (tagValue) => findClientInfo(tagValue)?.domain || null;

const getTypeFromTags = (envType) => {
    if (!Array.isArray(envType)) return CLIENT_TYPES.MCP_SERVER;
    for (const tag of envType) {
        if (tag.keyName && TYPE_TAG_TO_DISPLAY[tag.keyName]) return TYPE_TAG_TO_DISPLAY[tag.keyName];
    }
    return CLIENT_TYPES.MCP_SERVER;
};

const findAssetTag = (envType) => {
    if (!Array.isArray(envType)) return null;
    const keys = Object.values(ASSET_TAG_KEYS);
    for (const tag of envType) {
        if (tag.keyName && keys.includes(tag.keyName)) return { keyName: tag.keyName, value: tag.value };
    }
    return null;
};

export { formatDisplayName, getDomainForFavicon, findClientInfo, getTypeFromTags, findAssetTag, KNOWN_CLIENTS, CLIENT_TYPES, TYPE_TAG_KEYS, ASSET_TAG_KEYS };
export default formatDisplayName;
