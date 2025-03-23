import React, { useState, useEffect, useCallback, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {  AgentRun, AgentSubprocess, State } from "../../types";
import { CaretDownMinor } from "@shopify/polaris-icons";
import api from "../../api";
import { useAgentsStore } from "../../agents.store";
import STEPS_PER_AGENT_ID, { preRequisitesMap } from "../../constants";
import { VerticalStack, Text, HorizontalStack, Button } from "@shopify/polaris";
import OutputSelector, { getMessageFromObj } from "./OutputSelector";
import { intermediateStore } from "../../intermediate.store";
import func from "../../../../../../util/func";
import SelectedChoices from "./SelectedChoices";

interface SubProcessProps {
    agentId: string, 
    processId:string, 
    subProcessFromProp: AgentSubprocess, 
    setCurrentAgentRun: (agentRun: AgentRun|null) => void,
    triggerCallForSubProcesses: ()=> void ,
}

export const Subprocess = ({ agentId, processId, subProcessFromProp, triggerCallForSubProcesses, setCurrentAgentRun }: SubProcessProps) => {
    const [subprocess, setSubprocess] = useState<AgentSubprocess | null>(subProcessFromProp);
    const [expanded, setExpanded] = useState(true);

    const { finalCTAShow, setFinalCTAShow, setCurrentAttempt, 
        setCurrentSubprocess, currentSubprocess, currentAttempt, setAgentState, setPRState, agentState } = useAgentsStore(state => ({
        finalCTAShow: state.finalCTAShow,
        setFinalCTAShow: state.setFinalCTAShow,
        setCurrentAttempt: state.setCurrentAttempt,
        setCurrentSubprocess: state.setCurrentSubprocess,
        currentSubprocess: state.currentSubprocess,
        currentAttempt: state.currentAttempt,
        setAgentState: state.setAgentState,
        setPRState: state.setPRState,
        agentState: state.agentState
    }));  // Only subscribe to necessary store values

    const { setFilteredUserInput, setOutputOptions, setPreviousUserInput } = intermediateStore(state => ({ setFilteredUserInput: state.setFilteredUserInput, setOutputOptions: state.setOutputOptions, setPreviousUserInput: state.setPreviousUserInput })); 

    // Memoized function to create new subprocess
    const createNewSubprocess = useCallback(async (newSubIdNumber: number) => {

        // check for pre-requisites first
        const shouldCreateSubProcess = preRequisitesMap[agentId]?.[newSubIdNumber]?.action ? await preRequisitesMap[agentId][newSubIdNumber].action() : true;
        if (!shouldCreateSubProcess) {
            setFinalCTAShow(true);
            setPRState("2");
            return null;
        }

        const newSubId = newSubIdNumber.toString();
        const newRes = await api.updateAgentSubprocess({
            processId,
            subProcessId: newSubId,
            attemptId: 1,
            subProcessHeading: "Subprocess scheduled"
        });
        setCurrentSubprocess(newSubId);
        setCurrentAttempt(1);
        setAgentState("thinking");
        return newRes.subprocess as AgentSubprocess;
    }, [processId, setCurrentSubprocess, setCurrentAttempt, setAgentState]);

    useEffect(() => {
        const fetchSubprocess = async () => {
            if (currentSubprocess !== subProcessFromProp.subProcessId) return;

            const response = await api.getSubProcess({
                processId,
                subProcessId: currentSubprocess,
                attemptId: currentAttempt,
            });

            const newSubProcess = response.subprocess as AgentSubprocess;
            const agentRun = response.agentRun as AgentRun;

            if (!newSubProcess) return;

            if (newSubProcess.state === State.RUNNING) {
                if (agentState !== "error") {
                    setAgentState("thinking");
                }
            }

            if (newSubProcess.state === State.ACCEPTED) {
                const newSubIdNumber = Number(currentSubprocess) + 1;
                if (newSubIdNumber > STEPS_PER_AGENT_ID[agentId]) {
                    if (agentRun.state !== "COMPLETED") {
                        setFinalCTAShow(true);
                        await api.updateAgentRun({ processId, state: "COMPLETED" });
                    } else {
                        setAgentState("idle");
                    }
                } else {
                    const newSub = await createNewSubprocess(newSubIdNumber);
                    setSubprocess(newSub);
                    triggerCallForSubProcesses();
                }
            }

            if (newSubProcess.state === State.COMPLETED) {
                setAgentState("paused");
            }

            if (newSubProcess.state === State.DISCARDED) {
                setAgentState("idle");
            }

            if (newSubProcess.state === State.AGENT_ACKNOWLEDGED) {
                setPreviousUserInput(newSubProcess.userInput);
                const newSub = await createNewSubprocess(Number(currentSubprocess) + 1);
                setFilteredUserInput(null);
               
                setSubprocess(newSub);
                triggerCallForSubProcesses();
            }

            if (newSubProcess.state === State.FAILED) {
                await reRunTask();
            }

            if (newSubProcess.state === State.SCHEDULED) {
                setAgentState("idle");
            }

            if (JSON.stringify(newSubProcess) !== JSON.stringify(subprocess)) {
                setSubprocess(newSubProcess);
            }
        };

        const interval = setInterval(fetchSubprocess, 2000);
        /*
        We do not want to refresh current subprocess, 
        if we're already at final CTA.
        */
        if (finalCTAShow) clearInterval(interval);
        return () => clearInterval(interval);
    }, [currentSubprocess, finalCTAShow, processId, currentAttempt, subProcessFromProp, createNewSubprocess]);

    if (!subprocess) return null;

    const handleSelect = (selectedChoices: any, outputOptions: any) => {
        setOutputOptions(outputOptions); 
        setFilteredUserInput(selectedChoices);
    }

    async function reRunTask() {
        const tempRes = await api.updateAgentSubprocess({
            processId,
            subProcessId: currentSubprocess,
            attemptId: currentAttempt + 1,
            subProcessHeading: "Subprocess scheduled"
        });
        setSubprocess(tempRes.subprocess as AgentSubprocess);
        setCurrentAttempt(currentAttempt + 1);
        func.setToast(true, false, "Task submitted for re-run")
    }

    async function startAgentAgain() {
        let res = await api.updateAgentRun({ processId, state: "DISCARDED" });
        func.setToast(true, false, "Agent is being submitted for re-run")
        setAgentState("idle");
        setTimeout(async () => {
            const previousAgentRun = res.agentRun
            let data = await api.createAgentRun({
                agent: previousAgentRun.agent,
                data: previousAgentRun.agentInitDocument
            })
            if(data.agentRun){
                setCurrentAgentRun(data?.agentRun)
                func.setToast(true, false, "Agent submitted for re-run")
            } else {
                setCurrentAgentRun(null)
                func.setToast(true, true, "Unable to create agent run")
            }
        }, 5000)
    }

    async function stopAgent() {
        await api.updateAgentRun({ processId, state: "STOPPED" });
        func.setToast(true, false, "Agent has been stopped")
        setAgentState("idle");
    }

    return useMemo(() => (
        <VerticalStack gap="4">
            <div className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${expanded ? "gap-1" : "gap-0"}`}>
                <button className="bg-[#F6F6F7] w-full flex items-center cursor-pointer" onClick={() => setExpanded(!expanded)}>
                    <motion.div animate={{ rotate: expanded ? 0 : 270 }} transition={{ duration: 0.2 }}>
                        <CaretDownMinor height={20} width={20} />
                    </motion.div>
                    <Text as={"dd"}>{`${subprocess.subProcessHeading} ${currentAttempt > 1 ? `(Attempt ${currentAttempt})` : ""} `}</Text>
                </button>

                <AnimatePresence>
                    <motion.div animate={expanded ? "open" : "closed"} variants={{ open: { height: "auto", opacity: 1 }, closed: { height: 0, opacity: 0 } }} transition={{ duration: 0.2 }} className="overflow-hidden">
                        <div className="bg-[#F6F6F7] ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]">
                            <AnimatePresence initial={false}>
                                {subprocess?.logs?.sort((a,b) => {
                                    return a.eventTimestamp > b.eventTimestamp ? 1 : -1
                                }).map((log, index) => (
                                    <motion.p key={`${index}-${log.log}`} initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.2 }} className="text-xs text-[var(--text-subdued)] ml-3! p-0.5 hover:bg-[var(--background-selected)]">
                                        {log.log}
                                    </motion.p>
                                ))}
                            </AnimatePresence>
                        </div>
                    </motion.div>
                </AnimatePresence>
            </div>

            {subprocess.state === State.COMPLETED && subprocess.processOutput &&
                <OutputSelector processOutput={subprocess.processOutput} onHandleSelect={handleSelect} />
            }

            {subprocess.state === State.ACCEPTED && subprocess.processOutput &&
                <VerticalStack gap={"2"}>
                    <Text variant="bodyMd" as="span">{subprocess.processOutput?.outputMessage}</Text>
                    {/* TODO: Selected choices dialog, handle edge cases. */}
                    {/* <SelectedChoices userInput={subprocess.userInput}/> */}
                </VerticalStack>
            }

            {subprocess.state === State.FAILED &&
                <Text variant="bodyMd" as="span">Task failed. Attempting to re-run task.</Text>
            }

            {subprocess.state === State.DISCARDED && subprocess.userInput &&
                <VerticalStack gap={"2"}>
                    <Text as={"dd"}>This task has been discarded by you, would you like to:</Text>
                    <HorizontalStack gap={"2"}>
                        <Button size={"slim"} onClick={() => reRunTask()}>
                            Re-run task
                        </Button>
                        <Button plain onClick={()=> startAgentAgain()}>
                            Start again
                        </Button>
                        <Button plain onClick={()=> stopAgent()} destructive>
                            Stop agent
                        </Button>
                    </HorizontalStack>
                </VerticalStack>
            }
        </VerticalStack>
    ), [subprocess, expanded]);  // Only re-render if these values change
};