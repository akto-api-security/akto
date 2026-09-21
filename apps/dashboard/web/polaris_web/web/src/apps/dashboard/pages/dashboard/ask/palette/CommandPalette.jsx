import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Box, Text, HorizontalStack, VerticalStack } from "@shopify/polaris"
import PaletteCombobox from "./PaletteCombobox"
import PaletteChatPanel from "./PaletteChatPanel"
import { resolve } from "./resolveCommand"
import { applyNavigationSideEffects, SUGGESTED_PROMPTS } from "./paletteHelpers"
import { sendQuery } from "@/apps/dashboard/pages/agentic/services/agenticService"

const MODE_PALETTE = "palette"
const MODE_CHAT = "chat"

// The palette <-> chat state machine. Typing resolves locally and instantly (no server round
// trip — see resolveCommand.resolve); submitting either navigates (INTENT/COMMAND) or opens chat
// mode and asks Ask Akto (ASK_AKTO), reusing the SAME agenticService.sendQuery the full Ask Akto
// page and every other chat surface in the app already use — this is not a fourth chat
// implementation.
export default function CommandPalette({ onOpenRoute, seedPrompt }) {
    const [query, setQuery] = useState("")
    const [mode, setMode] = useState(MODE_PALETTE)
    const [messages, setMessages] = useState([])
    const [aiLoading, setAiLoading] = useState(false)
    const conversationIdRef = useRef(null)
    const unmountedRef = useRef(false)

    useEffect(() => () => { unmountedRef.current = true }, [])

    const { options, askFirst } = useMemo(() => resolve(query), [query])

    const startChat = useCallback(async (prompt) => {
        const trimmed = (prompt || "").trim()
        if (!trimmed) return
        setMode(MODE_CHAT)
        setMessages((prev) => [...prev, { role: "user", message: trimmed }])
        setAiLoading(true)
        try {
            const res = await sendQuery(trimmed, conversationIdRef.current, "COMMAND_PALETTE")
            if (unmountedRef.current) return
            conversationIdRef.current = res?.conversationId || conversationIdRef.current
            setMessages((prev) => [...prev, {
                role: "assistant",
                message: res?.response || "I couldn't get an answer just now — please try again.",
                userPrompt: trimmed,
                conversationId: conversationIdRef.current,
            }])
        } catch (e) {
            if (unmountedRef.current) return
            setMessages((prev) => [...prev, {
                role: "assistant",
                message: "Something went wrong reaching Ask Akto. Please try again.",
            }])
        } finally {
            if (!unmountedRef.current) setAiLoading(false)
        }
    }, [])

    const handleSelectOption = useCallback((option) => {
        if (!option) return
        if (option.kind === "ASK_AKTO") {
            startChat(option.query)
            setQuery("")
            return
        }
        applyNavigationSideEffects(option)
        onOpenRoute(option.route, option.params)
        setQuery("")
    }, [startChat, onOpenRoute])

    const collapseToPalette = useCallback(() => setMode(MODE_PALETTE), [])

    // A recommendation tile lives in a sibling component (RecommendationTiles), not inside this
    // state machine — seedPrompt is how its "Ask" click reaches in and starts a chat here, via a
    // {prompt, nonce} value so the SAME prompt text can be re-triggered (a bare string wouldn't
    // change and the effect wouldn't re-fire on a second click of the same tile).
    const lastSeedNonceRef = useRef(null)
    useEffect(() => {
        if (!seedPrompt || seedPrompt.nonce === lastSeedNonceRef.current) return
        lastSeedNonceRef.current = seedPrompt.nonce
        startChat(seedPrompt.prompt)
    }, [seedPrompt, startChat])

    if (mode === MODE_CHAT) {
        return (
            <PaletteChatPanel
                messages={messages}
                loading={aiLoading}
                onSendFollowUp={startChat}
                onCollapse={collapseToPalette}
                onOpenRoute={onOpenRoute}
            />
        )
    }

    return (
        <VerticalStack gap="3">
            <PaletteCombobox value={query} onChange={setQuery} options={options} onSelect={handleSelectOption} />
            {!query.trim() ? (
                <HorizontalStack gap="2" wrap>
                    <Text variant="bodySm" color="subdued">Try:</Text>
                    {SUGGESTED_PROMPTS.map((p) => (
                        <Box
                            key={p}
                            as="button"
                            onClick={() => startChat(p)}
                            padding="1"
                            background="bg-surface-secondary"
                            borderRadius="1"
                        >
                            <Text variant="bodySm">{p}</Text>
                        </Box>
                    ))}
                </HorizontalStack>
            ) : null}
        </VerticalStack>
    )
}
