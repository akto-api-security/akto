import { Badge, HorizontalStack, Text, Tooltip } from "@shopify/polaris";
import func from "@/util/func";

// Repeated Bedrock prompts from one caller are grouped into one activity for this account only (the
// threat backend's AKTO_GROUPED_ACTIVITY_ACCOUNTS), so the grouping toggle is shown only here.
const GROUPED_ACTIVITY_ACCOUNT_ID = 1703087742;

export function showsActivityGrouping() {
    return window?.ACTIVE_ACCOUNT === GROUPED_ACTIVITY_ACCOUNT_ID;
}

// On/off switch (Polaris has none) between grouped activities and one row per event.
export function ActivityGroupingToggle({ pressed, onToggle }) {
    if (!showsActivityGrouping()) return null;
    return (
        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
            <button
                type="button"
                role="switch"
                aria-checked={pressed}
                aria-label="Group repeated prompts"
                onClick={onToggle}
                style={{
                    position: "relative", width: 32, height: 18, padding: 0, border: "none", borderRadius: 9, cursor: "pointer",
                    background: pressed ? "var(--p-color-bg-interactive)" : "var(--p-color-border)",
                }}
            >
                <span style={{
                    position: "absolute", top: 2, left: pressed ? 16 : 2, width: 14, height: 14, borderRadius: "50%",
                    background: "var(--p-color-bg)", transition: "left 0.15s",
                }} />
            </button>
            <Text as="span" variant="bodyMd">Group repeated prompts</Text>
            <Badge>Beta</Badge>
        </HorizontalStack>
    );
}

// A grouped activity row stands for all its events, so actions on it apply to every one of them.
// eventIdsByRow: a grouped row's id -> the ids of its events.
export function expandToGroupEvents(ids, eventIdsByRow) {
    return [...new Set(ids.flatMap((id) => eventIdsByRow[id] || [id]))];
}

// How many events a grouped activity holds and, when they tripped more than one policy, which ones.
export function ActivityGroupBadges({ count, firstDetectedAt, lastDetectedAt, policies }) {
    const policyList = (policies || []).filter(Boolean);
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            <Tooltip content={`Same prompt from the same caller ${count} times, ${func.epochToDateTime(firstDetectedAt)} to ${func.epochToDateTime(lastDetectedAt)}`}>
                <Badge status="attention">{`${count} events`}</Badge>
            </Tooltip>
            {policyList.length > 1 && (
                <Tooltip content={policyList.join(", ")}>
                    <Badge status="info">{`${policyList.length} policies`}</Badge>
                </Tooltip>
            )}
        </HorizontalStack>
    );
}
