import { Box, Text, VerticalStack, HorizontalStack } from "@shopify/polaris";
import Dropdown from "../../../components/layouts/Dropdown";

export const CONFIDENCE_OPTIONS = [
    { label: "Disable", helpText: "No filtering applied.", value: "disable" },
    { label: "Trigger on low confidence", helpText: "Most protective - may flag borderline content.", value: "low" },
    { label: "Trigger on moderate confidence", helpText: "Balanced protection. Recommended for most teams.", value: "moderate" },
    { label: "Trigger only if high confidence", helpText: "Fewest false alarms - blocks only when the system is very sure.", value: "high" },
];

export const CONFIDENCE_VALUES = { high: 0.9, moderate: 0.6, low: 0.3 };
export const CONFIDENCE_VALUES_100 = { high: 75, moderate: 50, low: 25 };

// (enabled, score) -> option value; snaps score to the nearest bucket so legacy floats map cleanly.
export const toOption = (enabled, score, values = CONFIDENCE_VALUES) => {
    if (!enabled) return "disable";
    let best = "high", bestDist = Infinity;
    for (const [key, val] of Object.entries(values)) {
        const dist = Math.abs(val - (score ?? 0));
        if (dist < bestDist) { bestDist = dist; best = key; }
    }
    return best;
};

export const fromOption = (value, values = CONFIDENCE_VALUES) =>
    value === "disable" ? { enabled: false } : { enabled: true, confidenceScore: values[value] };

// Level filters invert: "high confidence" (blocks least) => LOW level, "low confidence" => HIGH.
export const LEVEL_OPTIONS = [
    { label: "Disable", helpText: "No filtering applied.", value: "disable" },
    { label: "Trigger on low confidence", helpText: "Most protective - may flag borderline content.", value: "high" },
    { label: "Trigger on moderate confidence", helpText: "Balanced protection. Recommended for most teams.", value: "medium" },
    { label: "Trigger only if high confidence", helpText: "Fewest false alarms - blocks only when the system is very sure.", value: "low" },
];

const LEVELS = ["high", "medium", "low"];

// Title/help on the left, dropdown on the right; children render below the row while active.
function DropdownShell({ title, helpText, id, options, current, onSelect, showChildren, children }) {
    return (
        <VerticalStack gap="2">
            <HorizontalStack align="space-between" blockAlign="center" wrap={false} gap="4">
                <Box style={{ flexGrow: 1, flexShrink: 1, minWidth: 0 }}>
                    <VerticalStack gap="1">
                        {typeof title === "string" ? <Text as="span">{title}</Text> : title}
                        {helpText && <Text as="span" color="subdued">{helpText}</Text>}
                    </VerticalStack>
                </Box>
                <Box style={{ flex: "0 0 auto", width: "260px" }}>
                    <Dropdown id={id} menuItems={options} initial={current} selected={onSelect} />
                </Box>
            </HorizontalStack>
            {showChildren && children && (
                <Box paddingBlockStart="2">{children}</Box>
            )}
        </VerticalStack>
    );
}

// onChange receives { enabled, confidenceScore } (confidenceScore omitted when disabled).
function ConfidenceDropdown({ title, helpText, enabled, score, onChange, values = CONFIDENCE_VALUES, id, children }) {
    return (
        <DropdownShell
            id={id}
            title={title}
            helpText={helpText}
            options={CONFIDENCE_OPTIONS}
            current={toOption(enabled, score, values)}
            onSelect={(value) => onChange(fromOption(value, values))}
            showChildren={enabled}
        >
            {children}
        </DropdownShell>
    );
}

// With `enabled`: onChange({ enabled, level }). Without: onChange(level), "Disable" => "none".
export function LevelDropdown({ title, helpText, level, enabled, onChange, id, children }) {
    const hasEnable = enabled !== undefined;
    const active = hasEnable ? !!enabled : level !== "none" && !!level;
    const current = !active ? "disable" : (LEVELS.includes(level) ? level : "high");
    const onSelect = (value) => {
        if (hasEnable) onChange(value === "disable" ? { enabled: false } : { enabled: true, level: value });
        else onChange(value === "disable" ? "none" : value);
    };
    return (
        <DropdownShell
            id={id}
            title={title}
            helpText={helpText}
            options={LEVEL_OPTIONS}
            current={current}
            onSelect={onSelect}
            showChildren={active}
        >
            {children}
        </DropdownShell>
    );
}

export default ConfidenceDropdown;
