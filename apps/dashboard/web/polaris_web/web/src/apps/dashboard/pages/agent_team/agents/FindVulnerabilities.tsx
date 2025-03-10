import React, { useEffect, useState } from 'react';
import api from '../../quick_start/api';
import agentTeamApi from '../api';
import { HorizontalStack, VerticalStack } from '@shopify/polaris';
import { AgentOptions } from '../components/agentResponses/AuthOptions';
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
            processId,
        });
        const subprocesses = response.subprocesses as AgentSubprocess[];
        console.log({ subprocesses });
        setSubprocesses(subprocesses);
       }, 2000);
    }

    useEffect(() => {
        let interval: NodeJS.Timeout;
        
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING) {
            interval = setInterval(getAllAgentRuns, 2000);
        }

        if (currentAgentRun?.state === State.RUNNING) {
            getAllSubProcesses({ processId: currentAgentRun.processId });
        }

        return () => {
            if (interval) {
                clearInterval(interval);
            }
        }
    }, [currentAgentRun]);

    return (
        <VerticalStack gap="2">
            {subprocesses.map((subprocess) => (
                <Subprocess key={subprocess.subprocessId} subprocessId={subprocess.subprocessId} processId={subprocess.processId} />
            ))}
        </VerticalStack>
    )
}
