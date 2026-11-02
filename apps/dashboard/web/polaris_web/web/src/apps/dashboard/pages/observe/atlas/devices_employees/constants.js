export const ATLAS_CHAT_SCOPE = "atlas_devices_employees";

export const ATLAS_EMPLOYEE_QUESTIONS = [
    "What did {name}'s agents do this week?",
    "Which of {name}'s tools need a decision?",
    "How does {name} compare to their team?",
];

export const ATLAS_DEVICE_QUESTIONS = [
    "What changed on {name} this week?",
    "Is guardrail enforcement working on {name}?",
    "Who else has the same AI setup as {name}?",
];

export const ACTION_UNAVAILABLE_TOOLTIP = "Atlas has no backend for this action yet";

export const ASK_ATLAS_PLACEHOLDER = "Ask about AI agents, MCP servers, skills, models or browser AI on your devices";

export const ASK_ATLAS_SUGGESTIONS = [
    "Which MCP servers can write to production systems?",
    "Who is using personal AI accounts with company data?",
    "What new AI tools appeared this week?",
    "Which agents run in auto-approve mode?",
];

export const ARGUS_CHAT_SCOPE = "argus_agent_discovery";

export const ASK_ARGUS_PLACEHOLDER = "Ask about your agents, tools and MCP servers";

export const ASK_ARGUS_SUGGESTIONS = [
    "Which agents can write to production databases?",
    "What's new since last week?",
    "Show shadow agents calling external LLM APIs",
    "Which MCP servers are exposed to the internet?",
    "Which agents use deprecated models?",
];
