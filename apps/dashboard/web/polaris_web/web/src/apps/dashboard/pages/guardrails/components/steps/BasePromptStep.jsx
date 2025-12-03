import { VerticalStack, Text, TextField, RangeSlider, FormLayout, Checkbox, Box } from "@shopify/polaris";

export const BasePromptConfig = {
    number: 7,
    title: "Base Prompt Rule",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enableBasePromptRule, basePromptAutoDetect, basePrompt, basePromptConfidenceScore }) => {
        if (!enableBasePromptRule) return null;
        const autoDetectText = basePromptAutoDetect ? ' (Auto-detect)' : '';
        const promptText = basePrompt ? ` - ${basePrompt.substring(0, 30)}${basePrompt.length > 30 ? '...' : ''}` : '';
        if (!(autoDetectText && promptText)) return null; 
        return `${autoDetectText}${promptText}, Confidence: ${basePromptConfidenceScore.toFixed(2)}`;
    }
};

const BasePromptStep = ({
    enableBasePromptRule,
    setEnableBasePromptRule,
    basePrompt,
    setBasePrompt,
    basePromptAutoDetect,
    setBasePromptAutoDetect,
    basePromptConfidenceScore,
    setBasePromptConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Base Prompt Rule</Text>
            <Text variant="bodyMd" tone="subdued">
                Configure a base prompt rule to check the intent of user input in agent prompts with placeholders like {`{var}`} or {`{}`}.
            </Text>

            <FormLayout>
                <Checkbox
                    label="Enable base prompt rule"
                    checked={enableBasePromptRule}
                    onChange={setEnableBasePromptRule}
                    helpText="When enabled, the guardrail will analyze user inputs that fill placeholders in the base prompt."
                />

                {enableBasePromptRule && (
                    <>
                        <Checkbox
                            label="Auto-detect base prompt from traffic"
                            checked={basePromptAutoDetect}
                            onChange={setBasePromptAutoDetect}
                            helpText="Automatically detect the base prompt pattern from agent traffic. If disabled, you must provide the base prompt manually."
                        />

                        { /* TODO: Add auto-detected base prompt display with fallback placeholder text when auto-detect is enabled */ }
                        {!basePromptAutoDetect && (
                            <TextField
                                label="Base Prompt Template"
                                value={basePrompt}
                                onChange={setBasePrompt}
                                multiline={5}
                                placeholder="You are a helpful assistant. Answer the following question: {}"
                                helpText="Provide the base prompt template with placeholders using {} or {var_name} syntax."
                            />
                        )}

                        <Box>
                            <Text variant="bodyMd" fontWeight="medium">Confidence Score: {basePromptConfidenceScore.toFixed(2)}</Text>
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
                    </>
                )}
            </FormLayout>
        </VerticalStack>
    );
};

export default BasePromptStep;

