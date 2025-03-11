import React, { useEffect, useRef, useState } from 'react';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';
import { useAgentsStore } from '../agents.store';
import api from '../api';

export const FindVulnerabilitiesAgent = (props) => {

    const {agentId} = props

    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    // ?? Where exactly is agent steps being used.
    // I didn't find any use case, we can remove it.
    const { agentSteps, setAgentSteps, setCurrentAttempt, setCurrentSubprocess } = useAgentsStore();

    const getAllAgentRuns = async () => {
        try {
            const response = (await api.getAllAgentRuns(agentId));
            const agentRuns = response.agentRuns as AgentRun[];
            setCurrentAgentRun(agentRuns[0]);
        } catch(error) {
            
        }
    }

    const getAllSubProcesses = async (processId: string) => {
       // polling for all subprocesses;
        const response = await api.getAllSubProcesses({
            processId: processId
        });
        const subprocesses = response.subProcesses as AgentSubprocess[];
        
        setSubprocesses(subprocesses);
        
        if(subprocesses.length > 0) { 
            // if page-reload, this will be called to fill the data required in the localstorage
            const existingData = {...agentSteps[agentId]};
            let newData = {...existingData}
            let newestSubprocess = subprocesses[0]
            subprocesses.forEach((subprocess) => {
                
                if(subprocess.createdTimestamp > newestSubprocess.createdTimestamp){
                    newestSubprocess = subprocess
                }

                const subProcessId: string = subprocess.subProcessId;
                const logs = subprocess?.logs || [];
                const processOutput = subprocess?.processOutput || {};
                newData[subProcessId] = {
                    heading: newData[subProcessId]?.heading || newData[subProcessId],
                    logs: logs,
                    processOutput: processOutput
                }
            });
            const finalMap = {...agentSteps, [agentId]: newData};
            setAgentSteps(finalMap);
            setCurrentSubprocess(newestSubprocess.subProcessId)
            setCurrentAttempt(newestSubprocess.attemptId)
        }
        if(subprocesses.length === 0) {
            // create first subprocess of the agent run here
            const response = await api.updateAgentSubprocess({
                processId: processId,
                subProcessId: "1",
                attemptId: 1
            });
            setCurrentSubprocess("1");
            setCurrentAttempt(1);
            const subprocess = response.subprocess as AgentSubprocess;
            setSubprocesses([...subprocesses, subprocess]);
        }

    }
    const intervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING) {
            setCurrentAttempt(0);
            setCurrentSubprocess("0");
            intervalRef.current = setInterval(getAllAgentRuns, 2000);
        } else {
            getAllSubProcesses(currentAgentRun.processId);
        }
    
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
        };
    }, [currentAgentRun]);

    return (
        <Scrollable className="h-full">
            <VerticalStack gap="2">
                {subprocesses.length > 0 && subprocesses.map((subprocess, index) => (
                    <Subprocess currentAgentType={agentId} processId={currentAgentRun?.processId || ""} key={subprocess.subProcessId} subProcessFromProp={subprocesses[index]} />
                ))}
            </VerticalStack>
        </Scrollable>
    )
}
