import { useState, useEffect, useRef } from "react";
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
    FormLayout,
    Badge
} from "@shopify/polaris";
import { PlusMinor, EditMinor, DeleteMinor, ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons";
import OwaspTag from "../OwaspTag";
import RuleLabelWithTag from "../RuleLabelWithTag";
import { RULE_OWASP_THREATS } from "../owaspConfig";
import { GENERAL_BLOCKS, GENERAL_BLOCK_GROUPS } from "../../generalBlocks";
import func from "@/util/func";

export const ContentPolicyConfig = {
    number: 2,
    title: "Content & Policy Guardrails",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ enablePromptAttacks, enableContextPoisoning, enableDeniedTopics, deniedTopics, selectedDefaultBlockKeys, enableHarmfulCategories, enableBasePromptRule }) => {
        const filters = [];
        if (enablePromptAttacks) filters.push('Prompt attacks');
        if (func.isDemoAccount() && enableContextPoisoning) filters.push('Context poisoning');
        if (enableDeniedTopics) {
            const total = (deniedTopics?.length || 0) + (selectedDefaultBlockKeys?.size || 0);
            if (total > 0) filters.push(`${total} denied topic${total !== 1 ? 's' : ''}`);
        }
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
    // Context poisoning (demo only)
    enableContextPoisoning,
    setEnableContextPoisoning,
    // Denied topics
    enableDeniedTopics,
    setEnableDeniedTopics,
    selectedDefaultBlockKeys,
    setSelectedDefaultBlockKeys,
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
    setBasePromptConfidenceScore,
    enterpriseLicenseComplianceCategories
}) => {
    // Denied topics state
    const [defaultPickerOpen, setDefaultPickerOpen] = useState(false);
    const [editingIndex, setEditingIndex] = useState(null);

    // Auto-open the default picker when an existing policy is loaded with defaults already selected.
    // Track the previous size to only trigger on a bulk load (size jump), not on individual checkbox toggles.
    const prevDefaultKeySizeRef = useRef(selectedDefaultBlockKeys.size);
    useEffect(() => {
        const prev = prevDefaultKeySizeRef.current;
        const curr = selectedDefaultBlockKeys.size;
        prevDefaultKeySizeRef.current = curr;
        // Jump of more than 1 means a policy was loaded (not a manual toggle)
        if (curr > 1 && prev <= 1) {
            setDefaultPickerOpen(true);
        }
    }, [selectedDefaultBlockKeys]);
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
            updatedTopics.unshift(topicData); // new topics appear at the top
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

    // Akto default blocks are tracked by key in selectedDefaultBlockKeys (separate from custom deniedTopics).
    const isBlockEnabled = (block) => selectedDefaultBlockKeys.has(block.key);

    const toggleGeneralBlock = (block, checked) => {
        const next = new Set(selectedDefaultBlockKeys);
        if (checked) {
            next.add(block.key);
        } else {
            next.delete(block.key);
        }
        setSelectedDefaultBlockKeys(next);
    };

    // Akto default topics are managed via the dropdown above; only custom topics get tiles below.
    const totalActiveTopicsCount = selectedDefaultBlockKeys.size + deniedTopics.length;

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

    // One unified card for every active denied topic. Recommended (catalogue)
    // topics get a "Recommended" badge and no edit action since they are predefined;
    // custom topics get "Custom" and full edit/delete.
    const renderViewRow = (topic, index) => {
        const isDefault = topic._isDefault;
        return (
            <Box key={isDefault ? topic._key : index} padding="4" borderColor="border" borderWidth="025" borderRadius="2">
                <HorizontalStack align="space-between" blockAlign="start">
                    <Box style={{ flex: 1 }}>
                        <VerticalStack gap="2">
                            <HorizontalStack gap="2" blockAlign="center">
                                <Text variant="headingSm" fontWeight="semibold">{topic.topic}</Text>
                                <Badge tone={isDefault ? "info" : undefined}>{isDefault ? "Akto default" : "Custom"}</Badge>
                            </HorizontalStack>
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
                        {!isDefault && (
                            <Button icon={EditMinor} onClick={() => startEditing(index)} accessibilityLabel="Edit topic" />
                        )}
                        <Button
                            icon={DeleteMinor}
                            tone="critical"
                            accessibilityLabel="Remove topic"
                            onClick={() => isDefault
                                ? toggleGeneralBlock(GENERAL_BLOCKS.find(b => b.key === topic._key), false)
                                : deleteRow(index)
                            }
                        />
                    </HorizontalStack>
                </HorizontalStack>
            </Box>
        );
    };

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
            <OwaspTag stepNumber={2} />

            <VerticalStack gap="4">
                {/* Prompt Injection Attacks */}
                <Box>
                    <Checkbox
                        label={<RuleLabelWithTag name="Enable prompt injection attacks filter" threats={RULE_OWASP_THREATS.promptInjection} />}
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

                {/* Context poisoning (demo only) */}
                {func.isDemoAccount() && (
                    <Box>
                        <Checkbox
                            label={<RuleLabelWithTag name="Enable context poisoning attacks" threats={RULE_OWASP_THREATS.contextPoisoning} />}
                            checked={enableContextPoisoning ?? false}
                            onChange={setEnableContextPoisoning}
                            helpText="Detect and block attempts to poison agent memory or context."
                        />
                    </Box>
                )}

                {/* Denied Topics */}
                <Box>
                    <Checkbox
                        label="Add denied topics"
                        checked={enableDeniedTopics}
                        onChange={setEnableDeniedTopics}
                        helpText="Add up to 30 denied topics to block user inputs or model responses associated with the topic."
                    />
                    {enterpriseLicenseComplianceCategories?.length > 0 && (
                        <Box paddingBlockStart="2" paddingInlineStart="6">
                            <Text variant="bodySm" tone="subdued">
                                {enterpriseLicenseComplianceCategories.length} topic{enterpriseLicenseComplianceCategories.length !== 1 ? 's are' : ' is'} managed by Enterprise License Compliance Guardrails.
                            </Text>
                        </Box>
                    )}
                    {enableDeniedTopics && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px' }}>
                            <VerticalStack gap="3">
                                <VerticalStack gap="2">
                                    {editingIndex === null && (
                                        <Button icon={PlusMinor} onClick={startAdding} fullWidth textAlign="left">Add denied topic</Button>
                                    )}
                                    <Button
                                        icon={defaultPickerOpen ? ChevronUpMinor : ChevronDownMinor}
                                        onClick={() => setDefaultPickerOpen(o => !o)}
                                        fullWidth
                                        textAlign="left"
                                    >
                                        <HorizontalStack gap="2" blockAlign="center">
                                            <span>{`Add Akto default topics${selectedDefaultBlockKeys.size > 0 ? ` (${selectedDefaultBlockKeys.size} selected)` : ''}`}</span>
                                            <Badge status="info">Beta</Badge>
                                        </HorizontalStack>
                                    </Button>
                                </VerticalStack>
                                {defaultPickerOpen && (
                                    <Box background="bg-surface-secondary" padding="3" borderRadius="2" borderWidth="1" borderColor="border">
                                        <VerticalStack gap="4">
                                            {Object.values(GENERAL_BLOCK_GROUPS).map(group => (
                                                <VerticalStack key={group} gap="2">
                                                    <Text variant="bodySm" fontWeight="semibold" tone="subdued">{group}</Text>
                                                    {GENERAL_BLOCKS.filter(b => b.group === group).map(block => (
                                                        <Checkbox
                                                            key={block.key}
                                                            label={block.label}
                                                            helpText={block.shortDescription}
                                                            checked={isBlockEnabled(block)}
                                                            onChange={(checked) => toggleGeneralBlock(block, checked)}
                                                        />
                                                    ))}
                                                </VerticalStack>
                                            ))}
                                        </VerticalStack>
                                    </Box>
                                )}
                                {editingIndex === deniedTopics.length && renderEditRow(true)}

                                {/* Akto defaults are managed in the dropdown; only custom topics get tiles here */}
                                {totalActiveTopicsCount > 0 && (
                                    <Text variant="bodySm" tone="subdued">{totalActiveTopicsCount} denied topic{totalActiveTopicsCount !== 1 ? 's' : ''} active</Text>
                                )}
                                {deniedTopics.map((topic, index) => {
                                    if (editingIndex === index) return renderEditRow(false);
                                    return renderViewRow(topic, index);
                                })}
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
                        label={<RuleLabelWithTag name="Enable agent intent verification" threats={RULE_OWASP_THREATS.intentVerification} />}
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

