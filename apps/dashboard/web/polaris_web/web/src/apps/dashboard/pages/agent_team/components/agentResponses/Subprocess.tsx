import React, { useState, useEffect, useCallback, useMemo } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {  AgentRun, AgentSubprocess, State } from "../../types";
import { CaretDownMinor } from "@shopify/polaris-icons";
import api from "../../api";
import { useAgentsStore } from "../../agents.store";
import STEPS_PER_AGENT_ID, { preRequisitesMap } from "../../constants";
import { VerticalStack, Text } from "@shopify/polaris";
import OutputSelector, { getMessageFromObj } from "./OutputSelector";
import { intermediateStore } from "../../intermediate.store";

interface SubProcessProps {
    agentId: string, 
    processId:string, 
    subProcessFromProp: AgentSubprocess, 
    finalCTAShow: boolean, 
    setFinalCTAShow: (show: boolean) => void,
    triggerCallForSubProcesses: ()=> void ,
}

export const Subprocess = ({ agentId, processId, subProcessFromProp, finalCTAShow, setFinalCTAShow, triggerCallForSubProcesses }: SubProcessProps) => {
    const [subprocess, setSubprocess] = useState<AgentSubprocess | null>(subProcessFromProp);
    const [expanded, setExpanded] = useState(true);

    const { setCurrentAttempt, setCurrentSubprocess, currentSubprocess, currentAttempt, setAgentState, setPRState } = useAgentsStore(state => ({
        setCurrentAttempt: state.setCurrentAttempt,
        setCurrentSubprocess: state.setCurrentSubprocess,
        currentSubprocess: state.currentSubprocess,
        currentAttempt: state.currentAttempt,
        setAgentState: state.setAgentState,
        setPRState: state.setPRState
    }));  // Only subscribe to necessary store values

    const { setFilteredUserInput } = intermediateStore(state => ({ setFilteredUserInput: state.setFilteredUserInput })); 

    // Memoized function to create new subprocess
    const createNewSubprocess = useCallback(async (newSubIdNumber: number) => {

        // check for pre-requisites first
        const shouldCreateSubProcess = preRequisitesMap[agentId]?.[newSubIdNumber]?.action ? await preRequisitesMap[agentId][newSubIdNumber].action() : true;
        if (!shouldCreateSubProcess) {
            setFinalCTAShow(false);
            setPRState("4");
            return null;
        }

        const newSubId = newSubIdNumber.toString();
        const newRes = await api.updateAgentSubprocess({
            processId,
            subProcessId: newSubId,
            attemptId: 1
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

            if (newSubProcess.state === State.RUNNING) setAgentState("thinking");

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
                const allowMultiple = subprocess?.processOutput?.selectionType === "multiple"

                const initialValue = !allowMultiple ?
        getMessageFromObj(subprocess?.processOutput?.outputOptions[0], "textValue") :
        subprocess?.processOutput?.outputOptions.map((option: any) => (option.textValue !== undefined ? {
            label: option?.textValue,
            value: option?.value !== undefined ? option?.value : JSON.stringify(option)
        } : option));

            console.log("initialValue", initialValue)
                // add default filtered input here if needed
                setFilteredUserInput(initialValue);
            }

            if (newSubProcess.state === State.RE_ATTEMPT) {
                const tempRes = await api.updateAgentSubprocess({
                    processId,
                    subProcessId: currentSubprocess,
                    attemptId: currentAttempt + 1
                });
                setSubprocess(tempRes.subprocess as AgentSubprocess);
                setCurrentAttempt(currentAttempt + 1);
                setAgentState("thinking");
            }

            if (newSubProcess.state === State.AGENT_ACKNOWLEDGED) {
                const newSub = await createNewSubprocess(Number(currentSubprocess) + 1);
                setFilteredUserInput(null);
                setSubprocess(newSub);
                triggerCallForSubProcesses();
            }

            if (newSubProcess.state === State.USER_PROVIDED_SOLUTION) {
                setAgentState("idle");
            }

            if (JSON.stringify(newSubProcess) !== JSON.stringify(subprocess)) {
                setSubprocess(newSubProcess);
            }
        };

        const interval = setInterval(fetchSubprocess, 2000);
        if (finalCTAShow) clearInterval(interval);
        return () => clearInterval(interval);
    }, [currentSubprocess, finalCTAShow, processId, currentAttempt, subProcessFromProp, createNewSubprocess]);

    if (!subprocess) return null;

    const handleSelect = (selectedChoices: any) => {
        console.log(selectedChoices);
        setFilteredUserInput(selectedChoices);
    }

    return useMemo(() => (
        <VerticalStack gap="4">
            <div className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${expanded ? "gap-1" : "gap-0"}`}>
                <button className="bg-[#F6F6F7] w-full flex items-center cursor-pointer" onClick={() => setExpanded(!expanded)}>
                    <motion.div animate={{ rotate: expanded ? 0 : 270 }} transition={{ duration: 0.2 }}>
                        <CaretDownMinor height={20} width={20} />
                    </motion.div>
                    <span className="text-sm text-[var(--text-default)]">{subprocess.subProcessHeading}</span>
                </button>

                <AnimatePresence>
                    <motion.div animate={expanded ? "open" : "closed"} variants={{ open: { height: "auto", opacity: 1 }, closed: { height: 0, opacity: 0 } }} transition={{ duration: 0.2 }} className="overflow-hidden">
                        <div className="bg-[#F6F6F7] ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]">
                            <AnimatePresence initial={false}>
                                {subprocess?.logs?.map((log, index) => (
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
                <Text variant="bodyMd" as="span">{subprocess.processOutput?.outputMessage}</Text>
            }

            {subprocess.state === State.DISCARDED && subprocess.userInput &&
                <Text variant="bodyMd" as="span">{subprocess.userInput?.inputMessage}</Text>
            }
        </VerticalStack>
    ), [subprocess, expanded]);  // Only re-render if these values change
};