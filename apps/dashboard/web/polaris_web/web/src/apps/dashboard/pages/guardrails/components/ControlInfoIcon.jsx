import { useState } from "react";
import { Popover, Avatar, Box, VerticalStack, HorizontalStack, Text, Button, Divider } from "@shopify/polaris";

// Click-to-open "More info" popover (not a modal — see GuardrailPolicies.jsx's presets Popover for
// the same active/onClose pattern). Shows a description plus zero or more concrete examples, each
// with its own "Try now" button that hands the example text off via onTryPrompt (wired by the parent
// to CreateGuardrailPage's handleSamplePayloadClick, which fills and focuses the Playground input).
const ControlInfoIcon = ({ description, examples = [], onTryPrompt, preferredPosition = "above" }) => {
    const [active, setActive] = useState(false);

    const handleTry = (event, text) => {
        // preventDefault (not stopPropagation) cancels the native label->checkbox activation this
        // button would otherwise trigger, without blocking Popover's own document-level "click
        // outside closes other popovers" listener.
        event.preventDefault();
        onTryPrompt?.(text);
        setActive(false);
    };

    return (
        <Popover
            active={active}
            preferredPosition={preferredPosition}
            activator={
                <Box
                    className="reduce-size"
                    aria-label="More info"
                    style={{ cursor: "pointer", display: "inline-flex" }}
                    onClick={(event) => {
                        // This icon usually sits inside a Checkbox/RangeSlider label, which is a
                        // native <label> tied to the underlying input. preventDefault cancels that
                        // native label->control activation. Deliberately NOT stopPropagation: that
                        // would also block Popover's own document-level "click outside" listener,
                        // which is what closes any other already-open popover when this one opens.
                        event.preventDefault();
                        setActive((prev) => !prev);
                    }}
                >
                    <Avatar shape="round" size="extraSmall" source="/public/info_filled_icon.svg" />
                </Box>
            }
            onClose={() => setActive(false)}
        >
            <Popover.Pane>
                <Box maxWidth="360px" padding="4">
                    <VerticalStack gap="3">
                        <Text as="span" variant="bodySm">{description}</Text>
                        {examples.length > 0 && (
                            <VerticalStack gap="3">
                                <Divider />
                                <Text as="span" variant="bodySm" fontWeight="semibold">
                                    {examples.length === 1 ? "Example" : "Examples"}
                                </Text>
                                {examples.map((example, index) => (
                                    <VerticalStack gap="1" key={index}>
                                        {example.label && (
                                            <Text as="span" variant="bodySm" fontWeight="semibold">{example.label}</Text>
                                        )}
                                        <Text as="span" variant="bodySm" tone="subdued">"{example.text}"</Text>
                                        <HorizontalStack align="end">
                                            <Button size="slim" onClick={(event) => handleTry(event, example.text)}>Try now</Button>
                                        </HorizontalStack>
                                    </VerticalStack>
                                ))}
                            </VerticalStack>
                        )}
                    </VerticalStack>
                </Box>
            </Popover.Pane>
        </Popover>
    );
};

export default ControlInfoIcon;
