import React, { useEffect, useRef, useState } from 'react';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';
import { useAgentsStore } from '../agents.store';
import api from '../api';

export const FindVulnerabilitiesAgent = () => {
    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    const { agentSteps, setAgentSteps, currentSubprocess, setCurrentAttempt, setCurrentSubprocess } = useAgentsStore();

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
        const subprocesses = response.subprocesses as AgentSubprocess[];
        
        setSubprocesses(subprocesses);
        let shouldCreateNextSubprocess:boolean = subprocesses.length === 0;
        // convert into agent steps here
        
        if(subprocesses.length > 0) { 
            const existingData = agentSteps.get("FIND_VULNERABILITIES_FROM_SOURCE_CODE") 
            const newData = { ...existingData } as Record<string,any>;
            subprocesses.forEach((subprocess) => {
                const subProcessId: string = subprocess.subprocessId;
                const logs = subprocess?.logs || [];
                const processOutput = subprocess?.processOutput || {};
                newData[subProcessId] = {
                    [subProcessId]: {
                        "logs": logs,
                        "processOutput": processOutput
                    }
                };
            })
            if((subprocesses[subprocesses.length - 1].state === State.RUNNING || subprocesses[subprocesses.length - 1].state === State.SCHEDULED)){
                shouldCreateNextSubprocess = false;
            }
            setAgentSteps("FIND_VULNERABILITIES_FROM_SOURCE_CODE", newData);
        }

        if(shouldCreateNextSubprocess){
            const newSubprocessId:string = (Number(currentSubprocess) + 1).toLocaleString();
            const response = await api.updateAgentSubprocess({
                processId: processId,
                subProcessId: newSubprocessId,
                attemptId: 1
            });
            setCurrentSubprocess(newSubprocessId);
            setCurrentAttempt(1);
            const subprocess = response.subprocess as AgentSubprocess;
            setSubprocesses([...subprocesses, subprocess]);
        }

    }
    const intervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING) {
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
                {subprocesses.length > 0 && subprocesses.map((subprocess) => (
                    <Subprocess key={subprocess.subprocessId} subprocessId={subprocess.subprocessId} processId={subprocess.processId} />
                ))}
            </VerticalStack>
        </Scrollable>
    )
}
