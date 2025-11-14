import React, { useState } from "react";
import {
    Modal,
    FormLayout,
    TextField,
    Button,
    LegacyStack,
    Text,
    LegacyCard,
    HorizontalStack,
    VerticalStack,
    Box,
    Icon,
    Collapsible
} from "@shopify/polaris";
import { ChevronDownMinor, DeleteMajor, PlusMinor } from "@shopify/polaris-icons";

const AddDeniedTopicModal = ({ isOpen, onClose, onSave, existingTopic = null }) => {
    const [name, setName] = useState(existingTopic?.topic || "");
    const [definition, setDefinition] = useState(existingTopic?.description || "");
    const [samplePhrases, setSamplePhrases] = useState(existingTopic?.samplePhrases || []);
    const [newPhrase, setNewPhrase] = useState("");
    const [showSamplePhrases, setShowSamplePhrases] = useState(false);
    const [loading, setLoading] = useState(false);

    const resetForm = () => {
        setName("");
        setDefinition("");
        setSamplePhrases([]);
        setNewPhrase("");
        setShowSamplePhrases(false);
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    const addSamplePhrase = () => {
        if (newPhrase.trim() && samplePhrases.length < 5) {
            setSamplePhrases([...samplePhrases, newPhrase.trim()]);
            setNewPhrase("");
        }
    };

    const removeSamplePhrase = (index) => {
        setSamplePhrases(samplePhrases.filter((_, i) => i !== index));
    };

    const handleSave = async () => {
        if (!name.trim() || !definition.trim()) {
            return;
        }

        setLoading(true);
        try {
            const topicData = {
                topic: name.trim(),
                description: definition.trim(),
                samplePhrases: samplePhrases
            };
            
            await onSave(topicData);
            handleClose();
        } catch (error) {
            console.error("Error saving denied topic:", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal
            open={isOpen}
            onClose={handleClose}
            title={existingTopic ? "Edit denied topic" : "Add denied topic"}
            primaryAction={{
                content: existingTopic ? "Update" : "Confirm",
                onAction: handleSave,
                loading: loading,
                disabled: !name.trim() || !definition.trim()
            }}
            secondaryActions={[
                {
                    content: "Cancel",
                    onAction: handleClose
                }
            ]}
            large
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <FormLayout>
                        <TextField
                            label="Name"
                            value={name}
                            onChange={setName}
                            placeholder="Medical Diagnosis"
                            helpText="Valid characters are a-z, A-Z, 0-9, underscore (_), hyphen (-), space, exclamation point (!), question mark (?), and period (.). The name can have up to 100 characters."
                            requiredIndicator
                        />
                        
                        <TextField
                            label="Definition for topic"
                            value={definition}
                            onChange={setDefinition}
                            multiline={4}
                            placeholder="Medical diagnosis refers to providing specific medical condition assessments, disease identification, symptom analysis, or treatment recommendations that require professional medical expertise."
                            helpText="Provide a clear definition to detect and block user inputs and FM responses that fall into this topic. Avoid starting with 'don't'."
                            requiredIndicator
                        />
                        <Text variant="bodyMd" tone="subdued">
                            The definition can have up to 200 characters.
                        </Text>

                        <Box paddingBlockStart="2">
                            <Button
                                onClick={() => setShowSamplePhrases(!showSamplePhrases)}
                                variant="plain"
                                icon={ChevronDownMinor}
                            >
                                <Text variant="headingMd">Add sample phrases - optional</Text>
                            </Button>
                            <Collapsible open={showSamplePhrases}>
                                <Box paddingBlockStart="3">
                                    <VerticalStack gap="3">
                                        <Text variant="bodyMd" tone="subdued">
                                            Representative phrases that refer to the topic. These phrases can represent a user input or a model response. Add up to 5 phrases. A sample phrase can have up to 100 characters.
                                        </Text>
                                        
                                        <HorizontalStack gap="2">
                                            <div style={{ flexGrow: 1 }}>
                                                <TextField
                                                    value={newPhrase}
                                                    onChange={setNewPhrase}
                                                    placeholder="Can you diagnose my symptoms?"
                                                    disabled={samplePhrases.length >= 5}
                                                />
                                            </div>
                                            <Button 
                                                onClick={addSamplePhrase} 
                                                disabled={!newPhrase.trim() || samplePhrases.length >= 5}
                                                icon={PlusMinor}
                                            >
                                                Add phrase
                                            </Button>
                                        </HorizontalStack>

                                        {samplePhrases.length > 0 && (
                                            <div style={{ padding: "16px", border: "1px solid #d1d5db", borderRadius: "8px", backgroundColor: "#f9fafb" }}>
                                                <VerticalStack gap="2">
                                                    <Text variant="headingMd">Sample phrases ({samplePhrases.length}/5)</Text>
                                                    {samplePhrases.map((phrase, index) => (
                                                        <HorizontalStack key={index} align="space-between" blockAlign="center">
                                                            <Box 
                                                                style={{ 
                                                                    padding: "8px 12px", 
                                                                    border: "1px solid #d1d5db", 
                                                                    borderRadius: "6px",
                                                                    backgroundColor: "#ffffff",
                                                                    flexGrow: 1,
                                                                    marginRight: "8px"
                                                                }}
                                                            >
                                                                <Text variant="bodyMd">{phrase}</Text>
                                                            </Box>
                                                            <Button
                                                                icon={DeleteMajor}
                                                                variant="plain"
                                                                tone="critical"
                                                                onClick={() => removeSamplePhrase(index)}
                                                            />
                                                        </HorizontalStack>
                                                    ))}
                                                </VerticalStack>
                                            </div>
                                        )}
                                    </VerticalStack>
                                </Box>
                            </Collapsible>
                        </Box>
                    </FormLayout>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default AddDeniedTopicModal;