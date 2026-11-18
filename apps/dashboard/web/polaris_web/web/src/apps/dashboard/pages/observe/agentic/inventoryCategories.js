// AI inventory categories (AgenticObserveAction.inventoryCategory keys): tab label and the Type
// text shown for rows in that category.
export const CATEGORY_TABS = [
    { id: "agent", label: "AI agents & CLIs", type: "AI agent" },
    { id: "ide", label: "IDE assistants", type: "IDE assistant" },
    { id: "mcp", label: "MCP servers", type: "MCP server" },
    { id: "browser_ai", label: "Browser AI apps", type: "Browser AI app" },
    { id: "skills", label: "Skills", type: "Agent skill" },
    { id: "plugins", label: "Plugins", type: "Plugin" },
    // Copilot Studio bots — not part of the device inventory design, so only shown when present.
    { id: "saas", label: "SaaS agents", type: "SaaS agent", hideWhenEmpty: true },
];

export const CATEGORY_LABELS = Object.fromEntries(CATEGORY_TABS.map((t) => [t.id, t.label]));
const CATEGORY_TYPES = Object.fromEntries(CATEGORY_TABS.map((t) => [t.id, t.type]));

export const typeLabelOf = (row) => CATEGORY_TYPES[row.inventoryCategory];

// "<deviceId>.<client>.<host>" hostnames -> [{ segment, devices }]: distinct devices per middle segment
// (the agent for MCP servers, the browser for browser AI apps), most used first.
export function hostSegmentCounts(hostNames = []) {
    const devicesBySegment = new Map();
    hostNames.forEach((host) => {
        const [device, segment] = String(host).split(".");
        if (!device || !segment) return;
        if (!devicesBySegment.has(segment)) devicesBySegment.set(segment, new Set());
        devicesBySegment.get(segment).add(device);
    });
    return [...devicesBySegment.entries()]
        .map(([segment, devices]) => ({ segment, devices: devices.size }))
        .sort((a, b) => b.devices - a.devices);
}

// Same counts as text: "Cursor (11), Claude CLI (3)".
export function summarizeHostSegment(hostNames = [], format = (s) => s) {
    return hostSegmentCounts(hostNames).map(({ segment, devices }) => `${format(segment)} (${devices})`).join(", ");
}
