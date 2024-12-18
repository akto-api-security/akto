import { Modal} from "@shopify/polaris"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import { useState } from "react"


const sourcesAvailable = [
    { label: "Postman", value: "POSTMAN" },
    { label: "HAR file", value: "HAR" },
    { label: "Burp", value: "BURP" },
]

export function SelectSource({show, setShow, primaryAction}) {
    const [currentSource, setCurrentSource] = useState(null)
    return (
        <Modal
            open={show}
            onClose={() => setShow(false)}
            title="Select the source for your APIs"
            primaryAction={{
                content: "Upload",
                onAction: () => {
                    primaryAction(currentSource)
                    setShow(false)
                }
            }}
        >
            <Modal.Section>
                <DropdownSearch
                    placeholder="Select source"
                    optionsList={sourcesAvailable}
                    setSelected={(val) => { setCurrentSource(val)}}
                    value={currentSource}
                />
            </Modal.Section>
        </Modal>
    )
}