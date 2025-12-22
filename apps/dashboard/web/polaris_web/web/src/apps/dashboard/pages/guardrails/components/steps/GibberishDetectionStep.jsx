import { VerticalStack, Text, RangeSlider, FormLayout, Checkbox, Box } from "@shopify/polaris";

export const GibberishDetectionConfig = {
    number: 8,
    title: "Gibberish Detection",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableGibberishDetection, gibberishConfidenceScore }) => {
        if (!enableGibberishDetection) return null;
        return `ML-based detection, Confidence: ${gibberishConfidenceScore.toFixed(2)}`;
    }
};

const GibberishDetectionStep = ({
    enableGibberishDetection,
    setEnableGibberishDetection,
    gibberishConfidenceScore,
    setGibberishConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Detect and block gibberish or nonsensical text in user inputs.
                This helps prevent meaningless prompts that could confuse the AI or be used as attack vectors.
            </Text>

            <FormLayout>
                <Checkbox
                    label="Enable gibberish detection"
                    checked={enableGibberishDetection}
                    onChange={setEnableGibberishDetection}
                />

                {enableGibberishDetection && (
                    <Box>
                        <Box paddingBlockStart="2">
                            <RangeSlider
                                label="Confidence Threshold"
                                value={gibberishConfidenceScore}
                                min={0}
                                max={1}
                                step={0.1}
                                output
                                onChange={setGibberishConfidenceScore}
                                helpText="Set the confidence threshold (0-1). Higher values are more permissive, lower values are more strict in detecting gibberish."
                            />
                        </Box>
                    </Box>
                )}
            </FormLayout>
        </VerticalStack>
    );
};

export default GibberishDetectionStep;