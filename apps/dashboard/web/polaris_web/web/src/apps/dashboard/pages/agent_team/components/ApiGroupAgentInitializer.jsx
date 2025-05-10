import React, { useState } from "react";
import PersistStore from "../../../../main/PersistStore";
import agentApi from '../api'
import func from "../../../../../util/func";
import DropDownAgentInitializer from "./DropDownAgentInitializer";
import { useAgentsStore } from "../agents.store";

function ApiGroupAgentInitializer(props) {

    const { agentType } = props
    const {selectedModel} = useAgentsStore(state => state)

    const [selectedCollections, setSelectedCollections] = useState([]);
    const allCollections = PersistStore(state => state.allCollections)
    const optionsList = allCollections.filter(x => !x.deactivated).map((x) => {
        return {
            label: x.displayName,
            value: x.id,
        }
    })

    async function startAgent(collectionIds) {
        if (collectionIds.length === 0) {
            func.setToast(true, true, "Please select collections to run the agent")
            return
        }

        await agentApi.createAgentRun({
            agent: agentType,
            data: {
                apiCollectionIds: collectionIds,
            },
            modelName: selectedModel.id
        })
        func.setToast(true, false, "Agent run scheduled")
    }

    return <DropDownAgentInitializer
        optionsList={optionsList}
        data={selectedCollections}
        setData={setSelectedCollections}
        startAgent={startAgent}
        agentText={"Hey! Let's select API collections to run the API grouping agent on."}
        agentProperty={"collection"}
    />
}


export default ApiGroupAgentInitializer