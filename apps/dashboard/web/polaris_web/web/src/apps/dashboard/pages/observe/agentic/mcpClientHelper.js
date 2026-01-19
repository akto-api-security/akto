const TYPE_TAG_KEYS = { MCP_SERVER: 'mcp-server', GEN_AI: 'gen-ai', BROWSER_LLM: 'browser-llm' };
const ASSET_TAG_KEYS = { MCP_CLIENT: 'mcp-client', AI_AGENT: 'ai-agent', BROWSER_LLM_AGENT: 'browser-llm-agent' };
const CLIENT_TYPES = { LLM: 'LLM', AI_AGENT: 'AI Agent', MCP_SERVER: 'MCP Server' };
const ROW_TYPES = { AGENT: 'agent', SERVICE: 'service' };
const TYPE_TAG_TO_DISPLAY = {
    [TYPE_TAG_KEYS.MCP_SERVER]: CLIENT_TYPES.MCP_SERVER,
    [TYPE_TAG_KEYS.GEN_AI]: CLIENT_TYPES.AI_AGENT,
    [TYPE_TAG_KEYS.BROWSER_LLM]: CLIENT_TYPES.LLM,
};

// Known clients: keyword -> { displayName, domain, agentType }
// agentType is used to infer the type for agent rows (not from tags)
const KNOWN_CLIENTS = {
    claude: { displayName: 'Claude', domain: 'claude.ai', agentType: CLIENT_TYPES.AI_AGENT },
    chatgpt: { displayName: 'ChatGPT', domain: 'openai.com', agentType: CLIENT_TYPES.AI_AGENT },
    openai: { displayName: 'OpenAI', domain: 'openai.com', agentType: CLIENT_TYPES.AI_AGENT },
    gemini: { displayName: 'Gemini', domain: 'gemini.google.com', agentType: CLIENT_TYPES.LLM },
    copilot: { displayName: 'Copilot', domain: 'copilot.microsoft.com', agentType: CLIENT_TYPES.AI_AGENT },
    cursor: { displayName: 'Cursor', domain: 'cursor.com', agentType: CLIENT_TYPES.AI_AGENT },
    grok: { displayName: 'Grok', domain: 'x.ai', agentType: CLIENT_TYPES.AI_AGENT },
    cody: { displayName: 'Cody', domain: 'sourcegraph.com', agentType: CLIENT_TYPES.AI_AGENT },
    windsurf: { displayName: 'Windsurf', domain: 'codeium.com', agentType: CLIENT_TYPES.AI_AGENT },
    codeium: { displayName: 'Codeium', domain: 'codeium.com', agentType: CLIENT_TYPES.AI_AGENT },
    tabnine: { displayName: 'Tabnine', domain: 'tabnine.com', agentType: CLIENT_TYPES.AI_AGENT },
    github: { displayName: 'GitHub', domain: 'github.com', agentType: CLIENT_TYPES.AI_AGENT },
    vscode: { displayName: 'VS Code', domain: 'code.visualstudio.com', agentType: CLIENT_TYPES.AI_AGENT },
    slack: { displayName: 'Slack', domain: 'slack.com', agentType: CLIENT_TYPES.AI_AGENT },
    notion: { displayName: 'Notion', domain: 'notion.so', agentType: CLIENT_TYPES.AI_AGENT },
    figma: { displayName: 'Figma', domain: 'figma.com', agentType: CLIENT_TYPES.AI_AGENT },
    stripe: { displayName: 'Stripe', domain: 'stripe.com', agentType: CLIENT_TYPES.MCP_SERVER },
    aws: { displayName: 'AWS', domain: 'aws.amazon.com', agentType: CLIENT_TYPES.MCP_SERVER },
    azure: { displayName: 'Azure', domain: 'azure.microsoft.com', agentType: CLIENT_TYPES.MCP_SERVER },
    google: { displayName: 'Google', domain: 'google.com', agentType: CLIENT_TYPES.AI_AGENT },
    antigravity: { displayName: 'Antigravity', domain: 'antigravity.google', agentType: CLIENT_TYPES.AI_AGENT },
    litellm: { displayName: 'LiteLLM', domain: 'litellm.ai', agentType: CLIENT_TYPES.AI_AGENT },
    filesystem: { displayName: 'Filesystem', domain: 'filesystem', agentType: CLIENT_TYPES.MCP_SERVER },
    universal: { displayName: 'Universal', domain: 'universal', agentType: CLIENT_TYPES.MCP_SERVER },
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

const capitalizeWord = (w) => w.toLowerCase() === 'cli' || w.toLowerCase() === 'mcp' ? w.toUpperCase() : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase();

const formatDisplayName = (tagValue) => {
    if (!tagValue) return 'Unknown';
    const info = findClientInfo(tagValue);
    if (!info) {
        return tagValue.split(/[-_\s]+/).map(capitalizeWord).join(' ');
    }
    const idx = tagValue.toLowerCase().indexOf(info.keyword);
    const before = tagValue.substring(0, idx).split(/[-_\s]+/).filter(w => w).map(capitalizeWord).join(' ');
    const after = tagValue.substring(idx + info.keyword.length).split(/[-_\s]+/).filter(w => w).map(capitalizeWord).join(' ');
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

const findTypeTag = (envType) => {
    if (!Array.isArray(envType)) return null;
    const keys = Object.values(TYPE_TAG_KEYS);
    for (const tag of envType) {
        if (tag.keyName && keys.includes(tag.keyName)) return { keyName: tag.keyName, value: tag.value };
    }
    return null;
};

// Get agent type from tag value using KNOWN_CLIENTS map (for agent rows)
const getAgentTypeFromValue = (tagValue) => {
    const info = findClientInfo(tagValue);
    return info?.agentType || CLIENT_TYPES.AI_AGENT;
};

export { 
    formatDisplayName, 
    getDomainForFavicon, 
    getTypeFromTags, 
    findAssetTag, 
    findTypeTag,
    getAgentTypeFromValue,
    CLIENT_TYPES, 
    TYPE_TAG_KEYS, 
    ASSET_TAG_KEYS,
    ROW_TYPES 
};
export default formatDisplayName;
