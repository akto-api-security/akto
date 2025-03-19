import React from "react"
import { Modal, VerticalStack, Text } from "@shopify/polaris"
import { intermediateStore } from "../../intermediate.store";
import api from "../../../../pages/observe/api"
import func from "../../../../../../util/func";

function AgentGroupCTA(props) {

    const { show, setShow } = props

    const { filteredUserInput, outputOptions } = intermediateStore();

    async function saveFunction () {

        let filteredCollections = outputOptions.outputOptions.filter(x => {
            return filteredUserInput.includes(x.value)
        })

        for(let index of filteredCollections){
            let collectionName = filteredCollections[index].value
            let apis = filteredCollections[index].apis
            await api.addApisToCustomCollection(apis, collectionName)
        }
        func.setToast(true, false, "API groups are being created")
        setShow(false)
    }

        return (
            <Modal
                title={"Save API groups"}
                primaryAction={{
                    content: 'Save',
                    onAction: () => saveFunction()
                }} open={show}
                onClose={() => setShow(false)}
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