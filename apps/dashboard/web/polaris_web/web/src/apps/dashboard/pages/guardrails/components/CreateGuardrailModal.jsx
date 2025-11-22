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
    ContentFiltersStep,
    ContentFiltersConfig,
    DeniedTopicsStep,
    DeniedTopicsConfig,
    WordFiltersStep,
    WordFiltersConfig,
    SensitiveInfoStep,
    SensitiveInfoConfig,
    LlmPromptStep,
    LlmPromptConfig,
    ExternalModelStep,
    ExternalModelConfig,
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
    const [applyToResponses, setApplyToResponses] = useState(false);

    // Step 2: Content filters
    const [enableHarmfulCategories, setEnableHarmfulCategories] = useState(false);
    const [harmfulCategoriesSettings, setHarmfulCategoriesSettings] = useState({
        hate: "HIGH",
        insults: "HIGH",
        sexual: "HIGH",
        violence: "HIGH",
        misconduct: "HIGH",
        useForResponses: false
    });
    const [enablePromptAttacks, setEnablePromptAttacks] = useState(false);
    const [promptAttackLevel, setPromptAttackLevel] = useState("HIGH");

    // Step 3: Denied topics
    const [deniedTopics, setDeniedTopics] = useState([]);

    // Step 4: Word filters
    const [wordFilters, setWordFilters] = useState({
        profanity: false,
        custom: []
    });
    const [newCustomWord, setNewCustomWord] = useState("");

    // Step 5: Sensitive information filters
    const [piiTypes, setPiiTypes] = useState([]);
    const [regexPatterns, setRegexPatterns] = useState([]);
    const [newRegexPattern, setNewRegexPattern] = useState("");

    // Step 6: LLM prompt based rule
    const [llmPrompt, setLlmPrompt] = useState("");
    const [llmConfidenceScore, setLlmConfidenceScore] = useState(0.5);

    // Step 7: External model based evaluation
    const [url, setUrl] = useState("");
    const [confidenceScore, setConfidenceScore] = useState(25); // Start with 25 (first checkpoint)

    // Step 8: Server settings
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
        // Step 2
        enableHarmfulCategories,
        enablePromptAttacks,
        // Step 3
        deniedTopics,
        // Step 4
        wordFilters,
        // Step 5
        piiTypes,
        regexPatterns,
        // Step 6
        llmPrompt,
        llmConfidenceScore,
        // Step 7
        url,
        confidenceScore,
        // Step 8
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
                number: ContentFiltersConfig.number,
                title: ContentFiltersConfig.title,
                summary: ContentFiltersConfig.getSummary(storedStateData),
                ...ContentFiltersConfig.validate(storedStateData)
            },
            {
                number: DeniedTopicsConfig.number,
                title: DeniedTopicsConfig.title,
                summary: DeniedTopicsConfig.getSummary(storedStateData),
                ...DeniedTopicsConfig.validate(storedStateData)
            },
            {
                number: WordFiltersConfig.number,
                title: WordFiltersConfig.title,
                summary: WordFiltersConfig.getSummary(storedStateData),
                ...WordFiltersConfig.validate(storedStateData)
            },
            {
                number: SensitiveInfoConfig.number,
                title: SensitiveInfoConfig.title,
                summary: SensitiveInfoConfig.getSummary(storedStateData),
                ...SensitiveInfoConfig.validate(storedStateData)
            },
            {
                number: LlmPromptConfig.number,
                title: LlmPromptConfig.title,
                summary: LlmPromptConfig.getSummary(storedStateData),
                ...LlmPromptConfig.validate(storedStateData)
            },
            {
                number: ExternalModelConfig.number,
                title: ExternalModelConfig.title,
                summary: ExternalModelConfig.getSummary(storedStateData),
                ...ExternalModelConfig.validate(storedStateData)
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
            .sort((a, b) => (b.startTs || 0) - (a.startTs || 0)) // Sort by creation time, latest first
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
            .sort((a, b) => (b.startTs || 0) - (a.startTs || 0)) // Sort by creation time, latest first
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
        setApplyToResponses(false);
        setEnableHarmfulCategories(false);
        setEnablePromptAttacks(false);
        setHarmfulCategoriesSettings({
            hate: "HIGH",
            insults: "HIGH",
            sexual: "HIGH",
            violence: "HIGH",
            misconduct: "HIGH",
            useForResponses: false
        });
        setPromptAttackLevel("HIGH");
        setDeniedTopics([]);
        setWordFilters({
            profanity: false,
            custom: []
        });
        setNewCustomWord("");
        setPiiTypes([]);
        setRegexPatterns([]);
        setNewRegexPattern("");
        setLlmPrompt("");
        setLlmConfidenceScore(0.5);
        setUrl("");
        setConfidenceScore(25);
        setSelectedMcpServers([]);
        setSelectedAgentServers([]);
        setApplyOnResponse(false);
        setApplyOnRequest(false);
    };

    const populateFormForEdit = (policy) => {
        setName(policy.name || "");
        setDescription(policy.description || "");
        setBlockedMessage(policy.blockedMessage || "");
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
                setPromptAttackLevel(policy.contentFiltering.promptAttacks.level || "HIGH");
            }
        }

        // Denied topics
        setDeniedTopics(policy.deniedTopics || []);

        // Word filters
        setWordFilters({
            profanity: policy.wordFilters?.profanity || false,
            custom: policy.wordFilters?.custom || []
        });

        // PII filters
        setPiiTypes(policy.piiTypes || []);

        // Regex patterns - prefer V2 format with behavior, fallback to old format
        if (policy.regexPatternsV2 && policy.regexPatternsV2.length > 0) {
            setRegexPatterns(policy.regexPatternsV2);
        } else if (policy.regexPatterns && policy.regexPatterns.length > 0) {
            // Convert old format to new format with default behavior
            const convertedPatterns = policy.regexPatterns.map(pattern => ({
                pattern: pattern,
                behavior: "block" // Default behavior for old data
            }));
            setRegexPatterns(convertedPatterns);
        } else {
            setRegexPatterns([]);
        }

        // LLM prompt
        if (policy.llmRule) {
            setLlmPrompt(policy.llmRule.userPrompt || "");
            setLlmConfidenceScore(policy.llmRule.confidenceScore !== undefined ? policy.llmRule.confidenceScore : 0.5);
        } else {
            setLlmPrompt("");
            setLlmConfidenceScore(0.5);
        }

        // External model based evaluation
        setUrl(policy.url || "");
        // Map existing confidence score to nearest checkpoint
        const existingScore = policy.confidenceScore || policy.riskScore || 25;
        const checkpoints = [25, 50, 75, 100];
        const nearestCheckpoint = checkpoints.reduce((prev, curr) =>
            Math.abs(curr - existingScore) < Math.abs(prev - existingScore) ? curr : prev
        );
        setConfidenceScore(nearestCheckpoint);

        // Server settings - prefer V2 format with names, fallback to old format
        if (policy.selectedMcpServersV2 && policy.selectedMcpServersV2.length > 0) {
            // Extract IDs from V2 format for form population
            setSelectedMcpServers(policy.selectedMcpServersV2.map(server => server.id));
        } else {
            setSelectedMcpServers(policy.selectedMcpServers || []);
        }

        if (policy.selectedAgentServersV2 && policy.selectedAgentServersV2.length > 0) {
            // Extract IDs from V2 format for form population
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
                .filter(serverId => serverId) // Filter out empty values
                .map(serverId => {
                    const server = mcpServers.find(s => s.value === serverId || s.value === serverId.toString());
                    return {
                        id: serverId.toString(),
                        name: server ? server.label : serverId.toString()
                    };
                });

            const transformedAgentServers = selectedAgentServers
                .filter(serverId => serverId) // Filter out empty values  
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
                applyToResponses,
                contentFilters: {
                    harmfulCategories: enableHarmfulCategories ? harmfulCategoriesSettings : null,
                    promptAttacks: enablePromptAttacks ? { level: promptAttackLevel } : null
                },
                deniedTopics,
                wordFilters,
                piiFilters: piiTypes,
                // Save in both old and new formats for backward compatibility
                regexPatterns: regexPatterns
                    .filter(r => r && r.pattern) // Ensure valid regex objects
                    .map(r => r.pattern), // Old format (just patterns)
                regexPatternsV2: regexPatterns
                    .filter(r => r && r.pattern && r.behavior) // Ensure valid regex objects with behavior
                    .map(r => ({
                        pattern: r.pattern,
                        behavior: r.behavior.toLowerCase() // Ensure consistent case
                    })), // New format (with behavior)
                ...(llmPrompt && llmPrompt.trim() ? {
                    llmRule: {
                        enabled: true,
                        userPrompt: llmPrompt.trim(),
                        confidenceScore: llmConfidenceScore
                    }
                } : {}),
                url: url || null,
                confidenceScore: confidenceScore,
                selectedMcpServers: selectedMcpServers, // Old format (just IDs)
                selectedAgentServers: selectedAgentServers, // Old format (just IDs)
                selectedMcpServersV2: transformedMcpServers, // New format (with names)
                selectedAgentServersV2: transformedAgentServers, // New format (with names)
                applyOnResponse,
                applyOnRequest,
                // Add edit mode information
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
        // Toggle collapse if clicking on already open step
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
                        applyToResponses={applyToResponses}
                        setApplyToResponses={setApplyToResponses}
                    />
                );
            case 2:
                return (
                    <ContentFiltersStep
                        enableHarmfulCategories={enableHarmfulCategories}
                        setEnableHarmfulCategories={setEnableHarmfulCategories}
                        harmfulCategoriesSettings={harmfulCategoriesSettings}
                        setHarmfulCategoriesSettings={setHarmfulCategoriesSettings}
                        enablePromptAttacks={enablePromptAttacks}
                        setEnablePromptAttacks={setEnablePromptAttacks}
                        promptAttackLevel={promptAttackLevel}
                        setPromptAttackLevel={setPromptAttackLevel}
                    />
                );
            case 3:
                return (
                    <DeniedTopicsStep
                        deniedTopics={deniedTopics}
                        setDeniedTopics={setDeniedTopics}
                    />
                );
            case 4:
                return (
                    <WordFiltersStep
                        wordFilters={wordFilters}
                        setWordFilters={setWordFilters}
                        newCustomWord={newCustomWord}
                        setNewCustomWord={setNewCustomWord}
                    />
                );
            case 5:
                return (
                    <SensitiveInfoStep
                        piiTypes={piiTypes}
                        setPiiTypes={setPiiTypes}
                        regexPatterns={regexPatterns}
                        setRegexPatterns={setRegexPatterns}
                        newRegexPattern={newRegexPattern}
                        setNewRegexPattern={setNewRegexPattern}
                    />
                );
            case 6:
                return (
                    <LlmPromptStep
                        llmRule={llmPrompt}
                        setLlmRule={setLlmPrompt}
                        llmConfidenceScore={llmConfidenceScore}
                        setLlmConfidenceScore={setLlmConfidenceScore}
                    />
                );
            case 7:
                return (
                    <ExternalModelStep
                        url={url}
                        setUrl={setUrl}
                        confidenceScore={confidenceScore}
                        setConfidenceScore={setConfidenceScore}
                    />
                );
            case 8:
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

        if (currentStep < 8) {
            actions.push({
                content: "Next",
                onAction: handleNext
            });
        }

        return actions;
    };

    const getPrimaryAction = () => {
        // Check if all steps are valid
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