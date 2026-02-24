import { useState, useEffect, useRef } from "react";
import {
    Modal,
    Text,
    LegacyCard,
    HorizontalStack,
    VerticalStack,
    Box,
    Icon,
    Scrollable
} from "@shopify/polaris";
import {
    ChecklistMajor,
    AlertMinor
} from "@shopify/polaris-icons";
import PersistStore from '../../../../main/PersistStore';
import {
    PolicyDetailsStep,
    PolicyDetailsConfig,
    ContentPolicyStep,
    ContentPolicyConfig,
    LanguageSafetyStep,
    LanguageSafetyConfig,
    SensitiveInfoStep,
    SensitiveInfoConfig,
    CodeDetectionStep,
    CodeDetectionConfig,
    CustomGuardrailsStep,
    CustomGuardrailsConfig,
    UsageGuardrailsStep,
    UsageGuardrailsConfig,
    AnomalyDetectionStep,
    AnomalyDetectionConfig,
    ServerSettingsStep,
    ServerSettingsConfig
} from './steps';

const CreateGuardrailModal = ({ isOpen, onClose, onSave, editingPolicy = null, isEditMode = false }) => {
    // Step management
    const [currentStep, setCurrentStep] = useState(1);
    const [loading, setLoading] = useState(false);

    // Step 1: Guardrail details
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [blockedMessage, setBlockedMessage] = useState("");
    const [severity, setSeverity] = useState("HIGH");
    const [applyToResponses, setApplyToResponses] = useState(false);

    // Step 2: Content & Policy Guardrails
    const [enablePromptAttacks, setEnablePromptAttacks] = useState(false);
    const [promptAttackLevel, setPromptAttackLevel] = useState("high");
    const [enableDeniedTopics, setEnableDeniedTopics] = useState(false);
    const [deniedTopics, setDeniedTopics] = useState([]);
    const [enableHarmfulCategories, setEnableHarmfulCategories] = useState(false);
    const [harmfulCategoriesSettings, setHarmfulCategoriesSettings] = useState({
        hate: "HIGH",
        insults: "HIGH",
        sexual: "HIGH",
        violence: "HIGH",
        misconduct: "HIGH",
        useForResponses: false
    });
    const [enableBasePromptRule, setEnableBasePromptRule] = useState(false);
    const [basePromptConfidenceScore, setBasePromptConfidenceScore] = useState(0.5);

    // Step 3: Language Safety & Abuse Guardrails
    const [enableGibberishDetection, setEnableGibberishDetection] = useState(false);
    const [gibberishConfidenceScore, setGibberishConfidenceScore] = useState(0.7);
    const [enableSentiment, setEnableSentiment] = useState(false);
    const [sentimentConfidenceScore, setSentimentConfidenceScore] = useState(0.7);
    const [wordFilters, setWordFilters] = useState({
        profanity: false,
        custom: []
    });
    const [newCustomWord, setNewCustomWord] = useState("");

    // Step 4: Sensitive Information Guardrails
    const [enablePiiTypes, setEnablePiiTypes] = useState(false);
    const [piiTypes, setPiiTypes] = useState([]);
    const [enableRegexPatterns, setEnableRegexPatterns] = useState(false);
    const [regexPatterns, setRegexPatterns] = useState([]);
    const [newRegexPattern, setNewRegexPattern] = useState("");
    const [enableSecrets, setEnableSecrets] = useState(false);
    const [secretsConfidenceScore, setSecretsConfidenceScore] = useState(0.7);
    const [enableAnonymize, setEnableAnonymize] = useState(false);
    const [anonymizeConfidenceScore, setAnonymizeConfidenceScore] = useState(0.7);

    // Step 5: Advanced Code Detection Filters
    const [enableCodeFilter, setEnableCodeFilter] = useState(false);
    const [codeFilterLevel, setCodeFilterLevel] = useState("high");
    const [enableBanCode, setEnableBanCode] = useState(false);
    const [banCodeConfidenceScore, setBanCodeConfidenceScore] = useState(0.7);

    // Step 6: Custom Guardrails
    const [enableLlmPrompt, setEnableLlmPrompt] = useState(false);
    const [llmPrompt, setLlmPrompt] = useState("");
    const [llmConfidenceScore, setLlmConfidenceScore] = useState(0.5);
    const [enableExternalModel, setEnableExternalModel] = useState(false);
    const [url, setUrl] = useState("");
    const [confidenceScore, setConfidenceScore] = useState(25);

    // Step 7: Usage based Guardrails
    const [enableTokenLimit, setEnableTokenLimit] = useState(false);
    const [tokenLimitConfidenceScore, setTokenLimitConfidenceScore] = useState(0.7);

    // Step 8: Anomaly Detection (coming soon)

    // Step 9: Server settings
    const [selectedMcpServers, setSelectedMcpServers] = useState([]);
    const [selectedAgentServers, setSelectedAgentServers] = useState([]);
    const [applyOnResponse, setApplyOnResponse] = useState(false);
    const [applyOnRequest, setApplyOnRequest] = useState(false);
    
    // Collections data
    const [mcpServers, setMcpServers] = useState([]);
    const [agentServers, setAgentServers] = useState([]);
    const [collectionsLoading, setCollectionsLoading] = useState(false);
    
    // Get collections from PersistStore
    const allCollections = PersistStore(state => state.allCollections);
    
    // Create validation state object
    const getStoredStateData = () => ({
        // Step 1
        name,
        blockedMessage,
        description,
        // Step 2
        enablePromptAttacks,
        promptAttackLevel,
        enableDeniedTopics,
        deniedTopics,
        enableHarmfulCategories,
        harmfulCategoriesSettings,
        enableBasePromptRule,
        basePromptConfidenceScore,
        // Step 3
        enableGibberishDetection,
        gibberishConfidenceScore,
        enableSentiment,
        sentimentConfidenceScore,
        wordFilters,
        // Step 4
        enablePiiTypes,
        piiTypes,
        enableRegexPatterns,
        regexPatterns,
        enableSecrets,
        secretsConfidenceScore,
        enableAnonymize,
        anonymizeConfidenceScore,
        // Step 5
        enableCodeFilter,
        codeFilterLevel,
        enableBanCode,
        banCodeConfidenceScore,
        // Step 6
        enableLlmPrompt,
        llmPrompt,
        llmConfidenceScore,
        enableExternalModel,
        url,
        confidenceScore,
        // Step 7
        enableTokenLimit,
        tokenLimitConfidenceScore,
        // Step 9
        selectedMcpServers,
        selectedAgentServers,
        mcpServers,
        agentServers,
        applyOnRequest,
        applyOnResponse
    });

    const getStepsWithSummary = () => {
        const storedStateData = getStoredStateData();

        return [
            {
                number: PolicyDetailsConfig.number,
                title: PolicyDetailsConfig.title,
                summary: PolicyDetailsConfig.getSummary(storedStateData),
                ...PolicyDetailsConfig.validate(storedStateData)
            },
            {
                number: ContentPolicyConfig.number,
                title: ContentPolicyConfig.title,
                summary: ContentPolicyConfig.getSummary(storedStateData),
                ...ContentPolicyConfig.validate(storedStateData)
            },
            {
                number: LanguageSafetyConfig.number,
                title: LanguageSafetyConfig.title,
                summary: LanguageSafetyConfig.getSummary(storedStateData),
                ...LanguageSafetyConfig.validate(storedStateData)
            },
            {
                number: SensitiveInfoConfig.number,
                title: SensitiveInfoConfig.title,
                summary: SensitiveInfoConfig.getSummary(storedStateData),
                ...SensitiveInfoConfig.validate(storedStateData)
            },
            {
                number: CodeDetectionConfig.number,
                title: CodeDetectionConfig.title,
                summary: CodeDetectionConfig.getSummary(storedStateData),
                ...CodeDetectionConfig.validate(storedStateData)
            },
            {
                number: CustomGuardrailsConfig.number,
                title: CustomGuardrailsConfig.title,
                summary: CustomGuardrailsConfig.getSummary(storedStateData),
                ...CustomGuardrailsConfig.validate(storedStateData)
            },
            {
                number: UsageGuardrailsConfig.number,
                title: UsageGuardrailsConfig.title,
                summary: UsageGuardrailsConfig.getSummary(storedStateData),
                ...UsageGuardrailsConfig.validate(storedStateData)
            },
            {
                number: AnomalyDetectionConfig.number,
                title: AnomalyDetectionConfig.title,
                summary: AnomalyDetectionConfig.getSummary(storedStateData),
                ...AnomalyDetectionConfig.validate(storedStateData)
            },
            {
                number: ServerSettingsConfig.number,
                title: ServerSettingsConfig.title,
                summary: ServerSettingsConfig.getSummary(storedStateData),
                ...ServerSettingsConfig.validate(storedStateData)
            }
        ];
    };

    const steps = getStepsWithSummary();

    // Filter collections when modal opens or allCollections changes
    useEffect(() => {
        if (isOpen) {
            if (allCollections && allCollections.length > 0) {
                filterCollections();
            } else {
                // Set empty arrays if no collections available
                setMcpServers([]);
                setAgentServers([]);
                setCollectionsLoading(false);
            }
        }
    }, [isOpen, allCollections]);

    // Populate form when editing
    useEffect(() => {
        if (isOpen && isEditMode && editingPolicy) {
            populateFormForEdit(editingPolicy);
        } else if (isOpen && !isEditMode) {
            resetForm();
        }
    }, [isOpen, isEditMode, editingPolicy]);

    const filterCollections = () => {
        setCollectionsLoading(true);
        try {
            const mcpServerCollections = allCollections.filter(collection => {
                const hasMcpEnvType = collection.envType && collection.envType.some(envType =>
                    envType.keyName === 'mcp-server' && envType.value === 'MCP Server'
                );
                return hasMcpEnvType;
            })
            .sort((a, b) => (b.startTs || 0) - (a.startTs || 0))
            .map(collection => ({
                label: collection.displayName,
                value: collection.id.toString()
            }));


            const agentServerCollections = allCollections.filter(collection => {
                const hasGenAiEnvType = collection.envType && collection.envType.some(envType =>
                    envType.keyName === 'gen-ai' && envType.value === 'Gen AI'
                );
                return hasGenAiEnvType;
            })
            .sort((a, b) => (b.startTs || 0) - (a.startTs || 0))
            .map(collection => ({
                label: collection.displayName,
                value: collection.id.toString()
            }));

            setMcpServers(mcpServerCollections);
            setAgentServers(agentServerCollections);
        } catch (error) {
            console.error("Error filtering collections:", error);
        } finally {
            setCollectionsLoading(false);
        }
    };

    const resetForm = () => {
        setCurrentStep(1);
        setName("");
        setDescription("");
        setBlockedMessage("");
        setSeverity("HIGH");
        setApplyToResponses(false);
        setEnablePromptAttacks(false);
        setPromptAttackLevel("high");
        setEnableDeniedTopics(false);
        setDeniedTopics([]);
        setEnableHarmfulCategories(false);
        setHarmfulCategoriesSettings({
            hate: "HIGH",
            insults: "HIGH",
            sexual: "HIGH",
            violence: "HIGH",
            misconduct: "HIGH",
            useForResponses: false
        });
        setEnableBasePromptRule(false);
        setBasePromptConfidenceScore(0.5);
        setEnableGibberishDetection(false);
        setGibberishConfidenceScore(0.7);
        setEnableSentiment(false);
        setSentimentConfidenceScore(0.7);
        setWordFilters({
            profanity: false,
            custom: []
        });
        setNewCustomWord("");
        setEnablePiiTypes(false);
        setPiiTypes([]);
        setEnableRegexPatterns(false);
        setRegexPatterns([]);
        setNewRegexPattern("");
        setEnableSecrets(false);
        setSecretsConfidenceScore(0.7);
        setEnableAnonymize(false);
        setAnonymizeConfidenceScore(0.7);
        setEnableCodeFilter(false);
        setCodeFilterLevel("high");
        setEnableBanCode(false);
        setBanCodeConfidenceScore(0.7);
        setEnableLlmPrompt(false);
        setLlmPrompt("");
        setLlmConfidenceScore(0.5);
        setEnableExternalModel(false);
        setUrl("");
        setConfidenceScore(25);
        setEnableTokenLimit(false);
        setTokenLimitConfidenceScore(0.7);
        setSelectedMcpServers([]);
        setSelectedAgentServers([]);
        setApplyOnResponse(false);
        setApplyOnRequest(false);
    };

    const populateFormForEdit = (policy) => {
        setName(policy.name || "");
        setDescription(policy.description || "");
        setBlockedMessage(policy.blockedMessage || "");
        setSeverity(policy.severity ? policy.severity.toUpperCase() : "HIGH");
        setApplyToResponses(policy.applyToResponses || false);
        
        // Content filters
        if (policy.contentFiltering) {
            if (policy.contentFiltering.harmfulCategories) {
                setEnableHarmfulCategories(true);
                setHarmfulCategoriesSettings({
                    hate: policy.contentFiltering.harmfulCategories.hate || "HIGH",
                    insults: policy.contentFiltering.harmfulCategories.insults || "HIGH",
                    sexual: policy.contentFiltering.harmfulCategories.sexual || "HIGH",
                    violence: policy.contentFiltering.harmfulCategories.violence || "HIGH",
                    misconduct: policy.contentFiltering.harmfulCategories.misconduct || "HIGH",
                    useForResponses: policy.contentFiltering.harmfulCategories.useForResponses || false
                });
            }
            if (policy.contentFiltering.promptAttacks) {
                setEnablePromptAttacks(true);
                const level = policy.contentFiltering.promptAttacks.level || "high";
                setPromptAttackLevel(level.toLowerCase());
            }
            if (policy.contentFiltering.code) {
                setEnableCodeFilter(true);
                if (typeof policy.contentFiltering.code === 'object' && policy.contentFiltering.code.level) {
                    const level = policy.contentFiltering.code.level || "high";
                    setCodeFilterLevel(level.toLowerCase());
                } else {
                    setCodeFilterLevel("high");
                }
            }
        }
        
        // Denied topics
        const hasDeniedTopics = policy.deniedTopics && policy.deniedTopics.length > 0;
        setEnableDeniedTopics(hasDeniedTopics);
        setDeniedTopics(policy.deniedTopics || []);
        
        // Word filters
        setWordFilters({
            profanity: policy.wordFilters?.profanity || false,
            custom: policy.wordFilters?.custom || []
        });
        
        // PII filters
        const hasPiiTypes = policy.piiTypes && policy.piiTypes.length > 0;
        setEnablePiiTypes(hasPiiTypes);
        setPiiTypes(policy.piiTypes || []);
        
        // Regex patterns
        let hasRegexPatterns = false;
        if (policy.regexPatternsV2 && policy.regexPatternsV2.length > 0) {
            hasRegexPatterns = true;
            setRegexPatterns(policy.regexPatternsV2);
        } else if (policy.regexPatterns && policy.regexPatterns.length > 0) {
            hasRegexPatterns = true;
            const convertedPatterns = policy.regexPatterns.map(pattern => ({
                pattern: pattern,
                behavior: "block"
            }));
            setRegexPatterns(convertedPatterns);
        } else {
            setRegexPatterns([]);
        }
        setEnableRegexPatterns(hasRegexPatterns);

        // LLM prompt
        if (policy.llmRule) {
            setEnableLlmPrompt(policy.llmRule.enabled || !!policy.llmRule.userPrompt);
            setLlmPrompt(policy.llmRule.userPrompt || "");
            setLlmConfidenceScore(policy.llmRule.confidenceScore !== undefined ? policy.llmRule.confidenceScore : 0.5);
        } else {
            setEnableLlmPrompt(false);
            setLlmPrompt("");
            setLlmConfidenceScore(0.5);
        }

        // Base Prompt Based Validation (AI Agents)
        if (policy.basePromptRule) {
            setEnableBasePromptRule(policy.basePromptRule.enabled || false);
            setBasePromptConfidenceScore(policy.basePromptRule.confidenceScore !== undefined ? policy.basePromptRule.confidenceScore : 0.5);
        } else {
            setEnableBasePromptRule(false);
            setBasePromptConfidenceScore(0.5);
        }

        // Gibberish Detection
        if (policy.gibberishDetection) {
            setEnableGibberishDetection(policy.gibberishDetection.enabled || false);
            setGibberishConfidenceScore(policy.gibberishDetection.confidenceScore !== undefined ? policy.gibberishDetection.confidenceScore : 0.7);
        } else {
            setEnableGibberishDetection(false);
            setGibberishConfidenceScore(0.7);
        }

        // Helper function for scanner state
        const setScannerState = (detection, setEnabled, setConfidence) => {
            if (detection) {
                setEnabled(detection.enabled || false);
                setConfidence(detection.confidenceScore !== undefined ? detection.confidenceScore : 0.7);
            } else {
                setEnabled(false);
                setConfidence(0.7);
            }
        };

        setScannerState(policy.anonymizeDetection, setEnableAnonymize, setAnonymizeConfidenceScore);
        setScannerState(policy.banCodeDetection, setEnableBanCode, setBanCodeConfidenceScore);
        setScannerState(policy.secretsDetection, setEnableSecrets, setSecretsConfidenceScore);
        setScannerState(policy.sentimentDetection, setEnableSentiment, setSentimentConfidenceScore);
        setScannerState(policy.tokenLimitDetection, setEnableTokenLimit, setTokenLimitConfidenceScore);

        // External model based evaluation
        const hasExternalModel = !!policy.url;
        setEnableExternalModel(hasExternalModel);
        setUrl(policy.url || "");
        const existingScore = policy.confidenceScore || policy.riskScore || 25;
        const checkpoints = [25, 50, 75, 100];
        const nearestCheckpoint = checkpoints.reduce((prev, curr) =>
            Math.abs(curr - existingScore) < Math.abs(prev - existingScore) ? curr : prev
        );
        setConfidenceScore(nearestCheckpoint);

        // Server settings
        if (policy.selectedMcpServersV2 && policy.selectedMcpServersV2.length > 0) {
            setSelectedMcpServers(policy.selectedMcpServersV2.map(server => server.id));
        } else {
            setSelectedMcpServers(policy.selectedMcpServers || []);
        }

        if (policy.selectedAgentServersV2 && policy.selectedAgentServersV2.length > 0) {
            setSelectedAgentServers(policy.selectedAgentServersV2.map(server => server.id));
        } else {
            setSelectedAgentServers(policy.selectedAgentServers || []);
        }
        setApplyOnResponse(policy.applyOnResponse || false);
        setApplyOnRequest(policy.applyOnRequest || false);
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    const handleNext = () => {
        if (currentStep < steps.length) {
            setCurrentStep(currentStep + 1);
        }
    };

    const handlePrevious = () => {
        if (currentStep > 1) {
            setCurrentStep(currentStep - 1);
        }
    };

    const handleSave = async () => {
        setLoading(true);
        try {
            // Transform selectedMcpServers and selectedAgentServers to include both ID and name
            const transformedMcpServers = selectedMcpServers
                .filter(serverId => serverId)
                .map(serverId => {
                    const server = mcpServers.find(s => s.value === serverId || s.value === serverId.toString());
                    return {
                        id: serverId.toString(),
                        name: server ? server.label : serverId.toString()
                    };
                });

            const transformedAgentServers = selectedAgentServers
                .filter(serverId => serverId)
                .map(serverId => {
                    const server = agentServers.find(s => s.value === serverId || s.value === serverId.toString());
                    return {
                        id: serverId.toString(),
                        name: server ? server.label : serverId.toString()
                    };
                });

            const guardrailData = {
                name,
                description,
                blockedMessage,
                severity,
                applyToResponses,
                contentFilters: {
                    harmfulCategories: enableHarmfulCategories ? harmfulCategoriesSettings : null,
                    promptAttacks: enablePromptAttacks ? { level: promptAttackLevel.toUpperCase() } : null,
                    code: enableCodeFilter ? { level: codeFilterLevel.toUpperCase() } : null
                },
                deniedTopics,
                wordFilters,
                piiFilters: piiTypes,
                regexPatterns: regexPatterns
                    .filter(r => r && r.pattern)
                    .map(r => r.pattern),
                regexPatternsV2: regexPatterns
                    .filter(r => r && r.pattern && r.behavior)
                    .map(r => ({
                        pattern: r.pattern,
                        behavior: r.behavior.toLowerCase()
                    })),
                ...(enableLlmPrompt && llmPrompt && llmPrompt.trim() ? {
                    llmRule: {
                        enabled: true,
                        userPrompt: llmPrompt.trim(),
                        confidenceScore: llmConfidenceScore
                    }
                } : {}),
                ...(enableBasePromptRule ? {
                    basePromptRule: {
                        enabled: true,
                        confidenceScore: basePromptConfidenceScore
                    }
                } : {}),
                gibberishDetection: {
                    enabled: enableGibberishDetection,
                    confidenceScore: gibberishConfidenceScore
                },
                anonymizeDetection: {
                    enabled: enableAnonymize,
                    confidenceScore: anonymizeConfidenceScore
                },
                banCodeDetection: {
                    enabled: enableBanCode,
                    confidenceScore: banCodeConfidenceScore
                },
                secretsDetection: {
                    enabled: enableSecrets,
                    confidenceScore: secretsConfidenceScore
                },
                sentimentDetection: {
                    enabled: enableSentiment,
                    confidenceScore: sentimentConfidenceScore
                },
                tokenLimitDetection: {
                    enabled: enableTokenLimit,
                    confidenceScore: tokenLimitConfidenceScore
                },
                url: enableExternalModel ? (url || null) : null,
                confidenceScore: enableExternalModel ? confidenceScore : null,
                selectedMcpServers: selectedMcpServers,
                selectedAgentServers: selectedAgentServers,
                selectedMcpServersV2: transformedMcpServers,
                selectedAgentServersV2: transformedAgentServers,
                applyOnResponse,
                applyOnRequest,
                ...(isEditMode && editingPolicy ? { hexId: editingPolicy.hexId } : {})
            };

            
            await onSave(guardrailData);
            handleClose();
        } catch (error) {
            console.error("Error creating guardrail:", error);
        } finally {
            setLoading(false);
        }
    };


    const stepRefs = useRef({});

    // Auto-scroll to current step when it changes
    useEffect(() => {
        if (currentStep && stepRefs.current[currentStep]) {
            stepRefs.current[currentStep].scrollIntoView({
                behavior: 'smooth',
                block: 'nearest'
            });
        }
    }, [currentStep]);

    const handleStepClick = (stepNumber) => {
        if (stepNumber === currentStep) {
            setCurrentStep(null);
        } else {
            setCurrentStep(stepNumber);
        }
    };

    const renderAllSteps = () => (
        <VerticalStack gap="2">
            {steps.map((step) => (
                <Box
                    key={step.number}
                    ref={(el) => stepRefs.current[step.number] = el}
                >
                    <LegacyCard sectioned>
                        <Box
                            style={{
                                backgroundColor: step.number === currentStep ? "#f6f6f7" : "transparent",
                                margin: "-16px",
                                padding: "16px",
                                borderRadius: "8px"
                            }}
                        >
                            <VerticalStack gap="3">
                                <Box
                                    style={{ cursor: "pointer" }}
                                    onClick={() => handleStepClick(step.number)}
                                >
                                    <HorizontalStack gap="3" blockAlign="center">
                                        <Box style={{
                                            width: "24px",
                                            height: "24px",
                                            borderRadius: "50%",
                                            backgroundColor: step.number === currentStep ? "#0070f3" :
                                                            (!step.isValid && step.number < currentStep) ? "#d72c0d" :
                                                            step.number < currentStep ? "#008060" : "#e1e3e5",
                                            color: step.number <= currentStep || !step.isValid ? "white" : "#6d7175",
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "center",
                                            fontSize: "12px",
                                            fontWeight: "bold",
                                            flexShrink: 0
                                        }}>
                                            {(!step.isValid && step.number < currentStep) ? <Icon source={AlertMinor} /> :
                                             step.number < currentStep ? <Icon source={ChecklistMajor} /> : step.number}
                                        </Box>
                                        <Box style={{ flexGrow: 1 }}>
                                            <VerticalStack gap="1">
                                                <HorizontalStack gap="2" blockAlign="center">
                                                    <Text
                                                        variant="bodyMd"
                                                        color={step.number === currentStep ? "success" : "subdued"}
                                                        fontWeight={step.number === currentStep ? "bold" : "regular"}
                                                    >
                                                        {step.title}
                                                    </Text>
                                                    {!step.isValid && step.number !== currentStep && (
                                                        <Icon source={AlertMinor} color="critical" />
                                                    )}
                                                </HorizontalStack>
                                                {step.number !== currentStep && (
                                                    <>
                                                        {step.summary && (
                                                            <Text variant="bodySm" color="subdued" fontWeight="medium">
                                                                {step.summary}
                                                            </Text>
                                                        )}
                                                        {!step.isValid && step.errorMessage && (
                                                            <Text variant="bodySm" color="critical" fontWeight="medium">
                                                                {step.errorMessage}
                                                            </Text>
                                                        )}
                                                    </>
                                                )}
                                            </VerticalStack>
                                        </Box>
                                    </HorizontalStack>
                                </Box>

                                {step.number === currentStep && (
                                    <Box paddingBlockStart="2">
                                        {renderStepContent(step.number)}
                                    </Box>
                                )}
                            </VerticalStack>
                        </Box>
                    </LegacyCard>
                </Box>
            ))}
        </VerticalStack>
    );

    const renderStepContent = (stepNumber) => {
        switch (stepNumber) {
            case 1:
                return (
                    <PolicyDetailsStep
                        name={name}
                        setName={setName}
                        description={description}
                        setDescription={setDescription}
                        blockedMessage={blockedMessage}
                        setBlockedMessage={setBlockedMessage}
                        severity={severity}
                        setSeverity={setSeverity}
                        applyToResponses={applyToResponses}
                        setApplyToResponses={setApplyToResponses}
                    />
                );
            case 2:
                return (
                    <ContentPolicyStep
                        enablePromptAttacks={enablePromptAttacks}
                        setEnablePromptAttacks={setEnablePromptAttacks}
                        promptAttackLevel={promptAttackLevel}
                        setPromptAttackLevel={setPromptAttackLevel}
                        enableDeniedTopics={enableDeniedTopics}
                        setEnableDeniedTopics={setEnableDeniedTopics}
                        deniedTopics={deniedTopics}
                        setDeniedTopics={setDeniedTopics}
                        enableHarmfulCategories={enableHarmfulCategories}
                        setEnableHarmfulCategories={setEnableHarmfulCategories}
                        harmfulCategoriesSettings={harmfulCategoriesSettings}
                        setHarmfulCategoriesSettings={setHarmfulCategoriesSettings}
                        enableBasePromptRule={enableBasePromptRule}
                        setEnableBasePromptRule={setEnableBasePromptRule}
                        basePromptConfidenceScore={basePromptConfidenceScore}
                        setBasePromptConfidenceScore={setBasePromptConfidenceScore}
                    />
                );
            case 3:
                return (
                    <LanguageSafetyStep
                        enableGibberishDetection={enableGibberishDetection}
                        setEnableGibberishDetection={setEnableGibberishDetection}
                        gibberishConfidenceScore={gibberishConfidenceScore}
                        setGibberishConfidenceScore={setGibberishConfidenceScore}
                        enableSentiment={enableSentiment}
                        setEnableSentiment={setEnableSentiment}
                        sentimentConfidenceScore={sentimentConfidenceScore}
                        setSentimentConfidenceScore={setSentimentConfidenceScore}
                        wordFilters={wordFilters}
                        setWordFilters={setWordFilters}
                        newCustomWord={newCustomWord}
                        setNewCustomWord={setNewCustomWord}
                    />
                );
            case 4:
                return (
                    <SensitiveInfoStep
                        enablePiiTypes={enablePiiTypes}
                        setEnablePiiTypes={setEnablePiiTypes}
                        piiTypes={piiTypes}
                        setPiiTypes={setPiiTypes}
                        enableRegexPatterns={enableRegexPatterns}
                        setEnableRegexPatterns={setEnableRegexPatterns}
                        regexPatterns={regexPatterns}
                        setRegexPatterns={setRegexPatterns}
                        newRegexPattern={newRegexPattern}
                        setNewRegexPattern={setNewRegexPattern}
                        enableSecrets={enableSecrets}
                        setEnableSecrets={setEnableSecrets}
                        secretsConfidenceScore={secretsConfidenceScore}
                        setSecretsConfidenceScore={setSecretsConfidenceScore}
                        enableAnonymize={enableAnonymize}
                        setEnableAnonymize={setEnableAnonymize}
                        anonymizeConfidenceScore={anonymizeConfidenceScore}
                        setAnonymizeConfidenceScore={setAnonymizeConfidenceScore}
                    />
                );
            case 5:
                return (
                    <CodeDetectionStep
                        enableCodeFilter={enableCodeFilter}
                        setEnableCodeFilter={setEnableCodeFilter}
                        codeFilterLevel={codeFilterLevel}
                        setCodeFilterLevel={setCodeFilterLevel}
                        enableBanCode={enableBanCode}
                        setEnableBanCode={setEnableBanCode}
                        banCodeConfidenceScore={banCodeConfidenceScore}
                        setBanCodeConfidenceScore={setBanCodeConfidenceScore}
                    />
                );
            case 6:
                return (
                    <CustomGuardrailsStep
                        enableLlmPrompt={enableLlmPrompt}
                        setEnableLlmPrompt={setEnableLlmPrompt}
                        llmRule={llmPrompt}
                        setLlmRule={setLlmPrompt}
                        llmConfidenceScore={llmConfidenceScore}
                        setLlmConfidenceScore={setLlmConfidenceScore}
                        enableExternalModel={enableExternalModel}
                        setEnableExternalModel={setEnableExternalModel}
                        url={url}
                        setUrl={setUrl}
                        confidenceScore={confidenceScore}
                        setConfidenceScore={setConfidenceScore}
                    />
                );
            case 7:
                return (
                    <UsageGuardrailsStep
                        enableTokenLimit={enableTokenLimit}
                        setEnableTokenLimit={setEnableTokenLimit}
                        tokenLimitConfidenceScore={tokenLimitConfidenceScore}
                        setTokenLimitConfidenceScore={setTokenLimitConfidenceScore}
                    />
                );
            case 8:
                return (
                    <AnomalyDetectionStep />
                );
            case 9:
                return (
                    <ServerSettingsStep
                        selectedMcpServers={selectedMcpServers}
                        setSelectedMcpServers={setSelectedMcpServers}
                        selectedAgentServers={selectedAgentServers}
                        setSelectedAgentServers={setSelectedAgentServers}
                        applyOnResponse={applyOnResponse}
                        setApplyOnResponse={setApplyOnResponse}
                        applyOnRequest={applyOnRequest}
                        setApplyOnRequest={setApplyOnRequest}
                        mcpServers={mcpServers}
                        agentServers={agentServers}
                        collectionsLoading={collectionsLoading}
                    />
                );
            default:
                return null;
        }
    };


    const getModalActions = () => {
        const actions = [];

        if (currentStep > 1) {
            actions.push({
                content: "Previous",
                onAction: handlePrevious
            });
        }

        if (currentStep < steps.length) {
            actions.push({
                content: "Next",
                onAction: handleNext
            });
        }

        return actions;
    };

    const getPrimaryAction = () => {
        const allStepsValid = steps.every(step => step.isValid);

            return {
                content: isEditMode ? "Update Guardrail" : "Create Guardrail",
                onAction: handleSave,
                loading: loading,
            disabled: !allStepsValid
        };
    };

    return (
        <>
            <Modal
                open={isOpen}
                onClose={handleClose}
                title={`${isEditMode ? 'Edit' : 'Create'} guardrail`}
                primaryAction={getPrimaryAction()}
                secondaryActions={[
                    {
                        content: "Cancel",
                        onAction: handleClose
                    },
                    ...getModalActions()
                ]}
                large
            >
                <Modal.Section>
                    <Scrollable style={{ height: "600px" }}>
                        {renderAllSteps()}
                            </Scrollable>
                </Modal.Section>
            </Modal>
        </>
    );
};

export default CreateGuardrailModal;
