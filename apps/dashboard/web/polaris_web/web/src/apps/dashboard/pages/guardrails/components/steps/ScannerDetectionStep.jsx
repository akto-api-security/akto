import { VerticalStack, Text, RangeSlider, FormLayout, Checkbox, Box } from "@shopify/polaris";

// Reusable component for scanner detection steps following GibberishDetection pattern
export const createScannerDetectionConfig = (number, title, defaultConfidenceScore = 0.7) => ({
    number,
    title,

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: (stateData) => {
        const enabledKey = `enable${title.replace(/\s+/g, '')}`;
        const confidenceKey = `${title.replace(/\s+/g, '').toLowerCase()}ConfidenceScore`;
        if (!stateData[enabledKey]) return null;
        return `ML-based detection, Confidence: ${stateData[confidenceKey]?.toFixed(2) || defaultConfidenceScore.toFixed(2)}`;
    }
});

const ScannerDetectionStep = ({
    title,
    description,
    checkboxLabel,
    enabled,
    onEnabledChange,
    confidenceScore,
    onConfidenceScoreChange,
    helpText
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingSm">{title}</Text>
            <Text variant="bodyMd" tone="subdued">
                {description}
            </Text>

            <FormLayout>
                <Checkbox
                    label={checkboxLabel}
                    checked={enabled}
                    onChange={onEnabledChange}
                />

                {enabled && (
                    <Box>
                        <Box paddingBlockStart="2">
                            <RangeSlider
                                label="Confidence Threshold"
                                value={confidenceScore}
                                min={0}
                                max={1}
                                step={0.1}
                                output
                                onChange={onConfidenceScoreChange}
                                helpText={helpText || "Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict."}
                            />
                        </Box>
                    </Box>
                )}
            </FormLayout>
        </VerticalStack>
    );
};

export default ScannerDetectionStep;

