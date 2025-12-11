import { Modal, Text, TextField, VerticalStack } from "@shopify/polaris";

const CompulsoryDescriptionModal = ({
    open,
    reasonLabel,
    description,
    onChangeDescription,
    onConfirm,
    onClose,
    loading = false
}) => {
    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Description Required"
            primaryAction={{
                content: loading ? 'Loading...' : 'Confirm',
                onAction: onConfirm,
                disabled: description.trim().length === 0 || loading
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
                        A description is required for this action based on your account settings. Please provide a reason for marking these issues as "{reasonLabel}".
                    </Text>
                    <TextField
                        label="Description"
                        value={description}
                        onChange={onChangeDescription}
                        multiline={4}
                        autoComplete="off"
                        placeholder="Please provide a description for this action..."
                        disabled={loading}
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default CompulsoryDescriptionModal
