import { useState } from "react";
import { Badge, Box, Button, Collapsible, HorizontalStack, Modal, Text, VerticalStack } from "@shopify/polaris";
import { ChevronDownMinor, ChevronRightMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import { formatCost } from "./constants";

export default function PromptDetailModal({ prompt, onClose }) {
    const [rawOpen, setRawOpen] = useState(false);

    if (!prompt) return null;

    return (
        <Modal open onClose={onClose} title="Prompt detail" large>
            <Modal.Section>
                <VerticalStack gap="4">
                    <HorizontalStack gap="2" wrap>
                        <Text variant="bodySm" tone="subdued">
                            {func.prettifyEpoch(Math.floor((prompt.timestamp || 0) / 1000))}
                        </Text>
                        {prompt.userName && <Badge>{prompt.userName}</Badge>}
                        {prompt.serviceId && <Badge tone="new">{prompt.serviceId}</Badge>}
                        {prompt._model && <Badge tone="success">{prompt._model}</Badge>}
                        <Badge tone="info">
                            {prompt._inputTokens + " in / " + prompt._outputTokens + " out"}
                        </Badge>
                        <Badge>{formatCost(prompt._inputTokens, prompt._outputTokens)}</Badge>
                    </HorizontalStack>

                    <VerticalStack gap="2">
                        <Text variant="headingSm">Prompt</Text>
                        <Box background="bg-surface-secondary" padding="4" borderRadius="2">
                            <Text variant="bodySm" as="p">{prompt._promptText || ""}</Text>
                        </Box>
                    </VerticalStack>

                    {prompt._responseText && (
                        <VerticalStack gap="2">
                            <Text variant="headingSm">Response</Text>
                            <Box background="bg-surface-secondary" padding="4" borderRadius="2">
                                <Text variant="bodySm" as="p">{prompt._responseText}</Text>
                            </Box>
                        </VerticalStack>
                    )}

                    <Button
                        plain monochrome removeUnderline textAlign="left"
                        icon={rawOpen ? ChevronDownMinor : ChevronRightMinor}
                        onClick={() => setRawOpen(o => !o)}
                    >
                        <Text variant="bodySm" fontWeight="semibold">RAW PAYLOADS</Text>
                    </Button>
                    <Collapsible open={rawOpen} id="raw-collapsible">
                        <VerticalStack gap="3">
                            <Box background="bg-surface-secondary" padding="3" borderRadius="2">
                                <Text variant="bodySm" as="p" tone="subdued">{prompt.queryPayload || ""}</Text>
                            </Box>
                            <Box background="bg-surface-secondary" padding="3" borderRadius="2">
                                <Text variant="bodySm" as="p" tone="subdued">{prompt.responsePayload || ""}</Text>
                            </Box>
                        </VerticalStack>
                    </Collapsible>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    );
}
