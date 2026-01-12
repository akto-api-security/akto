import { Modal, Text, VerticalStack, RadioButton, Box } from "@shopify/polaris";
import { useState } from "react";

const SeverityUpdateModal = ({
    open,
    onClose,
    onConfirm,
    loading = false,
    selectedCount = 0
}) => {
    const [selectedSeverity, setSelectedSeverity] = useState('HIGH');

    const severityOptions = [
        { label: 'Critical', value: 'CRITICAL' },
        { label: 'High', value: 'HIGH' },
        { label: 'Medium', value: 'MEDIUM' },
        { label: 'Low', value: 'LOW' }
    ];

    const handleConfirm = () => {
        onConfirm(selectedSeverity);
    };

    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Update Severity"
            primaryAction={{
                content: loading ? 'Updating...' : 'Update Severity',
                onAction: handleConfirm,
                disabled: loading
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: onClose
                }
            ]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd">
                        Update severity for {selectedCount} selected issue{selectedCount === 1 ? '' : 's'}
                    </Text>
                    <VerticalStack gap="3">
                        {severityOptions.map((option) => (
                            <Box key={option.value}>
                                <RadioButton
                                    label={option.label}
                                    checked={selectedSeverity === option.value}
                                    id={option.value}
                                    name="severity"
                                    onChange={() => setSelectedSeverity(option.value)}
                                />
                            </Box>
                        ))}
                    </VerticalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default SeverityUpdateModal;
