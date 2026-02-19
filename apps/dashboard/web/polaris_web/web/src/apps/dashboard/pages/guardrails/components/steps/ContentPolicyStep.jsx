import { useState } from "react";
import {
    VerticalStack,
    Text,
    Checkbox,
    Box,
    RangeSlider,
    HorizontalStack,
    Button,
    TextField,
    Tag,
    FormLayout
} from "@shopify/polaris";
import { PlusMinor, EditMinor, DeleteMinor } from "@shopify/polaris-icons";

export const ContentPolicyConfig = {
    number: 2,
    title: "Content & Policy Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enablePromptAttacks, enableDeniedTopics, deniedTopics, enableHarmfulCategories, enableBasePromptRule }) => {
        const filters = [];
        if (enablePromptAttacks) filters.push('Prompt attacks');
        if (enableDeniedTopics && deniedTopics?.length > 0) filters.push(`${deniedTopics.length} denied topic${deniedTopics.length !== 1 ? 's' : ''}`);
        if (enableHarmfulCategories) filters.push('Harmful categories');
        if (enableBasePromptRule) filters.push('Intent verification');
        return filters.length > 0 ? filters.join(', ') : null;
    }
};

const ContentPolicyStep = ({
    // Prompt attacks
    enablePromptAttacks,
    setEnablePromptAttacks,
    promptAttackLevel,
    setPromptAttackLevel,
    // Denied topics
    enableDeniedTopics,
    setEnableDeniedTopics,
    deniedTopics,
    setDeniedTopics,
    // Harmful categories
    enableHarmfulCategories,
    setEnableHarmfulCategories,
    harmfulCategoriesSettings,
    setHarmfulCategoriesSettings,
    // Intent based (Base Prompt)
    enableBasePromptRule,
    setEnableBasePromptRule,
    basePromptConfidenceScore,
    setBasePromptConfidenceScore
}) => {
    // Denied topics state
    const [editingIndex, setEditingIndex] = useState(null);
    const [editFormData, setEditFormData] = useState({
        topic: "",
        description: "",
        samplePhrases: []
    });
    const [newPhraseInput, setNewPhraseInput] = useState("");

    // Denied topics functions
    const startAdding = () => {
        setEditingIndex(deniedTopics.length);
        setEditFormData({ topic: "", description: "", samplePhrases: [] });
        setNewPhraseInput("");
    };

    const startEditing = (index) => {
        setEditingIndex(index);
        setEditFormData({ ...deniedTopics[index] });
        setNewPhraseInput("");
    };

    const cancelEditing = () => {
        setEditingIndex(null);
        setEditFormData({ topic: "", description: "", samplePhrases: [] });
        setNewPhraseInput("");
    };

    const validateTopicName = (name) => {
        if (!name) return null;
        if (name.length > 100) return "Name must not exceed 100 characters";
        const validCharsPattern = /^[a-zA-Z0-9_\-\s!?.]*$/;
        if (!validCharsPattern.test(name)) return "Name contains invalid characters";
        return null;
    };

    const validateDescription = (description) => {
        if (!description) return null;
        if (description.length > 200) return "Definition must not exceed 200 characters";
        return null;
    };

    const validateSamplePhrase = (phrase) => {
        if (!phrase) return null;
        if (phrase.length > 100) return "Phrase must not exceed 100 characters";
        return null;
    };

    const saveRow = () => {
        if (!editFormData.topic.trim() || !editFormData.description.trim()) return;
        if (validateTopicName(editFormData.topic)) return;
        if (validateDescription(editFormData.description)) return;
        const invalidPhrase = editFormData.samplePhrases.find(phrase => validateSamplePhrase(phrase));
        if (invalidPhrase) return;

        const topicData = {
            topic: editFormData.topic.trim(),
            description: editFormData.description.trim(),
            samplePhrases: editFormData.samplePhrases
        };

        const updatedTopics = [...deniedTopics];
        if (editingIndex === deniedTopics.length) {
            updatedTopics.push(topicData);
        } else {
            updatedTopics[editingIndex] = topicData;
        }
        setDeniedTopics(updatedTopics);
        cancelEditing();
    };

    const deleteRow = (index) => {
        const updatedTopics = deniedTopics.filter((_, i) => i !== index);
        setDeniedTopics(updatedTopics);
    };

    const addSamplePhrase = () => {
        const trimmedPhrase = newPhraseInput.trim();
        if (trimmedPhrase && editFormData.samplePhrases.length < 5 && trimmedPhrase.length <= 100) {
            setEditFormData({
                ...editFormData,
                samplePhrases: [...editFormData.samplePhrases, trimmedPhrase]
            });
            setNewPhraseInput("");
        }
    };

    const removeSamplePhrase = (phraseIndex) => {
        setEditFormData({
            ...editFormData,
            samplePhrases: editFormData.samplePhrases.filter((_, i) => i !== phraseIndex)
        });
    };

    const handlePhraseKeyPress = (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addSamplePhrase();
        }
    };

    const renderViewRow = (topic, index) => (
        <Box key={index} padding="4" borderColor="border" borderWidth="025" borderRadius="2">
            <HorizontalStack align="space-between" blockAlign="start">
                <Box style={{ flex: 1 }}>
                    <VerticalStack gap="2">
                        <Text variant="headingSm" fontWeight="semibold">{topic.topic}</Text>
                        <Text variant="bodyMd" tone="subdued">{topic.description}</Text>
                        {topic.samplePhrases.length > 0 && (
                            <HorizontalStack gap="1">
                                <Text variant="bodySm" tone="subdued">
                                    {topic.samplePhrases.length} sample phrase{topic.samplePhrases.length !== 1 ? 's' : ''}
                                </Text>
                            </HorizontalStack>
                        )}
                    </VerticalStack>
                </Box>
                <HorizontalStack gap="2">
                    <Button icon={EditMinor} onClick={() => startEditing(index)} accessibilityLabel="Edit topic" />
                    <Button icon={DeleteMinor} onClick={() => deleteRow(index)} tone="critical" accessibilityLabel="Delete topic" />
                </HorizontalStack>
            </HorizontalStack>
        </Box>
    );

    const renderEditRow = (isNew) => (
        <Box key={isNew ? "new" : editingIndex} padding="4" borderColor="border" borderWidth="025" borderRadius="2" background="bg-surface-secondary">
            <VerticalStack gap="4">
                <TextField
                    label="Name"
                    value={editFormData.topic}
                    onChange={(value) => setEditFormData({ ...editFormData, topic: value })}
                    placeholder="Medical Diagnosis"
                    helpText="Valid characters: a-z, A-Z, 0-9, _, -, space, !, ?, . Max 100 characters."
                    error={validateTopicName(editFormData.topic)}
                    requiredIndicator
                    autoComplete="off"
                />
                <TextField
                    label="Definition for topic"
                    value={editFormData.description}
                    onChange={(value) => setEditFormData({ ...editFormData, description: value })}
                    multiline={3}
                    placeholder="Medical diagnosis refers to providing specific medical condition assessments..."
                    helpText="Provide a clear definition. Max 200 characters."
                    error={validateDescription(editFormData.description)}
                    requiredIndicator
                    autoComplete="off"
                />
                <Box>
                    <VerticalStack gap="2">
                        <Text variant="bodyMd" fontWeight="medium">Sample Phrases (optional, {editFormData.samplePhrases.length}/5)</Text>
                        <Text variant="bodySm" tone="subdued">Representative phrases that refer to the topic. Max 5 phrases, 100 characters each.</Text>
                        {editFormData.samplePhrases.length > 0 && (
                            <HorizontalStack gap="2" wrap>
                                {editFormData.samplePhrases.map((phrase, idx) => (
                                    <Tag key={idx} onRemove={() => removeSamplePhrase(idx)}>{phrase}</Tag>
                                ))}
                            </HorizontalStack>
                        )}
                        {editFormData.samplePhrases.length < 5 && (
                            <HorizontalStack gap="2">
                                <Box style={{ flex: 1 }}>
                                    <TextField
                                        value={newPhraseInput}
                                        onChange={setNewPhraseInput}
                                        onKeyPress={handlePhraseKeyPress}
                                        placeholder="Type phrase and press Enter"
                                        error={validateSamplePhrase(newPhraseInput)}
                                        autoComplete="off"
                                    />
                                </Box>
                                <Button onClick={addSamplePhrase} disabled={!newPhraseInput.trim() || !!validateSamplePhrase(newPhraseInput)} icon={PlusMinor}>Add</Button>
                            </HorizontalStack>
                        )}
                    </VerticalStack>
                </Box>
                <HorizontalStack align="end" gap="2">
                    <Button onClick={cancelEditing}>Cancel</Button>
                    <Button
                        primary
                        onClick={saveRow}
                        disabled={!editFormData.topic.trim() || !editFormData.description.trim() || !!validateTopicName(editFormData.topic) || !!validateDescription(editFormData.description)}
                    >
                        {isNew ? "Save topic" : "Update topic"}
                    </Button>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    );

    return (
        <VerticalStack gap="4">
            <Text variant="bodyMd" tone="subdued">
                Configure content filtering and policy guardrails to protect against harmful content and policy violations.
            </Text>

            <VerticalStack gap="4">
                {/* Prompt Injection Attacks */}
                <Box>
                    <Checkbox
                        label="Enable prompt injection attacks filter"
                        checked={enablePromptAttacks}
                        onChange={setEnablePromptAttacks}
                        helpText="Detect and block user inputs attempting to override system instructions."
                    />
                    {enablePromptAttacks && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <Text variant="bodyMd" fontWeight="medium">Prompt Attack Level</Text>
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
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                {/* Denied Topics */}
                <Box>
                    <Checkbox
                        label="Add denied topics"
                        checked={enableDeniedTopics}
                        onChange={setEnableDeniedTopics}
                        helpText="Add up to 30 denied topics to block user inputs or model responses associated with the topic."
                    />
                    {enableDeniedTopics && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                {deniedTopics.map((topic, index) => {
                                    if (editingIndex === index) return renderEditRow(false);
                                    return renderViewRow(topic, index);
                                })}
                                {editingIndex === deniedTopics.length && renderEditRow(true)}
                                {editingIndex === null && (
                                    <Button icon={PlusMinor} onClick={startAdding} fullWidth textAlign="left">Add denied topic</Button>
                                )}
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                {/* Harmful Categories */}
                <Box>
                    <Checkbox
                        label="Enable harmful categories filters"
                        checked={enableHarmfulCategories}
                        onChange={setEnableHarmfulCategories}
                        helpText="Detect and block harmful user inputs and model responses."
                    />
                    {enableHarmfulCategories && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <HorizontalStack align="space-between">
                                    <Text variant="headingSm">Filters for prompts</Text>
                                    <Button variant="plain" onClick={() => {
                                        const resetSettings = { ...harmfulCategoriesSettings };
                                        Object.keys(resetSettings).forEach(key => {
                                            if (key !== 'useForResponses') resetSettings[key] = 'none';
                                        });
                                        setHarmfulCategoriesSettings(resetSettings);
                                    }}>Reset all</Button>
                                </HorizontalStack>
                                {Object.entries(harmfulCategoriesSettings).map(([category, level]) => {
                                    if (category === 'useForResponses') return null;
                                    return (
                                        <Box key={category}>
                                            <Text variant="bodyMd" fontWeight="medium" textTransform="capitalize">{category}</Text>
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
                                                        setHarmfulCategoriesSettings({ ...harmfulCategoriesSettings, [category]: levels[value] });
                                                    }}
                                                />
                                            </Box>
                                        </Box>
                                    );
                                })}
                                <Checkbox
                                    label="Use the same harmful categories filters for responses"
                                    checked={harmfulCategoriesSettings.useForResponses}
                                    onChange={(checked) => setHarmfulCategoriesSettings({ ...harmfulCategoriesSettings, useForResponses: checked })}
                                />
                            </VerticalStack>
                        </Box>
                    )}
                </Box>

                {/* Intent Based Guardrails (Base Prompt) */}
                <Box>
                    <Checkbox
                        label="Enable agent intent verification"
                        checked={enableBasePromptRule}
                        onChange={setEnableBasePromptRule}
                        helpText="Verify if agent requests match the intent of the base prompt. The base prompt is automatically detected from traffic, and user inputs filling placeholders like {var} or {} are checked against this intent."
                    />
                    {enableBasePromptRule && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <FormLayout>
                                <RangeSlider
                                    label="Confidence Threshold"
                                    value={basePromptConfidenceScore}
                                    min={0}
                                    max={1}
                                    step={0.1}
                                    output
                                    onChange={setBasePromptConfidenceScore}
                                    helpText="Set the confidence threshold (0-1). Higher values require more confidence to block content."
                                />
                            </FormLayout>
                        </Box>
                    )}
                </Box>
            </VerticalStack>
        </VerticalStack>
    );
};

export default ContentPolicyStep;

