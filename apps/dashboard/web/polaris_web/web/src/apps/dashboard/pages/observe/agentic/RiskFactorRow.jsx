import React from "react";
import { Box, HorizontalStack, VerticalStack, Text, Badge, Icon } from "@shopify/polaris";
import { ChevronRightMinor } from "@shopify/polaris-icons";

export function RiskFactorRow({ factor, onClick }) {
    const badgeStatus = factor.severity === "critical" ? "critical" : factor.severity === "high" ? "warning" : factor.severity === "medium" ? "attention" : "info";
    return (
        <div
            onClick={onClick}
            className="agentic-clickable-row"
        >
            <HorizontalStack gap="4" align="start" blockAlign="start" wrap={false}>
                <Box width="72px" paddingBlock="2">
                    <Badge status={badgeStatus}>{factor.severity.charAt(0).toUpperCase() + factor.severity.slice(1)}</Badge>
                </Box>
                <Box width="100%" paddingBlock="1">
                    <VerticalStack gap="1">
                        <Text variant="bodySm" fontWeight="semibold">{factor.title}</Text>
                        <Text variant="bodySm" color="subdued">{factor.description}</Text>
                    </VerticalStack>
                </Box>
                <Box paddingBlock="2">
                    <Icon source={ChevronRightMinor} color="subdued" />
                </Box>
            </HorizontalStack>
        </div>
    );
}

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };
const SEV_LABELS = { critical: "Critical", high: "High", medium: "Medium", low: "Low" };

// Risk factors of an asset row (violations by severity, personal accounts, malicious skill or MCP server), most
// severe first; empty when nothing stands out. Moved from the old flyout Overview tab.
export function computeAssetRiskFactors(asset) {
    const factors = [];
    Object.entries(SEV_LABELS).forEach(([sev, label]) => {
        const count = asset.violations?.[sev] || 0;
        if (count > 0) {
            factors.push({ severity: sev, title: `${count} ${label} Violation${count > 1 ? "s" : ""}`, description: `Contains ${label.toLowerCase()} violations`, type: "violation" });
        }
    });
    if (asset.hasPersonalAccount) {
        factors.push({ severity: "high", title: "Personal Account", description: "Contains personal account", type: "personal_account" });
    }
    if (asset.isMalicious) {
        const server = asset.type === "MCP Server";
        factors.push({ severity: "critical", title: server ? "Malicious MCP Server" : "Malicious Skill", description: server ? "Flagged as a malicious MCP server" : "Contains malicious skill", type: "malicious_skill" });
    }
    return factors.sort((a, b) => SEV_ORDER[a.severity] - SEV_ORDER[b.severity]);
}
