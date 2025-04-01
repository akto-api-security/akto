import React from "react";
import { useAgentsStore } from "../../agents.store";
import { intermediateStore } from "../../intermediate.store";
import { Modal, Text, VerticalStack } from "@shopify/polaris";
import func from "../../../../../../util/func";

interface AgentCoreCTAProps {
    onSave: () => void;
    modalTitle: string,
    actionText: string,
    contentString: string
}

function AgentCoreCTA({ onSave, modalTitle, actionText, contentString }: AgentCoreCTAProps) {

    const { finalCTAShow, setFinalCTAShow } = useAgentsStore()

    const { filteredUserInput, resetIntermediateStore } = intermediateStore();

    async function saveFunction() {
        onSave()
        setFinalCTAShow(false)
        resetIntermediateStore()
        func.setToast(true, false, "Vulnerabilities for the collection: juice-shop has been stored successfully")
    }

    async function closeFunction() {
        setFinalCTAShow(false)
        resetIntermediateStore()
    }

    return (!filteredUserInput || (filteredUserInput?.length === 0) ? <></> :
        <Modal
            title={modalTitle}
            primaryAction={{
                content: actionText,
                onAction: () => saveFunction()
            }} 
            open={finalCTAShow}
            onClose={() => closeFunction()}
        >
            <Modal.Section>
                <VerticalStack gap={"4"}>
                    <Text as={"dd"}>
                        {contentString}
                    </Text>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}

export default AgentCoreCTA;