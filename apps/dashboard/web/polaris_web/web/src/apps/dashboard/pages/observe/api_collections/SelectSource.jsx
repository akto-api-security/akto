import { Modal, Checkbox, Box, Text, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import { useState } from "react"


const sourcesAvailable = [
    { label: "Postman", value: "POSTMAN" },
    { label: "HAR file", value: "HAR" },
    { label: "Burp", value: "BURP" },
]

export function SelectSource({show, setShow, primaryAction}) {
    const [currentSource, setCurrentSource] = useState(null)
    const [skipLiveReplay, setSkipLiveReplay] = useState(false)
    return (
        <Modal
            open={show}
            onClose={() => setShow(false)}
            title="Select the source for your APIs"
            primaryAction={{
                content: "Upload",
                onAction: () => {
                    primaryAction(currentSource, skipLiveReplay)
                    setShow(false)
                }
            }}
        >
            <Modal.Section>
                <VerticalStack gap="4">
                    <DropdownSearch
                        placeholder="Select source"
                        optionsList={sourcesAvailable}
                        setSelected={(val) => { setCurrentSource(val)}}
                        value={currentSource}
                    />
                    <Box>
                        <Checkbox
                            label="Skip live replay (use spec examples only)"
                            helpText="Enable this if your server is not reachable from Akto. The spec's example responses will be used instead of making real HTTP requests."
                            checked={skipLiveReplay}
                            onChange={(val) => setSkipLiveReplay(val)}
                        />
                    </Box>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}