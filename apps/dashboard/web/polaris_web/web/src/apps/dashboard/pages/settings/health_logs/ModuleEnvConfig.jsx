import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, Divider, Text, Box, TextField, HorizontalGrid } from "@shopify/polaris";

const ModuleEnvConfigComponent = ({ title, description, module, onSaveEnv }) => {
    const [envData, setEnvData] = useState({});
    const [initialEnvData, setInitialEnvData] = useState({});
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);

    const envFields = [
        { key: "AKTO_KAFKA_BROKER_MAL", label: "Kafka Broker MAL" },
        { key: "AKTO_KAFKA_BROKER_URL", label: "Kafka Broker URL" },
        { key: "AKTO_TRAFFIC_BATCH_SIZE", label: "Traffic Batch Size" },
        { key: "AKTO_TRAFFIC_BATCH_TIME_SECS", label: "Traffic Batch Time (Seconds)" },
        { key: "AKTO_LOG_LEVEL", label: "Log Level" },
        { key: "DEBUG_URLS", label: "Debug URLs" },
        { key: "AKTO_K8_METADATA_CAPTURE", label: "K8 Metadata Capture" },
        { key: "AKTO_THREAT_ENABLED", label: "Threat Enabled" },
        { key: "AKTO_IGNORE_ENVOY_PROXY_CALLS", label: "Ignore Envoy Proxy Calls" },
        { key: "AKTO_IGNORE_IP_TRAFFIC", label: "Ignore IP Traffic" },
    ];

    useEffect(() => {
        if (module?.additionalData?.env) {
            const initialData = { ...module.additionalData.env };
            setEnvData(initialData);
            setInitialEnvData(initialData);
        }
    }, [module]);

    useEffect(() => {
        const hasChanges = JSON.stringify(envData) !== JSON.stringify(initialEnvData);
        setIsSaveDisabled(!hasChanges);
    }, [envData, initialEnvData]);

    const handleInputChange = (key, value) => {
        setEnvData((prev) => ({
            ...prev,
            [key]: value
        }));
    };

    const handleSave = async () => {
        try {
            await onSaveEnv(module.id, module.name, envData);
            setInitialEnvData({ ...envData });
        } catch (error) {
            func.setToast(true, true, "Error saving environment config");
        }
    };

    function TitleComponent({ title, description }) {
        return (
            <Box paddingBlockEnd="4">
                <Text variant="headingMd">{title}</Text>
                <Box paddingBlockStart="2">
                    <Text variant="bodyMd">{description}</Text>
                </Box>
            </Box>
        )
    }

    return (
        <LegacyCard
            title={<TitleComponent title={title} description={description} />}
            primaryFooterAction={{
                content: 'Save',
                onAction: handleSave,
                loading: false,
                disabled: isSaveDisabled
            }}
        >
            <Divider />
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    {envFields.map((field) => {
                        const fieldValue = envData[field.key] || "";
                        // Only show field if it has a value or if we're showing all fields
                        if (!fieldValue && initialEnvData[field.key] === undefined) {
                            return null;
                        }
                        return (
                            <HorizontalGrid key={field.key} columns={2} gap="4">
                                <Box>
                                    <Text variant="bodyMd" as="p" fontWeight="medium">
                                        {field.label}
                                    </Text>
                                </Box>
                                <TextField
                                    value={fieldValue}
                                    onChange={(value) => handleInputChange(field.key, value)}
                                    placeholder={`Enter ${field.label}`}
                                />
                            </HorizontalGrid>
                        );
                    })}
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );
};

export default ModuleEnvConfigComponent;
