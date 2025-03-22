import React, { useEffect, useState } from 'react';
import { useAgentsStore } from './agents.store';
import { Agent, AgentSubprocess } from './types';
import AgentWindow from './components/AgentWindow';
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards';
import GridRows from '../../components/shared/GridRows';
import AgentRowCard from './AgentRowCard';
import TitleWithInfo from "../../../../apps/dashboard/components/shared/TitleWithInfo"
import api from './api';
import { useAgentsStateStore } from './agents.state.store';
import transform from './transform';

const AGENT_IMAGES = ["/public/agents/secret-agent-1.svg",
    "/public/agents/secret-agent-2.svg",
    "/public/agents/secret-agent-3.svg",
    "/public/agents/secret-agent-4.svg",
    "/public/agents/secret-agent-5.svg"
]

function AgentTeam() {
    const { setAvailableModels, setCurrentAgent } = useAgentsStore();

    const [Agents, setAgents] = useState([])
    const {agentsStore, setCurrentAgentState} = useAgentsStateStore();

    useEffect(() => {
        api.getMemberAgents().then((res: { agents: any; }) => {
            if(res && res.agents){
                let agents = res.agents.map((x: { _name: string; agentFunctionalName: string; description: string; },i: number) => {
                    return {
                        id: x._name,
                        name: x.agentFunctionalName,
                        description: x.description,
                        image: AGENT_IMAGES[(i%(AGENT_IMAGES.length))],
                    }
                })
                setAgents(agents)
            }
        })

        api.getAgentModels().then((res: { models: any; }) => {
            if(res && res.models){
                let models = res.models.map((x: { _name: string; modelName: string; }) =>{
                    return {
                        id: x._name,
                        name: x.modelName
                    }
                })
                setAvailableModels(models);
            }
        })

    }, []);

    const closeAction = () => {
        setCurrentAgent(null)
        setShowAgentWindow(false)
    }

    const onButtonClick = (agent: Agent | null ) => {
        setCurrentAgent(agent)
        setShowAgentWindow(true)
    }

    const agents = (
        <GridRows 
            CardComponent={AgentRowCard} 
            columns="3"
            items={Agents}
            onButtonClick={onButtonClick}
            cardType="AGENT"
        />
    )

    const fetchSubprocess = async () => {
        try {
            Object.entries(agentsStore).forEach(async ([key,agent]) => {
                if(!agent.currentAgentSubprocess || agent.currentAgentSubprocess === "0" || !agent.currentAgentProcessId ) return;
                // TODO: creating Infinite loop of `no process found toast`, if process is not found
                console.log(agent)
                const response = await api.getSubProcess({
                    processId: agent.currentAgentProcessId,
                    subProcessId: agent.currentAgentSubprocess,
                    attemptId: agent.currentSubprocessAttempt,
                });
                let subProcess = response.subprocess as AgentSubprocess;
                console.log(response)
                setCurrentAgentState(key, transform.getStateToAgentState(subProcess.state));
            })
            
        } catch (error) {
            console.error("Error fetching subprocess:", error);
        }
    };
    

    const pageComponents = [agents]

    const [showAgentWindow, setShowAgentWindow] = useState(false)

    useEffect(() => {
        let intervalId ;
    
        if (!showAgentWindow) {
            intervalId = setInterval(() => {
                fetchSubprocess();
            }, 2000); 
        }
    
        return () => {
            if (intervalId) {
                clearInterval(intervalId);
            }
        };
    }, [showAgentWindow]);

    return (
        <>
            <PageWithMultipleCards
                components={pageComponents}
                isFirstPage={true}
                divider={true}
                title={
                    <TitleWithInfo
                        tooltipContent={"These are AI agents that can be used to provide insights"}
                        titleText={"AI Agents"}
                        // TODO: implement docsUrl functionality
                        docsUrl={"https://docs.akto.io"}
                    />
                }
                // secondaryActions={[
                //     // TODO: implement Knowledge base functionality
                //     <Button id={"Knowledge-base"} onClick={() => {}}>Knowledge base</Button>
                // ]}
            />
            <AgentWindow onClose={closeAction} open={showAgentWindow} />
        </>
    )
}

export default AgentTeam;