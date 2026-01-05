import { useState } from 'react';
import { Page, Box, Text, VerticalStack, HorizontalStack, Card, Icon } from '@shopify/polaris';
import { BugMajor, ApiMajor, LockMajor, ReportMinor } from '@shopify/polaris-icons';
import SearchInput from './components/SearchInput';
import ConversationPage from './ConversationPage';

/**
 * MainPage - Landing page with example queries
 * Uses only Polaris components, no HTML tags
 */
function MainPage() {
    const [activeView, setActiveView] = useState('main'); // 'main' or 'conversation'
    const [currentQuery, setCurrentQuery] = useState('');
    const [input, setInput] = useState('');

    // Example queries
    const examples = [
        {
            icon: BugMajor,
            title: 'Find Vulnerabilities',
            description: 'Scan for security issues in your APIs',
            query: 'Show me all vulnerabilities in my API endpoints'
        },
        {
            icon: ApiMajor,
            title: 'API Inventory',
            description: 'View all discovered APIs',
            query: 'List all API endpoints and their details'
        },
        {
            icon: LockMajor,
            title: 'Security Posture',
            description: 'Check overall security status',
            query: 'What is my current security posture?'
        },
        {
            icon: ReportMinor,
            title: 'Generate Report',
            description: 'Create security reports',
            query: 'Generate a security assessment report'
        }
    ];

    const handleExampleClick = (query) => {
        setCurrentQuery(query);
        setActiveView('conversation');
    };

    const handleSearchSubmit = (e) => {
        e.preventDefault();
        if (input.trim()) {
            setCurrentQuery(input);
            setInput('');
            setActiveView('conversation');
        }
    };

    const handleBack = () => {
        setActiveView('main');
        setCurrentQuery('');
    };

    // Show conversation view
    if (activeView === 'conversation') {
        return (
            <ConversationPage
                initialQuery={currentQuery}
                onBack={handleBack}
            />
        );
    }

    // Show main landing page
    return (
        <Page fullWidth>
            <Box paddingBlockStart="1600" paddingBlockEnd="1600">
                <Box maxWidth="720px" marginInline="auto">
                    <VerticalStack gap="8">
                        {/* Header */}
                        <VerticalStack gap="4" align="center">
                            <Text variant="heading2xl" as="h1" alignment="center">
                                Agentic AI Assistant
                            </Text>
                            <Text variant="bodyLg" as="p" tone="subdued" alignment="center">
                                Ask questions about your API security, vulnerabilities, and more
                            </Text>
                        </VerticalStack>

                        {/* Search Input */}
                        <SearchInput
                            input={input}
                            handleInputChange={(value) => {
                                const newValue = typeof value === 'string' ? value : value.target?.value || '';
                                setInput(newValue);
                            }}
                            handleSubmit={handleSearchSubmit}
                            isLoading={false}
                            placeholder="Ask anything..."
                            isFixed={false}
                        />

                        {/* Examples Grid */}
                        <VerticalStack gap="4">
                            <Text variant="headingMd" as="h2">
                                Try these examples:
                            </Text>
                            <VerticalStack gap="4">
                                {examples.map((example, index) => (
                                    <Card key={index}>
                                        <Box
                                            padding="400"
                                            onClick={() => handleExampleClick(example.query)}
                                            style={{ cursor: 'pointer' }}
                                        >
                                            <HorizontalStack gap="4" blockAlign="start">
                                                <Box>
                                                    <Icon source={example.icon} tone="base" />
                                                </Box>
                                                <VerticalStack gap="1">
                                                    <Text variant="headingSm" as="h3">
                                                        {example.title}
                                                    </Text>
                                                    <Text variant="bodyMd" as="p" tone="subdued">
                                                        {example.description}
                                                    </Text>
                                                </VerticalStack>
                                            </HorizontalStack>
                                        </Box>
                                    </Card>
                                ))}
                            </VerticalStack>
                        </VerticalStack>

                        {/* Footer Info */}
                        <Box paddingBlockStart="800">
                            <Text variant="bodySm" as="p" tone="subdued" alignment="center">
                                Note: This is a mock implementation for development. No real API calls are made.
                            </Text>
                        </Box>
                    </VerticalStack>
                </Box>
            </Box>
        </Page>
    );
}

export default MainPage;
