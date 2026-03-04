import { Box, HorizontalStack, Text } from "@shopify/polaris"

/**
 * Displays system information for a selected instance
 * @param {string} instanceId - Instance identifier
 * @param {Object} data - System info data
 * @param {Array} fields - Field definitions [{key, label, unit}]
 */
function SystemInfoBox({ instanceId, data, fields }) {
    if (!instanceId || !data) return null

    return (
        <Box background="bg-fill-tertiary" padding="3" borderRadius="200">
            <HorizontalStack gap="6">
                <HorizontalStack gap="2" blockAlign="center">
                    <Text variant="bodySm" tone="subdued">Instance:</Text>
                    <Text variant="bodySm" fontWeight="semibold">{instanceId}</Text>
                </HorizontalStack>

                {fields.map(field => (
                    data[field.key] && (
                        <HorizontalStack gap="2" blockAlign="center" key={field.key}>
                            <Text variant="bodySm" tone="subdued">{field.label}:</Text>
                            <Text variant="bodySm" fontWeight="semibold">
                                {data[field.key]} {field.unit || ''}
                            </Text>
                        </HorizontalStack>
                    )
                ))}
            </HorizontalStack>
        </Box>
    )
}

export default SystemInfoBox
