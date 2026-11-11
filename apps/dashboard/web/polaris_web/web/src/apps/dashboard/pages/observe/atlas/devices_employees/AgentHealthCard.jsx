import { Box, Button, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import InfoCard from "../../../dashboard/new_components/InfoCard";
import { BreakdownChip } from "../../agentic/AgenticStatsCard";
import SegmentBar from "../../../../components/shared/SegmentBar";
import { HEALTH_BACKGROUNDS, HEALTH_LABELS, HEALTH_ORDER, MODE_BACKGROUNDS, MODE_LABELS, MODE_ORDER, tokenVar } from "./peopleConstants";
import transform from "./transform";

const HEALTH_TOOLTIP = "Offline: no heartbeat for 7+ days. Outdated: older than the latest release for the device's OS.";

const BAR_HEIGHT = "22px";
const LIGHT_TEXT_HEALTH = ["healthy", "offline"];
const LIGHT_TEXT_MODES = ["enforce"];

function AgentHealthCard({ overview, status, activeHealth, activeMode, onSelectHealth, onSelectMode, onUpdated, queuedIds = [] }) {
    const health = overview?.agentHealth || {};
    const modes = overview?.guardrailModes || {};
    const outdatedModuleIds = (overview?.outdatedModuleIds || []).filter((moduleId) => !queuedIds.includes(moduleId));
    const outdatedCount = outdatedModuleIds.length;

    const updateOutdated = () => {
        const agentWord = `agent${outdatedCount > 1 ? "s" : ""}`;
        const message = `Are you sure you want to update ${outdatedCount} ${agentWord} to the latest version? They will pick up the change on their next reboot.`;
        func.showConfirmationModal(message, "Update", async () => {
            try {
                await settingRequests.bulkUpdateModuleEnvAndReboot(outdatedModuleIds, { UPDATE_TO_LATEST_VERSION: "true" });
                func.setToast(true, false, `${outdatedCount} ${agentWord} queued to update to the latest version.`);
                onUpdated(outdatedModuleIds);
            } catch (error) {
                func.setToast(true, true, "Failed to trigger update to latest version");
            }
        });
    };

    const healthSegments = HEALTH_ORDER.map((key) => ({
        key, label: HEALTH_LABELS[key], count: health[key] || 0, color: tokenVar(HEALTH_BACKGROUNDS[key]), lightText: LIGHT_TEXT_HEALTH.includes(key),
    }));
    const modeSegments = MODE_ORDER.map((key) => ({
        key, label: MODE_LABELS[key], count: modes[key] || 0, color: tokenVar(MODE_BACKGROUNDS[key]), lightText: LIGHT_TEXT_MODES.includes(key),
    }));

    const activeHealthSet = new Set(activeHealth ? [activeHealth] : []);
    const activeModeSet = new Set(activeMode ? [activeMode] : []);

    const body = (
        <VerticalStack gap="4">
            <VerticalStack gap="2">
                <Text as="h4" variant="headingSm">Managed devices</Text>
                <SegmentBar segments={healthSegments} height={BAR_HEIGHT} showLabels onSegmentClick={onSelectHealth} activeKey={activeHealth} />
                <HorizontalStack gap="1" wrap>
                    {healthSegments.map((segment) => (
                        <BreakdownChip key={segment.key} b={segment} activeFilter={activeHealthSet} onFilterClick={onSelectHealth} />
                    ))}
                </HorizontalStack>
            </VerticalStack>
            <Divider />
            <VerticalStack gap="2">
                <Text as="h4" variant="headingSm">Guardrail mode</Text>
                <SegmentBar segments={modeSegments} height={BAR_HEIGHT} showLabels onSegmentClick={onSelectMode} activeKey={activeMode} />
                <HorizontalStack gap="1" wrap>
                    {modeSegments.map((segment) => (
                        <BreakdownChip key={segment.key} b={segment} activeFilter={activeModeSet} onFilterClick={onSelectMode} />
                    ))}
                </HorizontalStack>
            </VerticalStack>
            {outdatedCount > 0 && (
                <Box>
                    <Button onClick={updateOutdated}>{`Update ${outdatedCount} outdated agent${outdatedCount > 1 ? "s" : ""}`}</Button>
                </Box>
            )}
            {queuedIds.length > 0 && (
                <Text as="p" variant="bodySm" color="subdued">{`${queuedIds.length} agent${queuedIds.length > 1 ? "s" : ""} queued to update on next reboot`}</Text>
            )}
        </VerticalStack>
    );

    return <InfoCard title="Atlas agent health" subtitle="Agent status and guardrail mode across managed devices" titleToolTip={transform.tooltip(HEALTH_TOOLTIP)} component={transform.cardBody(status, body)} />;
}

export default AgentHealthCard;
