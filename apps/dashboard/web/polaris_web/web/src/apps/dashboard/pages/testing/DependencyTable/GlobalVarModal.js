import { Modal, Scrollable, VerticalStack } from "@shopify/polaris"
import { useEffect, useState } from "react"
import api from "../api"
import EditGlobalDataFields from "./EditGlobalDataFields"

function GlobalVarModal(props) {
    const { active, setActive, apiCollectionIds } = props
    const [globalVarsData, setGlobalVarsData] = useState([])

    const handleTextFieldChange = (newVal, currentHost) => {
        const newGlobalVarsData = globalVarsData.map((ele, idx) => {
            if (ele["currentHost"] === currentHost) {
                ele["newHost"] = newVal
            }
            return ele
        })

        setGlobalVarsData(newGlobalVarsData)
    }

    const saveGlobalVars = async () => {
        await api.saveGlobalVars(globalVarsData)
        setActive(false)
    }

    const fetchGlobalVarsData = async () => {
        const resp = await api.fetchGlobalVars(apiCollectionIds)
        const modifyHostDetails = resp["modifyHostDetails"]
        return modifyHostDetails ? modifyHostDetails : []
    }

    useEffect(() => {
        if (active) {
            fetchGlobalVarsData().then((x) => {
                if (x) setGlobalVarsData(x)
            })
        }
    }, [active])

    const modalComponent = (
        <Modal
            key="global_vars_modal"
            open={active}
            onClose={() => setActive(false)}
            title="Edit Global Vars"
            primaryAction={{
                id: "edit-global-vars",
                content: 'Save',
                onAction: () => { saveGlobalVars() },
            }}
        >
            <Scrollable style={{ maxHeight: "400px" }} shadow>
                <Modal.Section>
                    <VerticalStack gap={2}>
                        {globalVarsData.map((ele, index) => {
                            return EditGlobalDataFields(ele, handleTextFieldChange)
                        })}
                    </VerticalStack>
                </Modal.Section>
            </Scrollable>
        </Modal>
    )

    return modalComponent
}

export default GlobalVarModal