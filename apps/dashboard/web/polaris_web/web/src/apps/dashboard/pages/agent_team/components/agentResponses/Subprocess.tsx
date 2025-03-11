import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import {  AgentSubprocess, State } from "../../types";
import { CaretDownMinor } from "@shopify/polaris-icons";
import api from "../../api";
import { useAgentsStore } from "../../agents.store";

export const Subprocess = ({currentAgentType, processId, subProcessFromProp}: {currentAgentType: string, processId:string, subProcessFromProp: AgentSubprocess}) => {
    const [subprocess, setSubprocess] = useState<AgentSubprocess | null>(null);
    const [expanded, setExpanded] = useState(true);

    const {agentSteps, setAgentSteps, setCurrentAttempt, setCurrentSubprocess, currentSubprocess, currentAttempt } = useAgentsStore();

    useEffect(() => {
        const fetchSubprocess = async () => {
            const response = await api.getSubProcess({
                processId: processId,
                subProcessId: currentSubprocess,
                attemptId: currentAttempt,
            });

            let subProcess = response.subprocess as AgentSubprocess;
            let subId = subProcess.subProcessId;

            // handle new subprocess creation from here
            // assuming there is no discard/approve button for now
            if(response.subProcess !== null && response.subProcess.state === State.PENDING) {
                // create new subprocess without retry attempt now
                const newSubId = (Number(currentSubprocess) + 1).toLocaleString()
                subId = newSubId;
                const newRes = await api.updateAgentSubprocess({
                    processId: processId,
                    subProcessId: newSubId,
                    attemptId: 1
                });
                subProcess = newRes.subprocess as AgentSubprocess;
                setCurrentSubprocess(newSubId);
                setCurrentAttempt(1);
            }
            const existingData = {...agentSteps[currentAgentType]};
            let newData = {...existingData}
            const logs = subProcess?.logs || [];
            const processOutput = subProcess?.processOutput || {};
            newData[subId] = {
                heading: newData[subId]?.heading || newData[subId],
                logs: logs || [],
                processOutput: processOutput || {}
            }
            const finalMap = {...agentSteps, [currentAgentType]: newData};
            setAgentSteps(finalMap);
            setSubprocess(subProcess);

        }

        const interval = setInterval(fetchSubprocess, 2000);

        return () => clearInterval(interval);
    }, [currentSubprocess]);

    if (!subprocess) {
        return null;
    }

    return (
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
    );
}