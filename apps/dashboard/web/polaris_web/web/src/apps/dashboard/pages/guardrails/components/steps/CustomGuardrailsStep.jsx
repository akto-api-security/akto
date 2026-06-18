import { useState, useRef } from "react";
import { VerticalStack, Text, FormLayout, TextField, RangeSlider, Box, Checkbox, HorizontalStack, Tag, Tooltip } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import guardrailApi from "../../api";

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
    // LLM rule compliance (controlled by parent)
    llmCompliance,
    setLlmCompliance,
    // External model based evaluation
    enableExternalModel,
    setEnableExternalModel,
    url,
    setUrl,
    confidenceScore,
    setConfidenceScore
}) => {
    const [urlError, setUrlError] = useState("");
    const [llmRuleCompliance, setLlmRuleCompliance] = useState({ loading: false, suggested: {}, accepted: {} });
    const [enableLlmComplianceMapping, setEnableLlmComplianceMapping] = useState(true);
    const debounceRef = useRef(null);

    const fetchLlmCompliance = async (rule) => {
        try {
            const resp = await guardrailApi.suggestGuardrailCompliance('llm_rule', { llmRule: rule });
            const suggested = resp?.response?.mapComplianceToListClauses || {};
            // By default, accept all suggested frameworks
            const accepted = Object.keys(suggested).reduce((acc, framework) => {
                acc[framework] = true;
                return acc;
            }, {});
            setLlmRuleCompliance({ suggested, accepted });
            // Update parent llmCompliance with framework names only
            const acceptedFrameworks = Object.keys(accepted);
            setLlmCompliance(acceptedFrameworks.length > 0 ? acceptedFrameworks : []);
        } catch (error) {
            console.error('Error fetching compliance suggestions:', error);
            setLlmRuleCompliance({ suggested: {}, accepted: {} });
        }
    };

    const handleLlmRuleChange = (value) => {
        setLlmRule(value);
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (!value || value.trim().length < 10 || !enableLlmComplianceMapping) {
            setLlmRuleCompliance({ loading: false, suggested: {}, accepted: {} });
            return;
        }
        debounceRef.current = setTimeout(() => fetchLlmCompliance(value.trim()), 1500);
    };

    const toggleLlmFramework = (framework) => {
        const currentEntry = llmRuleCompliance;
        const isAccepted = !!currentEntry.accepted[framework];
        const newAccepted = { ...currentEntry.accepted };
        if (isAccepted) {
            delete newAccepted[framework];
        } else {
            newAccepted[framework] = true;
        }
        // Update compliance state
        setLlmRuleCompliance({ ...currentEntry, accepted: newAccepted });
        // Update parent llmCompliance with framework names only
        setLlmCompliance(Object.keys(newAccepted).length > 0 ? Object.keys(newAccepted) : []);
    };

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
            <OwaspTag stepNumber={6} />

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
                                    onChange={handleLlmRuleChange}
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

                                <Box>
                                    <Tooltip content="Automatically map this rule to relevant compliance frameworks (GDPR, HIPAA, PCI DSS, ISO 27001, etc.) using AI. This helps you understand which regulations and standards your guardrail policies help satisfy, making compliance audits and policy documentation easier.">
                                        <Checkbox
                                            label="Enable LLM-based compliance mapping"
                                            checked={enableLlmComplianceMapping}
                                            onChange={setEnableLlmComplianceMapping}
                                            helpText="AI will suggest which compliance frameworks this rule relates to"
                                        />
                                    </Tooltip>
                                </Box>

                                {Object.keys(llmRuleCompliance.suggested).length > 0 && (
                                    <Box>
                                        <VerticalStack gap="2">
                                            <Text variant="bodySm" fontWeight="medium">Compliance mappings:</Text>
                                            <HorizontalStack gap="2" wrap>
                                                {Object.keys(llmRuleCompliance.accepted).map(framework => (
                                                    <Tag
                                                        key={framework}
                                                        onRemove={() => toggleLlmFramework(framework)}
                                                    >
                                                        {framework}
                                                    </Tag>
                                                ))}
                                            </HorizontalStack>
                                        </VerticalStack>
                                    </Box>
                                )}
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
