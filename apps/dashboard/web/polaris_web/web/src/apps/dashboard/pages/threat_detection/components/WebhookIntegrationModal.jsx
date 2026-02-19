import React, { useState, useEffect } from "react";
import { Modal, TextField, VerticalStack, Text, Checkbox } from "@shopify/polaris";
import CustomHeadersInput from "../../quick_start/components/CustomHeadersInput";

const formatLastSync = (lastSyncTime) => {
    if (!lastSyncTime || lastSyncTime <= 0) return "Never";
    try {
        const d = new Date(lastSyncTime * 1000);
        return d.toLocaleString();
    } catch (_) {
        return "Never";
    }
};

const WebhookIntegrationModal = ({ open, onClose, onSave, initialEndpoint = "", initialHeaders = [{ key: "", value: "" }], initialUseGzip = false, lastSyncTime = 0 }) => {
    const [webhookEndpoint, setWebhookEndpoint] = useState(initialEndpoint);
    const [customHeaders, setCustomHeaders] = useState(initialHeaders);
    const [useGzip, setUseGzip] = useState(initialUseGzip);

    useEffect(() => {
        if (open) {
            setWebhookEndpoint(initialEndpoint);
            setCustomHeaders(
                initialHeaders && initialHeaders.length > 0
                    ? initialHeaders
                    : [{ key: "", value: "" }]
            );
            setUseGzip(initialUseGzip);
        }
    }, [open, initialEndpoint, initialHeaders, initialUseGzip]);

    const handleSave = () => {
        const headersToSave = customHeaders.filter((h) => h.key?.trim());
        onSave?.({ webhookEndpoint: webhookEndpoint?.trim() || "", customHeaders: headersToSave, useGzip });
        onClose();
    };

    const isValid = webhookEndpoint?.trim().length > 0;

    return (
        <Modal
            open={open}
            onClose={onClose}
            title="Webhook integration"
            primaryAction={{
                content: "Save",
                onAction: handleSave,
                disabled: !isValid
            }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    {lastSyncTime > 0 && (
                        <Text variant="bodyMd" color="subdued">
                            Last synced: {formatLastSync(lastSyncTime)}
                        </Text>
                    )}
                    <TextField
                        label="Webhook endpoint"
                        value={webhookEndpoint}
                        onChange={setWebhookEndpoint}
                        placeholder="https://your-webhook-endpoint.com/events"
                        helpText="URL to which threat activity data will be sent in batches (every 15 minutes)"
                        autoComplete="url"
                    />
                    <Checkbox
                        label="Use gzip encoding"
                        helpText="Send request body compressed with gzip (Content-Encoding: gzip). If unchecked, payload is sent as plain JSON."
                        checked={useGzip}
                        onChange={setUseGzip}
                    />
                    <CustomHeadersInput
                        customHeaders={customHeaders}
                        setCustomHeaders={setCustomHeaders}
                        description="Add custom headers to be sent with each webhook request (e.g. Authorization, X-API-Key)."
                    />
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
};

export default WebhookIntegrationModal;
