import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, HorizontalStack, Divider, Text, Button, Box, TextField, HorizontalGrid } from "@shopify/polaris";
import api from "../../../pages/threat_detection/api.js";
import Dropdown from "../../../components/layouts/Dropdown.jsx";
import { DeleteMinor } from "@shopify/polaris-icons"

const RatelimitConfigComponent = ({ title, description }) => {
    const [ratelimitRules, setRatelimitRules] = useState([]);
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);

    const fetchData = async () => {
        const response = await api.fetchThreatConfiguration();
        const rules = response?.threatConfiguration?.ratelimitConfig?.rules || [];
        setRatelimitRules(rules);
    };

    const onSave = async () => {
        const payload = {
            ratelimitConfig: {
                rules: ratelimitRules
            }
        };
        await api.modifyThreatConfiguration(payload).then(() => {
            try {
                func.setToast(true, false, "Rate limit configuration saved successfully");
                fetchData()
            } catch (error) {
                func.setToast(true, true, "Error saving rate limit configuration");
            }
        });
    };

    const addRatelimitRule = () => {
        setRatelimitRules([...ratelimitRules, { 
            name: "", 
            period: 5, 
            maxRequests: 100, 
            mitigationPeriod: 15,
            action: "block",
            type: "default"
        }]);
    };

    useEffect(() => {
        fetchData().then(() => {
            validateSaveButton(ratelimitRules);
        });
    }, []);

    useEffect(() => {
        validateSaveButton(ratelimitRules);
    }, [ratelimitRules]);

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

    const handleInputChange = (index, field, value) => {
        setRatelimitRules((prevRules) => {
            const updatedRules = [...prevRules];
            // Convert to number for numeric fields
            if (['period', 'maxRequests', 'mitigationPeriod'].includes(field)) {
                value = parseInt(value) || 0;
            }
            updatedRules[index] = { ...updatedRules[index], [field]: value };
            validateSaveButton(updatedRules);
            return updatedRules;
        });
    };

    const handleDelete = (index) => {
        setRatelimitRules((prevRules) => {
            const updatedRules = [...prevRules];
            updatedRules.splice(index, 1);
            validateSaveButton(updatedRules);
            return updatedRules;
        });
    };

    const validateSaveButton = (rules) => {
        const hasEmptyFields = rules.some(rule => 
            !rule.name || 
            !rule.period || 
            !rule.maxRequests || 
            !rule.mitigationPeriod ||
            !rule.action
        );
        setIsSaveDisabled(hasEmptyFields);
    };

    const periodOptions = [
        { label: '5 minutes', value: 5 },
        { label: '15 minutes', value: 15 },
        { label: '30 minutes', value: 30 },
    ];

    const actionOptions = [
        { label: 'Block', value: 'block' },
    ];

    const typeOptions = [
        { label: 'Default', value: 'default' },
        { label: 'Custom', value: 'custom' },
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
                    {ratelimitRules.map((rule, index) => (
                        <Box key={index} padding="4" background="bg-surface-secondary" borderRadius="2">
                            <VerticalStack gap="4">
                                <HorizontalStack align="space-between">
                                    <Text variant="headingSm">Rule {index + 1}</Text>
                                    <Button
                                        icon={DeleteMinor}
                                        onClick={() => handleDelete(index)}
                                        accessibilityLabel="Delete rule"
                                        plain
                                    />
                                </HorizontalStack>
                                
                                <HorizontalGrid columns={3} gap="4">
                                    <TextField
                                        label="Rule Name"
                                        value={rule.name}
                                        onChange={(value) => handleInputChange(index, 'name', value)}
                                        placeholder="e.g., API Rate Limit"
                                        requiredIndicator={true}
                                    />
                                    
                                    <Dropdown
                                        menuItems={periodOptions}
                                        selected={(val) => handleInputChange(index, 'period', val)}
                                        label="Period (minutes)"
                                        initial={() => rule.period}
                                    />
                                    
                                    <TextField
                                        label="Max Requests"
                                        type="number"
                                        value={String(rule.maxRequests)}
                                        onChange={(value) => handleInputChange(index, 'maxRequests', value)}
                                        placeholder="e.g., 100"
                                        requiredIndicator={true}
                                    />
                                </HorizontalGrid>
                                
                                <HorizontalGrid columns={3} gap="4">
                                    <TextField
                                        label="Mitigation Period (minutes)"
                                        type="number"
                                        value={String(rule.mitigationPeriod)}
                                        onChange={(value) => handleInputChange(index, 'mitigationPeriod', value)}
                                        placeholder="e.g., 15"
                                        requiredIndicator={true}
                                    />
                                    
                                    <Dropdown
                                        menuItems={actionOptions}
                                        selected={(val) => handleInputChange(index, 'action', val)}
                                        label="Action"
                                        initial={() => rule.action}
                                    />
                                    
                                    <Dropdown
                                        menuItems={typeOptions}
                                        selected={(val) => handleInputChange(index, 'type', val)}
                                        label="Type"
                                        initial={() => rule.type}
                                    />
                                </HorizontalGrid>
                            </VerticalStack>
                        </Box>
                    ))}
                    
                    <Box paddingBlockStart="2">
                        <Button onClick={addRatelimitRule}>Add Rate Limit Rule</Button>
                    </Box>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    );
};

export default RatelimitConfigComponent;