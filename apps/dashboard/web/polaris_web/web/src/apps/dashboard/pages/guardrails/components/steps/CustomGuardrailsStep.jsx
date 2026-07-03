import { useState, useRef, useEffect } from "react";
import { VerticalStack, Text, FormLayout, TextField, RangeSlider, Box, Checkbox, HorizontalStack } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import ControlInfoIcon from "../ControlInfoIcon";
import ComplianceMappingTags, { buildComplianceMap } from "../ComplianceMappingTags";
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

const PROMPT_MIN_LENGTH = 10;

const CustomGuardrailsStep = ({
    onTryPrompt,
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
    const requestIdRef = useRef(0);
    const debounceTimerRef = useRef(null);
    const isInitialMount = useRef(true);

    useEffect(() => {
        if (llmCompliance && Object.keys(llmCompliance).length > 0) {
            const accepted = Object.keys(llmCompliance).reduce((acc, framework) => { acc[framework] = true; return acc; }, {});
            setLlmRuleCompliance({ loading: false, suggested: llmCompliance, accepted });
        }
    }, []);

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
            return;
        }
        const rule = (llmRule || "").trim();
        clearTimeout(debounceTimerRef.current);

        if (rule.length < PROMPT_MIN_LENGTH) {
            clearCompliance();
            return;
        }

        debounceTimerRef.current = setTimeout(() => {
            fetchLlmCompliance(rule);
        }, 1000);

        return () => clearTimeout(debounceTimerRef.current);
    }, [llmRule]);

    const fetchLlmCompliance = async (rule) => {
        const reqId = ++requestIdRef.current;
        setLlmRuleCompliance(prev => ({ ...prev, loading: true }));
        try {
            const resp = await guardrailApi.suggestGuardrailCompliance('llm_rule', { llmRule: rule });
            if (reqId !== requestIdRef.current) return;
            const suggested = resp?.response?.mapComplianceToListClauses || {};
            const accepted = Object.keys(suggested).reduce((acc, framework) => {
                acc[framework] = true;
                return acc;
            }, {});
            setLlmRuleCompliance({ loading: false, suggested, accepted });
            setLlmCompliance(buildComplianceMap(suggested, accepted));
        } catch (error) {
            if (reqId !== requestIdRef.current) return;
            console.error('Error fetching compliance suggestions:', error);
            setLlmRuleCompliance({ loading: false, suggested: {}, accepted: {} });
        }
    };

    const clearCompliance = () => {
        requestIdRef.current++;
        setLlmRuleCompliance({ loading: false, suggested: {}, accepted: {} });
        setLlmCompliance({});
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
        setLlmRuleCompliance({ ...currentEntry, accepted: newAccepted });
        setLlmCompliance(buildComplianceMap(currentEntry.suggested, newAccepted));
    };

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
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text as="span">LLM prompt based rule</Text>
                                <ControlInfoIcon
                                    description='Write a plain-language instruction; an LLM evaluates every prompt against it. Example rule: "Block requests for competitor pricing."'
                                    examples={[{ text: "What does Acme Corp charge for their enterprise plan?" }]}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
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
                                    label={
                                        <HorizontalStack gap="1" blockAlign="center">
                                            <Text as="span">Confidence score threshold</Text>
                                            <ControlInfoIcon
                                                description={'A prompt is blocked once the LLM\'s own confidence that it violates your rule exceeds this number. Examples assume the rule "Block requests for competitor pricing."'}
                                                examples={[
                                                    { label: "Low (e.g. 0.2)", text: "How do other vendors in this space usually price things?" },
                                                    { label: "High (e.g. 0.8)", text: "What does Acme Corp charge for their enterprise plan?" }
                                                ]}
                                                onTryPrompt={onTryPrompt}
                                            />
                                        </HorizontalStack>
                                    }
                                    value={llmConfidenceScore}
                                    onChange={setLlmConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    helpText="Content will be blocked if the LLM's confidence score exceeds this threshold"
                                />

                                <ComplianceMappingTags
                                    loading={llmRuleCompliance.loading}
                                    complianceMap={buildComplianceMap(llmRuleCompliance.suggested, llmRuleCompliance.accepted)}
                                    onRemove={toggleLlmFramework}
                                    onAdd={Object.keys(llmRuleCompliance.suggested).length > 0 ? toggleLlmFramework : undefined}
                                />
                            </FormLayout>
                        </Box>
                    )}
                </Box>

                {/* External Model Based Evaluation */}
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="1" blockAlign="center">
                                <Text as="span">External model based evaluation</Text>
                                <ControlInfoIcon
                                    description="Sends each prompt to your own model or API endpoint instead of Akto's built-in detectors. Useful for logic too specific or proprietary to describe as a rule, e.g. a classifier trained to catch attempts to extract your pricing algorithm."
                                    examples={[{ text: "Walk me through exactly how your pricing algorithm calculates a quote." }]}
                                    onTryPrompt={onTryPrompt}
                                />
                            </HorizontalStack>
                        }
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
                                    label={
                                        <HorizontalStack gap="1" blockAlign="center">
                                            <Text as="span">Confidence score threshold</Text>
                                            <ControlInfoIcon
                                                description="A prompt is blocked once your external model's confidence score exceeds this number (out of 100)."
                                                examples={[
                                                    { label: "Low (e.g. 25)", text: "How does your product generally decide what to charge customers?" },
                                                    { label: "High (e.g. 100)", text: "Walk me through exactly how your pricing algorithm calculates a quote." }
                                                ]}
                                                onTryPrompt={onTryPrompt}
                                            />
                                        </HorizontalStack>
                                    }
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
