// Fixed dummy data for MCP Endpoint Shield Metadata
// Uses relative time offsets to ensure consistent relative values

export const getMcpEndpointShieldData = () => {
    const now = Math.floor(Date.now() / 1000);

    return [
        {
            agentId: `agent-${now - 86400}`,
            deviceId: '2a27c88357b55e31a56bee74adc33d0f',
            username: 'john.doe',
            email: 'john.doe@bankone.com',
            lastHeartbeat: now - 8640, // 2.4 hours ago
            lastDeployed: now - 518400, // 6 days ago
        },
        {
            agentId: `agent-${now - 172800}`,
            deviceId: '3b38d99468c66f42b67cff85bed44e1g',
            username: 'jane.smith',
            email: 'jane.smith@bankone.com',
            lastHeartbeat: now - 22000, // ~6.1 hours ago
            lastDeployed: now - 432000, // 5 days ago
        },
        {
            agentId: `agent-${now - 259200}`,
            deviceId: '4c49eaa579d77g53c78dgg96cfe55f2h',
            username: 'bob.wilson',
            email: 'bob.wilson@bankone.com',
            lastHeartbeat: now - 34310, // ~9.5 hours ago
            lastDeployed: now - 345600, // 4 days ago
        },
        {
            agentId: `agent-${now - 345600}`,
            deviceId: '5d5afbb68ae88h64d89ehh07dff66g3i',
            username: 'alice.brown',
            email: 'alice.brown@bankone.com',
            lastHeartbeat: now - 65966, // ~18.3 hours ago
            lastDeployed: now - 604800, // 7 days ago
        },
        {
            agentId: `agent-${now - 432000}`,
            deviceId: '6e6bccc79bf99i75e9afii18egg77h4j',
            username: 'charlie.davis',
            email: 'charlie.davis@bankone.com',
            lastHeartbeat: now - 77324, // ~21.5 hours ago
            lastDeployed: now - 518435, // ~6 days ago
        }
    ];
};

// MCP Servers detected by each agent
export const getMcpServersByAgent = (agentId, deviceId) => {
    const serversByAgent = {
        default: [
            {
                serverName: 'akto_mcp_server',
                serverUrl: 'docker run --rm -i akto-api-security/akto-mcp-server',
                detected: true,
                lastSeen: Date.now() / 1000 - 3600,
                collectionName: `${deviceId}.akto_mcp_server`
            },
            {
                serverName: 'playwright-mcp',
                serverUrl: 'python -m playwright_mcp',
                detected: true,
                lastSeen: Date.now() / 1000 - 7200,
                collectionName: `${deviceId}.playwright-mcp`
            },
            {
                serverName: 'text-editor-mcp',
                serverUrl: 'npx -y text-editor-mcp',
                detected: true,
                lastSeen: Date.now() / 1000 - 1800,
                collectionName: `${deviceId}.text-editor-mcp`
            }
        ]
    };

    return serversByAgent[agentId] || serversByAgent.default;
};

// Agent Logs
export const getAgentLogs = (agentId) => {
    const now = Math.floor(Date.now() / 1000);

    return [
        {
            timestamp: now - 300, // 5 minutes ago
            level: 'INFO',
            message: 'Agent heartbeat sent successfully'
        },
        {
            timestamp: now - 900, // 15 minutes ago
            level: 'INFO',
            message: 'Discovered MCP server: akto_mcp_server'
        },
        {
            timestamp: now - 1800, // 30 minutes ago
            level: 'INFO',
            message: 'Discovered MCP server: playwright-mcp'
        },
        {
            timestamp: now - 2700, // 45 minutes ago
            level: 'INFO',
            message: 'Discovered MCP server: text-editor-mcp'
        },
        {
            timestamp: now - 3600, // 1 hour ago
            level: 'INFO',
            message: 'Agent started monitoring'
        }
    ];
};
