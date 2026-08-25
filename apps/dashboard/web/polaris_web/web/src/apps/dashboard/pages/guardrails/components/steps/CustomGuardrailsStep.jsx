import { useState, useRef, useEffect } from "react";
import { VerticalStack, Text, FormLayout, TextField, Box, Checkbox, HorizontalStack, Badge, Banner } from "@shopify/polaris";
import OwaspTag from "../OwaspTag";
import ControlInfoIcon from "../ControlInfoIcon";
import ComplianceMappingTags, { buildComplianceMap } from "../ComplianceMappingTags";
import guardrailApi from "../../api";
import { CUSTOM_GUARDRAILS_DESCRIPTIONS } from "../../guardrailDescriptions";

// URL validation function
const validateUrl = (url) => {
    const urlPattern = /^https?:\/\/(www\.)?[-a-zA-Z0-9@:%._\+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b([-a-zA-Z0-9()@:%_\+.~#?&//=]*)$/;
    return urlPattern.test(url);
};

const PROMPT_MIN_LENGTH = 10;

export const newRedactionRule = () => ({
    enabled: true,
    userPrompt: "",
    confidenceScore: 0.5
});

const validateRedactionRules = (rules) => {
    for (const rule of rules || []) {
        if (!rule.enabled) continue;
        const prompt = (rule.userPrompt || "").trim();
        if (prompt.length === 0) {
            return "Redaction instruction cannot be empty. Remove the rule or describe what to redact.";
        }
        if (prompt.length < PROMPT_MIN_LENGTH) {
            return `Redaction instruction is too short. Use at least ${PROMPT_MIN_LENGTH} characters so the model can act on it.`;
        }
    }
    return null;
};

export const CustomGuardrailsConfig = {
    number: 6,
    title: "Custom Guardrails",

    validate: ({ enableExternalModel, url, enableLlmRedaction, redactionRules }) => {
        const hasUrl = enableExternalModel && url && url.trim().length > 0;
        const urlError = hasUrl && !validateUrl(url.trim()) ? "Invalid URL format. Must be a valid http or https URL" : null;
        const redactionError = enableLlmRedaction ? validateRedactionRules(redactionRules) : null;
        const errorMessage = urlError || redactionError;

        return {
            isValid: !errorMessage,
            errorMessage
        };
    },

    getSummary: ({ enableLlmPrompt, llmPrompt, enableExternalModel, url, enableLlmRedaction, redactionRules }) => {
        const summaries = [];
        if (enableLlmPrompt && llmPrompt) {
            summaries.push(`LLM: ${llmPrompt.substring(0, 20)}${llmPrompt.length > 20 ? '...' : ''}`);
        }
        if (enableLlmRedaction) {
            const active = (redactionRules || []).filter(r => r.enabled && (r.userPrompt || "").trim());
            if (active.length === 1) {
                const p = active[0].userPrompt.trim();
                summaries.push(`Redact: ${p.substring(0, 20)}${p.length > 20 ? '...' : ''}`);
            } else if (active.length > 1) {
                summaries.push(`Redact: ${active.length} rules`);
            }
        }
        if (enableExternalModel && url) {
            summaries.push(`External: ${url.substring(0, 20)}${url.length > 20 ? '...' : ''}`);
        }
        return summaries.length > 0 ? summaries.join(', ') : null;
    }
};

const CustomGuardrailsStep = ({
    onTryPrompt,
    // LLM prompt based rule
    enableLlmPrompt,
    setEnableLlmPrompt,
    llmRule,
    setLlmRule,
    // LLM rule compliance (controlled by parent)
    llmCompliance,
    setLlmCompliance,
    // LLM prompt based redaction
    enableLlmRedaction,
    setEnableLlmRedaction,
    redactionRules,
    setRedactionRules,
    // External model based evaluation
    enableExternalModel,
    setEnableExternalModel,
    url,
    setUrl
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

    // One redaction instruction per policy — create a separate policy for another.
    // The stored field stays a list so any additional entries a policy already
    // carries (e.g. pushed by the GitHub workflow source) survive an edit here
    // untouched rather than being dropped on save.
    const redactionRule = (redactionRules || [])[0] || newRedactionRule();

    const updateRedactionRule = (patch) => {
        const current = redactionRules || [];
        setRedactionRules(current.length === 0
            ? [{ ...newRedactionRule(), ...patch }]
            : current.map((rule, i) => (i === 0 ? { ...rule, ...patch } : rule)));
    };

    const handleEnableLlmRedaction = (checked) => {
        setEnableLlmRedaction(checked);
        if (checked && (redactionRules || []).length === 0) {
            setRedactionRules([newRedactionRule()]);
        }
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
                                    {...CUSTOM_GUARDRAILS_DESCRIPTIONS.llmPromptRule}
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

                {/* LLM Prompt Based Redaction */}
                <Box>
                    <Checkbox
                        label={
                            <HorizontalStack gap="2" blockAlign="center">
                                <HorizontalStack gap="1" blockAlign="center">
                                    <Text as="span">LLM based redaction</Text>
                                    <ControlInfoIcon
                                        {...CUSTOM_GUARDRAILS_DESCRIPTIONS.llmRedactionRule}
                                        onTryPrompt={onTryPrompt}
                                    />
                                </HorizontalStack>
                                <Badge status="info">Beta</Badge>
                            </HorizontalStack>
                        }
                        checked={enableLlmRedaction}
                        onChange={handleEnableLlmRedaction}
                        helpText="Describe what to mask in plain language. Matching text is replaced in place and the request is allowed through, instead of being blocked."
                    />
                    {enableLlmRedaction && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="4">
                                <Banner>
                                    Requires Akto browser extension v1.0.69 or later. Currently
                                    supported only via the browser extension. Endpoint Shield
                                    Agent support is coming soon.
                                </Banner>
                                <FormLayout>
                                    <TextField
                                        label="Redaction instruction"
                                        value={redactionRule.userPrompt}
                                        onChange={(value) => updateRedactionRule({ userPrompt: value })}
                                        multiline={3}
                                        placeholder="e.g. Redact customer full names and home addresses"
                                        helpText="Be specific about what to mask. Anything not described here is left untouched. To redact a second, unrelated category, create another policy."
                                    />
                                </FormLayout>
                            </VerticalStack>
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
                                    {...CUSTOM_GUARDRAILS_DESCRIPTIONS.externalModel}
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
                            </FormLayout>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default CustomGuardrailsStep;
