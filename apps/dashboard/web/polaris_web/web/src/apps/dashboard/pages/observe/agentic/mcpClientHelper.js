/**
 * Tag keys for Type detection
 */
const TYPE_TAG_KEYS = {
    MCP_SERVER: 'mcp-server',
    GEN_AI: 'gen-ai',
    BROWSER_LLM: 'browser-llm',
};

/**
 * Tag keys for Asset/Client grouping
 */
const ASSET_TAG_KEYS = {
    MCP_CLIENT: 'mcp-client',
    AI_AGENT: 'ai-agent',
    BROWSER_LLM_AGENT: 'browser-llm-agent',
};

/**
 * Client types for display
 */
const CLIENT_TYPES = {
    LLM: 'LLM',
    AI_AGENT: 'AI Agent',
    MCP_SERVER: 'MCP Server',
};

/**
 * Map type tag keys to display types
 */
const TYPE_TAG_TO_DISPLAY = {
    [TYPE_TAG_KEYS.MCP_SERVER]: CLIENT_TYPES.MCP_SERVER,
    [TYPE_TAG_KEYS.GEN_AI]: CLIENT_TYPES.AI_AGENT,
    [TYPE_TAG_KEYS.BROWSER_LLM]: CLIENT_TYPES.LLM,
};

/**
 * High priority product keywords (check these first, they're more specific)
 * These take precedence over generic company names
 */
const HIGH_PRIORITY_CLIENTS = {
    'copilot': { displayName: 'Copilot', domain: 'copilot.microsoft.com' },
    'claude': { displayName: 'Claude', domain: 'claude.ai' },
    'chatgpt': { displayName: 'ChatGPT', domain: 'openai.com' },
    'gemini': { displayName: 'Gemini', domain: 'gemini.google.com' },
    'grok': { displayName: 'Grok', domain: 'x.ai' },
    'cody': { displayName: 'Cody', domain: 'sourcegraph.com' },
    'codewhisperer': { displayName: 'CodeWhisperer', domain: 'aws.amazon.com' },
};

/**

* Known client mappings
 * Maps keywords found in tag names to display names and domains (for favicon)
 */
