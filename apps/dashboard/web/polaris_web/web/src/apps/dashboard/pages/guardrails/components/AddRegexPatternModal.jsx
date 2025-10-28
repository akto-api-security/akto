import React, { useState } from "react";
import {
    Modal,
    FormLayout,
    TextField,
    Button,
    LegacyStack,
    Text,
    VerticalStack,
    Box
} from "@shopify/polaris";
import DropdownSearch from "../../../components/shared/DropdownSearch";

const AddRegexPatternModal = ({ isOpen, onClose, onSave }) => {
    const [regexPattern, setRegexPattern] = useState("");
    const [guardrailBehavior, setGuardrailBehavior] = useState("block");
    const [loading, setLoading] = useState(false);

    const behaviorOptions = [
        { label: "Block", value: "block" },
        { label: "Mask", value: "mask" }
    ];

    const resetForm = () => {
        setRegexPattern("");
        setGuardrailBehavior("block");
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    const handleSave = async () => {
        if (!regexPattern.trim()) {
            return;
        }

        setLoading(true);
        try {
            const regexData = {
                pattern: regexPattern.trim(),
                behavior: guardrailBehavior
            };
            
            await onSave(regexData);
            handleClose();
        } catch (error) {
            console.error("Error saving regex pattern:", error);
        } finally {
            setLoading(false);
        }
    };


    return (
        <Modal
            open={isOpen}
            onClose={handleClose}
            title="Add new regex pattern"
            primaryAction={{
                content: "Confirm",
                onAction: handleSave,
                loading: loading,
                disabled: !regexPattern.trim()
            }}
            secondaryActions={[
                {
                    content: "Cancel",
                    onAction: handleClose
                }
            ]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <FormLayout>
                        <TextField
                            label="Regex pattern"
                            value={regexPattern}
                            onChange={setRegexPattern}
                            placeholder="Example: \d{3}-\d{2}-\d{4} (SSN pattern)"
                            helpText="Enter a valid regex pattern to filter custom types of sensitive information"
                        />

                        <DropdownSearch
                            label="Guardrail behavior"
                            optionsList={behaviorOptions}
                            value={behaviorOptions.find(opt => opt.value === guardrailBehavior)?.label}
                            setSelected={setGuardrailBehavior}
                            searchDisable={true}
                            placeholder="Select behavior"
                        />

                        {guardrailBehavior === "block" && (
                            <Box paddingBlockStart="2">
                                <Text variant="bodyMd" tone="subdued">
                                    If this regex pattern is detected in the user input or model response, the guardrail returns a blocked message to the user.
                                </Text>
                            </Box>
                        )}

                        {guardrailBehavior === "mask" && (
                            <Box paddingBlockStart="2">
                                <Text variant="bodyMd" tone="subdued">
                                    If this regex pattern is detected in the model response, the guardrail replaces it with the identifier [MASKED].
                                </Text>
                            </Box>
                        )}
                    </FormLayout>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default AddRegexPatternModal;