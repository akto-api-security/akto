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
import { GENERAL_BLOCKS, GENERAL_BLOCK_GROUPS, toDeniedTopic } from "../../generalBlocks";
import func from "@/util/func";
import guardrailApi from "../../api";
import ComplianceMappingTags, { buildComplianceMap } from "../ComplianceMappingTags";

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
    setBasePromptConfidenceScore
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
    const [topicComplianceSuggestions, setTopicComplianceSuggestions] = useState({});
    const complianceTimerRef = useRef(null);
    const complianceRequestIdRef = useRef({});
    const editStartRef = useRef({ topic: '', description: '' });

    // Seed compliance suggestions from existing deniedTopics on mount (edit mode)
    useEffect(() => {
        const suggestions = {};
        deniedTopics.forEach((topic, index) => {
            if (topic.compliance && Object.keys(topic.compliance).length > 0) {
                const accepted = Object.keys(topic.compliance).reduce((acc, framework) => {
                    acc[framework] = true;
                    return acc;
                }, {});
                suggestions[index] = { suggested: topic.compliance, accepted };
            }
        });
        if (Object.keys(suggestions).length > 0) {
            setTopicComplianceSuggestions(suggestions);
        }
    }, []);

    useEffect(() => {
        if (editingIndex === null) return;
        const { topic, description } = editFormData;
        if (!topic.trim() || !description.trim()) return;
        if (topic.trim() === editStartRef.current.topic.trim() && description.trim() === editStartRef.current.description.trim()) return;

        clearTimeout(complianceTimerRef.current);
        complianceTimerRef.current = setTimeout(() => {
            fetchTopicCompliance(
                { topic: topic.trim(), description: description.trim(), samplePhrases: editFormData.samplePhrases },
                editingIndex
            );
        }, 1000);

        return () => clearTimeout(complianceTimerRef.current);
    }, [editFormData.description, editFormData.topic, editingIndex]);

    const startAdding = () => {
        editStartRef.current = { topic: '', description: '' };
        setEditingIndex(deniedTopics.length);
        setEditFormData({ topic: "", description: "", samplePhrases: [] });
        setNewPhraseInput("");
    };

    const startEditing = (index) => {
        editStartRef.current = { topic: deniedTopics[index]?.topic || '', description: deniedTopics[index]?.description || '' };
        setEditingIndex(index);
        setEditFormData({ ...deniedTopics[index] });
        setNewPhraseInput("");
    };

    const cancelEditing = () => {
        clearTimeout(complianceTimerRef.current);
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

        const savedIndex = editingIndex;
        const suggestion = topicComplianceSuggestions[savedIndex];
        const compliance = suggestion ? buildComplianceMap(suggestion.suggested || {}, suggestion.accepted || {}) : null;

        const topicData = {
            topic: editFormData.topic.trim(),
            description: editFormData.description.trim(),
            samplePhrases: editFormData.samplePhrases,
            ...(compliance && Object.keys(compliance).length > 0 ? { compliance } : {})
        };

        const updatedTopics = [...deniedTopics];
        if (editingIndex === deniedTopics.length) {
            updatedTopics.unshift(topicData); // new topics appear at the top
        } else {
            updatedTopics[editingIndex] = topicData;
        }
        setDeniedTopics(updatedTopics);
        cancelEditing();

        // Final fetch if compliance not yet available (user saved before debounce fired)
        if (!suggestion?.loading && (!suggestion?.suggested || Object.keys(suggestion.suggested).length === 0)) {
            fetchTopicCompliance(topicData, savedIndex);
        }
    };

    const deleteRow = (index) => {
        const updatedTopics = deniedTopics.filter((_, i) => i !== index);
        setDeniedTopics(updatedTopics);
        setTopicComplianceSuggestions(prev => {
            const updated = {};
            Object.keys(prev).forEach(k => {
                const ki = parseInt(k);
                if (ki !== index) updated[ki > index ? ki - 1 : ki] = prev[k];
            });
            return updated;
        });
        const updatedRequestIds = {};
        Object.keys(complianceRequestIdRef.current).forEach(k => {
            const ki = parseInt(k);
            if (ki !== index) updatedRequestIds[ki > index ? ki - 1 : ki] = complianceRequestIdRef.current[k];
        });
        complianceRequestIdRef.current = updatedRequestIds;
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

    // Unified list for rendering: selected Akto defaults + user custom topics.
    const activeDefaultTopics = GENERAL_BLOCKS
        .filter(b => selectedDefaultBlockKeys.has(b.key))
        .map(b => ({ ...toDeniedTopic(b), _isDefault: true, _key: b.key }));
    const allActiveTopics = [...activeDefaultTopics, ...deniedTopics];

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

    const fetchTopicCompliance = async (topicData, index) => {
        const reqId = (complianceRequestIdRef.current[index] || 0) + 1;
        complianceRequestIdRef.current = { ...complianceRequestIdRef.current, [index]: reqId };
        setTopicComplianceSuggestions(prev => ({ ...prev, [index]: { ...prev[index], loading: true } }));
        try {
            const resp = await guardrailApi.suggestGuardrailCompliance('denied_topic', {
                topicName: topicData.topic,
                topicDescription: topicData.description,
                samplePhrases: topicData.samplePhrases
            });
            if (reqId !== complianceRequestIdRef.current[index]) return;
            const suggested = resp?.response?.mapComplianceToListClauses || {};
            const accepted = Object.keys(suggested).reduce((acc, framework) => {
                acc[framework] = true;
                return acc;
            }, {});
            setTopicComplianceSuggestions(prev => ({
                ...prev,
                [index]: { suggested, accepted, loading: false }
            }));

            setDeniedTopics(prev => {
                const updatedTopics = [...prev];
                const compliance = buildComplianceMap(suggested, accepted);
                updatedTopics[index] = {
                    ...updatedTopics[index],
                    compliance: Object.keys(compliance).length > 0 ? compliance : undefined
                };
                return updatedTopics;
            });
        } catch (error) {
            if (reqId !== complianceRequestIdRef.current[index]) return;
            console.error('Error fetching compliance suggestions:', error);
            setTopicComplianceSuggestions(prev => ({ ...prev, [index]: { ...prev[index], loading: false } }));
        }
    };

    const toggleFrameworkAcceptance = (topicIndex, framework) => {
        const currentEntry = topicComplianceSuggestions[topicIndex] || { suggested: {}, accepted: {} };
        const isAccepted = !!currentEntry.accepted[framework];
        const newAccepted = { ...currentEntry.accepted };

        if (isAccepted) {
            delete newAccepted[framework];
        } else {
            newAccepted[framework] = true;
        }

        setTopicComplianceSuggestions(prev => ({
            ...prev,
            [topicIndex]: { ...currentEntry, accepted: newAccepted }
        }));

        setDeniedTopics(prev => {
            const updatedTopics = [...prev];
            const compliance = buildComplianceMap(currentEntry.suggested, newAccepted);
            updatedTopics[topicIndex] = {
                ...updatedTopics[topicIndex],
                compliance: Object.keys(compliance).length > 0 ? compliance : undefined
            };
            return updatedTopics;
        });
    };

    const renderViewRow = (topic, index) => {
        const isDefault = topic._isDefault;
        const suggestion = !isDefault ? topicComplianceSuggestions[index] : undefined;
        const acceptedCompliance = buildComplianceMap(suggestion?.suggested || {}, suggestion?.accepted || {});
        return (
            <Box key={isDefault ? topic._key : index} padding="4" borderColor="border" borderWidth="025" borderRadius="2">
                <VerticalStack gap="2">
                    <Box style={{ display: 'flex', flexDirection: 'row', alignItems: 'flex-start', gap: '12px' }}>
                        <Box style={{ flex: 1, minWidth: 0 }}>
                            <VerticalStack gap="1">
                                <Text variant="headingSm" fontWeight="semibold">{topic.topic}</Text>
                                <Text variant="bodyMd" tone="subdued">{topic.description}</Text>
                                {topic.samplePhrases.length > 0 && (
                                    <Text variant="bodySm" tone="subdued">
                                        {topic.samplePhrases.length} sample phrase{topic.samplePhrases.length !== 1 ? 's' : ''}
                                    </Text>
                                )}
                            </VerticalStack>
                        </Box>
                        <VerticalStack gap="2" inlineAlign="end">
                            <Badge tone={isDefault ? "info" : undefined}>{isDefault ? "Akto default" : "Custom"}</Badge>
                            {!isDefault && (
                                <Button icon={EditMinor} size="slim" onClick={() => startEditing(index)} accessibilityLabel="Edit topic" />
                            )}
                            <Button
                                icon={DeleteMinor}
                                size="slim"
                                tone="critical"
                                accessibilityLabel="Remove topic"
                                onClick={() => isDefault
                                    ? toggleGeneralBlock(GENERAL_BLOCKS.find(b => b.key === topic._key), false)
                                    : deleteRow(index)
                                }
                            />
                        </VerticalStack>
                    </Box>
                    {!isDefault && (
                        <ComplianceMappingTags
                            loading={suggestion?.loading}
                            complianceMap={acceptedCompliance}
                            onRemove={(framework) => toggleFrameworkAcceptance(index, framework)}
                            onAdd={Object.keys(suggestion?.suggested || {}).length > 0
                                ? (framework) => toggleFrameworkAcceptance(index, framework)
                                : undefined}
                        />
                    )}
                </VerticalStack>
            </Box>
        );
    };

    const renderEditRow = (isNew) => (
        <Box key={isNew ? "new" : editingIndex} padding="4" borderColor="border" borderWidth="1" borderRadius="2" background="bg-surface-secondary">
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
                <ComplianceMappingTags
                    loading={topicComplianceSuggestions[editingIndex]?.loading}
                    complianceMap={buildComplianceMap(
                        topicComplianceSuggestions[editingIndex]?.suggested || {},
                        topicComplianceSuggestions[editingIndex]?.accepted || {}
                    )}
                    onRemove={(framework) => toggleFrameworkAcceptance(editingIndex, framework)}
                    onAdd={Object.keys(topicComplianceSuggestions[editingIndex]?.suggested || {}).length > 0
                        ? (framework) => toggleFrameworkAcceptance(editingIndex, framework)
                        : undefined}
                />
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
                    {enableDeniedTopics && (
                        <Box paddingBlockStart="4" style={{ paddingLeft: '28px', overflowAnchor: 'none' }}>
                            <VerticalStack gap="3">
                                {editingIndex === null && (
                                    <Button icon={PlusMinor} onClick={startAdding} fullWidth textAlign="left">Add denied topic</Button>
                                )}
                                {editingIndex === deniedTopics.length && renderEditRow(true)}
                                {/* Custom topics */}
                                {deniedTopics.map((topic, customIdx) => {
                                    if (editingIndex === customIdx) return renderEditRow(false);
                                    return renderViewRow(topic, customIdx);
                                })}
                                {/* Akto default topics toggle */}
                                {editingIndex === null && (
                                    <Button
                                        icon={defaultPickerOpen ? ChevronUpMinor : ChevronDownMinor}
                                        onClick={() => setDefaultPickerOpen(o => !o)}
                                        fullWidth
                                        textAlign="left"
                                    >
                                        {`Add Akto default topics${selectedDefaultBlockKeys.size > 0 ? ` (${selectedDefaultBlockKeys.size} selected)` : ''}`}
                                    </Button>
                                )}
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
                                                            helpText={block.description}
                                                            checked={isBlockEnabled(block)}
                                                            onChange={(checked) => toggleGeneralBlock(block, checked)}
                                                        />
                                                    ))}
                                                </VerticalStack>
                                            ))}
                                        </VerticalStack>
                                    </Box>
                                )}
                                {/* Akto default topics */}
                                {activeDefaultTopics.map((topic) => renderViewRow(topic, -1))}
                                {allActiveTopics.length > 0 && (
                                    <Text variant="bodySm" tone="subdued">{allActiveTopics.length} denied topic{allActiveTopics.length !== 1 ? 's' : ''} active</Text>
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

