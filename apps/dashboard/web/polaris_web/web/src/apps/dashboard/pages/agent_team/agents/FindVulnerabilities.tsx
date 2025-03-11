import React, { useEffect, useRef, useState } from 'react';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';
import { useAgentsStore } from '../agents.store';
import api from '../api';

export const FindVulnerabilitiesAgent = () => {
    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    const { agentSteps, setAgentSteps, setCurrentAttempt, setCurrentSubprocess } = useAgentsStore();

    const getAllAgentRuns = async () => {
        try {
            const response = (await api.getAllAgentRuns("FIND_VULNERABILITIES_FROM_SOURCE_CODE"));
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
            const existingData = {...agentSteps["FIND_VULNERABILITIES_FROM_SOURCE_CODE"]};
            let newData = {...existingData}
            subprocesses.forEach((subprocess) => {
                const subProcessId: string = subprocess.subprocessId;
                const logs = subprocess?.logs || [];
                const processOutput = subprocess?.processOutput || {};
                newData[subProcessId] = {
                    heading: newData[subProcessId]?.heading || newData[subProcessId],
                    logs: logs,
                    processOutput: processOutput
                }
            });
            const finalMap = {...agentSteps, "FIND_VULNERABILITIES_FROM_SOURCE_CODE": newData};
            setAgentSteps(finalMap);
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
                    <Subprocess currentAgentType={"FIND_VULNERABILITIES_FROM_SOURCE_CODE"} processId={currentAgentRun?.processId || ""} key={subprocess.subprocessId} subProcessFromProp={subprocesses[index]} />
                ))}
            </VerticalStack>
        </Scrollable>
    )
}
