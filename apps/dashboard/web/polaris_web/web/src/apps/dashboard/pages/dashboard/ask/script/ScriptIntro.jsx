import { useMemo, useState } from "react"
import { Box, Button, Form, HorizontalStack, Text, TextField, VerticalStack } from "@shopify/polaris"
import { CancelMinor } from "@shopify/polaris-icons"
import DropdownSearch from "@/apps/dashboard/components/shared/DropdownSearch"
import PersistStore from "@/apps/main/PersistStore"
import { collectionOptions } from "./collectionOptions"

const EXAMPLE = "Write a post-request script: the chat POST returns a job id and the answer is polled from a GET."

// The script-writing mode's first screen: an optional collection and the request. The collection can
// also just be named in the text; the agent resolves it.
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
        <Box padding="5">
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text as="h2" variant="headingMd">Write a script with Ask Akto</Text>
                    <Button plain monochrome icon={CancelMinor} onClick={onClose} accessibilityLabel="Close" />
                </HorizontalStack>
                <Text as="p" color="subdued">
                    Ask Akto reads the captured samples of a collection's APIs, works out how they chain (for example a POST that
                    returns a job id and a GET that polls it), and writes a pre-request or post-request script. Validate it by
                    replaying an endpoint, then save it to the script library.
                </Text>
                <Form onSubmit={submit}>
                    <VerticalStack gap="3">
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
                            multiline={3}
                            autoComplete="off"
                            placeholder={EXAMPLE}
                        />
                        <HorizontalStack align="end">
                            <Button primary submit disabled={!text.trim()}>Start</Button>
                        </HorizontalStack>
                    </VerticalStack>
                </Form>
            </VerticalStack>
        </Box>
    )
}
