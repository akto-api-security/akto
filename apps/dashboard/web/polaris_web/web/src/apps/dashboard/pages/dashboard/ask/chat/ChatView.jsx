import { useEffect, useRef, useState } from "react"
import { Box, Button, Divider, Form, HorizontalStack, Text, TextField, VerticalStack } from "@shopify/polaris"
import { ArrowUpMinor, CancelMinor, ChevronLeftMinor } from "@shopify/polaris-icons"
import AgenticThinkingBox from "@/apps/dashboard/pages/agentic/components/AgenticThinkingBox"
import ChatMessage from "./ChatMessage"
import ScrollArea from "../components/ScrollArea"

// Sheet height min(700px, 100vh - 80px) minus the header and the composer.
const MESSAGES_HEIGHT = "min(548px, calc(100vh - 232px))"

// The overlay's chat mode — design_handoff_ask_akto_overlay/README.md, "4. Answer view".
// Presentational: the conversation itself lives in useAskChat, owned by AskOverlay.
export default function ChatView({ chat, onClose, onNewQuestion, onOpenRoute }) {
    const { messages, loading, acts, ask, markStreamed, reviewAction, cancelAction, confirmAction, undoAction } = chat
    const [draft, setDraft] = useState("")
    const scrollRef = useRef(null)

    useEffect(() => {
        const el = scrollRef.current
        if (!el) return
        requestAnimationFrame(() => { el.scrollTop = el.scrollHeight })
    }, [messages, loading, acts])

    const handleSubmit = () => {
        const text = draft.trim()
        if (!text || loading) return
        ask(text)
        setDraft("")
    }

    return (
        <>
            <Box paddingInlineStart="3" paddingInlineEnd="3" paddingBlockStart="2" paddingBlockEnd="2">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Button plain monochrome removeUnderline icon={ChevronLeftMinor} onClick={onNewQuestion}>New question</Button>
                    <Button plain monochrome icon={CancelMinor} onClick={onClose} accessibilityLabel="Close" />
                </HorizontalStack>
            </Box>
            <Divider borderColor="border-ask-divider" />

            <ScrollArea ref={scrollRef} height={MESSAGES_HEIGHT} padding="5" aria-live="polite" aria-busy={loading}>
                <VerticalStack gap="6">
                    {messages.map((msg) => msg.role === "user" ? (
                        <HorizontalStack key={msg.id} align="end">
                            <Box
                                maxWidth="80%"
                                background="bg-primary"
                                color="text-on-color"
                                borderRadiusStartStart="4"
                                borderRadiusStartEnd="4"
                                borderRadiusEndStart="4"
                                borderRadiusEndEnd="1"
                                paddingBlockStart="2"
                                paddingBlockEnd="2"
                                paddingInlineStart="3"
                                paddingInlineEnd="3"
                            >
                                <Text as="p" variant="bodyMd">{msg.message}</Text>
                            </Box>
                        </HorizontalStack>
                    ) : (
                        <ChatMessage
                            key={msg.id}
                            message={msg.message}
                            userPrompt={msg.userPrompt}
                            skipStreaming={Boolean(msg.streamed)}
                            onStreamingComplete={() => markStreamed(msg.id)}
                            actionState={acts[msg.id]}
                            onReviewAction={() => reviewAction(msg.id)}
                            onCancelAction={() => cancelAction(msg.id)}
                            onConfirmAction={() => confirmAction(msg.id)}
                            onUndoAction={() => undoAction(msg.id)}
                            onOpenRoute={onOpenRoute}
                            onAsk={ask}
                        />
                    ))}
                    {loading ? <AgenticThinkingBox /> : null}
                </VerticalStack>
            </ScrollArea>

            <Divider borderColor="border-ask-divider" />
            <Box paddingInlineStart="4" paddingInlineEnd="4" paddingBlockStart="3" paddingBlockEnd="3">
                <Form onSubmit={handleSubmit}>
                    <VerticalStack gap="1_5-experimental">
                        <Box background="bg" borderRadius="3" shadow="md" paddingInlineStart="3" paddingInlineEnd="2" paddingBlockStart="1" paddingBlockEnd="1">
                            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                <Box width="100%">
                                    <TextField
                                        label="Ask a follow-up"
                                        labelHidden
                                        borderless
                                        autoComplete="off"
                                        placeholder="Ask a follow-up"
                                        value={draft}
                                        onChange={setDraft}
                                    />
                                </Box>
                                <Button
                                    primary
                                    submit
                                    icon={ArrowUpMinor}
                                    accessibilityLabel="Send"
                                    loading={loading}
                                    disabled={!draft.trim()}
                                />
                            </HorizontalStack>
                        </Box>
                        <HorizontalStack align="center">
                            <Text as="p" variant="bodySm" color="subdued">Actions that change data always show a preview first.</Text>
                        </HorizontalStack>
                    </VerticalStack>
                </Form>
            </Box>
        </>
    )
}
