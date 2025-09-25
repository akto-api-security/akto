import React, { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { 
    VerticalStack, 
    Text, 
    Button, 
    HorizontalStack, 
    Box, 
    List,
    Badge,
    LegacyCard,
    TextField,
    Scrollable
} from '@shopify/polaris'
import { CaretDownMinor } from '@shopify/polaris-icons'
import SemiCircleProgress from '../../components/shared/SemiCircleProgress'
import ConfigurationRequired from './components/ConfigurationRequired'
import api from '../agent_team/api'
import { State } from '../agent_team/types'
import { useAgentsStore } from '../agent_team/agents.store'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { useParams } from 'react-router-dom'
import PersistStore from '../../../main/PersistStore'

function OpenApiAgentTester() {
    const [currentAgentRun, setCurrentAgentRun] = useState(null)
    const [subprocesses, setSubprocesses] = useState([])
    const [currentSubprocessData, setCurrentSubprocessData] = useState(null)
    const [expandedSubprocesses, setExpandedSubprocesses] = useState(new Set())
    const [editableJsonData, setEditableJsonData] = useState({})
    const [errorMessage, setErrorMessage] = useState('')
    const [hostname, setHostname] = useState('')
    const [authToken, setAuthToken] = useState('')
    const [currentBadErrorId, setCurrentBadErrorId] = useState(null)

    const { currentProcessId, setCurrentProcessId } = useAgentsStore()
    const pollingIntervalRef = useRef(null)
    const agentRunPollingIntervalRef = useRef(null)
    
    const fetchSubprocesses = useCallback(async (id) => {
        try {
            const response = await api.getAllSubProcesses( {processId: id, agent: 'DISCOVERY_AGENT'} )
            if (response.discoverySubProcesses && response.discoverySubProcesses.length > 0) {
                setSubprocesses(response.discoverySubProcesses)
                const latestSubprocess = response.discoverySubProcesses[response.discoverySubProcesses.length - 1]
                setCurrentSubprocessData(latestSubprocess)
                
                if (latestSubprocess.state === State.RE_ATTEMPT) {
                    if (latestSubprocess.badErrors) {
                        // find latest bad error
                        const latestBadError = Object.values(latestSubprocess.badErrors).sort((a, b) => a?.timestamp > b?.timestamp ? 1 : -1)[0]
                        if (latestBadError.url !== currentBadErrorId) {
                            setCurrentBadErrorId(latestBadError.url)
                            setErrorMessage(latestBadError.error)
                            setEditableJsonData(latestBadError.request)
                        }
                    }
                }
            }
            
        } catch (error) {
            console.error('Error fetching subprocesses:', error)
        }
    }, [currentBadErrorId])
    
    const fetchAgentRuns = useCallback(async () => {
        try {
            const response = await api.getAllAgentRuns('DISCOVERY_AGENT')
            if (response.discoveryAgentRuns && response.discoveryAgentRuns.length > 0) {
                const latestRun = response.discoveryAgentRuns[0]
                setCurrentAgentRun(latestRun)
                setCurrentProcessId(latestRun.processId)
                
                if (latestRun.state === State.RUNNING) {
                    await fetchSubprocesses(latestRun.processId)
                }
            }
        } catch (error) {
            console.error('Error fetching agent runs:', error)
        } finally {
        }
    }, [fetchSubprocesses, setCurrentProcessId])
    
    const pollApi = useCallback(async () => {
        try {
            if (currentProcessId) {
                if (currentAgentRun?.state === State.RUNNING) {
                    await fetchSubprocesses(currentProcessId)
                } else {
                    await fetchAgentRuns()
                }
            } else {
                await fetchAgentRuns()
            }
        } catch (error) {
            console.error('Polling error:', error)
        }
    }, [currentProcessId, currentAgentRun?.state, fetchSubprocesses, fetchAgentRuns])
    
    useEffect(() => {
        if (currentAgentRun?.state !== State.RE_ATTEMPT) {
            pollingIntervalRef.current = setInterval(pollApi, 2000)
        } else {
            if (pollingIntervalRef.current) {
                clearInterval(pollingIntervalRef.current)
                pollingIntervalRef.current = null
            }
        }
        
        // Poll agent runs every 2 minutes to check for stats changes
        agentRunPollingIntervalRef.current = setInterval(fetchAgentRuns, 120000)
        
        return () => {
            if (pollingIntervalRef.current) {
                clearInterval(pollingIntervalRef.current)
            }
            if (agentRunPollingIntervalRef.current) {
                clearInterval(agentRunPollingIntervalRef.current)
            }
        }
    }, [currentAgentRun?.state, pollApi, fetchAgentRuns])
    
    useEffect(() => {
        fetchAgentRuns()
    }, [fetchAgentRuns])
    
    const handleJsonChange = useCallback((newJsonData) => {
        setEditableJsonData(newJsonData)
    }, [])

    const handleCreateSubprocess =  useCallback(async ()=>{
        if(hostname && authToken){
            await api.createDiscoveryAgentRunSubprocess({
                processId: currentProcessId,
                hostname,
                authToken
            })
        }
    }, [hostname, authToken, currentProcessId])
    
    const handleRetry = async (type) => {
        if (type === 'retry' && currentSubprocessData && currentProcessId) {
            try {
                const response = await api.updateAgentSubprocess({
                    processId: currentProcessId,
                    subProcessId: currentSubprocessData.subProcessId,
                    data: {retryRequest: editableJsonData},
                    agent: 'DISCOVERY_AGENT',
                })
                setCurrentSubprocessData(response.subprocess)
            } catch (error) {
                console.error('Error updating subprocess:', error)
            }
        } else if (type === 'discard') {
            setCurrentAgentRun(null)
            setCurrentProcessId(null)
            setSubprocesses([])
            setCurrentSubprocessData(null)
        }
    }
    
    
    const toggleSubprocessExpansion = useCallback((subprocessId) => {
        setExpandedSubprocesses(prev => {
            const newSet = new Set(prev)
            if (newSet.has(subprocessId)) {
                newSet.delete(subprocessId)
            } else {
                newSet.add(subprocessId)
            }
            return newSet
        })
    }, [])

    const calculateProgress = useCallback(() => {
        if (!currentAgentRun?.agentStats || !currentSubprocessData) {
            return 0
        }

        // Get totalApis from agentStats (first element)
        const totalApis = currentAgentRun.agentStats[0]?.items?.totalApis || 0
        
        // Get processedApisTillNow from current subprocess
        const processedApis = currentSubprocessData.processedApisTillNow || 0
        
        if (totalApis === 0) {
            return 0
        }
        
        return Math.round((processedApis / totalApis) * 100)
    }, [currentAgentRun?.agentStats, currentSubprocessData])

    const camelCaseToReadable = useCallback((str) => {
        if (!str || typeof str !== 'string') return str
        
        return str
            // Insert a space before any capital letter that follows a lowercase letter
            .replace(/([a-z])([A-Z])/g, '$1 $2')
            // Insert a space before any capital letter that follows a number
            .replace(/([0-9])([A-Z])/g, '$1 $2')
            // Insert a space after any capital letter that precedes a lowercase letter
            .replace(/([A-Z])([a-z])/g, ' $1$2')
            // Remove leading space if it exists
            .replace(/^\s/, '')
            // Capitalize the first letter
            .replace(/^[a-z]/, (match) => match.toUpperCase())
    }, [])
    
    const renderSubprocessLogs = (subprocess, isCurrent = false) => {
        const isExpanded = expandedSubprocesses.has(subprocess.id)
        const logs = subprocess.logs || []
        const sortedLogs = [...logs].sort((a, b) => a.eventTimestamp > b.eventTimestamp ? 1 : -1)
        
        return (
            <div 
                key={subprocess.id}
                className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${isExpanded ? "gap-1" : "gap-0"}`}
            >
                <Button 
                    variant="plain" 
                    fullWidth
                    onClick={() => toggleSubprocessExpansion(subprocess.id)}
                    textAlign="left"
                    style={{ backgroundColor: '#F6F6F7' }}
                >
                    <HorizontalStack gap="2" align="start">
                        <motion.div animate={{ rotate: isExpanded ? 0 : 270 }} transition={{ duration: 0.2 }}>
                            <CaretDownMinor height={20} width={20} />
                        </motion.div>
                        <HorizontalStack gap="2" align="start">
                            <Text as="dd">
                                {subprocess.subProcessHeading || `Subprocess ${subprocess.subProcessId}`}
                                {subprocess.attemptId > 1 && ` (Attempt ${subprocess.attemptId})`}
                            </Text>
                            {isCurrent && <Badge tone="info">Current</Badge>}
                            <Badge tone={subprocess.state === State.RUNNING ? 'success' : 
                                       subprocess.state === State.COMPLETED ? 'success' : 
                                       subprocess.state === State.RE_ATTEMPT ? 'warning' : 'critical'}>
                                {subprocess.state}
                            </Badge>
                        </HorizontalStack>
                    </HorizontalStack>
                </Button>

                <AnimatePresence>
                    <motion.div 
                        animate={isExpanded ? "open" : "closed"} 
                        variants={{ 
                            open: { height: "auto", opacity: 1 }, 
                            closed: { height: 0, opacity: 0 } 
                        }} 
                        transition={{ duration: 0.2 }} 
                        className="overflow-hidden"
                    >
                        <div 
                            className="bg-[#F6F6F7] max-h-[45vh] overflow-auto ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]"
                        >
                            <AnimatePresence initial={false}>
                                {sortedLogs.map((log, index) => (
                                    <motion.p 
                                        key={`${index}-${log.log}`} 
                                        initial={{ opacity: 0, y: -10 }} 
                                        animate={{ opacity: 1, y: 0 }} 
                                        transition={{ duration: 0.2 }} 
                                        className="text-xs text-[var(--text-subdued)] ml-3! p-0.5 hover:bg-[var(--background-selected)]"
                                    >
                                        {log.log}
                                    </motion.p>
                                ))}
                            </AnimatePresence>
                        </div>
                    </motion.div>
                </AnimatePresence>
            </div>
        )
    }
    
    const renderAgentStats = (
        <VerticalStack gap="4" key="agent-stats">
            {currentAgentRun && currentAgentRun?.agentStats?.length > 0 && (
                <LegacyCard title="Agent Stats" sectioned>
                    {currentAgentRun?.agentStats.map((stat) => {
                        return (
                            <LegacyCard.Section title={stat?.name}>
                                <List type="bullet">
                                    {Object.keys(stat?.items).map((value) => {
                                        const itemValue = stat?.items[value]
                                        const isObject = typeof itemValue === 'object' && itemValue !== null && !Array.isArray(itemValue)
                                        const isArray = Array.isArray(itemValue);
                                        
                                        return (
                                            <List.Item key={value}>
                                                <div style={{display: 'flex', gap: '8px', alignItems: 'start'}}>
                                                    <Text variant="headingSm">{camelCaseToReadable(value)}</Text>: 
                                                    {isObject ? (
                                                        <HorizontalStack gap={"2"}>
                                                            {Object.keys(itemValue).map((subKey) => (
                                                                <HorizontalStack gap={"1"} key={subKey}>
                                                                    <Text variant="bodyMd">{camelCaseToReadable(subKey)}</Text>: <Text variant="bodyMd" color="subdued">{itemValue[subKey]}</Text>
                                                                </HorizontalStack>
                                                            ))}
                                                        </HorizontalStack>
                                                    ) : isArray ? (
                                                        <Scrollable shadow style={{height: '200px'}}>
                                                            <List type="number">
                                                                {itemValue.map((subKey, index) => {
                                                                    return (
                                                                        <List.Item key={index}>
                                                                            <VerticalStack gap="1" align="start">
                                                                                {Object.keys(subKey).map((val) => (
                                                                                    <HorizontalStack gap="2" key={val} align="start">
                                                                                        <Text variant="bodyMd">{camelCaseToReadable(val)}</Text>: 
                                                                                        <Text variant="bodyMd" color="subdued">{subKey[val]}</Text>
                                                                                    </HorizontalStack>
                                                                                ))}
                                                                            </VerticalStack>
                                                                        </List.Item>
                                                                    )
                                                                })} 
                                                            </List>
                                                        </Scrollable>
                                                    ) : (
                                                        <Text variant="bodyMd" color="subdued">{itemValue}</Text>
                                                    )}
                                                </div>
                                            </List.Item>
                                        )
                                    })}
                                </List>
                            </LegacyCard.Section>
                        )
                    })}
                </LegacyCard>
            )}

            {subprocesses.length > 0 && (
                <VerticalStack gap="2">
                    <Text variant="headingMd" as="h3">
                        Subprocess Logs
                    </Text>
                    {subprocesses.map((subprocess, index) => 
                        renderSubprocessLogs(subprocess, index === 0)
                    )}
                </VerticalStack>
            )}

            {currentSubprocessData?.state === State.RE_ATTEMPT && (
                <ConfigurationRequired
                    errorMessage={errorMessage}
                    editableJsonData={editableJsonData}
                    onJsonChange={handleJsonChange}
                    onRetry={handleRetry}
                    onDiscard={handleRetry}
                    currentUrl={currentSubprocessData?.currentUrlString}
                />
            )}
        </VerticalStack>
    )

    const params = useParams()
    const apiCollectionId = params.apiCollectionId

    const collectionsMap = PersistStore(state => state.collectionsMap)

    const progress = calculateProgress()
    const totalApis = currentAgentRun?.agentStats?.[0]?.items?.totalApis || 0
    const processedApis = currentSubprocessData?.processedApisTillNow || 0

    const descriptionInfo = (
        <LegacyCard title="Overview" sectioned>
           <VerticalStack gap={"3"}>
                <Text variant="bodyMd">
                    The open api agent helps you test your open api endpoints using real time data and creates a valid collection
                </Text>
                <Text variant="bodyMd">
                    Running for Collection: {collectionsMap[apiCollectionId]}
                </Text>
                
                {currentAgentRun?.state === State.RUNNING && totalApis > 0 && (
                    <HorizontalStack gap="4" align="start">
                        <Box style={{ minWidth: '120px' }}>
                            <SemiCircleProgress 
                                progress={progress} 
                                size={100} 
                                height={80} 
                                width={120}
                            />
                        </Box>
                        <VerticalStack gap="1">
                            <Text variant="headingSm" as="h4">
                                Progress: {progress}%
                            </Text>
                            <Text variant="bodySm" tone="subdued">
                                {processedApis} of {totalApis} APIs processed
                            </Text>
                            {currentSubprocessData?.currentUrlString && (
                                <Text variant="bodySm" tone="subdued">
                                    Currently processing: {currentSubprocessData.currentUrlString}
                                </Text>
                            )}
                        </VerticalStack>
                    </HorizontalStack>
                )}
           </VerticalStack>
        </LegacyCard>
    )

    const inputContainer = currentSubprocessData == null && currentAgentRun?.state === State.RUNNING && (
        <LegacyCard title="Input" sectioned primaryFooterAction={{content: 'Submit', onAction: () => handleCreateSubprocess()}}>
            <LegacyCard.Section title="Enter the hostname and auth token for testing the endpoints found in swagger file.">
                <VerticalStack gap="2">
                <TextField
                    label="Hostname"
                    value={hostname}
                    onChange={(value) => setHostname(value)}
                    type='url'
                />
                <TextField
                    label="Auth Token"
                    value={authToken}
                    onChange={(value) => setAuthToken(value)}
                    type='password'
                />
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    return (
        <PageWithMultipleCards
            title="Open API Agent Tester"
            components={[descriptionInfo, renderAgentStats, inputContainer]}
            isFirstPage={true}
        />
    )
}

export default OpenApiAgentTester