import React, { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { 
    VerticalStack, 
    Text, 
    Button, 
    HorizontalStack, 
    Card, 
    Box, 
    ChoiceList
} from '@shopify/polaris'
import { CaretDownMinor } from '@shopify/polaris-icons'
import JsonComponent from '../quick_start/components/shared/JsonComponent'
import api from '../agent_team/api'
import { State } from '../agent_team/types'
import { useAgentsStore } from '../agent_team/agents.store'


function OpenApiAgentTester() {
    const [expanded, setExpanded] = useState(true)
    const [logs, setLogs] = useState([])
    const [currentState, setCurrentState] = useState(State.SCHEDULED)
    const [errorMessage, setErrorMessage] = useState('')
    const [isLoading, setIsLoading] = useState(false)
    const [inputMethod, setInputMethod] = useState('sample')
    const [editableJsonData, setEditableJsonData] = useState({})

    const { setCurrentSubprocess, currentProcessId, setCurrentProcessId } = useAgentsStore()
    const pollingIntervalRef = useRef(null)
    
    const sortedLogs = useMemo(() => {
        return [...logs].sort((a, b) => b.eventTimestamp - a.eventTimestamp)
    }, [logs])
    
    const fetchAgentRuns = useCallback(async () => {
        try {
            const response = await api.getAllAgentRuns('DISCOVERY_AGENT')
            if (response.agentRuns && response.agentRuns.length > 0) {
                const latestRun = response.agentRuns[0]
                setCurrentProcessId(latestRun?.id)
            }
        } catch (error) {
            console.error('Error fetching agent runs:', error)
        }
    }, [])
    
    const fetchSubprocesses = useCallback(async (processId) => {
        try {
            const response = await api.getAllSubProcesses({ processId })
            if (response.subprocesses && response.subprocesses.length > 0) {
                const latestSubprocess = response.subprocesses[0]
                setCurrentSubprocess(latestSubprocess)
                setCurrentState(latestSubprocess.state)
                
                if (latestSubprocess.logs) {
                    setLogs(latestSubprocess.logs)
                }
                
                if (latestSubprocess.state === State.RE_ATTEMPT) {
                    setErrorMessage('Please provide additional configuration for the API endpoints')
                    if (latestSubprocess.processOutput) {
                        setEditableJsonData(latestSubprocess.processOutput)
                    }
                }
            }
        } catch (error) {
            console.error('Error fetching subprocesses:', error)
        }
    }, [])
    
    const pollApi = useCallback(async () => {
        try {
            setIsLoading(true)
            
            if (currentState === State.RUNNING && currentProcessId) {
                await fetchSubprocesses(currentProcessId)
            } else if (currentProcessId == null || currentProcessId === "") {
                await fetchAgentRuns()
            }
            
        } catch (error) {
            console.error('Polling error:', error)
        } finally {
            setIsLoading(false)
        }
    }, [currentState, currentProcessId, fetchSubprocesses, fetchAgentRuns])
    
    useEffect(() => {
        if (currentState !== State.RE_ATTEMPT) {
            pollingIntervalRef.current = setInterval(pollApi, 2000)
        } else {
            if (pollingIntervalRef.current) {
                clearInterval(pollingIntervalRef.current)
                pollingIntervalRef.current = null
            }
        }
        
        return () => {
            if (pollingIntervalRef.current) {
                clearInterval(pollingIntervalRef.current)
            }
        }
    }, [currentState, pollApi])
    
    useEffect(() => {
        fetchAgentRuns()
    }, [fetchAgentRuns])
    
    const handleJsonChange = useCallback((newJsonData) => {
        setEditableJsonData(newJsonData)
    }, [])
    
    const handleRetry = async (type) => {
        
    }
    
    const handleInputMethodChange = useCallback((value) => {
        setInputMethod(value)
    }, [])
    
    return (
        <VerticalStack gap="4">
            <Card>
                <Box padding="3">
                    <VerticalStack gap="2">
                        <Button 
                            variant="plain" 
                            fullWidth
                            onClick={() => setExpanded(!expanded)}
                            textAlign="left"
                        >
                            <HorizontalStack gap="2" align="start">
                                <motion.div animate={{ rotate: expanded ? 0 : 270 }} transition={{ duration: 0.2 }}>
                                    <CaretDownMinor height={20} width={20} />
                                </motion.div>
                                <Text as="dd">
                                    OpenAPI Agent Tester {isLoading && '(Running...)'}
                                </Text>
                            </HorizontalStack>
                        </Button>

                        <AnimatePresence>
                            <motion.div 
                                animate={expanded ? "open" : "closed"} 
                                variants={{ 
                                    open: { height: "auto", opacity: 1 }, 
                                    closed: { height: 0, opacity: 0 } 
                                }} 
                                transition={{ duration: 0.2 }} 
                                style={{ overflow: "hidden" }}
                            >
                                <Box 
                                    padding="2" 
                                    borderRadius="2"
                                    maxHeight="45vh"
                                    style={{ 
                                        overflow: "auto",
                                        backgroundColor: "var(--p-color-bg-surface-secondary)"
                                    }}
                                >
                                    <VerticalStack gap="1">
                                        <AnimatePresence initial={false}>
                                            {sortedLogs.map((log, index) => (
                                                <motion.div 
                                                    key={`${index}-${log.log}-${log.eventTimestamp}`} 
                                                    initial={{ opacity: 0, y: -10 }} 
                                                    animate={{ opacity: 1, y: 0 }} 
                                                    transition={{ duration: 0.2 }}
                                                >
                                                    <Box padding="1">
                                                        <Text variant="bodySm" tone="subdued">
                                                            {log.log}
                                                        </Text>
                                                    </Box>
                                                </motion.div>
                                            ))}
                                        </AnimatePresence>
                                    </VerticalStack>
                                </Box>
                            </motion.div>
                        </AnimatePresence>
                    </VerticalStack>
                </Box>
            </Card>

            {currentState === State.RE_ATTEMPT && (
                <VerticalStack gap="4">
                    <Card>
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <Text variant="headingMd" as="h3">
                                    Configuration Required
                                </Text>
                                
                                <Box padding="3" style={{ backgroundColor: "var(--p-color-bg-surface-critical-subdued)" }}>
                                    <Text variant="bodyMd" tone="critical">
                                        {errorMessage}
                                    </Text>
                                </Box>

                                <ChoiceList
                                    title="Choose input method:"
                                    choices={[
                                        { label: 'Use sample data', value: 'sample' },
                                        { label: 'Provide custom JSON input', value: 'custom' }
                                    ]}
                                    selected={[inputMethod]}
                                    onChange={handleInputMethodChange}
                                />
                                <JsonComponent
                                    dataString={JSON.stringify(editableJsonData, null, 2)}
                                    onClickFunc={() => {}}
                                    title="Request sample as hit by Agent"
                                    language="json"
                                    minHeight="200px"
                                    readOnly={false}
                                    getEditorData={handleJsonChange}
                                />
                                <HorizontalStack gap="2">
                                    <Button primary onClick={() => handleRetry('retry')}>
                                        Retry
                                    </Button>
                                    <Button onClick={() => handleRetry('discard')}>Discard</Button>
                                </HorizontalStack>
                            </VerticalStack>
                        </Box>
                    </Card>
                </VerticalStack>
            )}
        </VerticalStack>
    )
}

export default OpenApiAgentTester