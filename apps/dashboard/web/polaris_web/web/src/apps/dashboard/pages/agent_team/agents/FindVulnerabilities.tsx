import React, { useEffect, useRef, useState } from 'react';
import agentTeamApi from '../api';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';

export const FindVulnerabilitiesAgent = () => {
    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    const getAllAgentRuns = async () => {
        try {
            const response = (await agentTeamApi.getAllAgentRuns());
            const agentRuns = response.agentRuns as AgentRun[];
            setCurrentAgentRun(agentRuns.find((run) => run.state === State.RUNNING && run.agent === "FIND_VULNERABILITIES_FROM_SOURCE_CODE") || null);
        } catch(error) {
            
        }
    }

    const getAllSubProcesses = async ({ processId }: { processId: string }) => {
       // polling for all subprocesses;
       setInterval(async () => {
        const response = await agentTeamApi.getAllSubProcesses({
            processId
        });
        const subprocesses = response.subprocesses as AgentSubprocess[];
        console.log({ subprocesses });
        setSubprocesses(subprocesses);
       }, 2000);
    }
    const intervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING) {
            intervalRef.current = setInterval(getAllAgentRuns, 2000);
        } else {
            getAllSubProcesses({ processId: currentAgentRun.processId });
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
                {subprocesses.map((subprocess) => (
                    <Subprocess key={subprocess.subprocessId} subprocessId={subprocess.subprocessId} processId={subprocess.processId} />
                ))}
            </VerticalStack>
        </Scrollable>
    )
}
