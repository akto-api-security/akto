import React, { useEffect, useRef, useState } from 'react';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';
import { useAgentsStore } from '../agents.store';
import api from '../api';
import { useAgentsStateStore } from '../agents.state.store';

export const FindVulnerabilitiesAgent = (props) => {

    const {agentId, finalCTAShow, setFinalCTAShow} = props

    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    // ?? Where exactly is agent steps being used.
    // I didn't find any use case, we can remove it.
    const { setCurrentAttempt, setCurrentSubprocess, setCurrentProcessId, resetStore} = useAgentsStore();
    const {setCurrentSubprocessAttempt,setCurrentAgentProcessId,setCurrentAgentSubprocess, resetAgentState} = useAgentsStateStore();

    const getAllAgentRuns = async () => {
        try {
            const response = (await api.getAllAgentRuns(agentId));
            console.log("agent",agentId,response)
            const agentRuns = response.agentRuns as AgentRun[];
            setCurrentAgentRun(agentRuns[0]);
            if (agentRuns.length > 0 && agentRuns[0]?.processId) {
                setCurrentProcessId(agentRuns[0]?.processId)
                setCurrentAgentProcessId(agentId, agentRuns[0]?.processId)
            } else {
                // TODO: handle cases here, because the above API only gets "RUNNING" Agents.
                // setCurrentProcessId("")
                resetStore();
                resetAgentState(agentId);
            }
        } catch(error) {
            resetStore();
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
            let newestSubprocess = subprocesses[0]
            subprocesses.forEach((subprocess) => {
                if(subprocess.createdTimestamp > newestSubprocess.createdTimestamp){
                    newestSubprocess = subprocess
                }
            });
            setCurrentSubprocess(newestSubprocess.subProcessId)
            setCurrentAttempt(newestSubprocess.attemptId)
            setCurrentAgentSubprocess(agentId, newestSubprocess.subProcessId)
            setCurrentSubprocessAttempt(agentId,newestSubprocess.attemptId)
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
            setCurrentAgentSubprocess(agentId, "1")
            setCurrentSubprocessAttempt(agentId, 1)
            const subprocess = response.subprocess as AgentSubprocess;
            setSubprocesses([...subprocesses, subprocess]);

        }

    }
    const intervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING) {
            setCurrentAttempt(0);
            setCurrentSubprocess("0");
            setCurrentSubprocessAttempt(agentId, 0);
            setCurrentAgentSubprocess(agentId, "0");
            intervalRef.current = setInterval(getAllAgentRuns, 2000);
            
        } else {
            console.log("subprocess",currentAgentRun)
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

        <VerticalStack gap="2">
            {subprocesses.length > 0 && subprocesses.map((subprocess, index) => (
                <Subprocess
                    key={subprocess.subProcessId}
                    agentId={agentId}
                    processId={currentAgentRun?.processId || ""}
                    subProcessFromProp={subprocesses[index]}
                    finalCTAShow={finalCTAShow}
                    setFinalCTAShow={setFinalCTAShow} />
            ))}
        </VerticalStack>

    )
}
