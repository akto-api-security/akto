import { Box, Text, VerticalStack } from "@shopify/polaris";
import Pressable from "../../../dashboard/ask/components/Pressable";
import { STAT_EMPTY_VALUE } from "./statCards";

function formatStatValue(value) {
    if (value === null || value === undefined) return STAT_EMPTY_VALUE;
    return typeof value === "number" ? value.toLocaleString("en-US") : value;
}

function StatCard({ label, value, subtext, subtextTone, onClick }) {
    const content = (
        <VerticalStack gap="1">
            <Text as="p" variant="bodyMd" color="subdued">{label}</Text>
            <Text as="p" variant="heading2xl">{formatStatValue(value)}</Text>
            {subtext && <Text as="p" variant="bodyMd" color={subtextTone || "subdued"}>{subtext}</Text>}
        </VerticalStack>
    );

    if (!onClick) return <Box padding="4">{content}</Box>;

    return (
        <Pressable
            onClick={onClick}
            accessibilityLabel={label}
            background="bg"
            hoverBackground="bg-subdued"
            padding="4"
        >
            {content}
        </Pressable>
    );
}

export default StatCard;
