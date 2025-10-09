import React, { useState } from "react";
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

const CreateGuardrailModal = ({ isOpen, onClose, onSave }) => {
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
        hate: "high",
        insults: "high",
        sexual: "high",
        violence: "high",
        misconduct: "high",
        useForResponses: false
    });
    const [promptAttackLevel, setPromptAttackLevel] = useState("high");

    // Step 3: Denied topics
    const [deniedTopics, setDeniedTopics] = useState([]);

    // Step 4: Word filters
    const [filterProfanity, setFilterProfanity] = useState(false);
    const [customWords, setCustomWords] = useState([]);
    const [newWord, setNewWord] = useState("");

    // Step 5: Sensitive information filters
    const [piiTypes, setPiiTypes] = useState([]);


    // Sub-modal states
    const [showAddTopicModal, setShowAddTopicModal] = useState(false);
    const [showAddPiiModal, setShowAddPiiModal] = useState(false);
    const [editingTopic, setEditingTopic] = useState(null);

    const steps = [
        { number: 1, title: "Provide guardrail details", optional: false },
        { number: 2, title: "Configure content filters", optional: true },
        { number: 3, title: "Add denied topics", optional: true },
        { number: 4, title: "Add word filters", optional: true },
        { number: 5, title: "Add sensitive information filters", optional: true },
        { number: 6, title: "Review and create", optional: false }
    ];

    const resetForm = () => {
        setCurrentStep(1);
        setName("");
        setDescription("");
        setBlockedMessage("");
        setApplyToResponses(false);
        setEnableHarmfulCategories(false);
        setEnablePromptAttacks(false);
        setHarmfulCategoriesSettings({
            hate: "high",
            insults: "high",
            sexual: "high",
            violence: "high",
            misconduct: "high",
            useForResponses: false
        });
        setPromptAttackLevel("high");
        setDeniedTopics([]);
        setFilterProfanity(false);
        setCustomWords([]);
        setNewWord("");
        setPiiTypes([]);
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

    const handleSkipToReview = () => {
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
                piiFilters: piiTypes
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
                    <HorizontalStack key={step.number} gap="2" blockAlign="center">
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
                
                <LegacyCard sectioned>
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
                </LegacyCard>

                <LegacyCard sectioned>
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
                </LegacyCard>
            </VerticalStack>
        </LegacyCard>
    );

    const renderStep3 = () => (
        <LegacyCard sectioned>
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
                    <LegacyCard>
                        <DataTable
                            columnContentTypes={['text', 'text', 'text']}
                            headings={['Name', 'Definition', 'Sample phrases']}
                            rows={deniedTopics.map(topic => [
                                topic.name,
                                topic.definition,
                                `${topic.samplePhrases.length} phrase${topic.samplePhrases.length !== 1 ? 's' : ''}`
                            ])}
                        />
                    </LegacyCard>
                )}
            </VerticalStack>
        </LegacyCard>
    );

    const renderStep4 = () => (
        <LegacyCard sectioned>
            <VerticalStack gap="4">
                <Text variant="headingMd">Add word filters</Text>
                <Text variant="bodyMd" tone="subdued">
                    Use these filters to block certain words and phrases in user inputs and model responses.
                </Text>
                
                <LegacyCard sectioned>
                    <VerticalStack gap="3">
                        <Text variant="headingMd">Profanity filter</Text>
                        <Checkbox
                            label="Filter profanity"
                            checked={filterProfanity}
                            onChange={setFilterProfanity}
                            helpText="Enable this feature to block profane words in user inputs and model responses. The list of words is based on the global definition of profanity and is subject to change."
                        />
                    </VerticalStack>
                </LegacyCard>

                <LegacyCard sectioned>
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
                </LegacyCard>
            </VerticalStack>
        </LegacyCard>
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
                    <LegacyCard>
                        <DataTable
                            columnContentTypes={['text', 'text']}
                            headings={['Choose PII type', 'Guardrail behavior']}
                            rows={piiTypes.map(pii => [
                                pii.type.charAt(0).toUpperCase() + pii.type.slice(1),
                                pii.behavior.charAt(0).toUpperCase() + pii.behavior.slice(1)
                            ])}
                        />
                    </LegacyCard>
                )}

                <Button onClick={() => setShowAddPiiModal(true)}>Add new PII</Button>

                <Box paddingBlockStart="4">
                    <Text variant="headingMd">Regex patterns</Text>
                    <Text variant="bodyMd" tone="subdued">
                        Add up to 10 regex patterns to filter custom types of sensitive information for your specific use case. A blocked message will show if user input or model responses match these patterns.
                    </Text>
                </Box>
            </VerticalStack>
        </LegacyCard>
    );


    const renderStep6 = () => (
        <LegacyCard sectioned>
            <VerticalStack gap="4">
                <Text variant="headingMd">Review and create</Text>
                <Text variant="bodyMd" tone="subdued">
                    Review your guardrail configuration and create the guardrail.
                </Text>
                
                <LegacyCard sectioned>
                    <VerticalStack gap="3">
                        <Text variant="headingMd">Guardrail Summary</Text>
                        <Text variant="bodyMd"><strong>Name:</strong> {name || "Not specified"}</Text>
                        <Text variant="bodyMd"><strong>Description:</strong> {description || "Not specified"}</Text>
                        <Text variant="bodyMd"><strong>Content Filters:</strong> {enableHarmfulCategories || enablePromptAttacks ? "Enabled" : "Disabled"}</Text>
                        <Text variant="bodyMd"><strong>Denied Topics:</strong> {deniedTopics.length} topics</Text>
                        <Text variant="bodyMd"><strong>Word Filters:</strong> {filterProfanity || customWords.length > 0 ? "Enabled" : "Disabled"}</Text>
                        <Text variant="bodyMd"><strong>PII Filters:</strong> {piiTypes.length} types</Text>
                    </VerticalStack>
                </LegacyCard>
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
                content: "Skip to Review and create",
                onAction: handleSkipToReview
            });
        }

        return actions;
    };

    const getPrimaryAction = () => {
        if (currentStep === 6) {
            return {
                content: "Create Guardrail",
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
                title={`Create guardrail - Step ${currentStep}`}
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