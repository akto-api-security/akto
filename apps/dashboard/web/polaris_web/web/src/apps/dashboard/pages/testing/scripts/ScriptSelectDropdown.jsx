import Dropdown from "../../../components/layouts/Dropdown"
import { SCRIPT_TYPES, defaultSelectionForType, scriptOptionsForType } from "./scriptTypes"

/**
 * Picks the pre- or post-request script for a test run. A stored null (config made before the
 * library, or by CI/CD) runs the default script, so it is displayed as that script; an id that is no
 * longer in the library is shown as unavailable rather than silently as "No script".
 */
function ScriptSelectDropdown({ type, scripts, value, onChange }) {
    const options = scriptOptionsForType(scripts, type)
    const displayValue = value === null || value === undefined ? defaultSelectionForType(scripts, type) : value
    const known = options.some((option) => option.value === displayValue)
    const menuItems = known ? options : [...options, { label: "Unavailable script", value: displayValue }]
    const label = type === SCRIPT_TYPES.POST_REQUEST ? "Post-request script" : "Pre-request script"

    return (
        <Dropdown
            id={`${type.toLowerCase()}-script-dropdown`}
            label={label}
            menuItems={menuItems}
            initial={displayValue}
            selected={onChange}
        />
    )
}

export default ScriptSelectDropdown
