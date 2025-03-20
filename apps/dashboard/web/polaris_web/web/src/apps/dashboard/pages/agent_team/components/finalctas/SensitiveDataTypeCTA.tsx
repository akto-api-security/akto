import { Modal, VerticalStack, Text } from "@shopify/polaris"
import React from "react"
import { intermediateStore } from "../../intermediate.store";
import api from "./api";
import func from "../../../../../../util/func";
import { useAgentsStore } from "../../agents.store";

function SensitiveDataTypeCTA() {
    const { finalCTAShow, setFinalCTAShow } = useAgentsStore()
    const { filteredUserInput, resetIntermediateStore } = intermediateStore();

    async function saveFunction() {
        await api.createSensitiveResponseDataTypes({ dataTypeKeys: filteredUserInput })
        func.setToast(true, false, "Sensitive data types are being created")
        setFinalCTAShow(false)
        resetIntermediateStore()
    }

    async function closeFunction(){
        setFinalCTAShow(false)
        resetIntermediateStore()
    }

    return (filteredUserInput?.length || 0 == 0 ? <></> :
        <Modal
            title={"Save sensitive data types"}
            primaryAction={{
                content: 'Save',
                onAction: () => saveFunction()
            }} open={finalCTAShow}
            onClose={() => closeFunction()}
        >
            <Modal.Section>
                <VerticalStack gap={"4"}>
                    <Text as={"dd"}>
                        Do you want to add the {filteredUserInput?.length} selected sensitive data types to Akto ?
                    </Text>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}
export default SensitiveDataTypeCTA