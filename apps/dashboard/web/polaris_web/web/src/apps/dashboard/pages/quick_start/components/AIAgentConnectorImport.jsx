import { Box, Button, ButtonGroup, Divider, Text, TextField, VerticalStack } from '@shopify/polaris';
import React, { useState } from 'react';
import api from '../api';
import func from "@/util/func";
import PasswordTextField from '../../../components/layouts/PasswordTextField';
import GoToDocsButton from './shared/GoToDocsButton';

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
    const [formData, setFormData] = useState(
        fields.reduce((acc, field) => ({ ...acc, [field.name]: '' }), {})
    );


    const validateForm = () => {
        for (const field of fields) {
            const value = formData[field.name];
            if (!value || value.length === 0) {
                func.setToast(true, true, `Please enter a valid ${field.label}.`);
                return false;
            }
        }
        return true;
    };

    const buildConnectorConfig = () => {
        const config = {};
        fields.forEach(field => {
            if (field.configKey) {
                config[field.configKey] = formData[field.name];
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
        return fields.every(field => formData[field.name]?.length > 0);
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
                {fields.map((field) => {
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

                    return (
                        <TextField
                            key={field.name}
                            label={field.label}
                            value={formData[field.name]}
                            type={field.type || 'text'}
                            onChange={(value) => updateField(field.name, value)}
                            placeholder={field.placeholder}
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
                    <GoToDocsButton docsUrl={docsUrl} />
                </ButtonGroup>
            </VerticalStack>
        </div>
    );
};

export default AIAgentConnectorImport;
