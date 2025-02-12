import { Modal, VerticalStack } from "@shopify/polaris"
import { useRef, useState } from "react";
import EditTextField from "./EditTextField";



function EditModal(props) {
   const {editData, active, setActive, saveEditData, modifyEditData} = props
    const modalComponent = (
        <Modal
            key="edit_modal"
            open={active}
            onClose={() => setActive(false)}
            title="Edit dependencies"
            primaryAction={{
                id: "edit-dependency",
                content: 'Save',
                onAction: () => {saveEditData(editData)},
            }}
            large={true}
        >
            <Modal.Section>
                <VerticalStack gap={2}>
                    {editData.map((ele, index) => {
                        return EditTextField(ele, modifyEditData)
                    })}
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )

    return modalComponent
}


export default EditModal
