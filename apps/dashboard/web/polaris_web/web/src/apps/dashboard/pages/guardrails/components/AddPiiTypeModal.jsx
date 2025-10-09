import React, { useState } from "react";
import {
    Modal,
    FormLayout,
    Button,
    LegacyStack,
    Text,
    VerticalStack,
    Box
} from "@shopify/polaris";
import DropdownSearch from "../../../components/shared/DropdownSearch";

const AddPiiTypeModal = ({ isOpen, onClose, onSave }) => {
    const [selectedPiiType, setSelectedPiiType] = useState("");
    const [guardrailBehavior, setGuardrailBehavior] = useState("block");
    const [loading, setLoading] = useState(false);

    const piiTypes = [
        { label: "Email", value: "email" },
        { label: "Name", value: "name" },
        { label: "Phone", value: "phone" },
        { label: "Address", value: "address" },
        { label: "Age", value: "age" },
        { label: "Username", value: "username" },
        { label: "Password", value: "password" },
        { label: "Driver ID", value: "driver_id" },
        { label: "License plate", value: "license_plate" },
        { label: "Vehicle Identification number (VIN)", value: "vin" }
    ];

    const behaviorOptions = [
        { label: "Block", value: "block" },
        { label: "Mask", value: "mask" }
    ];

    const resetForm = () => {
        setSelectedPiiType("");
        setGuardrailBehavior("block");
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    const handleSave = async () => {
        if (!selectedPiiType) {
            return;
        }

        setLoading(true);
        try {
            const piiData = {
                type: selectedPiiType,
                behavior: guardrailBehavior
            };
            
            await onSave(piiData);
            handleClose();
        } catch (error) {
            console.error("Error saving PII type:", error);
        } finally {
            setLoading(false);
        }
    };


    return (
        <Modal
            open={isOpen}
            onClose={handleClose}
            title="Add new PII"
            primaryAction={{
                content: "Confirm",
                onAction: handleSave,
                loading: loading,
                disabled: !selectedPiiType
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
                        <DropdownSearch
                            label="Choose PII type"
                            optionsList={piiTypes}
                            value={piiTypes.find(type => type.value === selectedPiiType)?.label}
                            setSelected={setSelectedPiiType}
                            searchDisable={false}
                            placeholder="Select PII type"
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
                                    If sensitive information is detected in the user input or model response, the guardrail returns a blocked message to the user.
                                </Text>
                            </Box>
                        )}

                        {guardrailBehavior === "mask" && (
                            <Box paddingBlockStart="2">
                                <Text variant="bodyMd" tone="subdued">
                                    If sensitive information is detected in the model response, the guardrail replaces it with the identifier [MASKED].
                                </Text>
                            </Box>
                        )}
                    </FormLayout>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default AddPiiTypeModal;