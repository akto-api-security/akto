import { useState, useEffect, useRef } from "react";
import {
    Text,
    HorizontalStack,
    VerticalStack,
    Box,
    Icon,
    Button
} from "@shopify/polaris";
import {
    CancelMajor,
    SettingsMajor
} from "@shopify/polaris-icons";
import PersistStore from '../../../../main/PersistStore';
import AgenticSearchInput from '../../agentic/components/AgenticSearchInput';
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
    ServerSettingsStep,
    ServerSettingsConfig
} from './steps';
import "./createGuardrailPage.css";

const CreateGuardrailPage = ({ onClose, onSave, editingPolicy = null, isEditMode = false }) => {
    // Step management
    const [currentStep, setCurrentStep] = useState(1);
    const [loading, setLoading] = useState(false);

    // Playground state
    const [playgroundInput, setPlaygroundInput] = useState("");
    const [playgroundLoading, setPlaygroundLoading] = useState(false);
    const [playgroundMessages, setPlaygroundMessages] = useState([]);
    const playgroundScrollRef = useRef(null);

    // Step 1: Guardrail details
    const [name, setName] = useState("");
    const [description, setDescription] = useState("");
    const [blockedMessage, setBlockedMessage] = useState("");
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
                number: ServerSettingsConfig.number,
                title: ServerSettingsConfig.title,
                summary: ServerSettingsConfig.getSummary(storedStateData),
                ...ServerSettingsConfig.validate(storedStateData)
            }
        ];
    };

    const steps = getStepsWithSummary();

    // Filter collections when component mounts or allCollections changes
    useEffect(() => {
        if (allCollections && allCollections.length > 0) {
            filterCollections();
        } else {
            setMcpServers([]);
            setAgentServers([]);
            setCollectionsLoading(false);
        }
    }, [allCollections]);

    // Auto-scroll to bottom when new messages are added
    useEffect(() => {
        if (playgroundScrollRef.current && playgroundMessages.length > 0) {
            playgroundScrollRef.current.scrollTop = playgroundScrollRef.current.scrollHeight;
        }
    }, [playgroundMessages]);

    // Populate form when editing
    useEffect(() => {
        if (isEditMode && editingPolicy) {
            populateFormForEdit(editingPolicy);
        } else {
            resetForm();
        }
    }, [isEditMode, editingPolicy]);

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
        setApplyToResponses(false);
        setPlaygroundMessages([]);
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
        setEnableLlmPrompt(policy.llmRule?.enabled || !!policy.llmRule?.userPrompt);
        setLlmPrompt(policy.llmRule?.userPrompt || "");
        setLlmConfidenceScore(policy.llmRule?.confidenceScore ?? 0.5);

        // Base Prompt Based Validation (AI Agents)
        setEnableBasePromptRule(policy.basePromptRule?.enabled || false);
        setBasePromptConfidenceScore(policy.basePromptRule?.confidenceScore ?? 0.5);

        // Gibberish Detection
        setEnableGibberishDetection(policy.gibberishDetection?.enabled || false);
        setGibberishConfidenceScore(policy.gibberishDetection?.confidenceScore ?? 0.7);

        const setScannerState = (detection, setEnabled, setConfidence) => {
            setEnabled(detection?.enabled || false);
            setConfidence(detection?.confidenceScore ?? 0.7);
        };

        setScannerState(policy.anonymizeDetection, setEnableAnonymize, setAnonymizeConfidenceScore);
        setScannerState(policy.banCodeDetection, setEnableBanCode, setBanCodeConfidenceScore);
        setScannerState(policy.secretsDetection, setEnableSecrets, setSecretsConfidenceScore);
        setScannerState(policy.sentimentDetection, setEnableSentiment, setSentimentConfidenceScore);
        setScannerState(policy.tokenLimitDetection, setEnableTokenLimit, setTokenLimitConfidenceScore);

        // External model based evaluation
        setEnableExternalModel(!!policy.url);
        setUrl(policy.url || "");
        const existingScore = policy.confidenceScore || policy.riskScore || 25;
        const nearestCheckpoint = [25, 50, 75, 100].reduce((prev, curr) =>
            Math.abs(curr - existingScore) < Math.abs(prev - existingScore) ? curr : prev
        );
        setConfidenceScore(nearestCheckpoint);

        // Server settings
        setSelectedMcpServers(
            policy.selectedMcpServersV2?.length > 0
                ? policy.selectedMcpServersV2.map(server => server.id)
                : policy.selectedMcpServers || []
        );
        setSelectedAgentServers(
            policy.selectedAgentServersV2?.length > 0
                ? policy.selectedAgentServersV2.map(server => server.id)
                : policy.selectedAgentServers || []
        );
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

    const handleStepClick = (stepNumber) => {
        setCurrentStep(stepNumber);
    };

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

    const handlePlaygroundTest = () => {
        if (!playgroundInput.trim()) return;

        setPlaygroundLoading(true);
        const inputToTest = playgroundInput;
        setPlaygroundInput("");

        setTimeout(() => {
            const input = inputToTest.toLowerCase();
            let response = { userPrompt: inputToTest };

            if (input.includes('hack') || input.includes('exploit') || input.includes('attack')) {
                response = {
                    ...response,
                    action: 'Blocked',
                    reason: 'Prompt Attack Detected',
                    message: 'This request was blocked because it contains potential prompt injection or jailbreak attempts.'
                };
            } else if (input.includes('hate') || input.includes('violence') || input.includes('harmful')) {
                response = {
                    ...response,
                    action: 'Blocked',
                    reason: 'Harmful Content Detected',
                    message: 'This request was blocked due to harmful content categories.'
                };
            } else {
                response = {
                    ...response,
                    action: 'Passed',
                    aiResponse: 'This is a sample response based on your guardrail settings. Your actual AI model would respond here based on the input prompt.'
                };
            }

            setPlaygroundMessages(prev => [...prev, response]);
            setPlaygroundLoading(false);
        }, 1500);
    };

    const allStepsValid = steps.every(step => step.isValid);

    return (
        <div className="guardrail-page-wrapper">
            <div className="guardrail-page-header">
                <HorizontalStack gap="3" align="center">
                    <Icon source={SettingsMajor} />
                    <Text variant="headingLg" as="h1">
                        Create Guardrail Policy
                    </Text>
                </HorizontalStack>
                <button className="Polaris-Modal-CloseButton" onClick={handleClose}>
                    <Icon source={CancelMajor} />
                </button>
            </div>

            <div className="guardrail-page-container">
                <div className="guardrail-sidebar">
                    <Box padding="5" paddingBlockEnd="4">
                        <Text variant="headingMd" as="h3" fontWeight="semibold">Guardrail Categories</Text>
                    </Box>
                    <Box paddingInline="2">
                        <VerticalStack gap="0">
                            {steps.map((step) => (
                                <div
                                    key={step.number}
                                    className={`guardrail-nav-item ${step.number === currentStep ? 'active' : ''}`}
                                    onClick={() => handleStepClick(step.number)}
                                    data-completed={step.number < currentStep}
                                >
                                    <div className={`step-indicator ${
                                        step.number === currentStep ? 'current' :
                                        step.number < currentStep ? (step.isValid ? 'completed' : 'error') : 'pending'
                                    }`} />
                                    <div style={{ flex: 1, paddingTop: '4px' }}>
                                        <Text
                                            variant="bodyMd"
                                            fontWeight={step.number === currentStep ? "semibold" : "regular"}
                                        >
                                            {step.title}
                                        </Text>
                                    </div>
                                </div>
                            ))}
                        </VerticalStack>
                    </Box>
                </div>

                <div className="guardrail-content">
                    <div className="guardrail-content-inner">
                        <Box padding="5">
                            <VerticalStack gap="4">
                                <Text variant="headingMd" as="h2" fontWeight="semibold">
                                    {steps.find(s => s.number === currentStep)?.title}
                                </Text>
                                <Box>
                                    {renderStepContent(currentStep)}
                                </Box>
                            </VerticalStack>
                        </Box>
                    </div>
                    <div className="guardrail-content-footer">
                        <HorizontalStack gap="2" align="space-between">
                            <Button onClick={handlePrevious} disabled={currentStep === 1}>
                                Previous
                            </Button>
                            <HorizontalStack gap="2">
                                <Button onClick={handleNext} disabled={currentStep === steps.length}>
                                    Next
                                </Button>
                                <Button
                                    primary
                                    onClick={handleSave}
                                    loading={loading}
                                    disabled={!allStepsValid}
                                >
                                    Create policy
                                </Button>
                            </HorizontalStack>
                        </HorizontalStack>
                    </div>
                </div>

                <div className="guardrail-playground">
                    <Box padding="5">
                        <Text variant="headingMd" as="h3" fontWeight="semibold">Playground</Text>
                    </Box>
                    <div
                        ref={playgroundScrollRef}
                        style={{
                            flex: 1,
                            overflowY: 'auto',
                            padding: '0 20px 20px',
                            minHeight: 0
                        }}
                    >
                                {playgroundMessages.length === 0 ? (
                                    <Text variant="bodyMd" color="subdued">
                                        Test your guardrail policy by entering a prompt below.
                                    </Text>
                                ) : (
                                    <VerticalStack gap="5">
                                        {playgroundMessages.map((message, index) => (
                                            <VerticalStack key={index} gap="3">
                                                <HorizontalStack align="end">
                                                    <Box
                                                        maxWidth="70%"
                                                        padding="3"
                                                        paddingInline="4"
                                                        borderWidth="1"
                                                        borderColor="border"
                                                        background="bg-surface"
                                                        borderRadius='3'
                                                        borderRadiusStartEnd='1'
                                                    >
                                                        <Text variant="bodyMd" color="subdued">
                                                            {message.userPrompt}
                                                        </Text>
                                                    </Box>
                                                </HorizontalStack>

                                                <Box paddingBlockStart="1">
                                                    <Text
                                                        variant="bodyMd"
                                                        color={message.action === 'Blocked' ? 'success' : 'subdued'}
                                                    >
                                                        {message.action}
                                                        {message.reason && ` - ${message.reason}`}
                                                    </Text>
                                                </Box>

                                                {message.action === 'Blocked' ? (
                                                    <HorizontalStack align="start">
                                                        <Box
                                                            maxWidth="70%"
                                                            background="bg-fill-critical"
                                                            borderRadius='3'
                                                            borderRadiusEndStart='1'
                                                        >
                                                            <Text variant="bodyMd">
                                                                {message.message}
                                                            </Text>
                                                        </Box>
                                                    </HorizontalStack>
                                                ) : message.aiResponse && (
                                                    <HorizontalStack align="start">
                                                        <Box
                                                            maxWidth="70%"
                                                            padding="3"
                                                            paddingInline="4"
                                                            background="bg-surface"
                                                            borderWidth="1"
                                                            borderColor="border"
                                                            borderRadius='3'
                                                            borderRadiusEndStart='1'
                                                        >
                                                            <Text variant="bodyMd" style={{ whiteSpace: 'pre-wrap' }}>
                                                                {message.aiResponse}
                                                            </Text>
                                                        </Box>
                                                    </HorizontalStack>
                                                )}
                                            </VerticalStack>
                                        ))}
                                    </VerticalStack>
                                )}
                        </div>
                        <div className="playground-input-wrapper">
                            <AgenticSearchInput
                                value={playgroundInput}
                                onChange={setPlaygroundInput}
                                onSubmit={handlePlaygroundTest}
                                placeholder="Test your guardrail policy..."
                                isStreaming={playgroundLoading}
                            />
                        </div>
                </div>
            </div>
        </div>
    );
};

export default CreateGuardrailPage;
