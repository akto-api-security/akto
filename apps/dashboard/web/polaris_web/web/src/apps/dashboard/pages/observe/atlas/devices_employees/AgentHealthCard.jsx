import { Box, Button, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import Pressable from "../../../dashboard/ask/components/Pressable";
import { LegendDot } from "../../agentic/AgenticStatsCard";
import InsightCard from "./InsightCard";
import SegmentedBar from "./SegmentedBar";
import { HEALTH_BACKGROUNDS, HEALTH_LABELS, MODE_BACKGROUNDS, MODE_LABELS, tokenVar } from "./peopleConstants";

const HEALTH_ORDER = ["healthy", "outdated", "offline"];
const MODE_ORDER = ["enforce", "monitor", "none"];

function LegendRow({ color, label, count, active, onClick }) {
    const row = (
        <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <LegendDot color={color} />
                <Text as="span">{label}</Text>
            </HorizontalStack>
            <Text as="span" fontWeight="semibold">{count.toLocaleString("en-US")}</Text>
        </HorizontalStack>
    );
    if (!onClick) return row;
    return (
        <Pressable onClick={onClick} accessibilityLabel={label} background={active ? "bg-primary-subdued-hover" : undefined} hoverBackground="bg-subdued" borderRadius="2" padding="1">
            {row}
        </Pressable>
    );
}

function AgentHealthCard({ overview, status, activeHealth, activeMode, onSelectHealth, onSelectMode, onUpdated }) {
    const health = overview?.agentHealth || {};
    const modes = overview?.guardrailModes || {};
    const outdatedModuleIds = overview?.outdatedModuleIds || [];
    const outdatedCount = outdatedModuleIds.length;

    const updateOutdated = () => {
        const agentWord = `agent${outdatedCount > 1 ? "s" : ""}`;
        const message = `Are you sure you want to update ${outdatedCount} ${agentWord} to the latest version? They will pick up the change on their next reboot.`;
        func.showConfirmationModal(message, "Update", async () => {
            try {
                await settingRequests.bulkUpdateModuleEnvAndReboot(outdatedModuleIds, { UPDATE_TO_LATEST_VERSION: "true" });
                func.setToast(true, false, `${outdatedCount} ${agentWord} queued to update to the latest version.`);
                onUpdated();
            } catch (error) {
                func.setToast(true, true, "Failed to trigger update to latest version");
            }
        });
    };

    const healthSegments = HEALTH_ORDER.map((key) => ({
        key, label: HEALTH_LABELS[key], count: health[key] || 0, background: HEALTH_BACKGROUNDS[key], lightText: key !== "outdated",
    }));
    const modeSegments = MODE_ORDER.map((key) => ({
        key, label: MODE_LABELS[key], count: modes[key] || 0, background: MODE_BACKGROUNDS[key], lightText: key === "enforce",
    }));

    return (
        <InsightCard
            title="Atlas agent health"
            subtitle={`${(overview?.deviceCount || 0).toLocaleString("en-US")} managed devices`}
            status={status}
        >
            <VerticalStack gap="3">
                <SegmentedBar segments={healthSegments} onSelect={onSelectHealth} activeKey={activeHealth} thin />
                <VerticalStack gap="1">
                    {HEALTH_ORDER.map((key) => (
                        <LegendRow key={key} color={tokenVar(HEALTH_BACKGROUNDS[key])} label={HEALTH_LABELS[key]} count={health[key] || 0}
                            active={activeHealth === key} onClick={() => onSelectHealth(key)} />
                    ))}
                </VerticalStack>
            </VerticalStack>
            <Divider />
            <VerticalStack gap="2">
                <Text as="h4" variant="headingSm">Guardrail mode</Text>
                <SegmentedBar segments={modeSegments} onSelect={onSelectMode} activeKey={activeMode} thin />
                <HorizontalStack align="space-between">
                    {MODE_ORDER.map((key) => (
                        <Text key={key} as="span" variant="bodySm" color="subdued">{MODE_LABELS[key]} · {(modes[key] || 0).toLocaleString("en-US")}</Text>
                    ))}
                </HorizontalStack>
            </VerticalStack>
            {outdatedCount > 0 && (
                <Box>
                    <Button onClick={updateOutdated}>{`Update ${outdatedCount} outdated agent${outdatedCount > 1 ? "s" : ""}`}</Button>
                </Box>
            )}
        </InsightCard>
    );
}

export default AgentHealthCard;
