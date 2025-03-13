import { Modal, VerticalStack, Text } from "@shopify/polaris"
import React from "react"
import { intermediateStore } from "../../intermediate.store";
import api from "./api";
import func from "../../../../../../util/func";

function SensitiveDataTypeCTA(props) {

    const { show, setShow } = props

    const { filteredUserInput } = intermediateStore();

    async function saveFunction () {
        await api.createSensitiveResponseDataTypes({ dataTypeKeys: filteredUserInput })
        func.setToast(true, false, "Sensitive data types are being created")
        setShow(false)
    }

    return (
        <Modal
            title={"Save sensitive data types"}
            primaryAction={{
                content: 'Save',
                onAction: () => saveFunction()
            }} open={show}
            onClose={() => setShow(false)}
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