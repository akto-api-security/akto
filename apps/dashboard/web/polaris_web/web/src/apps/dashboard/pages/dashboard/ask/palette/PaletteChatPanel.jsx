import { useEffect, useRef, useState } from "react"
import { Box, Button, HorizontalStack, Icon, Text, VerticalStack } from "@shopify/polaris"
import { CancelMajor } from "@shopify/polaris-icons"
import AgenticSearchInput from "@/apps/dashboard/pages/agentic/components/AgenticSearchInput"
import AgenticStreamingResponse from "@/apps/dashboard/pages/agentic/components/AgenticStreamingResponse"
import AgenticUserMessage from "@/apps/dashboard/pages/agentic/components/AgenticUserMessage"
import AgenticThinkingBox from "@/apps/dashboard/pages/agentic/components/AgenticThinkingBox"
import ActionChips from "./ActionChips"

// The palette's chat mode — composes the SAME primitives AskAktoSection.jsx does (verbatim
// AgenticSearchInput config, same near-bottom auto-scroll behavior), not a fourth chat
// implementation. Message shape matches that file's convention: {role: 'user'|'assistant',
// message, isFromHistory?, userPrompt?, conversationId?} — userPrompt/conversationId are read by
// ActionChips off the assistant message they belong to.
export default function PaletteChatPanel({ messages, loading, onSendFollowUp, onCollapse, onOpenRoute }) {
    const [followUpValue, setFollowUpValue] = useState("")
    const scrollRef = useRef(null)

    useEffect(() => {
        const el = scrollRef.current
        if (!el) return
        const NEAR_BOTTOM_THRESHOLD_PX = 80
        const observer = new MutationObserver(() => {
            const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
            if (distanceFromBottom <= NEAR_BOTTOM_THRESHOLD_PX) el.scrollTop = el.scrollHeight
        })
        observer.observe(el, { childList: true, subtree: true, characterData: true })
        return () => observer.disconnect()
    }, [])

    const handleSubmit = (value) => {
        if (value && value.trim()) {
            onSendFollowUp(value.trim())
            setFollowUpValue("")
        }
    }

    return (
        <Box background="bg-surface" borderRadius="3" shadow="card" padding="5">
            <VerticalStack gap="4">
                <HorizontalStack align="space-between" blockAlign="center">
                    <Text variant="headingSm">Ask Akto</Text>
                    <Button plain icon={<Icon source={CancelMajor} />} onClick={onCollapse} accessibilityLabel="Close chat" />
                </HorizontalStack>

                {/* aria-live="polite", never "assertive" — an assertive region on a streaming
                    response would interrupt a screen reader on every token appended.
                    aria-busy suppresses announcement of partial content while loading. */}
                <Box ref={scrollRef} maxHeight="420px" overflowY="scroll" as="section" aria-live="polite" aria-atomic="false" aria-busy={loading}>
                    <VerticalStack gap="3">
                        {messages.map((msg, idx) =>
                            msg.role === "user" ? (
                                <AgenticUserMessage key={idx} content={msg.message} />
                            ) : (
                                <VerticalStack key={idx} gap="2">
                                    <AgenticStreamingResponse content={msg.message} skipStreaming={msg.isFromHistory || false} />
                                    <ActionChips
                                        userPrompt={msg.userPrompt}
                                        assistantText={msg.message}
                                        conversationId={msg.conversationId}
                                        onSendFollowUp={onSendFollowUp}
                                        onOpenRoute={onOpenRoute}
                                    />
                                </VerticalStack>
                            )
                        )}
                        {loading && <AgenticThinkingBox />}
                    </VerticalStack>
                </Box>

                <AgenticSearchInput
                    value={followUpValue}
                    onChange={setFollowUpValue}
                    onSubmit={handleSubmit}
                    placeholder="Ask a follow up…"
                    isStreaming={loading}
                    isFixed={false}
                    inputWidth="100%"
                    containerStyle={{ display: "block" }}
                />
            </VerticalStack>
        </Box>
    )
}
