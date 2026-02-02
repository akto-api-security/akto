import React, { useState, useEffect } from "react";
import { Modal, TextField, VerticalStack, Text, Checkbox, BlockStack } from "@shopify/polaris";
import CustomHeadersInput from "../../quick_start/components/CustomHeadersInput";

const CONTEXT_SOURCE_OPTIONS = [
    { value: "API", label: "API" },
    { value: "DAST", label: "DAST" },
    { value: "MCP", label: "MCP" },
    { value: "GEN_AI", label: "Gen AI" },
    { value: "AGENTIC", label: "Agentic" },
    { value: "ENDPOINT", label: "Endpoint" }
];

const formatLastSync = (lastSyncTime) => {
    if (!lastSyncTime || lastSyncTime <= 0) return "Never";
    try {
        const d = new Date(lastSyncTime * 1000);
        return d.toLocaleString();
    } catch (_) {
        return "Never";
    }
};

const WebhookIntegrationModal = ({ open, onClose, onSave, initialEndpoint = "", initialHeaders = [{ key: "", value: "" }], initialContextSources = ["API"], lastSyncTime = 0 }) => {
    const [webhookEndpoint, setWebhookEndpoint] = useState(initialEndpoint);
    const [customHeaders, setCustomHeaders] = useState(initialHeaders);
    const [selectedContextSources, setSelectedContextSources] = useState(initialContextSources);

    useEffect(() => {
        if (open) {
            setWebhookEndpoint(initialEndpoint);
            setCustomHeaders(
                initialHeaders && initialHeaders.length > 0
                    ? initialHeaders
                    : [{ key: "", value: "" }]
            );
            setSelectedContextSources(
                Array.isArray(initialContextSources) && initialContextSources.length > 0
                    ? initialContextSources
                    : ["API"]
            );
        }
    }, [open, initialEndpoint, initialHeaders, initialContextSources]);

    const toggleContextSource = (value) => {
        setSelectedContextSources((prev) =>
            prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]
        );
    };

    const handleSave = () => {
        const headersToSave = customHeaders.filter((h) => h.key?.trim());
        const contextSourcesToSave = selectedContextSources.length > 0 ? selectedContextSources : ["API"];
        onSave?.({ webhookEndpoint: webhookEndpoint?.trim() || "", customHeaders: headersToSave, contextSources: contextSourcesToSave });
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
                        helpText="URL to which guardrail activity data will be sent in batches (gzip, every 15 minutes)"
                        autoComplete="url"
                    />
                    <BlockStack gap="200">
                        <Text variant="bodyMd" fontWeight="semibold">Context sources to sync</Text>
                        <Text variant="bodySm" color="subdued">Threat data for these context sources will be sent to the webhook.</Text>
                        {CONTEXT_SOURCE_OPTIONS.map((opt) => (
                            <Checkbox
                                key={opt.value}
                                label={opt.label}
                                checked={selectedContextSources.includes(opt.value)}
                                onChange={() => toggleContextSource(opt.value)}
                            />
                        ))}
                    </BlockStack>
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
