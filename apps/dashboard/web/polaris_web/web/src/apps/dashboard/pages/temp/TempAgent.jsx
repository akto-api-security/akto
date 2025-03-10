import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { LegacyCard, Text, TextField, VerticalStack } from '@shopify/polaris'
import api from '../agent_team/api';

function TempAgent() {

    const [projectDir, setProjectDir] = useState('');

    const handleSubmit = async() => {
        await api.createAgentRun({
            agent: "FIND_VULNERABILITIES_FROM_SOURCE_CODE",
            data: {
                projectDir: projectDir
            }
        })
    }

    const initComponent = (
        <LegacyCard title= "Init vulnerability agent" primaryFooterAction={{content: "Init", onAction: () => handleSubmit()}}>
            <LegacyCard.Section >
                <TextField label="Project directory" value={projectDir} onChange={setProjectDir} /> 
            </LegacyCard.Section>
        </LegacyCard>
    )

    const fetchData = async() => {
        const response = await api.getSubProcess({
            processId: "e6ab6a38-8037-4d49-9f3b-9af145d43a72",
            subProcessId: currentSubProcess,
            attemptId: attemptId
        })
        if(response?.subProcess !== undefined){
            let resp = response.subProcess;
            setSubProcessesMap((prev) => {
                return {
                    ...prev,
                    [currentSubProcess]: {
                        ...prev[currentSubProcess],
                        logs: resp?.logs || [],
                        processOutput: resp?.processOutput || {}
                    }
                }
            })
        }
    }

    useEffect(() => {
        fetchData();
    },[])

    const subProcessMap = {
        "1": {
            heading: "Find backend directory",
            state: "SCHEDULED"
        },
        "2": {
            heading: "Find language and framework",
            state: "SCHEDULED",
        },
        "3": {
            heading: "Detect auth mechanism type",
            state: "SCHEDULED",
        }
    }

    const [subProcessesMap, setSubProcessesMap] = useState(subProcessMap);
    const [currentSubProcess, setCurrentSubProcess] = useState("1");
    const [attemptId, setAttemptId] = useState(0); 

    const handleStartSubProcess =async()=> {
        await api.updateAgentSubprocess({
            processId: "e6ab6a38-8037-4d49-9f3b-9af145d43a72",
            subProcessId: currentSubProcess,
            attemptId: attemptId
        })
    }

    const handleSave = async(userInput = null, reRun = false) => {
        await api.updateAgentSubprocess({
            processId: "e6ab6a38-8037-4d49-9f3b-9af145d43a72",
            subProcessId: currentSubProcess,
            attemptId: attemptId,
            data: userInput,
            state: "COMPLETED"
        })
        setCurrentSubProcess((prev) => {
            return String(Number(prev) + 1)
        })
    }

    const subProcessComponent = (
        <LegacyCard title= "Start/Update sub process" primaryFooterAction={{content: "Start", onAction: () => handleStartSubProcess()}}
            secondaryFooterActions={[
                {content: 'Save with user input', onAction: () => {}},
                {content: 'Accept', onAction: () => handleSave()},
                {content: 'Retry', onAction: () => handleSave(null, true)}       
            ]}
        >
            <LegacyCard.Section >
                <VerticalStack gap={"4"}>
                    <Text variant="headingMd">Sub Process : {subProcessMap[currentSubProcess]?.heading}</Text>
                    <VerticalStack gap={"2"}>
                        <Text>
                            {subProcessesMap[currentSubProcess]?.logs}
                        </Text>
                        <Text>
                            {subProcessMap[currentSubProcess]?.processOutput}
                        </Text>
                    </VerticalStack>
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const components = [initComponent, subProcessComponent];
    return (
        <PageWithMultipleCards
            title={<Text variant="headingLg">Agents</Text>}
            components={components}
            firstPage={true}
            divider
        />
    )
}

export default TempAgent