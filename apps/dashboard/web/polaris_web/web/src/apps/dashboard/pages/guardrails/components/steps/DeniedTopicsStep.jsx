import { useState } from "react";
import {
    VerticalStack,
    Text,
    Button,
    Box,
    TextField,
    HorizontalStack,
    Icon,
    Tag
} from "@shopify/polaris";
import { PlusMinor, EditMinor, DeleteMinor } from "@shopify/polaris-icons";

export const DeniedTopicsConfig = {
    number: 3,
    title: "Add denied topics",

    validate: () => {
        return { isValid: true, errorMessage: null };
    },

    getSummary: ({ deniedTopics }) => {
        return deniedTopics.length > 0 ? `${deniedTopics.length} topic${deniedTopics.length !== 1 ? 's' : ''}` : null;
    }
};

const DeniedTopicsStep = ({
    deniedTopics,
    setDeniedTopics
}) => {
    const [editingIndex, setEditingIndex] = useState(null);
    const [editFormData, setEditFormData] = useState({
        topic: "",
        description: "",
        samplePhrases: []
    });
    const [newPhraseInput, setNewPhraseInput] = useState("");

    const startAdding = () => {
        setEditingIndex(deniedTopics.length); // New row
        setEditFormData({
            topic: "",
            description: "",
            samplePhrases: []
        });
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

    const saveRow = () => {
        if (!editFormData.topic.trim() || !editFormData.description.trim()) {
            return;
        }

        const updatedTopics = [...deniedTopics];
        if (editingIndex === deniedTopics.length) {
            // Adding new
            updatedTopics.push(editFormData);
        } else {
            // Editing existing
            updatedTopics[editingIndex] = editFormData;
        }
        setDeniedTopics(updatedTopics);
        cancelEditing();
    };

    const deleteRow = (index) => {
        const updatedTopics = deniedTopics.filter((_, i) => i !== index);
        setDeniedTopics(updatedTopics);
    };

    const addSamplePhrase = () => {
        if (newPhraseInput.trim() && editFormData.samplePhrases.length < 5) {
            setEditFormData({
                ...editFormData,
                samplePhrases: [...editFormData.samplePhrases, newPhraseInput.trim()]
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
        <Box
            key={index}
            padding="4"
            borderColor="border"
            borderWidth="025"
            borderRadius="2"
        >
            <HorizontalStack align="space-between" blockAlign="start">
                <Box style={{ flex: 1 }}>
                    <VerticalStack gap="2">
                        <Text variant="headingSm" fontWeight="semibold">
                            {topic.topic}
                        </Text>
                        <Text variant="bodyMd" tone="subdued">
                            {topic.description}
                        </Text>
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
                    <Button
                        icon={EditMinor}
                        onClick={() => startEditing(index)}
                        accessibilityLabel="Edit topic"
                    />
                    <Button
                        icon={DeleteMinor}
                        onClick={() => deleteRow(index)}
                        tone="critical"
                        accessibilityLabel="Delete topic"
                    />
                </HorizontalStack>
            </HorizontalStack>
        </Box>
    );

    const renderEditRow = (isNew) => (
        <Box
            key={isNew ? "new" : editingIndex}
            padding="4"
            borderColor="border"
            borderWidth="025"
            borderRadius="2"
            background="bg-surface-secondary"
        >
            <VerticalStack gap="4">
                <TextField
                    label="Name"
                    value={editFormData.topic}
                    onChange={(value) => setEditFormData({ ...editFormData, topic: value })}
                    placeholder="Medical Diagnosis"
                    helpText="Valid characters are a-z, A-Z, 0-9, underscore (_), hyphen (-), space, exclamation point (!), question mark (?), and period (.). Max 100 characters."
                    requiredIndicator
                    autoComplete="off"
                />

                <TextField
                    label="Definition for topic"
                    value={editFormData.description}
                    onChange={(value) => setEditFormData({ ...editFormData, description: value })}
                    multiline={3}
                    placeholder="Medical diagnosis refers to providing specific medical condition assessments, disease identification, symptom analysis..."
                    helpText="Provide a clear definition to detect and block user inputs. Max 200 characters."
                    requiredIndicator
                    autoComplete="off"
                />

                <Box>
                    <VerticalStack gap="2">
                        <Text variant="bodyMd" fontWeight="medium">
                            Sample Phrases (optional, {editFormData.samplePhrases.length}/5)
                        </Text>
                        <Text variant="bodySm" tone="subdued">
                            Representative phrases that refer to the topic. Max 5 phrases, 100 characters each.
                        </Text>

                        {editFormData.samplePhrases.length > 0 && (
                            <HorizontalStack gap="2" wrap>
                                {editFormData.samplePhrases.map((phrase, idx) => (
                                    <Tag
                                        key={idx}
                                        onRemove={() => removeSamplePhrase(idx)}
                                    >
                                        {phrase}
                                    </Tag>
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
                                        autoComplete="off"
                                    />
                                </Box>
                                <Button
                                    onClick={addSamplePhrase}
                                    disabled={!newPhraseInput.trim()}
                                    icon={PlusMinor}
                                >
                                    Add
                                </Button>
                            </HorizontalStack>
                        )}
                    </VerticalStack>
                </Box>

                <HorizontalStack align="end" gap="2">
                    <Button onClick={cancelEditing}>
                        Cancel
                    </Button>
                    <Button
                        primary
                        onClick={saveRow}
                        disabled={!editFormData.topic.trim() || !editFormData.description.trim()}
                    >
                        {isNew ? "Save topic" : "Update topic"}
                    </Button>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    );

    return (
        <VerticalStack gap="4">
            <Text variant="headingMd">Add denied topics</Text>
            <Text variant="bodyMd" tone="subdued">
                Add up to 30 denied topics to block user inputs or model responses associated with the topic.
            </Text>

            <VerticalStack gap="3">
                {deniedTopics.map((topic, index) => {
                    if (editingIndex === index) {
                        return renderEditRow(false);
                    }
                    return renderViewRow(topic, index);
                })}

                {editingIndex === deniedTopics.length && renderEditRow(true)}

                {editingIndex === null && (
                    <Button
                        icon={PlusMinor}
                        onClick={startAdding}
                        fullWidth
                        textAlign="left"
                    >
                        Add denied topic
                    </Button>
                )}
            </VerticalStack>
        </VerticalStack>
    );
};

export default DeniedTopicsStep;
