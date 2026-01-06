import { VerticalStack, Text, Checkbox, Box, RangeSlider } from "@shopify/polaris";

export const AdvancedScannersConfig = {
    number: 8,
    title: "Token Limit Filter",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: (stateData) => {
        const scanners = [];
        const scannerKeys = [
            { key: 'enableTokenLimit', label: 'Token Limit' }
        ];

        scannerKeys.forEach(({ key, label }) => {
            if (stateData[key]) {
                scanners.push(label);
            }
        });

        return scanners.length > 0 ? scanners.join(', ') : null;
    }
};

const AdvancedScannersStep = ({
    // TokenLimit
    enableTokenLimit,
    setEnableTokenLimit,
    tokenLimitConfidenceScore,
    setTokenLimitConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Token Limit Filter</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure token limit detection to block overly long user inputs.
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

export default AdvancedScannersStep;

