import { useState } from "react"
import { Banner, Modal, Text, TextField, VerticalStack } from "@shopify/polaris"
import func from "@/util/func"
import api from "../../../testing/api"
import { scriptTypeLabel } from "../../../testing/scripts/scriptTypes"
import ScriptTypePicker from "./ScriptTypePicker"

const MAX_NAME_LENGTH = 64

// Saves an Ask Akto script to the library through the same api/addScript the Scripts page uses.
export default function SaveScriptModal({ block, type, onTypeChange, onClose }) {
    const [name, setName] = useState(block.name || "")
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState(null)

    const save = async () => {
        setSaving(true)
        setError(null)
        try {
            await api.addScript({ name: name.trim(), scriptType: type, javascript: block.javascript })
            func.setToast(true, false, `Saved "${name.trim()}" to the script library`)
            onClose()
        } catch (e) {
            // request.js already toasts the server's message; keep it here too so the modal says why.
            setError(e?.response?.data?.actionErrors?.[0] || "The script could not be saved. Nothing was changed.")
            setSaving(false)
        }
    }

    return (
        <Modal
            open
            onClose={onClose}
            title="Save script to library"
            primaryAction={{ content: "Save", onAction: save, loading: saving, disabled: !name.trim() || !type }}
            secondaryActions={[{ content: "Cancel", onAction: onClose }]}
        >
            <Modal.Section>
                <VerticalStack gap="3">
                    {error ? <Banner status="critical">{error}</Banner> : null}
                    {block.type ? (
                        <Text as="p">Type: {scriptTypeLabel(type)}</Text>
                    ) : (
                        <ScriptTypePicker type={type} onChange={onTypeChange} />
                    )}
                    <TextField
                        label="Name"
                        value={name}
                        onChange={setName}
                        maxLength={MAX_NAME_LENGTH}
                        showCharacterCount
                        autoComplete="off"
                        helpText="Letters, digits, spaces, '.', '_' and '-'. The name cannot be changed later."
                    />
                    <Text as="p" color="subdued">Saved scripts can be chosen per test run. Saving does not change any existing run.</Text>
                </VerticalStack>
            </Modal.Section>
        </Modal>
    )
}
