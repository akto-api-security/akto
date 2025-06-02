import React, { useEffect, useRef, useState } from 'react';
import { Scrollable, VerticalStack } from '@shopify/polaris';
import { AgentRun, AgentState, AgentSubprocess, State } from '../types';
import { Subprocess } from '../components/agentResponses/Subprocess';
import { useAgentsStore } from '../agents.store';
import api from '../api';
import SpinnerCentered from '../../../components/progress/SpinnerCentered';
import { intermediateStore } from '../intermediate.store';

export const FindVulnerabilitiesAgent = () => {

    const [currentAgentRun, setCurrentAgentRun] = useState<AgentRun | null>(null);
    const [subprocesses, setSubprocesses] = useState<AgentSubprocess[]>([]);

    const { currentProcessId, currentAgent, setCurrentAttempt, setCurrentSubprocess, setCurrentProcessId, resetStore, agentState, setAgentState} = useAgentsStore();
    const { resetIntermediateStore, setAgentInitDocument } = intermediateStore(state => ({ resetIntermediateStore: state.resetIntermediateStore,  setAgentInitDocument: state.setAgentInitDocument })); 

    useEffect(()=>{
        setAgentInitDocument(currentAgentRun?.agentInitDocument || {})
    },[currentAgentRun])

    const getAllAgentRuns = async () => {
        try {
            const response = (await api.getAllAgentRuns(currentAgent?.id));
            const agentRuns = response.agentRuns as AgentRun[];
            setCurrentAgentRun(agentRuns[0]);
            if (agentRuns.length > 0 && agentRuns[0]?.processId) {
                setCurrentProcessId(agentRuns[0]?.processId)
            } else {
                // TODO: handle cases here, because the above API only gets "RUNNING" Agents.
                // setCurrentProcessId("")
                resetStore();
                resetIntermediateStore();
            }
        } catch(error) {
            resetStore();
            resetIntermediateStore();
        }
    }

    const fetchAgentModuleHealth = async () => {
        try {
            const response = await api.checkAgentRunModule({ processId: currentAgentRun?.processId });
            const agentRunningOnModule = response?.agentRunningOnModule;
            if (!agentRunningOnModule) {
                setAgentState((prev: AgentState) => {
                    return prev === "thinking" ? "error" : prev
                });
            }
        } catch (error) {
            if (agentState === "thinking") {
                setAgentState((prev: AgentState) => {
                    return prev === "thinking" ? "error" : prev
                });
            }
        }
    }

    const getAllSubProcesses = async (processId: string, shouldSetState: boolean) => {
       // polling for all subprocesses;
        const response = await api.getAllSubProcesses({
            processId: processId
        });
        let subprocesses = response.subProcesses as AgentSubprocess[];
        
        let subProcessesLatestAttempts : AgentSubprocess[] = []

        subprocesses.sort((a,b) => {
            let spa = Number(a.subProcessId)
            let spb = Number(b.subProcessId)
            let ata = a.attemptId
            let atb = b.attemptId
            if(spa < spb){
                return -1;
            } else if(spa > spb){
                return 1;
            } else {
                if(ata < atb){
                    return -1
                } else {
                    return 1
                }
            }
        })

        let currentSubprocessId = 0;
        let currentAttemptId = 0;

        for(let temp of subprocesses){
            let tempSubProcessId = Number(temp.subProcessId)
            let tempAttemptId = temp.attemptId
            if (currentSubprocessId === tempSubProcessId &&
                tempAttemptId > currentAttemptId) {
                subProcessesLatestAttempts.pop()
            }
            currentSubprocessId = tempSubProcessId
            currentAttemptId = tempAttemptId
            subProcessesLatestAttempts.push(temp)
        }
        subprocesses = subProcessesLatestAttempts
        setSubprocesses(subprocesses);
        
        if(subprocesses.length > 0 && shouldSetState){ 
            // if page-reload, this will be called to fill the data required in the localstorage
            let newestSubprocess = subprocesses[0]
            subprocesses.forEach((subprocess) => {
                if ((Number(subprocess.subProcessId) > Number(newestSubprocess.subProcessId))
                    || (Number(subprocess.subProcessId) === Number(newestSubprocess.subProcessId)
                        && subprocess.attemptId > newestSubprocess.attemptId)) {
                    newestSubprocess = subprocess
                }
            });
            setCurrentSubprocess(newestSubprocess.subProcessId)
            setCurrentAttempt(newestSubprocess.attemptId)
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
            const subprocess = response.subprocess as AgentSubprocess;
            setSubprocesses([...subprocesses, subprocess]);
        }

    }
    const intervalRef = useRef<number | null>(null);
    const healthCheckIntervalRef = useRef<number | null>(null);

    useEffect(() => {
        if (!currentAgentRun || currentAgentRun?.state !== State.RUNNING) {
            setCurrentAttempt(0);
            setCurrentSubprocess("0");
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
                {((currentProcessId?.length || 0) > 0 && subprocesses.length == 0) ? <SpinnerCentered /> : subprocesses.map((subprocess, index) => (
                    <Subprocess
                        key={subprocess.subProcessId}
                        agentId={currentAgent?.id || ""}
                        processId={currentAgentRun?.processId || ""}
                        subProcessFromProp={subprocess}
                        setCurrentAgentRun={setCurrentAgentRun}
                        triggerCallForSubProcesses={triggerCallForSubProcesses}
                    />
                ))}
            </VerticalStack>
        </Scrollable>
    )
}