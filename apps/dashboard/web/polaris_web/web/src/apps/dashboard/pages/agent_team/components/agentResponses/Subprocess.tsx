import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {  AgentSubprocess, State } from "../../types";
import { CaretDownMinor } from "@shopify/polaris-icons";
import api from "../../api";
import { useAgentsStore } from "../../agents.store";
import STEPS_PER_AGENT_ID from "../../constants";
import { VerticalStack, Text } from "@shopify/polaris";
import OutputSelector from "./OutputSelector";
import { intermediateStore } from "../../intermediate.store";

export const Subprocess = ({agentId, processId, subProcessFromProp, finalCTAShow, setFinalCTAShow}: {agentId: string, processId:string, subProcessFromProp: AgentSubprocess, finalCTAShow: boolean, setFinalCTAShow: (show: boolean) => void}) => {
    const [subprocess, setSubprocess] = useState<AgentSubprocess | null>(subProcessFromProp);
    const [expanded, setExpanded] = useState(true);

    const {setCurrentAttempt, setCurrentSubprocess, currentSubprocess, currentAttempt, setAgentState } = useAgentsStore();
    const  { setFilteredUserInput } = intermediateStore();

    const createNewSubprocess = async (newSubIdNumber: number) => {
        let subProcess: AgentSubprocess;
        const newSubId = (newSubIdNumber).toLocaleString()
        const newRes = await api.updateAgentSubprocess({
            processId: processId,
            subProcessId: newSubId,
            attemptId: 1
        });
        subProcess = newRes.subprocess as AgentSubprocess;
        setCurrentSubprocess(newSubId);
        setCurrentAttempt(1);
        setAgentState('thinking');
        return subProcess;
    }

    const handleSelect = (selectedChoices: any) => {
        console.log(selectedChoices);
        setFilteredUserInput(selectedChoices);
    }

    useEffect(() => {
        const fetchSubprocess = async () => {
            const response = await api.getSubProcess({
                processId: processId,
                subProcessId: currentSubprocess,
                attemptId: currentAttempt,
            });
            let subProcess = response.subprocess as AgentSubprocess;
            let inputAcknowledged = response.inputAcknowledged as boolean;

            /* handle new subprocess creation from here
             State => ACCEPTED.
             Since we already know, how many total steps are going to be there,
             we cross check if this would have been the last step.
            */
            if(subProcess !== null && subProcess.state === State.ACCEPTED ) {
                const newSubIdNumber = Number(currentSubprocess) + 1;
                if (newSubIdNumber > STEPS_PER_AGENT_ID[agentId]) {
                    setFinalCTAShow(true)
                    await api.updateAgentRun({ processId: processId, state: "COMPLETED" })
                } else {
                    // create new subprocess without retry attempt now
                    subProcess = await createNewSubprocess(newSubIdNumber);
                }
            }

            /*
            PENDING => completed by the agent
            blocked on user to accept / discard (provide solution) / retry with hint.
            */ 
            if(subProcess !== null && subProcess.state === State.COMPLETED) {
                setAgentState("paused")
            }

            /*
            DISCARDED => discarded by the user.
            */
            if(subProcess !== null && subProcess.state === State.DISCARDED) {
                if(inputAcknowledged){
                    const newSubIdNumber = Number(currentSubprocess) + 1;
                    subProcess = await createNewSubprocess(newSubIdNumber);
                }
            }
            if(subProcess !== null) {
                setSubprocess(subProcess);
            }else{
                setAgentState("idle")
            }

        }

        const interval = setInterval(fetchSubprocess, 2000);
        /*
        We do not want to refresh current subprocess, 
        if we're already at final CTA.
        */
        if (finalCTAShow) {
            clearInterval(interval);
        }
        return () => clearInterval(interval);
    }, [currentSubprocess, finalCTAShow]);

    if (!subprocess) {
        return null;
    }

    return (
        <VerticalStack gap ="4">
        <div className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${expanded ? "gap-1" : "gap-0"}`}>
            <button
                className="bg-[#F6F6F7] w-full flex items-center cursor-pointer"
                onClick={() => setExpanded(!expanded)}
            >
                <motion.div
                    animate={{ rotate: expanded ? 0 : 270 }}
                    transition={{ duration: 0.2 }}
                >
                    <CaretDownMinor height={20} width={20} />
                </motion.div>
                <span className="text-sm text-[var(--text-default)]">
                    {subprocess.subProcessHeading}
                </span>
            </button>
            
            <AnimatePresence>
                <motion.div
                    animate={expanded ? "open" : "closed"}
                    variants={{
                        open: { height: "auto", opacity: 1 },
                        closed: { height: 0, opacity: 0 }
                    }}
                    transition={{ duration: 0.2 }}
                    className="overflow-hidden"
                >
                    <div className="bg-[#F6F6F7] ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]">
                        <AnimatePresence initial={false}>
                            {subprocess?.logs?.map((log, index) => (
                                <motion.p 
                                    key={`${index}-${log.log}`}
                                    initial={{ opacity: 0, y: -10 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    transition={{ duration: 0.2 }}
                                    className="text-xs text-[var(--text-subdued)] ml-3! p-0.5 hover:bg-[var(--background-selected)]"
                                >
                                    {log.log}
                                </motion.p>
                            ))}
                        </AnimatePresence>
                    </div>
                </motion.div>
            </AnimatePresence>
        </div>
        {subprocess.state === State.COMPLETED && subprocess.processOutput !== null && 
            <OutputSelector
                processOutput={subprocess.processOutput}
                onHandleSelect={(selectedChoices: any) => {
                    handleSelect(selectedChoices);
                }}
            />
        }
        {subprocess.state === State.ACCEPTED &&  subprocess.processOutput !== null && 
            <Text variant="bodyMd" as="span">{subprocess.processOutput?.outputMessage}</Text>
        }
        {subprocess.state === State.DISCARDED &&  subprocess.userInput !== null && 
            <Text variant="bodyMd" as="span">{subprocess.userInput?.inputMessage}</Text>
        }
        </VerticalStack>
        
    );
}