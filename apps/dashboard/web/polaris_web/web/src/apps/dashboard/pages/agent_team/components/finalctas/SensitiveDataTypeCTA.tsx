import { Modal, VerticalStack, Text } from "@shopify/polaris"
import React from "react"

function SensitiveDataTypeCTA(props) {

    const { show, setShow } = props
    // console.log("show: ", show)

    return (
        <Modal
            title={"Save sensitive data types"}
            primaryAction={{
                content: 'Save',
                onAction: () => console.log("saving")
            }} open={show}
            onClose={() => setShow(false)}
        >
            <Modal.Section>
                <VerticalStack gap={"4"}>
                    <Text as={"dd"}>
                        Do you want to add the selected sensitive data types to Akto ?
                    </Text>


                </VerticalStack>
            </Modal.Section>
        </Modal>

    )
}

export default SensitiveDataTypeCTA