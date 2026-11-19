import { useMemo, useState } from "react"
import { Box, Modal, Text, TextField, VerticalStack } from "@shopify/polaris"
import DropdownSearch from "@/apps/dashboard/components/shared/DropdownSearch"
import PersistStore from "@/apps/main/PersistStore"
import { collectionOptions } from "./collectionOptions"

const EXAMPLE = "Write a post-request script: the chat POST returns a job id and the answer is polled from a GET."
// Room for the collection list to open inside the modal rather than off its bottom edge.
const BODY_MIN_HEIGHT = "360px"

// The script-writing mode's first screen: an optional collection and the request. The collection can
// also just be named in the text; the agent resolves it.
// A Polaris Modal (like RunTest), not the Ask overlay's custom sheet: Polaris lifts popovers one layer
// above the modal they open from, so the collection dropdown opens on top. Inside the sheet it opened
// behind it.
export default function ScriptIntro({ initialCollectionId, onStart, onClose }) {
    const allCollections = PersistStore((state) => state.allCollections)
    const [apiCollectionId, setApiCollectionId] = useState(initialCollectionId ?? null)
    const [text, setText] = useState("")

    const options = useMemo(() => collectionOptions(allCollections), [allCollections])
    const selectedLabel = options.find((o) => o.value === apiCollectionId)?.label || ""

    const submit = () => {
        const trimmed = text.trim()
        if (!trimmed) return
        onStart(trimmed, apiCollectionId)
    }

    return (
        <Modal
            open
            large
            onClose={onClose}
            title="Write a script with Ask Akto"
            primaryAction={{ content: "Start", onAction: submit, disabled: !text.trim() }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <Box minHeight={BODY_MIN_HEIGHT}>
                    <VerticalStack gap="4">
                        <Text as="p" color="subdued">
                            Ask Akto reads the captured samples of a collection's APIs, works out how they chain (for example a POST
                            that returns a job id and a GET that polls it), and writes a pre-request or post-request script. Validate
                            it by replaying an endpoint, then save it to the script library.
                        </Text>
                        <DropdownSearch
                            id="ask-script-collection"
                            label="Collection (optional: you can also name it below)"
                            placeholder="Select collection"
                            optionsList={options}
                            setSelected={setApiCollectionId}
                            value={selectedLabel}
                            preSelected={apiCollectionId !== null ? [apiCollectionId] : []}
                        />
                        <TextField
                            label="What should the script do?"
                            value={text}
                            onChange={setText}
                            multiline={4}
                            autoComplete="off"
                            placeholder={EXAMPLE}
                        />
                    </VerticalStack>
                </Box>
            </Modal.Section>
        </Modal>
    )
}
