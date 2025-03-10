import React, { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { AgentLog, AgentSubprocess } from "../../types";
import { getFakeSubProcess, appendLog } from "../../utils";
import { Box, Button, Icon } from "@shopify/polaris";
import { CaretDownMinor } from "@shopify/polaris-icons";
import api from "../../api";

type SubprocessProps = {
    subprocessId: string;
    processId: string;
}

export const Subprocess = ({ subprocessId, processId }: SubprocessProps) => {
    const [subprocess, setSubprocess] = useState<AgentSubprocess | null>(null);
    const [expanded, setExpanded] = useState(true);

    useEffect(() => {
        const fetchSubprocess = async () => {
            const response = await api.getSubProcess({
                processId,
                subprocessId,
            });
            setSubprocess(response.subProcess);
        }

        const interval = setInterval(fetchSubprocess, 5000);

        return () => clearInterval(interval);
    }, [subprocessId, processId]);

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
                    {subprocess.subprocessHeading}
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
                            {subprocess.logs.map((log, index) => (
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