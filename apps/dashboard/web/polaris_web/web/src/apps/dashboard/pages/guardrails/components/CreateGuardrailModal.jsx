import React, { useState, useEffect } from "react";
import {
    Modal,
    FormLayout,
    TextField,
    Checkbox,
    Button,
    LegacyStack,
    Text,
    LegacyCard,
    HorizontalStack,
    RadioButton,
    VerticalStack,
    Box,
    Divider,
    Badge,
    Icon,
    Scrollable,
    RangeSlider,
    List,
    ButtonGroup,
    DataTable
} from "@shopify/polaris";
import {
    ChecklistMajor,
    CircleInformationMajor,
    DeleteMajor,
    PlusMinor,
    EditMajor
} from "@shopify/polaris-icons";
import AddDeniedTopicModal from "./AddDeniedTopicModal";
import AddPiiTypeModal from "./AddPiiTypeModal";
import DropdownSearch from "../../../components/shared/DropdownSearch";
import api from "../api";
import PersistStore from '../../../../main/PersistStore';

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
    const [enablePromptAttacks, setEnablePromptAttacks] = useState(false);
    const [harmfulCategoriesSettings, setHarmfulCategoriesSettings] = useState({
        hate: "HIGH",
        insults: "HIGH",
        sexual: "HIGH",
        violence: "HIGH",
        misconduct: "HIGH",
        useForResponses: false
    });
    const [promptAttackLevel, setPromptAttackLevel] = useState("HIGH");

    // Step 3: Denied topics
    const [deniedTopics, setDeniedTopics] = useState([]);

    // Step 4: Word filters
    const [filterProfanity, setFilterProfanity] = useState(false);
    const [customWords, setCustomWords] = useState([]);
    const [newWord, setNewWord] = useState("");

    // Step 5: Sensitive information filters
    const [piiTypes, setPiiTypes] = useState([]);
    const [regexPatterns, setRegexPatterns] = useState([]);
    const [newRegexPattern, setNewRegexPattern] = useState("");

    // Step 6: Server and application settings
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

    // Sub-modal states
    const [showAddTopicModal, setShowAddTopicModal] = useState(false);
    const [showAddPiiModal, setShowAddPiiModal] = useState(false);
    const [editingTopic, setEditingTopic] = useState(null);

    const getStepsWithSummary = () => [
        { 
            number: 1, 
            title: "Provide guardrail details", 
            optional: false,
            summary: name ? `${name}${description ? ` - ${description.substring(0, 30)}${description.length > 30 ? '...' : ''}` : ''}` : null
        },
        { 
            number: 2, 
            title: "Configure content filters", 
            optional: true,
            summary: (enableHarmfulCategories || enablePromptAttacks) 
                ? `${enableHarmfulCategories ? 'Harmful categories' : ''}${enableHarmfulCategories && enablePromptAttacks ? ', ' : ''}${enablePromptAttacks ? 'Prompt attacks' : ''}`
                : null
        },
        { 
            number: 3, 
            title: "Add denied topics", 
            optional: true,
            summary: deniedTopics.length > 0 ? `${deniedTopics.length} topic${deniedTopics.length !== 1 ? 's' : ''}` : null
        },
        { 
            number: 4, 
            title: "Add word filters", 
            optional: true,
            summary: (filterProfanity || customWords.length > 0 || regexPatterns.length > 0) 
                ? `${filterProfanity ? 'Profanity' : ''}${filterProfanity && customWords.length > 0 ? ', ' : ''}${customWords.length > 0 ? `${customWords.length} custom word${customWords.length !== 1 ? 's' : ''}` : ''}${(filterProfanity || customWords.length > 0) && regexPatterns.length > 0 ? ', ' : ''}${regexPatterns.length > 0 ? `${regexPatterns.length} regex pattern${regexPatterns.length !== 1 ? 's' : ''}` : ''}`
                : null
        },
        { 
            number: 5, 
            title: "Add sensitive information filters", 
            optional: true,
            summary: piiTypes.length > 0 ? `${piiTypes.length} PII type${piiTypes.length !== 1 ? 's' : ''}` : null
        },
        { 
            number: 6, 
            title: "Server and application settings", 
            optional: false,
            summary: (selectedMcpServers.length > 0 || selectedAgentServers.length > 0) 
                ? `${selectedMcpServers.length} MCP, ${selectedAgentServers.length} Agent${(applyOnRequest || applyOnResponse) ? ` - ${applyOnRequest ? 'Req' : ''}${applyOnRequest && applyOnResponse ? '/' : ''}${applyOnResponse ? 'Res' : ''}` : ''}`
                : null
        }
    ];

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
            console.log("allCollections:", allCollections);
            console.log("Sample collection structure:", allCollections[0]);

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
        setFilterProfanity(false);
        setCustomWords([]);
        setNewWord("");
        setPiiTypes([]);
        setRegexPatterns([]);
        setNewRegexPattern("");
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
        if (policy.wordFilters) {
            setFilterProfanity(policy.wordFilters.profanity || false);
            setCustomWords(policy.wordFilters.custom || []);
        }
        
        // PII filters
        setPiiTypes(policy.piiTypes || []);
        
        // Regex patterns
        setRegexPatterns(policy.regexPatterns || []);
        
        // Server settings
        setSelectedMcpServers(policy.selectedMcpServers || []);
        setSelectedAgentServers(policy.selectedAgentServers || []);
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

    const handleSkipToServers = () => {
        setCurrentStep(6);
    };

    const handleSave = async () => {
        setLoading(true);
        try {
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
                wordFilters: {
                    profanity: filterProfanity,
                    custom: customWords
                },
                piiFilters: piiTypes,
                regexPatterns: regexPatterns,
                selectedMcpServers,
                selectedAgentServers,
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

    const addCustomWord = () => {
        if (newWord.trim() && !customWords.includes(newWord.trim())) {
            setCustomWords([...customWords, newWord.trim()]);
            setNewWord("");
        }
    };

    const removeCustomWord = (word) => {
        setCustomWords(customWords.filter(w => w !== word));
    };

    const addDeniedTopic = (topic) => {
        setDeniedTopics([...deniedTopics, topic]);
    };

    const removeDeniedTopic = (index) => {
        setDeniedTopics(deniedTopics.filter((_, i) => i !== index));
    };

    const addPiiType = (piiType) => {
        setPiiTypes([...piiTypes, piiType]);
    };

    const removePiiType = (index) => {
        setPiiTypes(piiTypes.filter((_, i) => i !== index));
    };

    const addRegexPattern = () => {
        if (newRegexPattern.trim() && !regexPatterns.includes(newRegexPattern.trim())) {
            setRegexPatterns([...regexPatterns, newRegexPattern.trim()]);
            setNewRegexPattern("");
        }
    };

    const removeRegexPattern = (pattern) => {
        setRegexPatterns(regexPatterns.filter(p => p !== pattern));
    };

    const handleSaveTopic = (topicData) => {
        if (editingTopic !== null) {
            // Update existing topic
            const updatedTopics = [...deniedTopics];
            updatedTopics[editingTopic] = topicData;
            setDeniedTopics(updatedTopics);
            setEditingTopic(null);
        } else {
            // Add new topic
            setDeniedTopics([...deniedTopics, topicData]);
        }
        setShowAddTopicModal(false);
    };

    const handleSavePii = (piiData) => {
        setPiiTypes([...piiTypes, piiData]);
        setShowAddPiiModal(false);
    };

    const renderStepIndicator = () => (
        <Box paddingBlockEnd="4">
            <VerticalStack gap="2">
                {steps.map((step) => (
                    <VerticalStack key={step.number} gap="1">
                        <HorizontalStack gap="2" blockAlign="center">
                            <div style={{
                                width: "24px",
                                height: "24px",
                                borderRadius: "50%",
                                backgroundColor: step.number === currentStep ? "#0070f3" : 
                                                step.number < currentStep ? "#008060" : "#e1e3e5",
                                color: step.number <= currentStep ? "white" : "#6d7175",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontSize: "12px",
                                fontWeight: "bold"
                            }}>
                                {step.number < currentStep ? <Icon source={ChecklistMajor} /> : step.number}
                            </div>
                            <Text 
                                variant="bodyMd" 
                                color={step.number === currentStep ? "critical" : "subdued"}
                                fontWeight={step.number === currentStep ? "bold" : "regular"}
                            >
                                {step.title}
                            </Text>
                            {step.optional && (
                                <Badge size="small" tone="info">optional</Badge>
                            )}
                        </HorizontalStack>
                        {step.summary && (
                            <Box paddingInlineStart="6">
                                <Text variant="bodySm" color="subdued" fontWeight="medium">
                                    {step.summary}
                                </Text>
                            </Box>
                        )}
                    </VerticalStack>
                ))}
            </VerticalStack>
        </Box>
    );

    const renderStep1 = () => (
        <LegacyCard sectioned>
            <VerticalStack gap="4">
                <Text variant="headingMd">Guardrail details</Text>
                <FormLayout>
                    <TextField
                        label="Name"
                        value={name}
                        onChange={setName}
                        placeholder="chatbot-guardrail"
                        helpText="Valid characters are a-z, A-Z, 0-9, _ (underscore) and - (hyphen). The name can have up to 50 characters."
                        requiredIndicator
                    />
                    <TextField
                        label="Description"
                        value={description}
                        onChange={setDescription}
                        multiline={3}
                        placeholder="This guardrail blocks toxic content, assistance related to - investment, insurance, medical and programming."
                        helpText="The description can have up to 200 characters."
                    />
                    <TextField
                        label="Messaging for blocked prompts"
                        value={blockedMessage}
                        onChange={setBlockedMessage}
                        multiline={3}
                        placeholder="Sorry, the model cannot answer this question. This has been blocked by chatbot-guardrail."
                        helpText="Enter a message to display if your guardrail blocks the user prompt."
                        requiredIndicator
                    />
                    <Checkbox
                        label="Apply the same blocked message for responses"
                        checked={applyToResponses}
                        onChange={setApplyToResponses}
                    />
                </FormLayout>
            </VerticalStack>
        </LegacyCard>
    );

    const renderStep2 = () => (
        <LegacyCard sectioned>
            <VerticalStack gap="4">
                <Text variant="headingMd">Configure content filters</Text>
                <Text variant="bodyMd" tone="subdued">
                    Configure content filters by adjusting the degree of filtering to detect and block harmful user inputs and model responses that violate your usage policies.
                </Text>
                
                <div style={{ padding: "16px", border: "1px solid #d1d5db", borderRadius: "8px", backgroundColor: "#FFFFFF" }}>
                    <VerticalStack gap="4">
                        <Text variant="headingMd">Harmful categories</Text>
                        <Text variant="bodyMd" tone="subdued">
                            Enable to detect and block harmful user inputs and model responses. Use a higher filter strength to increase the likelihood of filtering harmful content in a given category.
                        </Text>
                        <Checkbox
                            label="Enable harmful categories filters"
                            checked={enableHarmfulCategories}
                            onChange={setEnableHarmfulCategories}
                        />
                        {enableHarmfulCategories && (
                            <VerticalStack gap="3">
                                <HorizontalStack align="space-between">
                                    <Text variant="headingMd">Filters for prompts</Text>
                                    <Button variant="plain" onClick={() => {
                                        const resetSettings = { ...harmfulCategoriesSettings };
                                        Object.keys(resetSettings).forEach(key => {
                                            if (key !== 'useForResponses') resetSettings[key] = 'none';
                                        });
                                        setHarmfulCategoriesSettings(resetSettings);
                                    }}>
                                        Reset all
                                    </Button>
                                </HorizontalStack>
                                {Object.entries(harmfulCategoriesSettings).map(([category, level]) => {
                                    if (category === 'useForResponses') return null;
                                    return (
                                        <Box key={category}>
                                            <Text variant="bodyMd" fontWeight="medium" textTransform="capitalize">
                                                {category}
                                            </Text>
                                            <Box paddingBlockStart="2">
                                                <RangeSlider
                                                    label=""
                                                    value={level === 'none' ? 0 : level === 'low' ? 1 : level === 'medium' ? 2 : 3}
                                                    min={0}
                                                    max={3}
                                                    step={1}
                                                    output
                                                    onChange={(value) => {
                                                        const levels = ['none', 'low', 'medium', 'high'];
                                                        setHarmfulCategoriesSettings({
                                                            ...harmfulCategoriesSettings,
                                                            [category]: levels[value]
                                                        });
                                                    }}
                                                />
                                            </Box>
                                        </Box>
                                    );
                                })}
                                <Checkbox
                                    label="Use the same harmful categories filters for responses"
                                    checked={harmfulCategoriesSettings.useForResponses}
                                    onChange={(checked) => setHarmfulCategoriesSettings({
                                        ...harmfulCategoriesSettings,
                                        useForResponses: checked
                                    })}
                                />
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </div>

                <div style={{ padding: "16px", border: "1px solid #d1d5db", borderRadius: "8px", backgroundColor: "#FFFFFF" }}>
                    <VerticalStack gap="4">
                        <Text variant="headingMd">Prompt attacks</Text>
                        <Text variant="bodyMd" tone="subdued">
                            Enable to detect and block user inputs attempting to override system instructions. To avoid misclassifying system prompts as a prompt attack and ensure that the filters are selectively applied to user inputs, use input tagging.
                        </Text>
                        <Checkbox
                            label="Enable prompt attacks filter"
                            checked={enablePromptAttacks}
                            onChange={setEnablePromptAttacks}
                        />
                        {enablePromptAttacks && (
                            <Box>
                                <Text variant="bodyMd" fontWeight="medium">Prompt Attack</Text>
                                <Box paddingBlockStart="2">
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
                                </Box>
                            </Box>
                        )}
                    </VerticalStack>
                </div>
            </VerticalStack>
        </LegacyCard>
    );

    const renderStep3 = () => (
            <VerticalStack gap="4">
                <Text variant="headingMd">Add denied topics</Text>
                <Text variant="bodyMd" tone="subdued">
                    Add up to 30 denied topics to block user inputs or model responses associated with the topic.
                </Text>
                
                <HorizontalStack align="space-between">
                    <Text variant="headingMd">Denied topics ({deniedTopics.length})</Text>
                    <HorizontalStack gap="2">
                        <Button onClick={() => {}}>Edit</Button>
                        <Button onClick={() => setDeniedTopics([])}>Delete</Button>
                        <Button primary onClick={() => setShowAddTopicModal(true)}>Add denied topic</Button>
                    </HorizontalStack>
                </HorizontalStack>

                {deniedTopics.length > 0 && (
                    <div style={{ border: "1px solid #d1d5db", borderRadius: "8px", overflow: "hidden" }}>
                        <DataTable
                            columnContentTypes={['text', 'text', 'text']}
                            headings={['Name', 'Definition', 'Sample phrases']}
                            rows={deniedTopics.map(topic => [
                                topic.topic,
                                topic.description,
                                `${topic.samplePhrases.length} phrase${topic.samplePhrases.length !== 1 ? 's' : ''}`
                            ])}
                        />
                    </div>
                )}
            </VerticalStack>

    );

    const renderStep4 = () => (
            <VerticalStack gap="4">
                <Text variant="headingMd">Add word filters</Text>
                <Text variant="bodyMd" tone="subdued">
                    Use these filters to block certain words and phrases in user inputs and model responses.
                </Text>
                
                <div style={{ padding: "16px", border: "1px solid #d1d5db", borderRadius: "8px", backgroundColor: "#FFFFFF" }}>
                    <VerticalStack gap="3">
                        <Text variant="headingMd">Profanity filter</Text>
                        <Checkbox
                            label="Filter profanity"
                            checked={filterProfanity}
                            onChange={setFilterProfanity}
                            helpText="Enable this feature to block profane words in user inputs and model responses. The list of words is based on the global definition of profanity and is subject to change."
                        />
                    </VerticalStack>
                </div>

                <div style={{ padding: "16px", border: "1px solid #d1d5db", borderRadius: "8px", backgroundColor: "#FFFFFF" }}>
                    <VerticalStack gap="3">
                        <Text variant="headingMd">Add custom words and phrases</Text>
                        <Text variant="bodyMd" tone="subdued">
                            Specify up to 10,000 words or phrases (max 3 words) to be blocked by the guardrail. A blocked message will show if user input or model responses contain these words or phrases.
                        </Text>
                        
                        <HorizontalStack gap="2">
                            <div style={{ flexGrow: 1 }}>
                                <TextField
                                    value={newWord}
                                    onChange={setNewWord}
                                    placeholder="Example - Where should I invest my money?"
                                />
                            </div>
                            <Button onClick={addCustomWord} disabled={!newWord.trim()}>
                                Add word or phrase
                            </Button>
                        </HorizontalStack>

                        {customWords.length > 0 && (
                            <Box>
                                <Text variant="headingMd">View and edit words and phrases ({customWords.length})</Text>
                                <Box paddingBlockStart="2">
                                    <VerticalStack gap="2">
                                        {customWords.map((word, index) => (
                                            <HorizontalStack key={index} align="space-between" blockAlign="center">
                                                <Text variant="bodyMd">{word}</Text>
                                                <Button
                                                    icon={DeleteMajor}
                                                    variant="plain"
                                                    onClick={() => removeCustomWord(word)}
                                                />
                                            </HorizontalStack>
                                        ))}
                                    </VerticalStack>
                                </Box>
                            </Box>
                        )}
                    </VerticalStack>
                </div>
            </VerticalStack>
    );

    const renderStep5 = () => (
        <LegacyCard sectioned>
            <VerticalStack gap="4">
                <Text variant="headingMd">Add sensitive information filters</Text>
                <Text variant="bodyMd" tone="subdued">
                    Use these filters to handle any data related to privacy.
                </Text>
                
                <Text variant="headingMd">Personally Identifiable Information (PII) types</Text>
                <Text variant="bodyMd" tone="subdued">
                    Specify the types of PII to be filtered and the desired guardrail behavior.
                </Text>

                <HorizontalStack align="space-between">
                    <Text variant="headingMd">PII types ({piiTypes.length})</Text>
                    <HorizontalStack gap="2">
                        <Button onClick={() => setPiiTypes([])}>Delete all</Button>
                        <Button primary onClick={() => {}}>Add all PII types</Button>
                    </HorizontalStack>
                </HorizontalStack>

                {piiTypes.length > 0 && (
                    <div style={{ border: "1px solid #d1d5db", borderRadius: "8px", overflow: "hidden" }}>
                        <DataTable
                            columnContentTypes={['text', 'text']}
                            headings={['Choose PII type', 'Guardrail behavior']}
                            rows={piiTypes.map(pii => [
                                pii.type.charAt(0).toUpperCase() + pii.type.slice(1),
                                pii.behavior.charAt(0).toUpperCase() + pii.behavior.slice(1)
                            ])}
                        />
                    </div>
                )}

                <Button onClick={() => setShowAddPiiModal(true)}>Add new PII</Button>

                <Box paddingBlockStart="4">
                    <VerticalStack gap="3">
                        <Text variant="headingMd">Regex patterns</Text>
                        <Text variant="bodyMd" tone="subdued">
                            Add up to 10 regex patterns to filter custom types of sensitive information for your specific use case. A blocked message will show if user input or model responses match these patterns.
                        </Text>
                        
                        <HorizontalStack gap="2">
                            <div style={{ flexGrow: 1 }}>
                                <TextField
                                    value={newRegexPattern}
                                    onChange={setNewRegexPattern}
                                    placeholder="Example - \d{3}-\d{2}-\d{4} (SSN pattern)"
                                    helpText="Enter a valid regex pattern"
                                />
                            </div>
                            <Button onClick={addRegexPattern} disabled={!newRegexPattern.trim()}>
                                Add pattern
                            </Button>
                        </HorizontalStack>

                        {regexPatterns.length > 0 && (
                            <Box>
                                <Text variant="headingMd">View and edit regex patterns ({regexPatterns.length})</Text>
                                <Box paddingBlockStart="2">
                                    <VerticalStack gap="2">
                                        {regexPatterns.map((pattern, index) => (
                                            <HorizontalStack key={index} align="space-between" blockAlign="center">
                                                <Text variant="bodyMd" fontWeight="medium">{pattern}</Text>
                                                <Button
                                                    icon={DeleteMajor}
                                                    variant="plain"
                                                    onClick={() => removeRegexPattern(pattern)}
                                                />
                                            </HorizontalStack>
                                        ))}
                                    </VerticalStack>
                                </Box>
                            </Box>
                        )}
                    </VerticalStack>
                </Box>
            </VerticalStack>
        </LegacyCard>
    );

    const renderStep6 = () => (
        <LegacyCard sectioned>
            <VerticalStack gap="4">
                <Text variant="headingMd">Server and application settings</Text>
                <Text variant="bodyMd" tone="subdued">
                    Configure which servers the guardrail should be applied to and specify whether it applies to requests, responses, or both.
                </Text>

                <FormLayout>
                    <DropdownSearch
                        label="Select MCP Servers"
                        placeholder="Choose MCP servers where guardrail should be applied"
                        optionsList={mcpServers}
                        setSelected={setSelectedMcpServers}
                        preSelected={selectedMcpServers}
                        allowMultiple={true}
                        disabled={collectionsLoading}
                    />

                    <DropdownSearch
                        label="Select Agent Servers"
                        placeholder="Choose agent servers where guardrail should be applied"
                        optionsList={agentServers}
                        setSelected={setSelectedAgentServers}
                        preSelected={selectedAgentServers}
                        allowMultiple={true}
                        disabled={collectionsLoading}
                    />

                    <div style={{ padding: "16px", border: "1px solid #d1d5db", borderRadius: "8px", backgroundColor: "#FFFFFF" }}>
                        <VerticalStack gap="3">
                            <Text variant="headingMd">Application Settings</Text>
                            <Text variant="bodyMd" tone="subdued">
                                Specify whether the guardrail should be applied to responses and/or requests.
                            </Text>

                            <VerticalStack gap="2">
                                <Checkbox
                                    label="Apply guardrail to responses"
                                    checked={applyOnResponse}
                                    onChange={setApplyOnResponse}
                                    helpText="When enabled, this guardrail will filter and evaluate model responses before they're sent to users."
                                />

                                <Checkbox
                                    label="Apply guardrail to requests"
                                    checked={applyOnRequest}
                                    onChange={setApplyOnRequest}
                                    helpText="When enabled, this guardrail will filter and evaluate user inputs before they're processed by the model."
                                />
                            </VerticalStack>
                        </VerticalStack>
                    </div>
                </FormLayout>
            </VerticalStack>
        </LegacyCard>
    );


    const renderCurrentStep = () => {
        switch (currentStep) {
            case 1: return renderStep1();
            case 2: return renderStep2();
            case 3: return renderStep3();
            case 4: return renderStep4();
            case 5: return renderStep5();
            case 6: return renderStep6();
            default: return renderStep1();
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

        if (currentStep > 1 && currentStep < 6) {
            actions.push({
                content: "Skip to Server settings",
                onAction: handleSkipToServers
            });
        }

        return actions;
    };

    const getPrimaryAction = () => {
        if (currentStep === 6) {
            return {
                content: isEditMode ? "Update Guardrail" : "Create Guardrail",
                onAction: handleSave,
                loading: loading,
                disabled: !name.trim() || !blockedMessage.trim()
            };
        } else if (currentStep < 6) {
            return {
                content: "Next",
                onAction: handleNext,
                disabled: currentStep === 1 && (!name.trim() || !blockedMessage.trim())
            };
        }
        return null;
    };

    return (
        <>
            <Modal
                open={isOpen}
                onClose={handleClose}
                title={`${isEditMode ? 'Edit' : 'Create'} guardrail - Step ${currentStep}`}
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
                    <HorizontalStack gap="6" align="start">
                        <Box minWidth="200px">
                            {renderStepIndicator()}
                        </Box>
                        <Box width="100%">
                            <Scrollable style={{ height: "500px" }}>
                                {renderCurrentStep()}
                            </Scrollable>
                        </Box>
                    </HorizontalStack>
                </Modal.Section>
            </Modal>

            <AddDeniedTopicModal
                isOpen={showAddTopicModal}
                onClose={() => {
                    setShowAddTopicModal(false);
                    setEditingTopic(null);
                }}
                onSave={handleSaveTopic}
                existingTopic={editingTopic !== null ? deniedTopics[editingTopic] : null}
            />

            <AddPiiTypeModal
                isOpen={showAddPiiModal}
                onClose={() => setShowAddPiiModal(false)}
                onSave={handleSavePii}
            />
        </>
    );
};

export default CreateGuardrailModal;