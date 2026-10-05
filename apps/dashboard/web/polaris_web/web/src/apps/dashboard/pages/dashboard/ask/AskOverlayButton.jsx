import { useCallback, useState } from "react"
import { Box, HorizontalStack, Text } from "@shopify/polaris"
import AskOverlay from "./AskOverlay"
import Pressable from "./components/Pressable"
import Dot from "./components/Dot"
import useAskShortcut from "./palette/useAskShortcut"
import "./askOverlay.css"

// The topbar's Ask Akto entry point: the button, the ⌘K binding, and the overlay itself. Which
// dashboard's tiles and prompts it shows follows the current dashboard category.
export default function AskOverlayButton() {
    const [open, setOpen] = useState(false)
    const handleToggle = useCallback(() => setOpen((o) => !o), [])
    const handleClose = useCallback(() => setOpen(false), [])

    useAskShortcut(handleToggle)

    return (
        <>
            <Pressable
                onClick={handleToggle}
                background="bg-primary-subdued-hover"
                hoverBackground="bg-ask-magic-hover"
                borderWidth="1"
                borderColor="border-ask-magic"
                borderRadius="full"
                color="text-ask-magic"
                paddingBlockStart="2"
                paddingBlockEnd="2"
                paddingInlineStart="3"
                paddingInlineEnd="1_5-experimental"
            >
                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                    <Dot background="bg-primary" />
                    <Text as="span" variant="bodyMd" fontWeight="semibold">Ask Akto</Text>
                    <Box as="span" background="bg" borderRadius="full" paddingInlineStart="2" paddingInlineEnd="2">
                        <Text as="span" variant="bodySm" fontWeight="medium">⌘K</Text>
                    </Box>
                </HorizontalStack>
            </Pressable>
            <AskOverlay open={open} onClose={handleClose} />
        </>
    )
}
