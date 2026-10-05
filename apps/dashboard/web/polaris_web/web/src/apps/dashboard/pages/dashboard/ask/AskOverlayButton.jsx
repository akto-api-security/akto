import { useCallback, useState } from "react"
import { Box, HorizontalStack, Text } from "@shopify/polaris"
import { CATEGORY_AGENTIC_SECURITY } from "@/apps/main/labelHelper"
import AskOverlay from "./AskOverlay"
import Pressable from "./components/Pressable"
import Dot from "./components/Dot"
import useAskShortcut from "./palette/useAskShortcut"
import useDashboardCategory from "./palette/useDashboardCategory"
import "./askOverlay.css"

const LIGHT_TOPBAR_TONE = {
    background: "bg-primary-subdued-hover",
    hoverBackground: "bg-ask-magic-hover",
    borderColor: "border-ask-magic",
    color: "text-ask-magic",
    dot: "bg-primary",
    chip: "bg",
}

// The Agentic Security topbar is a purple gradient that forces its text white
// (components/layouts/header/Headers.css), so the lavender pill would read white-on-lavender.
const DARK_TOPBAR_TONE = {
    background: "bg-ask-on-dark",
    hoverBackground: "bg-ask-on-dark-hover",
    borderColor: "border-ask-on-dark",
    color: "text-on-color",
    dot: "bg",
    chip: "bg-ask-on-dark-chip",
}

// The topbar's Ask Akto entry point: the button, the ⌘K binding, and the overlay itself. Which
// dashboard's tiles and prompts it shows follows the current dashboard category.
export default function AskOverlayButton() {
    const [open, setOpen] = useState(false)
    const handleToggle = useCallback(() => setOpen((o) => !o), [])
    const handleClose = useCallback(() => setOpen(false), [])
    const tone = useDashboardCategory() === CATEGORY_AGENTIC_SECURITY ? DARK_TOPBAR_TONE : LIGHT_TOPBAR_TONE

    useAskShortcut(handleToggle)

    return (
        <>
            <Pressable
                onClick={handleToggle}
                accessibilityLabel="Ask Akto"
                background={tone.background}
                hoverBackground={tone.hoverBackground}
                borderWidth="1"
                borderColor={tone.borderColor}
                borderRadius="full"
                color={tone.color}
                paddingBlockStart="2"
                paddingBlockEnd="2"
                paddingInlineStart="3"
                paddingInlineEnd="1_5-experimental"
            >
                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                    <Dot background={tone.dot} />
                    <Text as="span" variant="bodyMd" fontWeight="semibold">Ask Akto</Text>
                    <Box as="span" background={tone.chip} borderRadius="full" paddingInlineStart="2" paddingInlineEnd="2">
                        <Text as="span" variant="bodySm" fontWeight="medium">⌘K</Text>
                    </Box>
                </HorizontalStack>
            </Pressable>
            <AskOverlay open={open} onClose={handleClose} />
        </>
    )
}
