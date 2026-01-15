import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, Divider, Text, Box, TextField, HorizontalGrid } from "@shopify/polaris";

const ModuleEnvConfigComponent = ({ title, description, module, allowedEnvFields, onSaveEnv }) => {
    const [envData, setEnvData] = useState({});
    const [initialEnvData, setInitialEnvData] = useState({});
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);

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
                    {allowedEnvFields && allowedEnvFields.map((field) => {
                        const fieldValue = envData[field.key] || "";
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
