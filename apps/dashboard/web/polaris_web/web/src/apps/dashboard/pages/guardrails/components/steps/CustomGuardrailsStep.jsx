import { useState } from "react";
import { VerticalStack, Text, FormLayout, TextField, RangeSlider, Box, Checkbox } from "@shopify/polaris";

// URL validation function
const validateUrl = (url) => {
    const urlPattern = /^https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)$/;
    return urlPattern.test(url);
};

export const CustomGuardrailsConfig = {
    number: 6,
    title: "Custom Guardrails",

    validate: ({ enableExternalModel, url }) => {
        const hasUrl = enableExternalModel && url && url.trim().length > 0;
        const urlError = hasUrl && !validateUrl(url.trim()) ? "Invalid URL format. Must be a valid http or https URL" : null;

        return {
            isValid: !urlError,
            errorMessage: urlError
        };
    },

    getSummary: ({ enableLlmPrompt, llmPrompt, enableExternalModel, url }) => {
        const summaries = [];
        if (enableLlmPrompt && llmPrompt) {
            summaries.push(`LLM: ${llmPrompt.substring(0, 20)}${llmPrompt.length > 20 ? '...' : ''}`);
        }
        if (enableExternalModel && url) {
            summaries.push(`External: ${url.substring(0, 20)}${url.length > 20 ? '...' : ''}`);
        }
        return summaries.length > 0 ? summaries.join(', ') : null;
    }
};

const CustomGuardrailsStep = ({
    // LLM prompt based rule
    enableLlmPrompt,
    setEnableLlmPrompt,
    llmRule,
    setLlmRule,
    llmConfidenceScore,
    setLlmConfidenceScore,
    // External model based evaluation
    enableExternalModel,
    setEnableExternalModel,
    url,
    setUrl,
    confidenceScore,
    setConfidenceScore
}) => {
    const [urlError, setUrlError] = useState("");

    // Handle URL input with validation
    const handleUrlChange = (value) => {
        setUrl(value);
        if (value && value.trim() && !validateUrl(value.trim())) {
            setUrlError("Invalid URL format. Must be a valid http or https URL");
        } else {
            setUrlError("");
        }
    };

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Create custom guardrails using LLM prompts or external model endpoints.
            </Text>

            <VerticalStack gap="4">
                {/* LLM Prompt Based Rule */}
                <Box>
                    <Checkbox
                        label="LLM prompt based rule"
                        checked={enableLlmPrompt}
                        onChange={setEnableLlmPrompt}
                        helpText="Create a custom rule using a prompt that is evaluated against user inputs or model responses."
                    />
                    {enableLlmPrompt && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
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
                        </Box>
                    )}
                </Box>

                {/* External Model Based Evaluation */}
                <Box>
                    <Checkbox
                        label="External model based evaluation"
                        checked={enableExternalModel}
                        onChange={setEnableExternalModel}
                        helpText="Configure an external model endpoint to evaluate content against custom criteria."
                    />
                    {enableExternalModel && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <FormLayout>
                                <TextField
                                    label="URL"
                                    value={url}
                                    onChange={handleUrlChange}
                                    placeholder="https://api.example.com/evaluate"
                                    helpText="The endpoint URL for your external evaluation model"
                                    error={urlError}
                                />

                                <RangeSlider
                                    label="Confidence score threshold"
                                    value={confidenceScore}
                                    onChange={setConfidenceScore}
                                    min={0}
                                    max={100}
                                    step={25}
                                    output
                                    helpText="Content will be blocked if the model's confidence score exceeds this threshold (0-100)"
                                />
                            </FormLayout>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default CustomGuardrailsStep;