const KNOWN_CLIENTS = {
    // LLMs
    'claude': { displayName: 'Claude', domain: 'claude.ai' },
    'anthropic': { displayName: 'Anthropic', domain: 'anthropic.com' },
    'chatgpt': { displayName: 'ChatGPT', domain: 'openai.com' },
    'openai': { displayName: 'OpenAI', domain: 'openai.com' },
    'gpt': { displayName: 'GPT', domain: 'openai.com' },
    'grok': { displayName: 'Grok', domain: 'x.ai' },
    'xai': { displayName: 'xAI', domain: 'x.ai' },
    'gemini': { displayName: 'Gemini', domain: 'gemini.google.com' },
    'bard': { displayName: 'Gemini', domain: 'gemini.google.com' },
    
    // AI Agents
    'copilot': { displayName: 'Copilot', domain: 'copilot.microsoft.com' },
    'cursor': { displayName: 'Cursor', domain: 'cursor.com' },
    'cline': { displayName: 'Cline', domain: 'cline.bot' },
    'windsurf': { displayName: 'Windsurf', domain: 'codeium.com' },
    'codeium': { displayName: 'Codeium', domain: 'codeium.com' },
    'continue': { displayName: 'Continue', domain: 'continue.dev' },
    'sourcegraph': { displayName: 'Sourcegraph', domain: 'sourcegraph.com' },
    'cody': { displayName: 'Cody', domain: 'sourcegraph.com' },
    'tabnine': { displayName: 'Tabnine', domain: 'tabnine.com' },
    'codewhisperer': { displayName: 'CodeWhisperer', domain: 'aws.amazon.com' },
    'amazon': { displayName: 'Amazon Q', domain: 'aws.amazon.com' },
    'zed': { displayName: 'Zed', domain: 'zed.dev' },
    'replit': { displayName: 'Replit', domain: 'replit.com' },
    
    // MCP Servers
    'vscode': { displayName: 'VS Code', domain: 'code.visualstudio.com' },
    'jetbrains': { displayName: 'JetBrains', domain: 'jetbrains.com' },
    'intellij': { displayName: 'IntelliJ', domain: 'jetbrains.com' },
    'pycharm': { displayName: 'PyCharm', domain: 'jetbrains.com' },
    'webstorm': { displayName: 'WebStorm', domain: 'jetbrains.com' },
    'neovim': { displayName: 'Neovim', domain: 'neovim.io' },
    'nvim': { displayName: 'Neovim', domain: 'neovim.io' },
    'vim': { displayName: 'Vim', domain: 'vim.org' },
    'github': { displayName: 'GitHub', domain: 'github.com' },
    'gitlab': { displayName: 'GitLab', domain: 'gitlab.com' },
    'bitbucket': { displayName: 'Bitbucket', domain: 'bitbucket.org' },
    'postman': { displayName: 'Postman', domain: 'postman.com' },
    'insomnia': { displayName: 'Insomnia', domain: 'insomnia.rest' },
    'semgrep': { displayName: 'Semgrep', domain: 'semgrep.dev' },
    'slack': { displayName: 'Slack', domain: 'slack.com' },
    'discord': { displayName: 'Discord', domain: 'discord.com' },
    'notion': { displayName: 'Notion', domain: 'notion.so' },
    'linear': { displayName: 'Linear', domain: 'linear.app' },
    'jira': { displayName: 'Jira', domain: 'atlassian.com' },
    'confluence': { displayName: 'Confluence', domain: 'atlassian.com' },
    'asana': { displayName: 'Asana', domain: 'asana.com' },
    'trello': { displayName: 'Trello', domain: 'trello.com' },
    'obsidian': { displayName: 'Obsidian', domain: 'obsidian.md' },
    'figma': { displayName: 'Figma', domain: 'figma.com' },
    'stripe': { displayName: 'Stripe', domain: 'stripe.com' },
    'razorpay': { displayName: 'Razorpay', domain: 'razorpay.com' },
    'square': { displayName: 'Square', domain: 'squareup.com' },
    'kite': { displayName: 'Kite', domain: 'kite.trade' },
    'aws': { displayName: 'AWS', domain: 'aws.amazon.com' },
    'azure': { displayName: 'Azure', domain: 'azure.microsoft.com' },
    'google': { displayName: 'Google', domain: 'google.com' },
    'microsoft': { displayName: 'Microsoft', domain: 'microsoft.com' },
    'lambdatest': { displayName: 'LambdaTest', domain: 'lambdatest.com' },
    'twilio': { displayName: 'Twilio', domain: 'twilio.com' },
    'raycast': { displayName: 'Raycast', domain: 'raycast.com' },
};

/**
 * Find matching client info from tag value, also returns the matched keyword
 * Checks high-priority product names first, then falls back to general matching
 * @param {string} tagValue - The asset tag value (mcp-client, ai-agent, or browser-llm-agent)
 * @returns {Object|null} { clientInfo, matchedKeyword } or null
 */
const findClientInfoWithKeyword = (tagValue) => {
    if (!tagValue) return null;
    
    const lowerValue = tagValue.toLowerCase();
    const valueParts = lowerValue.split(/[-_\s]+/);
    
    // PRIORITY 1: Check high-priority product keywords first (more specific)
    for (const part of valueParts) {
        if (HIGH_PRIORITY_CLIENTS[part]) {
            return { clientInfo: HIGH_PRIORITY_CLIENTS[part], matchedKeyword: part };
        }
    }
    
    // PRIORITY 2: Check if high-priority keyword is contained in value
    for (const [key, clientInfo] of Object.entries(HIGH_PRIORITY_CLIENTS)) {
        if (lowerValue.includes(key)) {
            return { clientInfo, matchedKeyword: key };
        }
    }
    
    // PRIORITY 3: Check each part against general known clients
    for (const part of valueParts) {
        if (KNOWN_CLIENTS[part]) {
            return { clientInfo: KNOWN_CLIENTS[part], matchedKeyword: part };
        }
    }
    
    // PRIORITY 4: Check if any known client key is contained in the value
    for (const [key, clientInfo] of Object.entries(KNOWN_CLIENTS)) {
        if (lowerValue.includes(key)) {
            return { clientInfo, matchedKeyword: key };
        }
    }
    
    return null;
};

