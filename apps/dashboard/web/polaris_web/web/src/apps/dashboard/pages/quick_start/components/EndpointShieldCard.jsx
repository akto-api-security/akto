import React from 'react';
import { Card, VerticalStack, HorizontalStack, Text, Button, Box, Avatar } from '@shopify/polaris';
import '../QuickStart.css';

function EndpointShieldCard({ onInstall, onSeeDocs }) {
    // IDE icons
    const ideIcons = [
        { name: 'VSCode', icon: '/public/vscode.svg' },
        { name: 'Windsurf', icon: '/public/windsurf.svg' },
        { name: 'Cursor', icon: '/public/cursor.svg' },
        { name: 'Antigravity', icon: '/public/antigravity.svg' },
        { name: 'Claude Code', icon: '/public/claude-code.svg' }
    ];

    // Browser icons
    const browserIcons = [
        { name: 'Chrome', icon: '/public/chrome.svg' },
        { name: 'Firefox', icon: '/public/firefox.svg' },
        { name: 'Safari', icon: '/public/safari.svg' },
        { name: 'Brave', icon: '/public/brave.svg' }
    ];

    // Agentic Connector icons
    const agenticIcons = [
        { name: 'Microsoft Copilot', icon: '/public/microsoft-copilot.svg' },
        { name: 'n8n', icon: '/public/n8n.svg' },
        { name: 'Langchain', icon: '/public/langchain.svg' },
        { name: 'Bedrock', icon: '/public/bedrock.svg' }
    ];

    const renderIconRow = (icons, label) => (
        <VerticalStack gap="2">
            <Text variant="bodyMd" as="p" fontWeight="medium" color="subdued">{label}</Text>
            <HorizontalStack gap="2" align="start">
                {icons.map((item, index) => (
                    <Avatar
                        key={index}
                        customer
                        size="extraSmall"
                        name={item.name}
                        source={item.icon}
                        shape="square"
                    />
                ))}
            </HorizontalStack>
        </VerticalStack>
    );

    return (
        <Box className="endpoint-shield-card-wrapper">
            <Card>
                <Box className="endpoint-shield-card">
                    <VerticalStack gap="5">
                        <HorizontalStack gap="3" align="start">
                            <Box padding="2" borderWidth="1" borderColor="border-subdued" borderRadius="2">
                                <Avatar
                                    customer
                                    size="medium"
                                    name="Endpoint Shield"
                                    source="/public/mcp.svg"
                                    shape="square"
                                />
                            </Box>
                            <VerticalStack gap="2">
                                <Text variant="headingMd" as="h5">Endpoint Shield</Text>
                                <Box className="endpoint-shield-description">
                                    <Text variant="bodyMd" color="subdued">
                                        All-in-one protection covering IDEs, browsers, MCP servers, and agent workflows with runtime security and auto-discovery requiring no configuration changes.
                                    </Text>
                                </Box>
                            </VerticalStack>
                        </HorizontalStack>

                        <VerticalStack gap="10">
                            <Box
                                background="bg-surface-secondary"
                                borderRadius="2"
                            >
                                <HorizontalStack gap="5" align="start">
                                    {renderIconRow(ideIcons, 'IDEs')}
                                    <Box className="endpoint-shield-divider" />
                                    {renderIconRow(browserIcons, 'Browsers')}
                                    <Box className="endpoint-shield-divider" />
                                    {renderIconRow(agenticIcons, 'Agentic Connectors')}
                                </HorizontalStack>
                            </Box>

                            <HorizontalStack gap="4" align="start">
                                <Button onClick={onInstall}>Install Endpoint Shield</Button>
                                <Button plain onClick={onSeeDocs}>See Docs</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </Card>
        </Box>
    );
}

export default EndpointShieldCard;
