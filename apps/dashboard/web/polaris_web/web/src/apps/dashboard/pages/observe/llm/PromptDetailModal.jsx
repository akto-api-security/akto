import { Badge, Box, HorizontalStack, Modal, Text, VerticalStack } from "@shopify/polaris";
import { ClockMinor, HashtagMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import { truncate } from "./constants";
import ChatMessage from "../../testing/TestRunResultPage/components/ChatMessage";

export default function PromptDetailModal({ prompt, onClose }) {
    if (!prompt) return null;

    const spanTimestamp = Math.floor((prompt.timestamp || 0) / 1000);

    return (
        <Modal open onClose={onClose} title="Span detail" large>
            <Modal.Section>
                <VerticalStack gap="4">
                    {/* Meta row */}
                    <HorizontalStack gap="2" wrap>
                        <HorizontalStack gap="1" blockAlign="center">
                            <Box style={{ color: "var(--p-color-icon-subdued)", display: "flex" }}>
                                <ClockMinor width={16} height={16} />
                            </Box>
                            <Text variant="bodySm" tone="subdued">
                                {func.prettifyEpoch(spanTimestamp)}
                            </Text>
                        </HorizontalStack>
                        {prompt.traceId && (
                            <HorizontalStack gap="1" blockAlign="center">
                                <Box style={{ color: "var(--p-color-icon-subdued)", display: "flex" }}>
                                    <HashtagMinor width={16} height={16} />
                                </Box>
                                <Text variant="bodySm" tone="subdued">
                                    {truncate(prompt.traceId, 24)}
                                </Text>
                            </HorizontalStack>
                        )}
                        {prompt.userName && <Badge>{prompt.userName}</Badge>}
                        {prompt.serviceId && <Badge tone="new">{prompt.serviceId}</Badge>}
                        {prompt._model && <Badge tone="success">{prompt._model}</Badge>}
                        <Badge tone="info">
                            {(prompt._inputTokens || 0) + " in / " + (prompt._outputTokens || 0) + " out"}
                        </Badge>
                    </HorizontalStack>

                    {/* Prompt */}
                    {prompt._promptText && (
                        <ChatMessage
                            isExternalAgentRequest={true}
                            type="request"
                            content={prompt._promptText}
                            timestamp={spanTimestamp}
                            customLabel="Prompt"
                            isCode={false}
                            toolsMetadata={{}}
                        />
                    )}

                    {/* Response */}
                    {prompt._responseText && (
                        <ChatMessage
                            type="response"
                            content={prompt._responseText}
                            timestamp={spanTimestamp}
                            customLabel="Response"
                            isCode={false}
                            toolsMetadata={{}}
                        />
                    )}
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}
