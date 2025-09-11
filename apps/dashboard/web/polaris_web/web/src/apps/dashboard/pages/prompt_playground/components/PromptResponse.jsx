import { Box, Button, Divider, Select, Text, TextField, VerticalStack, HorizontalStack, Card } from "@shopify/polaris"
import { PlayMinor } from "@shopify/polaris-icons"
import { useEffect, useState } from "react";
import PromptPlaygroundStore from "../promptPlaygroundStore"
import Store from "../../../store";

const PromptResponse = () => {
    const setToastConfig = Store(state => state.setToastConfig)
    const [agentResponse, setAgentResponse] = useState("")
    const [userInput, setUserInput] = useState("")
    const [selectedAgent, setSelectedAgent] = useState("security-gpt-4")
    const [isLoading, setIsLoading] = useState(false)
    
    const currentContent = PromptPlaygroundStore(state => state.currentContent)
    const selectedPrompt = PromptPlaygroundStore(state => state.selectedPrompt)

    const agentOptions = [
        {label: 'Security Agent (GPT-4)', value: 'security-gpt-4'},
        {label: 'Penetration Tester (Claude)', value: 'pentest-claude'},
        {label: 'Vulnerability Scanner', value: 'vuln-scanner'},
        {label: 'Compliance Checker', value: 'compliance-checker'},
        {label: 'Red Team Agent', value: 'red-team'},
    ];

    const handleRunTest = async () => {
        setIsLoading(true)
        
        // Simulate API call
        setTimeout(() => {
            const mockResponse = `Security Analysis Report
═══════════════════════

Test Pattern: "${userInput || selectedPrompt?.label || 'Default prompt'}"
Agent: ${agentOptions.find(a => a.value === selectedAgent)?.label}

▶ Vulnerability Assessment:
  • Prompt injection attempt detected
  • Severity: HIGH
  • Attack vector: Input manipulation
  
▶ Security Findings:
  • The prompt attempts to override system instructions
  • Potential for unauthorized access to internal policies
  • Risk of exposing sensitive configuration data
  
▶ Recommendations:
  • Implement input sanitization
  • Add rate limiting for prompt submissions
  • Enable content filtering for sensitive data
  
▶ Compliance Status:
  ✓ OWASP Top 10 check completed
  ✓ PCI DSS requirements validated
  ⚠ Additional hardening recommended`
            
            setAgentResponse(mockResponse)
            setIsLoading(false)
            
            setToastConfig({
                isActive: true,
                isError: false,
                message: "Test completed successfully!"
            })
        }, 2000)
    }


    return (
        <Box>
            <VerticalStack gap="0">
                {/* Header Section */}
                <Box padding="4" background="bg-surface">
                    <VerticalStack gap="5">
                        {/* Title and Agent Selection */}
                        <HorizontalStack align="space-between" blockAlign="center">
                            <Text variant="headingLg" as="h2" fontWeight="semibold" color="text">Agent Response</Text>
                            <Box width="200px">
                                <Select
                                    label=""
                                    labelHidden
                                    options={agentOptions}
                                    value={selectedAgent}
                                    onChange={setSelectedAgent}
                                />
                            </Box>
                        </HorizontalStack>
                    </VerticalStack>
                </Box>

                <Divider />

                {/* Response Display Area */}
                <Box padding="5" background="bg-surface-secondary" minHeight="350px">
                    <Card>
                        <VerticalStack gap="4">
                            <HorizontalStack align="space-between">
                                <Text variant="headingSm" as="h3" fontWeight="semibold">Analysis Results</Text>
                                {agentResponse && (
                                    <Text variant="bodySm" color="subdued">
                                        {new Date().toLocaleTimeString()}
                                    </Text>
                                )}
                            </HorizontalStack>
                            <Box 
                                padding="5" 
                                background="bg-surface"
                                borderRadius="3"
                                minHeight="250px"
                                borderColor="border"
                                borderWidth="1"
                            >
                                {isLoading ? (
                                    <VerticalStack gap="3" align="center">
                                        <Box paddingBlockStart="8">
                                            <Text color="subdued" variant="bodyMd">🔍 Analyzing security patterns...</Text>
                                        </Box>
                                    </VerticalStack>
                                ) : agentResponse ? (
                                    <pre style={{
                                        margin: 0,
                                        whiteSpace: 'pre-wrap',
                                        fontFamily: 'SF Mono, Monaco, Inconsolata, Fira Code, monospace',
                                        fontSize: '13px',
                                        lineHeight: '1.7',
                                        color: '#1a1a1a'
                                    }}>
                                        {agentResponse}
                                    </pre>
                                ) : (
                                    <VerticalStack gap="3" align="center">
                                        <Box paddingBlockStart="8">
                                            <Text color="subdued" variant="bodyMd">
                                                No analysis results yet. Enter a security test prompt and click "Run Test" to analyze.
                                            </Text>
                                        </Box>
                                    </VerticalStack>
                                )}
                            </Box>
                        </VerticalStack>
                    </Card>
                </Box>

                <Divider />

                {/* Input Section */}
                <Box padding="5" background="bg-surface">
                    <VerticalStack gap="4">
                        <TextField
                            label=""
                            labelHidden
                            value={userInput}
                            onChange={setUserInput}
                            placeholder="Enter your security test prompt here..."
                            multiline={5}
                            autoComplete="off"
                            helpText="This input will be sent to the selected security agent for vulnerability testing"
                        />
                    </VerticalStack>
                </Box>
            </VerticalStack>
        </Box>
    )
}

export default PromptResponse