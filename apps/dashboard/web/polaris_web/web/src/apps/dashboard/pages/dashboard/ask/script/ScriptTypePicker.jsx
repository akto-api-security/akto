import { Select } from "@shopify/polaris"
import { SCRIPT_TYPES, scriptTypeLabel } from "../../../testing/scripts/scriptTypes"

const OPTIONS = [
    { label: "Choose a type", value: "" },
    ...Object.values(SCRIPT_TYPES).map((t) => ({ label: scriptTypeLabel(t), value: t })),
]

// Shown only when the script block's header did not say which type it is.
export default function ScriptTypePicker({ type, onChange }) {
    return <Select label="Script type" options={OPTIONS} value={type || ""} onChange={(v) => onChange(v || null)} />
}
