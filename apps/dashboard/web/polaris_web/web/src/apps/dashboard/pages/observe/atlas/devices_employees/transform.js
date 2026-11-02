const ALL_CATEGORIES = ["agents", "ide", "mcp", "browser"];

const STAT_CARD_TARGETS = {
    managedDevices: { tab: "devices", filters: {} },
    agentActive: { tab: "devices", filters: {} },
    devicesWithAiTools: { tab: "devices", filters: { categories: ALL_CATEGORIES } },
    employeesUsingAi: { tab: "employees", filters: {} },
    devicesWithUnapprovedTools: { tab: "devices", filters: { hasUnapproved: true } },
    monitorOnlyDevices: { tab: "devices", filters: { monitorMode: true } },
};

const formatCount = (count) => count.toLocaleString("en-US");

const transform = {
    statCardTarget: (id) => STAT_CARD_TARGETS[id],
    buildStatCards: (deviceStats) => {
        if (!deviceStats) return null;
        const {
            managedDevices,
            agentActive,
            offlineSevenDays,
            devicesWithAiTools,
            devicesWithAiToolsPercent,
            employeesUsingAi,
            totalEmployees,
            devicesWithUnapprovedTools,
            unapprovedTeams,
            monitorOnlyDevices,
        } = deviceStats;
        const hasOffline = offlineSevenDays > 0;
        const hasTeams = unapprovedTeams > 0;
        return {
            managedDevices: { value: managedDevices },
            agentActive: {
                value: agentActive,
                subtext: hasOffline ? `${formatCount(offlineSevenDays)} offline for 7+ days` : undefined,
                tone: hasOffline ? "critical" : undefined,
            },
            devicesWithAiTools: {
                value: devicesWithAiTools,
                subtext: devicesWithAiToolsPercent != null ? `${devicesWithAiToolsPercent}% of fleet` : undefined,
            },
            employeesUsingAi: {
                value: employeesUsingAi,
                subtext: totalEmployees != null ? `of ${formatCount(totalEmployees)}` : undefined,
            },
            devicesWithUnapprovedTools: {
                value: devicesWithUnapprovedTools,
                subtext: hasTeams ? `Across ${formatCount(unapprovedTeams)} ${unapprovedTeams === 1 ? "team" : "teams"}` : undefined,
                tone: hasTeams ? "critical" : undefined,
            },
            monitorOnlyDevices: {
                value: monitorOnlyDevices,
                subtext: monitorOnlyDevices != null ? "Not blocking violations" : undefined,
                tone: monitorOnlyDevices != null ? "critical" : undefined,
            },
        };
    },
};

export default transform;
