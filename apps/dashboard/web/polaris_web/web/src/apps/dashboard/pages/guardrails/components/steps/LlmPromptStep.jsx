import { VerticalStack, Text, TextField, RangeSlider, FormLayout, Checkbox, Box } from "@shopify/polaris";

export const LlmPromptConfig = {
    number: 6,
    title: "LLM prompt based rule",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ llmPrompt, llmConfidenceScore, enablePromptAttacks }) => {
        const summaries = [];
        if (enablePromptAttacks) summaries.push('Prompt attacks');
        if (llmPrompt) summaries.push(`${llmPrompt.substring(0, 30)}${llmPrompt.length > 30 ? '...' : ''}, Confidence: ${llmConfidenceScore.toFixed(2)}`);
        return summaries.length > 0 ? summaries.join(', ') : null;
    }
};

const LlmPromptStep = ({
    llmRule,
    setLlmRule,
    llmConfidenceScore,
    setLlmConfidenceScore,
    enablePromptAttacks,
    setEnablePromptAttacks,
    promptAttackLevel,
    setPromptAttackLevel
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">LLM prompt based rule</Text>
            <Text variant="bodyMd" tone="subdued">
                Create a custom rule using a prompt that is evaluated against user inputs or model responses.
            </Text>

            <VerticalStack gap="4">
                <FormLayout>
                    <TextField
                        label="Prompt"
                        value={llmRule}
                        onChange={setLlmRule}
                        multiline={4}
                        placeholder="Enter your LLM evaluation prompt here..."
                        helpText="This prompt will be used to evaluate whether content should be blocked. Be specific about what you want to detect."
                    />

                    <RangeSlider
                        label="Confidence score threshold"
                        value={llmConfidenceScore}
                        onChange={setLlmConfidenceScore}
                        min={0}
                        max={1}
                        step={0.1}
                        output
                        helpText="Content will be blocked if the LLM's confidence score exceeds this threshold"
                    />
                </FormLayout>

                <Box>
                    <Checkbox
                        label="Enable prompt attacks filter"
                        checked={enablePromptAttacks}
                        onChange={setEnablePromptAttacks}
                        helpText="Enable to detect and block user inputs attempting to override system instructions. To avoid misclassifying system prompts as a prompt attack and ensure that the filters are selectively applied to user inputs, use input tagging."
                    />
                    {/* Prompt attacks level slider */}
                    {enablePromptAttacks && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Prompt Attack Level</Text>
                                <RangeSlider
                                    label=""
                                    value={promptAttackLevel === 'none' ? 0 : promptAttackLevel === 'low' ? 1 : promptAttackLevel === 'medium' ? 2 : 3}
                                    min={0}
                                    max={3}
                                    step={1}
                                    output
                                    onChange={(value) => {
                                        const levels = ['none', 'low', 'medium', 'high'];
                                        setPromptAttackLevel(levels[value]);
                                    }}
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default LlmPromptStep;
