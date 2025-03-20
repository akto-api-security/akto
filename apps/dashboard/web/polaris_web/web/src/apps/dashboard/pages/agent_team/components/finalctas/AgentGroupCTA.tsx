import React from "react"
import { Modal, VerticalStack, Text } from "@shopify/polaris"
import { intermediateStore } from "../../intermediate.store";
import api from "../../../../pages/observe/api"
import func from "../../../../../../util/func";
import { useAgentsStore } from "../../agents.store";

function AgentGroupCTA() {

    const { finalCTAShow, setFinalCTAShow } = useAgentsStore()

    const { filteredUserInput, outputOptions, resetIntermediateStore } = intermediateStore();

    async function saveFunction() {
        let filteredCollections = outputOptions.outputOptions.filter(x => {
            return filteredUserInput.includes(x.value)
        })
        for (let index in filteredCollections) {
            let collectionName = filteredCollections[index].value
            let apis = filteredCollections[index].apis
            await api.addApisToCustomCollection(apis, collectionName)
        }
        func.setToast(true, false, "API groups are being created")
        setFinalCTAShow(false)
        resetIntermediateStore()
    }

    async function closeFunction(){
        setFinalCTAShow(false)
        resetIntermediateStore()
    }

    return (filteredUserInput?.length || 0 == 0 ? <></> :
        <Modal
            title={"Save API groups"}
            primaryAction={{
                content: 'Save',
                onAction: () => saveFunction()
            }} open={finalCTAShow}
            onClose={() => closeFunction()}
        >
            <Modal.Section>
                <VerticalStack gap={"4"}>
                    <Text as={"dd"}>
                        Do you want to add the {filteredUserInput?.length} selected API groups to Akto ?
                    </Text>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default AgentGroupCTA