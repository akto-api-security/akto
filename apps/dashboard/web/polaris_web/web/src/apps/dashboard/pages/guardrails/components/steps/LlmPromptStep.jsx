import { VerticalStack, Text, TextField, RangeSlider, FormLayout } from "@shopify/polaris";

export const LlmPromptConfig = {
    number: 6,
    title: "LLM prompt based rule",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ llmPrompt, llmConfidenceScore }) => {
        return llmPrompt ? `${llmPrompt.substring(0, 30)}${llmPrompt.length > 30 ? '...' : ''}, Confidence: ${llmConfidenceScore.toFixed(2)}` : null;
    }
};

const LlmPromptStep = ({
    llmRule,
    setLlmRule,
    llmConfidenceScore,
    setLlmConfidenceScore
}) => {
    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">LLM prompt based rule</Text>
            <Text variant="bodyMd" tone="subdued">
                Create a custom rule using a prompt that is evaluated against user inputs or model responses.
            </Text>

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
        </VerticalStack>
    );
};

export default LlmPromptStep;