/**
 * Find matching client info from tag value
 * @param {string} tagValue - The asset tag value
 * @returns {Object|null} Client info { displayName, domain } or null
 */
const findClientInfo = (tagValue) => {
    const result = findClientInfoWithKeyword(tagValue);
    return result ? result.clientInfo : null;
};

/**
 * Format a tag value into a display name
 * Preserves suffix after known client name (e.g., "claude-cli" -> "Claude CLI")
 * @param {string} tagValue - The asset tag value (e.g., "claude-cli-project")
 * @returns {string} Formatted display name (e.g., "Claude CLI Project")
 */
const formatDisplayName = (tagValue) => {
    if (!tagValue) return 'Unknown';
    
    const result = findClientInfoWithKeyword(tagValue);
    
    if (result) {
        const { clientInfo, matchedKeyword } = result;
        const lowerValue = tagValue.toLowerCase();
        
        // Find where the matched keyword is in the value
        const keywordIndex = lowerValue.indexOf(matchedKeyword);
        
        if (keywordIndex !== -1) {
            // Get the parts before and after the matched keyword
            const beforeKeyword = tagValue.substring(0, keywordIndex);
            const afterKeyword = tagValue.substring(keywordIndex + matchedKeyword.length);
            
            // Format the remaining parts
            const formatPart = (str) => {
                if (!str) return '';
                return str
                    .split(/[-_\s]+/)
                    .filter(word => word.length > 0)
                    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
                    .join(' ');
            };
            
            const beforeFormatted = formatPart(beforeKeyword);
            const afterFormatted = formatPart(afterKeyword);
            
            // Combine: before + displayName + after
            const parts = [beforeFormatted, clientInfo.displayName, afterFormatted].filter(p => p.length > 0);
            return parts.join(' ');
        }
        
        // Fallback: just return the display name
        return clientInfo.displayName;
    }
    
    // For unknown clients, format the tag value nicely
    // e.g., "antigravity" -> "Antigravity", "my-custom-tool" -> "My Custom Tool"
    return tagValue
        .split(/[-_\s]+/)
        .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
        .join(' ');
};

/**
 * Get domain for favicon lookup from tag value
 * @param {string} tagValue - The asset tag value
 * @returns {string|null} Domain for favicon lookup or null
 */
const getDomainForFavicon = (tagValue) => {
    if (!tagValue) return null;
    
    const clientInfo = findClientInfo(tagValue);
    if (clientInfo && clientInfo.domain) {
        return clientInfo.domain;
    }
    
    return null;
};

/**
 * Get type from collection's envType tags
 * Checks for type tag keys: mcp-server, gen-ai, browser-llm
 * @param {Array} envType - Array of tag objects from collection
 * @returns {string} Client type: "LLM", "AI Agent", or "MCP Server"
 */
const getTypeFromTags = (envType) => {
    if (!envType || !Array.isArray(envType)) return CLIENT_TYPES.MCP_SERVER;
    
    for (const tag of envType) {
        if (tag.keyName && TYPE_TAG_TO_DISPLAY[tag.keyName]) {
            return TYPE_TAG_TO_DISPLAY[tag.keyName];
        }
    }
    
    return CLIENT_TYPES.MCP_SERVER;
};

/**
 * Find asset grouping tag from collection's envType tags
 * Checks for: mcp-client, ai-agent, browser-llm-agent
 * @param {Array} envType - Array of tag objects from collection
 * @returns {Object|null} { keyName, value } or null if not found
 */
const findAssetTag = (envType) => {
    if (!envType || !Array.isArray(envType)) return null;
    
    const assetTagKeys = Object.values(ASSET_TAG_KEYS);
    
    for (const tag of envType) {
        if (tag.keyName && assetTagKeys.includes(tag.keyName)) {
            return { keyName: tag.keyName, value: tag.value };
        }
    }
    
    return null;
};

export { 
    formatDisplayName, 
    getDomainForFavicon, 
    findClientInfo, 
    getTypeFromTags,
    findAssetTag,
    KNOWN_CLIENTS, 
    CLIENT_TYPES,
    TYPE_TAG_KEYS,
    ASSET_TAG_KEYS
};
export default formatDisplayName;
