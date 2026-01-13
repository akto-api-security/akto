import React, { useState } from 'react';
import { Badge, Box, Modal, RadioButton, VerticalStack, Text } from '@shopify/polaris';

const SeveritySelector = ({
    open,
    onClose,
    onConfirm,
    selectedCount = 0,
    pageType = 'test results'
}) => {
    const [selectedSeverity, setSelectedSeverity] = useState(null);

    const handleConfirm = () => {
        if (selectedSeverity) {
            onConfirm(selectedSeverity);
            onClose();
        }
    };

    const severityOptions = [
        {
            value: 'CRITICAL',
            label: 'Critical',
            status: 'critical',
            className: 'badge-wrapper-CRITICAL'
        },
        {
            value: 'HIGH',
            label: 'High',
            status: 'warning',
            className: 'badge-wrapper-HIGH'
        },
        {
            value: 'MEDIUM',
            label: 'Medium',
            status: 'attention',
            className: 'badge-wrapper-MEDIUM'
        },
        {
            value: 'LOW',
            label: 'Low',
            status: 'success',
            className: 'badge-wrapper-LOW'
        }
    ];

    const handleClose = () => {
        setSelectedSeverity(null);
        onClose();
    };

    return (
        <Modal
            open={open}
            onClose={handleClose}
            title="Update Severity"
            primaryAction={{
                content: 'Update Severity',
                onAction: handleConfirm,
                disabled: !selectedSeverity
            }}
            secondaryActions={[
                {
                    content: 'Cancel',
                    onAction: handleClose
                }
            ]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <Text variant="bodyMd" as="p">
                        Select new severity for {selectedCount} {pageType}{selectedCount === 1 ? '' : 's'}
                    </Text>
                    <VerticalStack gap="3">
                        {severityOptions.map(option => (
                            <RadioButton
                                key={option.value}
                                label={
                                    <Box className={option.className}>
                                        <Badge size="small" status={option.status}>
                                            {option.label}
                                        </Badge>
                                    </Box>
                                }
                                checked={selectedSeverity === option.value}
                                id={option.value}
                                onChange={() => setSelectedSeverity(option.value)}
                            />
                        ))}
                    </VerticalStack>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default SeveritySelector;
