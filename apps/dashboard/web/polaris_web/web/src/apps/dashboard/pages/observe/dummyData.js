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
