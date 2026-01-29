import { VerticalStack, Text, Checkbox, Box, RangeSlider } from "@shopify/polaris";

export const UsageGuardrailsConfig = {
    number: 7,
    title: "Usage based Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableTokenLimit }) => {
        const scanners = [];
        if (enableTokenLimit) {
            scanners.push('Token Limit');
        }
        return scanners.length > 0 ? scanners.join(', ') : null;
    }
};

const UsageGuardrailsStep = ({
    enableTokenLimit,
    setEnableTokenLimit,
    tokenLimitConfidenceScore,
    setTokenLimitConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure usage-based guardrails to control token limits and prevent abuse.
            </Text>

            <VerticalStack gap="4">
                <Box>
                    <Checkbox
                        label="Enable token limit detection"
                        checked={enableTokenLimit}
                        onChange={setEnableTokenLimit}
                        helpText="Detect when user inputs exceed token limits and block overly long inputs."
                    />
                    {enableTokenLimit && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Confidence Threshold</Text>
                                <RangeSlider
                                    label=""
                                    value={tokenLimitConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setTokenLimitConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in enforcing token limits."
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default UsageGuardrailsStep;

