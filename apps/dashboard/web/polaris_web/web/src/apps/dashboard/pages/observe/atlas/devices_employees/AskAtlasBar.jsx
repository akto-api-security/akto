import { useState, useRef, useCallback } from "react";
import { Box, Button, Divider, Form, HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris";
import func from "@/util/func";
import Pressable from "../../../dashboard/ask/components/Pressable";
import "../../../dashboard/ask/askOverlay.css";
import "../../../agentic/AgenticConversationPage.css";
import AgenticUserMessage from "../../../agentic/components/AgenticUserMessage";
import AgenticStreamingResponse from "../../../agentic/components/AgenticStreamingResponse";
import AgenticThinkingBox from "../../../agentic/components/AgenticThinkingBox";
import { sendQuery } from "../../../agentic/services/agenticService";
import { buildAgenticObserveChatMetadata } from "../../agentic/agenticObserveApi";
import { ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS, ATLAS_CHAT_SCOPE } from "./constants";

function AskAtlasBar({
    placeholder = ASK_ATLAS_PLACEHOLDER,
    suggestions = ASK_ATLAS_SUGGESTIONS,
    chatScope = ATLAS_CHAT_SCOPE,
}) {
    const [query, setQuery] = useState("");
    const [messages, setMessages] = useState([]);
    const [conversationId, setConversationId] = useState(null);
    const [loading, setLoading] = useState(false);
    const latestRequest = useRef(0);

    const submit = useCallback(async (text) => {
        const question = (text ?? query).trim();
        if (!question || loading) return;

        const requestId = ++latestRequest.current;
        setMessages((prev) => [...prev, { id: `user_${requestId}`, role: "user", message: question }]);
        setQuery("");
        setLoading(true);

        try {
            const response = await sendQuery(question, conversationId, "ASK_AKTO", buildAgenticObserveChatMetadata(chatScope));
            if (latestRequest.current !== requestId) return;
            if (response?.conversationId) {
                setConversationId((prev) => prev || response.conversationId);
            }
            setMessages((prev) => [
                ...prev,
                { id: `system_${requestId}`, role: "system", message: response?.response || "Atlas returned no answer for this question." },
            ]);
        } catch {
            if (latestRequest.current !== requestId) return;
            func.setToast(true, true, "Unable to get a response from Atlas. Please try again.");
        } finally {
            if (latestRequest.current === requestId) setLoading(false);
        }
    }, [query, loading, conversationId, chatScope]);

    const clear = useCallback(() => {
        latestRequest.current += 1;
        setMessages([]);
        setConversationId(null);
        setLoading(false);
    }, []);

    return (
        <LegacyCard>
            <Box padding="4">
                <VerticalStack gap="3">
                    <Form onSubmit={() => submit()}>
                        <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                            <Box
                                width="100%"
                                background="bg"
                                borderWidth="1"
                                borderColor="border-magic"
                                borderRadius="3"
                                outlineWidth="2"
                                outlineColor="border-ask-magic"
                                paddingInlineStart="3"
                                paddingInlineEnd="3"
                                paddingBlockStart="1"
                                paddingBlockEnd="1"
                            >
                                <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                                    <Box
                                        as="span"
                                        minWidth="max-content"
                                        background="bg-primary-subdued-hover"
                                        color="text-ask-magic"
                                        borderRadius="full"
                                        paddingBlockStart="05"
                                        paddingBlockEnd="05"
                                        paddingInlineStart="3"
                                        paddingInlineEnd="3"
                                    >
                                        <Text as="span" variant="bodySm" fontWeight="bold">Ask Atlas</Text>
                                    </Box>
                                    <Box width="100%">
                                        <Box className="agentic-search-input">
                                            <TextField
                                                label="Ask Atlas"
                                                labelHidden
                                                borderless
                                                autoComplete="off"
                                                placeholder={placeholder}
                                                value={query}
                                                onChange={setQuery}
                                            />
                                        </Box>
                                    </Box>
                                </HorizontalStack>
                            </Box>
                            <Button primary submit size="large" loading={loading}>Ask</Button>
                        </HorizontalStack>
                    </Form>
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text variant="bodySm" color="subdued">Try</Text>
                        {suggestions.map((suggestion) => (
                            <Pressable
                                key={suggestion}
                                onClick={() => submit(suggestion)}
                                background="bg-subdued"
                                hoverBackground="bg-primary-subdued-hover"
                                borderWidth="1"
                                borderColor="border"
                                hoverBorderColor="border-ask-magic"
                                borderRadius="full"
                                opacity={loading ? "0.6" : undefined}
                                paddingBlockStart="1"
                                paddingBlockEnd="1"
                                paddingInlineStart="3"
                                paddingInlineEnd="3"
                            >
                                <Text as="span" variant="bodySm">{suggestion}</Text>
                            </Pressable>
                        ))}
                    </HorizontalStack>
                    {messages.length > 0 && (
                        <VerticalStack gap="3">
                            <Divider />
                            {messages.map((msg) => (
                                msg.role === "user"
                                    ? <AgenticUserMessage key={msg.id} content={msg.message} />
                                    : <AgenticStreamingResponse key={msg.id} content={msg.message} />
                            ))}
                            {loading && <AgenticThinkingBox />}
                            <HorizontalStack align="end">
                                <Button plain onClick={clear}>Clear conversation</Button>
                            </HorizontalStack>
                        </VerticalStack>
                    )}
                </VerticalStack>
            </Box>
        </LegacyCard>
    );
}

export default AskAtlasBar;
