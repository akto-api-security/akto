import { useMemo } from "react"
import DropdownSearch from "../../../components/shared/DropdownSearch"
import { NO_SCRIPT, SCRIPT_TYPES, defaultSelectionForType, scriptOptionsForType } from "./scriptTypes"

// DropdownSearch matches options with option.value.match(selected), and "" matches every value,
// so "No script" uses its own option value inside this component and maps back to NO_SCRIPT ("").
const NO_SCRIPT_OPTION = "__no_script__"
const UNAVAILABLE_LABEL = "Unavailable script"

const toOptionValue = (scriptId) => (scriptId === NO_SCRIPT ? NO_SCRIPT_OPTION : scriptId)
const fromOptionValue = (optionValue) => (optionValue === NO_SCRIPT_OPTION ? NO_SCRIPT : optionValue)

/**
 * Searchable picker for the pre- or post-request script of a test run. A stored null (config made
 * before the library, or by CI/CD) runs the default script, so it is displayed as that script; an id
 * that is no longer in the library is shown as unavailable rather than silently as "No script".
 */
function ScriptSelectDropdown({ type, scripts, value, onChange }) {
    const selectedValue = toOptionValue(
        value === null || value === undefined ? defaultSelectionForType(scripts, type) : value
    )

    const optionsList = useMemo(() => {
        const options = scriptOptionsForType(scripts, type)
            .map((option) => ({ ...option, value: toOptionValue(option.value) }))
        const known = options.some((option) => option.value === selectedValue)
        return known ? options : [...options, { label: UNAVAILABLE_LABEL, value: selectedValue }]
    }, [scripts, type, selectedValue])

    const selectedLabel = optionsList.find((option) => option.value === selectedValue)?.label ?? UNAVAILABLE_LABEL
    const label = type === SCRIPT_TYPES.POST_REQUEST ? "Post-request script" : "Pre-request script"

    return (
        <DropdownSearch
            id={`${type.toLowerCase()}-script-dropdown`}
            label={label}
            placeholder="Search scripts"
            optionsList={optionsList}
            value={selectedLabel}
            preSelected={[selectedValue]}
            setSelected={(optionValue) => onChange(fromOptionValue(optionValue))}
        />
    )
}

export default ScriptSelectDropdown
