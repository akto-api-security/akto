// Hands a red-team learning draft to the Test Editor, which only knows saved templates. The draft is kept in this
// tab's sessionStorage under a one-off editor id, so a refresh keeps it and another tab never sees it.

const DRAFT_TEST_ID_PREFIX = "REDTEAM_DRAFT_"
const STORAGE_KEY = "akto.redteamLearningDraft"

export function isDraftTestId(testId) {
    return typeof testId === "string" && testId.startsWith(DRAFT_TEST_ID_PREFIX)
}

/** Stores the draft and returns the editor id to open it under, or null when it cannot be stored. */
export function stashDraftForEditor(draft) {
    const testId = DRAFT_TEST_ID_PREFIX + Date.now().toString(36)
    try {
        sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
            testId,
            draftId: draft.id,
            name: draft.name,
            yamlContent: draft.yamlContent,
            testCategory: draft.testCategory || "",
        }))
        return testId
    } catch (e) {
        return null
    }
}

/** The stashed draft for this editor id, or null when there is none (opened elsewhere, or storage cleared). */
export function stashedDraft(testId) {
    try {
        const stored = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "null")
        return stored && stored.testId === testId && stored.yamlContent ? stored : null
    } catch (e) {
        return null
    }
}

export function clearStashedDraft() {
    try {
        sessionStorage.removeItem(STORAGE_KEY)
    } catch (e) {
        // nothing stored, or storage unavailable
    }
}

/** The editor's entries for a draft: shaped like transform.mapCategoryToSubcategory's, marked as an unsaved custom test. */
export function draftEditorEntries(draft) {
    const label = `${draft.name} (draft)`
    return {
        label,
        data: {
            content: draft.yamlContent,
            lastUpdated: "not saved yet",
            superCategory: draft.testCategory,
            type: "CUSTOM",
            category: draft.testCategory,
            inactive: false,
            severity: "",
            nature: "",
            duration: "",
            value: draft.testId,
            isCustom: true,
            author: "",
            compliance: [],
        },
    }
}
