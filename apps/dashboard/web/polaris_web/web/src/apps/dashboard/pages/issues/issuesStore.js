import {create} from "zustand"
import {devtools} from "zustand/middleware"

let issuesStore = (set)=>({
    createJiraIssueFieldMetaData: {},
    setCreateJiraIssueFieldMetaData: (createJiraIssueFieldMetaData) => set({ createJiraIssueFieldMetaData: createJiraIssueFieldMetaData }),
    displayJiraIssueFieldValues: {},
    setDisplayJiraIssueFieldValues: (displayJiraIssueFieldValues) => set({ displayJiraIssueFieldValues: displayJiraIssueFieldValues }),
    updateDisplayJiraIssueFieldValues: (fieldId, value) =>
        set((state) => ({
            displayJiraIssueFieldValues: {
                ...state.displayJiraIssueFieldValues,
                [fieldId]: value
            }
        })),
})

issuesStore = devtools(issuesStore)
const IssuesStore = create(issuesStore)
export default IssuesStore

