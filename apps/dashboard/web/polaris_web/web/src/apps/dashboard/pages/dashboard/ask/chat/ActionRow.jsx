import { Box, Button, HorizontalStack, Icon, Spinner, Text, VerticalStack } from "@shopify/polaris"
import { CircleAlertMajor, CircleTickMajor, ExternalMinor } from "@shopify/polaris-icons"
import MarkdownViewer from "@/apps/dashboard/components/shared/MarkdownViewer"
import IconTile from "../components/IconTile"

// One "Suggested actions" row — design_handoff_ask_akto_overlay/README.md, "5. Suggested
// actions". Two kinds:
//
// - `nav`: a destination. Opening it closes the popup and navigates, with no preview.
// - `write`: the two-phase MCP write-tool contract, made visible. /api/chatAndStore returns plain
//   text, so the preview IS the assistant's own dry-run message, and Confirm re-sends a fixed
//   confirmation phrase over the same conversation — the model never confirms on its own.
//   `doneText` is the model's real reply, which per the write-tool contract states the re-read
//   count, never a generic "success".
function ActionTrailing({ kind, state, onOpenNav, onReview }) {
    if (kind === "nav") return <Button size="slim" onClick={onOpenNav}>Open</Button>
    if (state === "idle") return <Button size="slim" onClick={onReview}>Review</Button>
    if (state === "running") {
        return (
            <HorizontalStack gap="1_5-experimental" blockAlign="center" wrap={false}>
                <Spinner size="small" accessibilityLabel="Applying" />
                <Text as="span" variant="bodySm" color="subdued">Applying</Text>
            </HorizontalStack>
        )
    }
    if (state === "done") {
        return (
            <Box color="text-ask-success">
                <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                    <Icon source={CircleTickMajor} color="success" />
                    <Text as="span" variant="bodySm" fontWeight="medium">Done</Text>
                </HorizontalStack>
            </Box>
        )
    }
    return null
}

// Indents the preview/done panels past the row's icon, as in the design.
function ActionPanel({ children }) {
    return (
        <Box paddingInlineStart="16" paddingInlineEnd="4" paddingBlockEnd="3">
            {children}
        </Box>
    )
}

export default function ActionRow({ kind, label, desc, state, previewText, doneText, onReview, onCancel, onConfirm, onUndo, onOpenNav }) {
    const isWrite = kind === "write"

    return (
        <VerticalStack gap="0">
            <Box paddingBlockStart="3" paddingBlockEnd="3" paddingInlineStart="4" paddingInlineEnd="4">
                <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                    <IconTile
                        source={isWrite ? CircleAlertMajor : ExternalMinor}
                        background={isWrite ? "bg-primary-subdued-hover" : "bg-hover"}
                        padding="1_5-experimental"
                    />
                    <Box width="100%">
                        <VerticalStack gap="0">
                            <Text as="span" variant="bodyMd" fontWeight="medium">{label}</Text>
                            <Text as="span" variant="bodySm" color="subdued">{desc}</Text>
                        </VerticalStack>
                    </Box>
                    <ActionTrailing kind={kind} state={state} onOpenNav={onOpenNav} onReview={onReview} />
                </HorizontalStack>
            </Box>

            {isWrite && state === "confirm" ? (
                <ActionPanel>
                    <Box padding="3" borderRadius="2" background="bg-subdued" borderWidth="1" borderColor="border-subdued">
                        <VerticalStack gap="2">
                            <Text as="p" variant="bodySm" fontWeight="semibold" color="subdued">Preview — nothing has changed yet</Text>
                            <MarkdownViewer markdown={previewText || ""} noPadding />
                            <HorizontalStack align="end" gap="2">
                                <Button size="slim" onClick={onCancel}>Cancel</Button>
                                <Button size="slim" primary onClick={onConfirm}>Confirm</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                </ActionPanel>
            ) : null}

            {isWrite && state === "done" ? (
                <ActionPanel>
                    <Box paddingBlockStart="2" paddingBlockEnd="2" paddingInlineStart="3" paddingInlineEnd="3" borderRadius="2" background="bg-success-subdued-hover">
                        <HorizontalStack gap="3" blockAlign="center" wrap={false}>
                            <Box width="100%">
                                <MarkdownViewer markdown={doneText || ""} noPadding />
                            </Box>
                            <Button size="slim" onClick={onUndo}>Undo</Button>
                        </HorizontalStack>
                    </Box>
                </ActionPanel>
            ) : null}
        </VerticalStack>
    )
}
