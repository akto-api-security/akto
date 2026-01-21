import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, Divider, Text, Box, TextField, HorizontalGrid } from "@shopify/polaris";
import api from "../../../pages/threat_detection/api.js";
import Dropdown from "../../../components/layouts/Dropdown.jsx";

const ParamEnumerationConfigComponent = ({ title, description }) => {
    const [uniqueParamThreshold, setUniqueParamThreshold] = useState(50);
    const [windowSizeMinutes, setWindowSizeMinutes] = useState(5);
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);
    const [initialConfig, setInitialConfig] = useState(null);

    const fetchData = async () => {
        const response = await api.fetchThreatConfiguration();
        const config = response?.threatConfiguration?.paramEnumerationConfig || {};
        const threshold = config.uniqueParamThreshold || 50;
        const windowSize = config.windowSizeMinutes || 5;

        setUniqueParamThreshold(threshold);
        setWindowSizeMinutes(windowSize);
        setInitialConfig({ uniqueParamThreshold: threshold, windowSizeMinutes: windowSize });
        setIsSaveDisabled(true);
    };

    const onSave = async () => {
        const payload = {
            paramEnumerationConfig: {
                uniqueParamThreshold: uniqueParamThreshold,
                windowSizeMinutes: windowSizeMinutes
            }
        };
        await api.modifyThreatConfiguration(payload).then(() => {
            try {
                func.setToast(true, false, "Param enumeration configuration saved successfully");
                fetchData();
            } catch (error) {
                func.setToast(true, true, "Error saving param enumeration configuration");
            }
        });
    };

    useEffect(() => {
        fetchData();
    }, []);

    useEffect(() => {
        validateSaveButton();
    }, [uniqueParamThreshold, windowSizeMinutes]);

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

    const validateSaveButton = () => {
        // Check if values are valid
        if (!uniqueParamThreshold || uniqueParamThreshold <= 0 || !windowSizeMinutes) {
            setIsSaveDisabled(true);
            return;
        }

        // Check if values have changed from initial
        if (initialConfig) {
            const hasChanges =
                uniqueParamThreshold !== initialConfig.uniqueParamThreshold ||
                windowSizeMinutes !== initialConfig.windowSizeMinutes;
            setIsSaveDisabled(!hasChanges);
        }
    };

    const handleThresholdChange = (value) => {
        const numValue = parseInt(value) || 0;
        setUniqueParamThreshold(numValue);
    };

    const handleWindowSizeChange = (value) => {
        setWindowSizeMinutes(value);
    };

    const windowSizeOptions = [
        { label: '5 minutes', value: 5 },
        { label: '10 minutes', value: 10 },
        { label: '15 minutes', value: 15 },
    ];

    return (
        <LegacyCard title={<TitleComponent title={title} description={description} />}
            primaryFooterAction={{
                content: 'Save',
                onAction: onSave,
                loading: false,
                disabled: isSaveDisabled
            }}
        >
            <Divider />
            <LegacyCard.Section>
                <VerticalStack gap="4">
                    <Box padding="4" background="bg-surface-secondary" borderRadius="2">
                        <VerticalStack gap="4">
                            <HorizontalGrid columns={2} gap="4">
                                <TextField
                                    label="Unique Param Threshold"
                                    type="number"
                                    value={String(uniqueParamThreshold)}
                                    onChange={handleThresholdChange}
                                    placeholder="e.g., 50"
                                    helpText="Number of unique parameter values to trigger detection"
                                    requiredIndicator={true}
                                    min="1"
                                />

                                <Dropdown
                                    menuItems={windowSizeOptions}
                                    selected={handleWindowSizeChange}
                                    label="Window Size (minutes)"
                                    initial={() => windowSizeMinutes}
                                />
                            </HorizontalGrid>
                        </VerticalStack>
                    </Box>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );
};

export default ParamEnumerationConfigComponent;
