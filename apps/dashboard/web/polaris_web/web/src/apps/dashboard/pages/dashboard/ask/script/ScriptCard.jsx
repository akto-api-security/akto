import { useState } from "react"
import { Badge, Box, Button, HorizontalStack, Text, VerticalStack } from "@shopify/polaris"
import SampleData from "@/apps/dashboard/components/shared/SampleData"
import func from "@/util/func"
import { scriptTypeLabel } from "../../../testing/scripts/scriptTypes"
import ValidateScriptModal from "./ValidateScriptModal"
import SaveScriptModal from "./SaveScriptModal"

// One script from an Ask Akto reply: read-only view, Copy, Validate (replay an endpoint with it) and
// Save to library. See service/scripts/validation/CLAUDE.md.
export default function ScriptCard({ block, conversationScripts, onAskToFix }) {
    const [validateOpen, setValidateOpen] = useState(false)
    const [saveOpen, setSaveOpen] = useState(false)
    const [type, setType] = useState(block.type)

    return (
        <Box background="bg-subdued" borderWidth="1" borderColor="border-subdued" borderRadius="3" padding="3">
            <VerticalStack gap="3">
                <HorizontalStack align="space-between" blockAlign="center" gap="2">
                    <HorizontalStack gap="2" blockAlign="center">
                        <Text as="h3" variant="headingSm">Script {block.index + 1}{block.name ? `: ${block.name}` : ""}</Text>
                        {type ? <Badge status="info">{scriptTypeLabel(type)}</Badge> : <Badge status="warning">Type unknown</Badge>}
                    </HorizontalStack>
                    <HorizontalStack gap="2">
                        <Button size="slim" onClick={() => func.copyToClipboard(block.javascript, null, "Script copied")}>Copy</Button>
                        <Button size="slim" onClick={() => setSaveOpen(true)}>Save to library</Button>
                        <Button size="slim" primary onClick={() => setValidateOpen(true)}>Validate</Button>
                    </HorizontalStack>
                </HorizontalStack>
                {block.headerError ? <Text as="p" color="warning">{block.headerError}</Text> : null}
                <SampleData data={{ message: block.javascript }} editorLanguage="javascript" minHeight="240px" readOnly={true} wordWrap={true} />
            </VerticalStack>
            {validateOpen ? (
                <ValidateScriptModal
                    block={block}
                    type={type}
                    onTypeChange={setType}
                    conversationScripts={conversationScripts}
                    onAskToFix={onAskToFix}
                    onClose={() => setValidateOpen(false)}
                />
            ) : null}
            {saveOpen ? (
                <SaveScriptModal block={block} type={type} onTypeChange={setType} onClose={() => setSaveOpen(false)} />
            ) : null}
        </Box>
    )
}
