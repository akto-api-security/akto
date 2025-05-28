import React, { useEffect, useState } from 'react';
import { useAgentsStore } from './agents.store';
import { Agent } from './types';
import AgentWindow from './components/AgentWindow';
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards';
import GridRows from '../../components/shared/GridRows';
import AgentRowCard from './AgentRowCard';
import TitleWithInfo from "../../../../apps/dashboard/components/shared/TitleWithInfo"
import api from './api';

const AGENT_IMAGES = ["/public/agents/secret-agent-1.svg",
    "/public/agents/secret-agent-2.svg",
    "/public/agents/secret-agent-3.svg",
    "/public/agents/secret-agent-4.svg",
    "/public/agents/secret-agent-5.svg"
]

function AgentTeam() {
    const { setAvailableModels, setCurrentAgent } = useAgentsStore();

    const [Agents, setAgents] = useState([])
    const [showConfigCTA, setShowConfigCTA] = useState(true)

    useEffect(() => {
        api.getMemberAgents().then((res: { agents: any; }) => {
            if(res && res.agents){
                let agents = res.agents.map((x: { _name: string; agentEnglishName: string; agentFunctionalName: string; description: string; },i: number) => {
                    return {
                        id: x._name,
                        name: x.agentEnglishName + " | " +x.agentFunctionalName,
                        description: x.description,
                        image: AGENT_IMAGES[(i%(AGENT_IMAGES.length))],
                    }
                })
                setAgents(agents)
            }
        })

        api.getAgentModels().then((res: { models: any; }) => {
            if(res && res.models){
                let models = res.models.map((x: { name: string; }) =>{
                    return {
                        id: x.name,
                        name: x.name
                    }
                })
                if(models?.length > 0){
                    setShowConfigCTA(false)
                }
                setAvailableModels(models);
            }
        })

    }, []);

    const [newCol, setNewCol] = useState(0)

    const closeAction = () => {
        setCurrentAgent(null)
        setNewCol(0)
        setShowAgentWindow(false)
    }

    const onButtonClick = (agent: Agent | null ) => {
        setNewCol(1)
        setCurrentAgent(agent)
        setShowAgentWindow(true)
    }

    const agents = (
        <GridRows CardComponent={AgentRowCard} columns="3"
            items={Agents}
            onButtonClick={onButtonClick}
            cardType="AGENT"
            changedColumns={newCol}
        />
    )

    const pageComponents = [agents]

    const [showAgentWindow, setShowAgentWindow] = useState(false)

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
                        // TODO: Add docs page for agents
                        docsUrl={"https://docs.akto.io"}
                    />
                }
                // secondaryActions={[
                //     // TODO: implement Knowledge base functionality
                //     <Button id={"Knowledge-base"} onClick={() => {}}>Knowledge base</Button>
                // ]}
            />
            <AgentWindow onClose={closeAction} open={showAgentWindow} showConfigCTA={showConfigCTA}/>
        </>
    )
}

export default AgentTeam;