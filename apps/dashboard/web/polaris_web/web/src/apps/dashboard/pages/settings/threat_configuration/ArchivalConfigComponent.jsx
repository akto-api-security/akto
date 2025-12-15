import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, Divider, Text, Button, Box, Checkbox } from "@shopify/polaris";
import api from "../../../pages/threat_detection/api.js";
import Dropdown from "../../../components/layouts/Dropdown.jsx";

const ArchivalConfigComponent = ({ title, description }) => {
    const [archivalDays, setArchivalDays] = useState(60);
    const [archivalEnabled, setArchivalEnabled] = useState(false);
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);
    const [isToggleChanged, setIsToggleChanged] = useState(false);
    const [isDaysChanged, setIsDaysChanged] = useState(false);

    const fetchData = async () => {
        const response = await api.fetchThreatConfiguration();
        const days = response?.threatConfiguration?.archivalDays;
        const value = days === 30 || days === 60 || days === 90 ? days : 60;
        setArchivalDays(value);

        const enabled = response?.threatConfiguration?.archivalEnabled || false;
        setArchivalEnabled(enabled);

        setIsSaveDisabled(true);
        setIsToggleChanged(false);
        setIsDaysChanged(false);
    };

    const onSave = async () => {
        try {
            // Save archival days if changed
            if (isDaysChanged) {
                const payload = {
                    archivalDays: archivalDays
                };
                await api.modifyThreatConfiguration(payload);
            }

            // Toggle archival enabled if changed
            if (isToggleChanged) {
                await api.toggleArchivalEnabled(archivalEnabled);
            }

            func.setToast(true, false, "Archival configuration saved successfully");
            fetchData();
        } catch (error) {
            func.setToast(true, true, "Error saving archival configuration");
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const options = [
        { value: 30, label: "30 days" },
        { value: 60, label: "60 days" },
        { value: 90, label: "90 days" },
    ];

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

    const onChange = (val) => {
        setArchivalDays(val);
        setIsDaysChanged(true);
        setIsSaveDisabled(false);
    };

    const onToggleEnabled = (val) => {
        setArchivalEnabled(val);
        setIsToggleChanged(true);
        setIsSaveDisabled(false);
    };

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
                    <Checkbox
                        label="Enable archival cron"
                        checked={archivalEnabled}
                        onChange={onToggleEnabled}
                        helpText="When enabled, malicious events older than the configured archival time will be automatically archived."
                    />
                    <Box width="200px">
                        <Dropdown
                            menuItems={options}
                            selected={(val) => onChange(val)}
                            label="Archival Time"
                            initial={() => archivalDays}
                        />
                    </Box>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );
};

export default ArchivalConfigComponent;


