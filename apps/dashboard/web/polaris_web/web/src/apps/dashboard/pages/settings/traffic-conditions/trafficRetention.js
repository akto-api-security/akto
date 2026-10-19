import observeFunc from "../../observe/transform"

// Mirrors TrafficRetentionService.MIN_DAYS / MAX_DAYS. The backend enforces them; these only keep the UI honest.
export const MIN_DAYS = 30
export const MAX_DAYS = 3650
export const CUSTOM_AGE = "custom"

export const AGE_OPTIONS = [
    { label: "Older than 3 months", value: "90" },
    { label: "Older than 6 months", value: "180" },
    { label: "Older than 1 year", value: "365" },
    { label: "Older than a custom number of days", value: CUSTOM_AGE },
]

export const RESULT_STATE = { OK: "OK", FAILED: "FAILED" }

// null means "not a valid age yet", so callers disable the action instead of sending a bad request.
export function resolveDays(ageChoice, customDays) {
    const days = ageChoice === CUSTOM_AGE ? Number(customDays) : Number(ageChoice)
    return Number.isInteger(days) && days >= MIN_DAYS && days <= MAX_DAYS ? days : null
}

export function countFailedCollections(preview) {
    return (preview?.collections || []).filter(c => c.state !== RESULT_STATE.OK).length
}

// A failed collection has no count (staleApis is null), so it is shown as unavailable, never as 0.
export function toTableRows(collections) {
    return (collections || []).map(c => ({
        id: c.apiCollectionId,
        name: c.name,
        staleApis: c.state === RESULT_STATE.OK ? observeFunc.formatNumberWithCommas(c.staleApis) : "Unavailable",
        status: c.state === RESULT_STATE.OK ? "Evaluated" : "Failed",
        details: c.state === RESULT_STATE.OK ? "" : (c.error || "Could not read this collection"),
    }))
}

export const TABLE_HEADERS = [
    { text: "Collection", value: "name", itemOrder: 1 },
    { text: "APIs with no recent traffic", value: "staleApis", itemOrder: 2 },
    { text: "Status", value: "status", itemOrder: 3 },
    { text: "Details", value: "details", itemOrder: 3 },
]

export const TABLE_RESOURCE_NAME = { singular: "collection", plural: "collections" }
