import { Box, Button, Divider, HorizontalStack, Text, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import settingRequests from "../../../settings/api";
import Pressable from "../../../dashboard/ask/components/Pressable";
import InfoCard from "../../../dashboard/new_components/InfoCard";
import { LegendDot } from "../../agentic/AgenticStatsCard";
import SegmentBar from "../../../../components/shared/SegmentBar";
import { HEALTH_BACKGROUNDS, HEALTH_LABELS, HEALTH_ORDER, MODE_BACKGROUNDS, MODE_LABELS, MODE_ORDER, tokenVar } from "./peopleConstants";
import transform from "./transform";

const HEALTH_TOOLTIP = "Offline: no heartbeat for 7+ days. Outdated: older than the latest release for the device's OS.";

const HEALTH_BAR_HEIGHT = "12px";
const MODE_BAR_HEIGHT = "8px";

function LegendRow({ color, label, count, active, onClick }) {
    return (
        <Pressable onClick={onClick} accessibilityLabel={label} background={active ? "bg-primary-subdued-hover" : undefined} hoverBackground="bg-subdued" borderRadius="2" padding="1">
            <HorizontalStack align="space-between" blockAlign="center" wrap={false}>
                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                    <LegendDot color={color} />
                    <Text as="span">{label}</Text>
                </HorizontalStack>
                <Text as="span" fontWeight="semibold">{count.toLocaleString("en-US")}</Text>
            </HorizontalStack>
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
        key, label: HEALTH_LABELS[key], count: health[key] || 0, color: tokenVar(HEALTH_BACKGROUNDS[key]),
    }));
    const modeSegments = MODE_ORDER.map((key) => ({
        key, label: MODE_LABELS[key], count: modes[key] || 0, color: tokenVar(MODE_BACKGROUNDS[key]),
    }));

    const body = (
        <VerticalStack gap="4">
            <Text as="p" color="subdued">{`${(overview?.deviceCount || 0).toLocaleString("en-US")} managed devices`}</Text>
            <VerticalStack gap="3">
                <SegmentBar segments={healthSegments} height={HEALTH_BAR_HEIGHT} onSegmentClick={onSelectHealth} activeKey={activeHealth} />
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
                <SegmentBar segments={modeSegments} height={MODE_BAR_HEIGHT} onSegmentClick={onSelectMode} activeKey={activeMode} />
                <HorizontalStack align="space-between">
                    {MODE_ORDER.map((key) => (
                        <Pressable
                            key={key}
                            accessibilityLabel={MODE_LABELS[key]}
                            onClick={() => onSelectMode(key)}
                            background={activeMode === key ? "bg-primary-subdued-hover" : undefined}
                            hoverBackground="bg-subdued"
                            borderRadius="2"
                            padding="1"
                        >
                            <Text as="span" variant="bodySm" color={activeMode === key ? undefined : "subdued"}>{MODE_LABELS[key]} · {(modes[key] || 0).toLocaleString("en-US")}</Text>
                        </Pressable>
                    ))}
                </HorizontalStack>
            </VerticalStack>
            {outdatedCount > 0 && (
                <Box>
                    <Button onClick={updateOutdated}>{`Update ${outdatedCount} outdated agent${outdatedCount > 1 ? "s" : ""}`}</Button>
                </Box>
            )}
        </VerticalStack>
    );

    return <InfoCard title="Atlas agent health" titleToolTip={HEALTH_TOOLTIP} component={transform.cardBody(status, body)} />;
}

export default AgentHealthCard;
