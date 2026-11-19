import { Box, HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import func from '@/util/func'

// time | rail | content. Grid cells stretch to the row's height, so the rail's border runs from
// under the dot to the next row — rows have no gap, the content's bottom padding is the spacing,
// which keeps the line unbroken. Each row's dot is colored by severity unless it already carries
// an explicit dotColor (a caller with no severity of its own, e.g. a non-finding event stream).
function ProfileTimeline({ title, subtitle, rows, total }) {
    rows = rows || []
    return (
        <VerticalStack gap="4">
            <VerticalStack gap="05">
                {title && <Text variant="headingSm">{title}</Text>}
                {subtitle && <Text variant="bodySm" color="subdued">{subtitle}</Text>}
            </VerticalStack>
            {rows.length === 0 ? (
                <Text variant="bodySm" color="subdued">Nothing recorded in this window.</Text>
            ) : (
                <VerticalStack gap="0">
                    {rows.map((r, i) => (
                        <HorizontalGrid key={i} columns="96px 8px minmax(0, 1fr)" gap="3">
                            <Text variant="bodySm" color="subdued" alignment="end">{func.prettifyEpoch(r.timestamp || 0)}</Text>
                            <Box position="relative">
                                <Box paddingBlockStart="1">
                                    <Box className="agentic-dot" style={{ '--dot-color': r.dotColor || func.getHexColorForSeverity(String(r.severity || '').toUpperCase()) }} />
                                </Box>
                                {i < rows.length - 1 && (
                                    <Box position="absolute" insetBlockStart="4" insetBlockEnd="0" width="4px" borderInlineEndWidth="1" borderColor="border-subdued" />
                                )}
                            </Box>
                            <Box paddingBlockEnd="5">
                                <VerticalStack gap="05">
                                    <Text variant="bodyMd" fontWeight="semibold">{r.title}</Text>
                                    {r.detail && <Text variant="bodySm" color="subdued">{r.detail}</Text>}
                                </VerticalStack>
                            </Box>
                        </HorizontalGrid>
                    ))}
                </VerticalStack>
            )}
            {total > rows.length && (
                <Text variant="bodySm" color="subdued">Showing {rows.length} of {total}.</Text>
            )}
        </VerticalStack>
    )
}

export default ProfileTimeline
