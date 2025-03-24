import React, { useEffect, useRef, useState } from 'react';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';
import { useAgentsStore } from '../agents.store';
import api from '../api';
import { useAgentsStateStore } from '../agents.state.store';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import { intermediateStore } from '../intermediate.store';
import transform from '../transform';

export const FindVulnerabilitiesAgent = () => {

    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    const { currentProcessId, currentAgent, setCurrentAttempt, setCurrentSubprocess, setCurrentProcessId, resetStore, agentState, setAgentState} = useAgentsStore();
    const { resetIntermediateStore } = intermediateStore(state => ({ resetIntermediateStore: state.resetIntermediateStore })); 

    const {setCurrentSubprocessAttempt,setCurrentAgentProcessId,setCurrentAgentSubprocess, resetAgentState, setCurrentAgentState} = useAgentsStateStore();

    const getAllAgentRuns = async () => {
        try {
            const response = (await api.getAllAgentRuns(currentAgent?.id));
            const agentRuns = response.agentRuns as AgentRun[];
            setCurrentAgentRun(agentRuns[0]);
            if (agentRuns.length > 0 && agentRuns[0]?.processId) {
                setCurrentProcessId(agentRuns[0]?.processId)
                if(agentRuns[0]?.agent){
                    setCurrentAgentProcessId(agentRuns[0]?.agent, agentRuns[0]?.processId)
                }
            } else {
                // TODO: handle cases here, because the above API only gets "RUNNING" Agents.
                // setCurrentProcessId("")
                resetStore();
                if(currentAgent?.id)resetAgentState(currentAgent?.id);
                setSubprocesses([]);
            }
        } catch(error) {
            resetStore();
            if(currentAgent?.id)resetAgentState(currentAgent?.id);
            resetIntermediateStore();
        }
    }

    const fetchAgentModuleHealth = async () => {
        try {
            const response = await api.checkAgentRunModule({ processId: currentAgentRun?.processId });
            const agentRunningOnModule = response?.agentRunningOnModule;
            if (!agentRunningOnModule) {
                if (agentState === "thinking") {
                    transform.updateAgentState("error", currentAgent?.id??"", setAgentState, setCurrentAgentState);
                }
            }
        } catch (error) {
            if (agentState === "thinking") {
                transform.updateAgentState("error", currentAgent?.id??"", setAgentState, setCurrentAgentState);
            }
        }
    }

    const getAllSubProcesses = async (processId: string, shouldSetState: boolean) => {
       // polling for all subprocesses;
        const response = await api.getAllSubProcesses({
            processId: processId
        });
        const subprocesses = response.subProcesses as AgentSubprocess[];
        
        setSubprocesses(subprocesses);
        
        if(subprocesses.length > 0 && shouldSetState){ 
            // if page-reload, this will be called to fill the data required in the localstorage
            let newestSubprocess = subprocesses[0]
            subprocesses.forEach((subprocess) => {
                if(subprocess.createdTimestamp > newestSubprocess.createdTimestamp){
                    newestSubprocess = subprocess
                }
            });
            setCurrentSubprocess(newestSubprocess.subProcessId)
            setCurrentAttempt(newestSubprocess.attemptId)
            if(currentAgentRun?.agent)setCurrentAgentSubprocess(currentAgentRun?.agent, newestSubprocess.subProcessId)
            if(currentAgentRun?.agent)setCurrentSubprocessAttempt(currentAgentRun?.agent,newestSubprocess.attemptId)
        }
        if(subprocesses.length === 0 && shouldSetState) {
            // create first subprocess of the agent run here
            const response = await api.updateAgentSubprocess({
                processId: processId,
                subProcessId: "1",
                attemptId: 1,
                subProcessHeading: "Subprocess scheduled"
            });
            setCurrentSubprocess("1");
            setCurrentAttempt(1);
            if(currentAgentRun?.agent)setCurrentAgentSubprocess(currentAgentRun?.agent, "1")
                if(currentAgentRun?.agent)setCurrentSubprocessAttempt(currentAgentRun?.agent, 1)
            const subprocess = response.subprocess as AgentSubprocess;
            setSubprocesses([...subprocesses, subprocess]);

        }

    }
    const intervalRef = useRef<number | null>(null);
    const healthCheckIntervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING || currentAgentRun?.agent !== currentAgent?.id) {
            setCurrentAttempt(0);
            setCurrentSubprocess("0");
            if(currentAgentRun?.agent)setCurrentSubprocessAttempt(currentAgentRun?.agent, 0);
            if(currentAgentRun?.agent)setCurrentAgentSubprocess(currentAgentRun?.agent, "0");
            intervalRef.current = setInterval(getAllAgentRuns, 2000);
            
        } else {
            getAllSubProcesses(currentAgentRun.processId, true);
            healthCheckIntervalRef.current = setInterval(fetchAgentModuleHealth, 2000)
        }
    
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
                intervalRef.current = null;
            }
            if (healthCheckIntervalRef.current) {
                clearInterval(healthCheckIntervalRef.current);
                healthCheckIntervalRef.current = null;
            }
        };
    }, [currentAgentRun, currentAgent]);

    const triggerCallForSubProcesses = async () => {
        // call for all subprocesses triggered on new subprocess creation
        getAllSubProcesses(currentAgentRun?.processId || "", false);
    }

    return (
        <Scrollable className="h-full">
            <VerticalStack gap="2">
            {((currentProcessId?.length || 0) > 0 && subprocesses.length == 0) ? <SpinnerCentered /> : 
            subprocesses.map((subprocess, index) => (
                    <Subprocess 
                    key={subprocess.subProcessId}
                        agentId={currentAgent?.id || ""}
                        processId={currentAgentRun?.processId || ""}
                        subProcessFromProp={subprocesses[index]}
                        setCurrentAgentRun={setCurrentAgentRun}
                        triggerCallForSubProcesses={triggerCallForSubProcesses}/>
                ))}
            </VerticalStack>
        </Scrollable>
    )
}
