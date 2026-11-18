// Script library constants shared by the Scripts page and the Run Test modal.
// Id semantics on TestingRunConfig (see service/scripts/CLAUDE.md):
//   null/absent -> the default-test-{type} script, "" -> no script, otherwise a script id.

export const SCRIPT_TYPES = {
    PRE_REQUEST: "PRE_REQUEST",
    POST_REQUEST: "POST_REQUEST",
}

export const NO_SCRIPT = ""

export const DEFAULT_SCRIPT_NAMES = {
    [SCRIPT_TYPES.PRE_REQUEST]: "default-test-pre",
    [SCRIPT_TYPES.POST_REQUEST]: "default-test-post",
}

export function scriptTypeLabel(type) {
    return type === SCRIPT_TYPES.POST_REQUEST ? "Post-request" : "Pre-request"
}

export function isDefaultScript(script) {
    return script?.name === DEFAULT_SCRIPT_NAMES[script?.type]
}

/** Dropdown options for one phase of a run: "No script" plus the library scripts of that type. */
export function scriptOptionsForType(scripts, type) {
    const options = (scripts || [])
        .filter((script) => script.type === type)
        .map((script) => ({ label: script.name, value: script.id }))
    return [{ label: "No script", value: NO_SCRIPT }, ...options]
}

/** The id a new run should preselect: the default script when the account has one, otherwise none. */
export function defaultSelectionForType(scripts, type) {
    const defaultScript = (scripts || []).find((script) => script.type === type && isDefaultScript(script))
    return defaultScript ? defaultScript.id : NO_SCRIPT
}
