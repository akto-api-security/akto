import { Box, Button, ButtonGroup, Divider, Select, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState, useMemo } from 'react';
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';

/**
 * Common component for AI Agent Connector imports (N8N, Langchain, Copilot Studio)
 */
const AIAgentConnectorImport = ({
    connectorType,
    connectorName,
    description,
    fields,
    docsUrl,
    recurringIntervalSeconds = 300,
    onSuccess
}) => {
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState(() => {
        const initial = fields.reduce((acc, field) => {
            if (field.defaultValue !== undefined) {
                acc[field.name] = field.defaultValue;
            } else {
                acc[field.name] = '';
            }
            return acc;
        }, {});
        return initial;
    });

    // Get auth type for conditional field rendering (for Snowflake)
    const authType = formData.snowflakeAuthType || 'PASSWORD';

    // Filter fields based on showWhen condition
    const visibleFields = useMemo(() => {
        return fields.filter(field => {
            if (field.showWhen && typeof field.showWhen === 'function') {
                return field.showWhen(authType);
            }
            return true;
        });
    }, [fields, authType]);


    const validateForm = () => {
        for (const field of visibleFields) {
            // Skip validation for optional fields
            if (field.required === false) {
                continue;
            }
            
            const value = formData[field.name];
            if (!value || (typeof value === 'string' && value.trim().length === 0)) {
                func.setToast(true, true, `Please enter a valid ${field.label}.`);
                return false;
            }
        }
        return true;
    };

    const buildConnectorConfig = () => {
        const config = {};
        visibleFields.forEach(field => {
            if (field.configKey) {
                const value = formData[field.name];
                // Only include non-empty values (skip empty optional fields)
                if (value && (typeof value === 'string' ? value.trim().length > 0 : true)) {
                    config[field.configKey] = value;
                }
            }
        });
        return config;
    };

    const primaryAction = () => {
        if (!validateForm()) {
            return;
        }

        setLoading(true);
        const connectorConfig = buildConnectorConfig();
        const dataIngestionUrl = formData.dataIngestionUrl;

        api.initiateAIAgentConnectorImport(
            connectorType,
            connectorConfig,
            dataIngestionUrl,
            recurringIntervalSeconds
        ).then((res) => {
            func.setToast(true, false, `${connectorName} Import initiated successfully. Please check your dashboard for updates.`);
            if (onSuccess) {
                onSuccess(res);
            }
        }).catch((err) => {
            console.error(`Error initiating ${connectorName} import:`, err);
            func.setToast(true, true, `Ensure that you have added the correct ${connectorName} credentials.`);
        }).finally(() => {
            setLoading(false);
            // Reset form
            setFormData(fields.reduce((acc, field) => ({ ...acc, [field.name]: '' }), {}));
        });
    };

    const isFormValid = () => {
        return visibleFields.every(field => {
            if (field.required === false) {
                return true; // Optional fields don't need validation
            }
            const value = formData[field.name];
            return value && (typeof value === 'string' ? value.trim().length > 0 : true);
        });
    };

    const goToDocs = () => {
        window.open(docsUrl);
    };

    const updateField = (fieldName, value) => {
        setFormData(prev => ({ ...prev, [fieldName]: value }));
    };

    return (
        <div className='card-items'>
            <Text variant='bodyMd'>
                {description}
            </Text>

            <Box paddingBlockStart={3}><Divider /></Box>

            <VerticalStack gap="2">
                {visibleFields.map((field) => {
                    // Handle Select dropdown
                    if (field.type === 'select') {
                        return (
                            <Select
                                key={field.name}
                                label={field.label}
                                options={field.options || []}
                                value={formData[field.name] || field.defaultValue || ''}
                                onChange={(value) => updateField(field.name, value)}
                                helpText={field.helpText}
                            />
                        );
                    }

                    // Handle password fields
                    if (field.type === 'password') {
                        return (
                            <PasswordTextField
                                key={field.name}
                                label={field.label}
                                setField={(value) => updateField(field.name, value)}
                                onFunc={true}
                                field={formData[field.name]}
                                placeholder={field.placeholder}
                            />
                        );
                    }

                    // Handle textarea fields
                    if (field.type === 'textarea') {
                        return (
                            <TextField
                                key={field.name}
                                label={field.label}
                                value={formData[field.name] || ''}
                                onChange={(value) => updateField(field.name, value)}
                                placeholder={field.placeholder}
                                multiline={field.multiline || 4}
                                helpText={field.helpText}
                            />
                        );
                    }

                    // Handle regular text fields
                    return (
                        <TextField
                            key={field.name}
                            label={field.label}
                            value={formData[field.name] || ''}
                            type={field.type || 'text'}
                            onChange={(value) => updateField(field.name, value)}
                            placeholder={field.placeholder}
                            helpText={field.helpText}
                        />
                    );
                })}

                <ButtonGroup>
                    <Button
                        onClick={primaryAction}
                        primary
                        disabled={!isFormValid()}
                        loading={loading}
                    >
                        Import
                    </Button>
                    <Button onClick={goToDocs}>Go to docs</Button>
                </ButtonGroup>
            </VerticalStack>
        </div>
    );
};

export default AIAgentConnectorImport;
