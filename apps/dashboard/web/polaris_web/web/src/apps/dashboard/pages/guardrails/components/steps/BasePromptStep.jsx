import { VerticalStack, Text, RangeSlider, FormLayout, Checkbox, Box } from "@shopify/polaris";

export const BasePromptConfig = {
    number: 7,
    title: "Intent verification using base prompt (AI Agents)",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableBasePromptRule, basePromptConfidenceScore }) => {
        if (!enableBasePromptRule) return null;
        return `Auto-detect from traffic, Confidence: ${basePromptConfidenceScore.toFixed(2)}`;
    }
};

const BasePromptStep = ({
    enableBasePromptRule,
    setEnableBasePromptRule,
    basePromptConfidenceScore,
    setBasePromptConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Verify if agent requests match the intent of the base prompt. The base prompt is automatically detected from traffic, and user inputs filling placeholders like {`{var}`} or {`{}`} are checked against this intent.
            </Text>

            <FormLayout>
                <Checkbox
                    label="Enable agent intent verification"
                    checked={enableBasePromptRule}
                    onChange={setEnableBasePromptRule}
                />

                {enableBasePromptRule && (
                    <Box>
                        <Box paddingBlockStart="2">
                            <RangeSlider
                                label="Confidence Threshold"
                                value={basePromptConfidenceScore}
                                min={0}
                                max={1}
                                step={0.1}
                                output
                                onChange={setBasePromptConfidenceScore}
                                helpText="Set the confidence threshold (0-1). Higher values require more confidence to block content."
                            />
                        </Box>
                    </Box>
                )}
            </FormLayout>
        </VerticalStack>
    );
};

export default BasePromptStep;

