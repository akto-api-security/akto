import React, { useState, useEffect } from 'react';
import {
    Page, Layout, Card, Button, Text, VerticalStack, HorizontalStack,
    Banner, Spinner, Checkbox, Badge, Icon
} from '@shopify/polaris';
import {
    ProfileMinor,
    HideMinor,
    LockMinor,
    CircleAlertMajor
} from '@shopify/polaris-icons';
import mcpGuardrailApi from '../api';
import func from '@/util/func';

function GuardrailPolicyPage() {
    const [policy, setPolicy] = useState({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    
    // Guardrail types and actions
    const [guardrailTypes, setGuardrailTypes] = useState({});
    
    // Modal state
    const [selectedGuardrail, setSelectedGuardrail] = useState(null);

    // Icon mapping for guardrail types
    const getGuardrailIcon = (guardrailType) => {
        switch (guardrailType) {
            case 'PII_GUARDRAILS':
                return ProfileMinor;
            case 'WORD_MASK_GUARDRAILS':
                return HideMinor;
            case 'INJECTION_GUARDRAILS':
                return LockMinor;
            case 'RESPONSE_BLOCK':
                return CircleAlertMajor;
            default:
                return LockMinor; // Default fallback
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const fetchData = async () => {
        try {
            setLoading(true);
            
            const [policyResponse, typesResponse] = await Promise.all([
                mcpGuardrailApi.getPolicy(),
                mcpGuardrailApi.getGuardrailTypes(),
            ]);
            
            setPolicy(policyResponse.policy);
            setTimeout(() => {
            }, 1000);
            setGuardrailTypes(typesResponse.guardrailTypes || {});
        } catch (error) {
            console.error('Error fetching data:', error);
            func.setToast(true, true, 'Failed to fetch data. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const handleToggleGuardrail = async (guardrailType, enabled) => {
        try {
            setSaving(true);
            
            const response = await mcpGuardrailApi.toggleGuardrail(guardrailType, enabled);
            func.setToast(true, false, `Guardrail ${enabled ? 'enabled' : 'disabled'} successfully`);
            // Update local state
            setPolicy(prev => ({
                ...prev,
                guardrailConfigs: {
                    ...prev.guardrailConfigs,
                    [guardrailType]: {
                        ...prev.guardrailConfigs[guardrailType],
                        enabled: enabled
                    }
                }
            }));
        } catch (error) {
            func.setToast(true, true, 'Failed to update guardrail. Please try again.');
        } finally {
            setSaving(false);
        }
    };

    const handleTogglePolicy = async () => {
        try {
            setSaving(true);
            
            const response = await mcpGuardrailApi.togglePolicy();
            func.setToast(true, false, `Policy ${response.responseEnabled ? 'enabled' : 'disabled'} successfully`);
            setPolicy(prev => ({ ...prev, active: response.responseEnabled }));
        } catch (error) {
            func.setToast(true, true, 'Failed to update policy. Please try again.');
        } finally {
            setSaving(false);
        }
    };

    const renderGuardrailCard = (guardrailType, config) => {
        const typeInfo = guardrailTypes[guardrailType];
        if (!typeInfo) return null;
        
        const isEnabled = config?.enabled || false;
        const IconComponent = getGuardrailIcon(guardrailType);
        
        return (
            <Card key={guardrailType} sectioned>
                <VerticalStack spacing="loose">
                    {/* Header Section */}
                    <HorizontalStack alignment="center" spacing="loose">
                        {/* Controls Section - LEFT SIDE */}
                        <VerticalStack spacing="loose" alignment="leading">
                            <Checkbox
                                label=""
                                checked={isEnabled}
                                onChange={(checked) => handleToggleGuardrail(guardrailType, checked)}
                                disabled={saving}
                            />
                        </VerticalStack>
                        
                        {/* Right side: Icon, name, badge, description */}
                        <VerticalStack spacing="tight">
                            <HorizontalStack spacing="tight" alignment="center" gap="2">
                                <div style={{ margin: 0 }}>
                                    <Icon source={IconComponent} tone="base" />
                                </div>
                                <Text variant="headingMd" as="h3">
                                    {typeInfo.displayName}
                                </Text>
                                <Badge status={isEnabled ? 'success' : 'info'}>
                                    {isEnabled ? 'Enabled' : 'Disabled'}
                                </Badge>
                            </HorizontalStack>
                            <Text variant="bodyMd" color="subdued">
                                {typeInfo.description}
                            </Text>
                        </VerticalStack>
                    </HorizontalStack>
                    
                    {/* Action Configuration Section - REMOVED */}
                </VerticalStack>
            </Card>
        );
    };

    if (loading) {
        return (
            <Page title="Guardrail Policy">
                <Layout>
                    <Layout.Section>
                        <Card>
                            <div style={{ textAlign: 'center', padding: '3rem' }}>
                                <VerticalStack spacing="loose" alignment="center">
                                    <Spinner size="large" />
                                    <Text variant="bodyMd" color="subdued">Loading guardrail policy...</Text>
                                </VerticalStack>
                            </div>
                        </Card>
                    </Layout.Section>
                </Layout>
            </Page>
        );
    }

    console.log( "policy", policy);

    return (
        <Page
            title="Guardrail Policy"
            subtitle="Configure and manage MCP guardrail policies"
            primaryAction={{
                content: policy?.active ? 'Disable Policy' : 'Enable Policy',
                onAction: handleTogglePolicy,
                loading: saving,
                destructive: policy?.active
            }}
        >
            <Layout>
                <Layout.Section>
                    <VerticalStack spacing="loose" gap="4">
                        {/* Policy Status Card */}
                        <Card sectioned>
                            <VerticalStack spacing="loose">
                                <HorizontalStack alignment="center" distribution="equalSpacing">
                                    <VerticalStack spacing="tight">
                                        <HorizontalStack spacing="tight" alignment="center" gap="2">
                                            <Text variant="headingLg" as="h2">
                                                {policy?.policyName || 'Default MCP Guardrail Policy'}
                                            </Text>
                                            <Badge status={policy?.active ? 'success' : 'info'} size="large">
                                                {policy?.active ? 'Policy Active' : 'Policy Inactive'}
                                            </Badge>
                                        </HorizontalStack>
                                        <Text variant="bodyMd" color="subdued">
                                            {policy?.policyDescription || 'Configure your MCP guardrail policies'}
                                        </Text>
                                    </VerticalStack>
                                </HorizontalStack>
                            </VerticalStack>
                        </Card>
                        
                        {/* Guardrail Cards */}
                        {Object.keys(guardrailTypes).length > 0 && (
                            <VerticalStack spacing="loose" gap="4">
                                {Object.entries(policy?.guardrailConfigs || {}).map(([guardrailType, config]) => {
                                    const typeInfo = guardrailTypes[guardrailType];
                                    if (!typeInfo) return null;
                                    return renderGuardrailCard(guardrailType, config);
                                })}
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </Layout.Section>
            </Layout>
        </Page>
    );
}

export default GuardrailPolicyPage;
