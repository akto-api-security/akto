import { useState, useEffect } from "react";
import func from "@/util/func"
import { LegacyCard, VerticalStack, HorizontalStack, Divider, Text, Button, Box, TextField, HorizontalGrid } from "@shopify/polaris";
import api from "../../../pages/threat_detection/api.js";
import Dropdown from "../../../components/layouts/Dropdown.jsx";
import { DeleteMinor } from "@shopify/polaris-icons"

// Constants for rule types
const RULE_TYPES = {
    DEFAULT: 'DEFAULT',
    CUSTOM: 'CUSTOM'
};

// Constants for behaviours
const BEHAVIOURS = {
    STATIC: 'STATIC',
    DYNAMIC: 'DYNAMIC'
};

// Constants for actions
const ACTIONS = {
    BLOCK: 'BLOCK'
};

const RatelimitConfigComponent = ({ title, description }) => {
    const [ratelimitRules, setRatelimitRules] = useState([]);
    const [isSaveDisabled, setIsSaveDisabled] = useState(true);

    const fetchData = async () => {
        const response = await api.fetchThreatConfiguration();
        const rules = response?.threatConfiguration?.ratelimitConfig?.rules || [];
        setRatelimitRules(rules);
    };

    const onSave = async () => {
        // Ensure type is set correctly based on index
        const rulesWithType = ratelimitRules.map((rule, index) => ({
            ...rule,
            type: index === 0 ? RULE_TYPES.DEFAULT : RULE_TYPES.CUSTOM
        }));
        
        const payload = {
            ratelimitConfig: {
                rules: rulesWithType
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
        const newRule = ratelimitRules.length === 0 
            ? {
                // First rule - use Global Rate Limit Rule defaults
                name: "Global Rate Limit Rule", 
                period: 5, 
                maxRequests: 100, 
                mitigationPeriod: 5,
                action: ACTIONS.BLOCK,
                type: RULE_TYPES.DEFAULT,
                behaviour: BEHAVIOURS.DYNAMIC,
                autoThreshold: {
                    percentile: "p75",
                    overflowPercentage: 20,
                    baselinePeriod: 2
                },
                rateLimitConfidence: 0.5
            }
            : {
                // Subsequent rules - empty name and custom type
                name: "", 
                period: 5, 
                maxRequests: 1000, 
                mitigationPeriod: 5,
                action: ACTIONS.BLOCK,
                type: RULE_TYPES.CUSTOM,
                behaviour: BEHAVIOURS.STATIC,
                rateLimitConfidence: 0.5
            };
        
        setRatelimitRules([...ratelimitRules, newRule]);
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
            // Convert to float for rateLimitConfidence
            if (field === 'rateLimitConfidence') {
                value = parseFloat(value) || 0.5;
            }
            // Handle nested autoThreshold fields
            if (field.startsWith('autoThreshold.')) {
                const subField = field.split('.')[1];
                updatedRules[index] = {
                    ...updatedRules[index],
                    autoThreshold: {
                        ...updatedRules[index].autoThreshold,
                        [subField]: ['overflowPercentage', 'baselinePeriod'].includes(subField) ? parseInt(value) || 0 : value
                    }
                };
            } else {
                updatedRules[index] = { ...updatedRules[index], [field]: value };
            }
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
        const hasEmptyFields = rules.some(rule => {
            // Basic required fields
            if (!rule.name || !rule.period || !rule.action) {
                return true;
            }
            // If behaviour is static, maxRequests is required
            if (rule.behaviour === BEHAVIOURS.STATIC) {
                if (!rule.maxRequests) {
                    return true;
                }
            }
            // If behaviour is dynamic, autoThreshold fields are required
            if (rule.behaviour === BEHAVIOURS.DYNAMIC) {
                if (!rule.autoThreshold?.percentile || !rule.autoThreshold?.overflowPercentage) {
                    return true;
                }
            }
            return false;
        });
        setIsSaveDisabled(hasEmptyFields);
    };

    const periodOptions = [
        { label: '5 minutes', value: 5 },
        { label: '15 minutes', value: 15 },
        { label: '30 minutes', value: 30 },
    ];

    const actionOptions = [
        { label: 'Block', value: ACTIONS.BLOCK },
    ];

    
    const behaviourOptions = [
        { label: 'Static', value: BEHAVIOURS.STATIC },
        { label: 'Dynamic', value: BEHAVIOURS.DYNAMIC },
    ];
    
    const percentileOptions = [
        { label: '90th Percentile', value: 'p90' },
        { label: '75th Percentile', value: 'p75' },
        { label: '50th Percentile', value: 'p50' },
    ];
    
    const baselinePeriodOptions = [
        { label: '1 day', value: 1 },
        { label: '2 days', value: 2 },
        { label: '3 days', value: 3 },
        { label: '4 days', value: 4 },
        { label: '5 days', value: 5 },
        { label: '6 days', value: 6 },
        { label: '7 days', value: 7 },
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
                                
                                <HorizontalGrid columns={2} gap="4">
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
                                </HorizontalGrid>
                                
                                <HorizontalGrid columns={2} gap="4">
                                    <Dropdown
                                        menuItems={actionOptions}
                                        selected={(val) => handleInputChange(index, 'action', val)}
                                        label="Action"
                                        initial={() => rule.action}
                                    />
                                    
                                    <Dropdown
                                        menuItems={behaviourOptions}
                                        selected={(val) => handleInputChange(index, 'behaviour', val)}
                                        label="Behaviour"
                                        initial={() => rule.behaviour || BEHAVIOURS.DYNAMIC}
                                    />
                                </HorizontalGrid>
                                
                                {rule.behaviour === BEHAVIOURS.STATIC && (
                                    <Box padding="4" background="bg-surface-secondary" borderRadius="2">
                                        <VerticalStack gap="4">
                                            <Text variant="headingSm">Static Threshold Settings</Text>
                                            <TextField
                                                label="Max Requests"
                                                type="number"
                                                value={String(rule.maxRequests || 100)}
                                                onChange={(value) => handleInputChange(index, 'maxRequests', value)}
                                                placeholder="e.g., 100"
                                                requiredIndicator={true}
                                            />
                                            <TextField
                                                label="Rate Limit Confidence"
                                                type="number"
                                                value={String(rule.rateLimitConfidence !== undefined ? rule.rateLimitConfidence : 0.5)}
                                                onChange={(value) => handleInputChange(index, 'rateLimitConfidence', value)}
                                                placeholder="e.g., 0.5"
                                                helpText="Confidence level between 0 and 1"
                                                min="0"
                                                max="1"
                                                step="0.1"
                                            />
                                        </VerticalStack>
                                    </Box>
                                )}
                                
                                {rule.behaviour === BEHAVIOURS.DYNAMIC && (
                                    <Box padding="4" background="bg-surface-secondary" borderRadius="2">
                                        <VerticalStack gap="4">
                                            <Text variant="headingSm">Dynamic Threshold Settings</Text>
                                            <Dropdown
                                                menuItems={baselinePeriodOptions}
                                                selected={(val) => handleInputChange(index, 'autoThreshold.baselinePeriod', val)}
                                                label="Baseline Period (days)"
                                                initial={() => rule.autoThreshold?.baselinePeriod || 2}
                                            />
                                            <HorizontalGrid columns={2} gap="4">
                                                <Dropdown
                                                    menuItems={percentileOptions}
                                                    selected={(val) => handleInputChange(index, 'autoThreshold.percentile', val)}
                                                    label="Percentile"
                                                    initial={() => rule.autoThreshold?.percentile || 'p75'}
                                                />
                                                
                                                <TextField
                                                    label="Overflow Percentage"
                                                    type="number"
                                                    value={String(rule.autoThreshold?.overflowPercentage || 20)}
                                                    onChange={(value) => handleInputChange(index, 'autoThreshold.overflowPercentage', value)}
                                                    placeholder="e.g., 20"
                                                    suffix="%"
                                                />
                                            </HorizontalGrid>
                                            <TextField
                                                label="Rate Limit Confidence"
                                                type="number"
                                                value={String(rule.rateLimitConfidence !== undefined ? rule.rateLimitConfidence : 0.5)}
                                                onChange={(value) => handleInputChange(index, 'rateLimitConfidence', value)}
                                                placeholder="e.g., 0.5"
                                                helpText="Confidence level between 0 and 1"
                                                min="0"
                                                max="1"
                                                step="0.1"
                                            />
                                        </VerticalStack>
                                    </Box>
                                )}
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