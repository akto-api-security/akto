import { Box, HorizontalStack, Text } from "@shopify/polaris";
import Pressable from "../../../dashboard/ask/components/Pressable";

const MIN_LABEL_PERCENT = 10;
const BLANK_LABEL = "\u00A0";

function SegmentedBar({ segments, onSelect, showPercent = true }) {
    const total = segments.reduce((sum, segment) => sum + segment.count, 0);
    if (!total) return <Box background="bg-strong" borderRadius="2" paddingBlockStart="4" paddingBlockEnd="4" width="100%" />;

    return (
        <Box borderRadius="2" overflowX="hidden" overflowY="hidden" width="100%">
            <HorizontalStack wrap={false} gap="0">
                {segments.filter((segment) => segment.count > 0).map((segment) => {
                    const percent = (100 * segment.count) / total;
                    const content = (
                        <HorizontalStack align="center">
                            <Text as="span" variant="bodySm" fontWeight="semibold">
                                {showPercent && percent >= MIN_LABEL_PERCENT ? `${Math.round(percent)}%` : BLANK_LABEL}
                            </Text>
                        </HorizontalStack>
                    );
                    const boxProps = {
                        width: `${percent}%`,
                        background: segment.background,
                        color: segment.lightText ? "text-on-color" : undefined,
                        paddingBlockStart: "3",
                        paddingBlockEnd: "3",
                        title: segment.tooltip || `${segment.label}: ${segment.count.toLocaleString("en-US")} (${Math.round(percent)}%)`,
                    };
                    return onSelect ? (
                        <Pressable key={segment.key} accessibilityLabel={segment.label} onClick={() => onSelect(segment.key)} {...boxProps}>
                            {content}
                        </Pressable>
                    ) : (
                        <Box key={segment.key} {...boxProps}>{content}</Box>
                    );
                })}
            </HorizontalStack>
        </Box>
    );
}

export default SegmentedBar;
