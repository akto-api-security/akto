import { Box, HorizontalStack, Text } from "@shopify/polaris";
import Pressable from "../../../dashboard/ask/components/Pressable";

const MIN_LABEL_PERCENT = 10;

function SegmentedBar({ segments, onSelect, height = "2.25rem" }) {
    const total = segments.reduce((sum, segment) => sum + segment.count, 0);
    if (!total) return <Box background="bg-strong" borderRadius="2" minHeight={height} width="100%" />;

    return (
        <Box borderRadius="2" overflowX="hidden" overflowY="hidden" width="100%">
            <HorizontalStack wrap={false} gap="0">
                {segments.filter((segment) => segment.count > 0).map((segment) => {
                    const percent = (100 * segment.count) / total;
                    const fill = (
                        <Box style={{ background: segment.color, minHeight: height, display: "flex", alignItems: "center", justifyContent: "center" }}>
                            {percent >= MIN_LABEL_PERCENT && (
                                <Box as="span" color={segment.lightText ? "text-on-color" : undefined}>
                                    <Text as="span" variant="bodySm" fontWeight="semibold">{Math.round(percent)}%</Text>
                                </Box>
                            )}
                        </Box>
                    );
                    const title = segment.tooltip || `${segment.label}: ${segment.count}`;
                    return onSelect ? (
                        <Pressable key={segment.key} width={`${percent}%`} title={title} accessibilityLabel={segment.label} onClick={() => onSelect(segment.key)}>
                            {fill}
                        </Pressable>
                    ) : (
                        <Box key={segment.key} width={`${percent}%`} title={title}>{fill}</Box>
                    );
                })}
            </HorizontalStack>
        </Box>
    );
}

export default SegmentedBar;
