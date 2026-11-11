import { useEffect, useRef, useState } from "react"
import { Badge, Banner, Divider, HorizontalStack, LegacyCard, Text, TextField, VerticalStack } from "@shopify/polaris"
import SampleData from "../../../components/shared/SampleData"
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import func from "@/util/func"
import api from "../api"
import { scriptTypeLabel } from "./scriptTypes"

const MAX_NAME_LENGTH = 64

/**
 * Editor for one library script. Edit mode loads the body by id (the list never carries bodies);
 * create mode asks for a name. Name and type are immutable after creation.
 */
function ScriptEditor({ scriptId, createType, canWrite, onSaved, onClose }) {
    const isCreate = !scriptId
    const [script, setScript] = useState(null)
    const [loading, setLoading] = useState(!isCreate)
    const [loadFailed, setLoadFailed] = useState(false)
    const [saving, setSaving] = useState(false)
    const [name, setName] = useState("")
    const contentRef = useRef("")

    async function loadScript() {
        setLoading(true)
        setLoadFailed(false)
        try {
            const resp = await api.fetchScript(scriptId)
            setScript(resp.testScript)
            contentRef.current = resp.testScript?.javascript ?? ""
        } catch (e) {
            setLoadFailed(true)
        }
        setLoading(false)
    }

    useEffect(() => {
        if (isCreate) {
            contentRef.current = ""
            return
        }
        loadScript()
    }, [scriptId])

    async function save() {
        setSaving(true)
        try {
            const resp = isCreate
                ? await api.addScript({ name, scriptType: createType, javascript: contentRef.current })
                : await api.updateScript(scriptId, contentRef.current)
            func.setToast(true, false, isCreate ? "Script created" : "Script saved")
            onSaved(resp.testScript)
        } catch (e) {
            // the request layer already shows the server's reason; keep the user's edits in place
        }
        setSaving(false)
    }

    const type = isCreate ? createType : script?.type
    const title = (
        <HorizontalStack gap="2" blockAlign="center">
            <Text variant="headingMd" as="h3">{isCreate ? "New script" : (script?.name ?? "Script")}</Text>
            {type ? <Badge>{scriptTypeLabel(type)}</Badge> : null}
        </HorizontalStack>
    )

    if (loading) {
        return <LegacyCard sectioned title={title}><SpinnerCentered /></LegacyCard>
    }

    if (loadFailed) {
        return (
            <LegacyCard sectioned title={title}>
                <Banner status="critical" title="Script unavailable" action={{ content: "Retry", onAction: loadScript }}>
                    <Text as="p">The script could not be loaded.</Text>
                </Banner>
            </LegacyCard>
        )
    }

    const nameMissing = isCreate && name.trim().length === 0

    return (
        <LegacyCard
            title={title}
            actions={[{ content: "Close", onAction: onClose }]}
            primaryFooterAction={{
                content: isCreate ? "Create" : "Save",
                onAction: save,
                loading: saving,
                disabled: !canWrite || nameMissing,
            }}
        >
            <LegacyCard.Section>
                <VerticalStack gap="3">
                    {isCreate ? (
                        <TextField
                            label="Script name"
                            value={name}
                            onChange={setName}
                            maxLength={MAX_NAME_LENGTH}
                            showCharacterCount
                            autoComplete="off"
                            helpText="Letters, digits, spaces, '.', '_' and '-'. The name cannot be changed later."
                        />
                    ) : (
                        <Text as="p" color="subdued">
                            Created by {script?.createdBy ?? "unknown"} · last updated by {script?.lastUpdatedBy ?? script?.author ?? "unknown"} {func.prettifyEpoch(script?.lastUpdatedAt)}
                        </Text>
                    )}
                    <Divider />
                    <SampleData
                        key={scriptId || `new-${createType}`}
                        data={{ message: script?.javascript ?? "" }}
                        editorLanguage="javascript"
                        minHeight="500px"
                        readOnly={!canWrite}
                        getEditorData={(value) => { contentRef.current = value }}
                        wordWrap={true}
                    />
                    {!canWrite ? <Text as="p" color="subdued">You have read-only access to scripts.</Text> : null}
                </VerticalStack>
            </LegacyCard.Section>
        </LegacyCard>
    )
}

export default ScriptEditor
