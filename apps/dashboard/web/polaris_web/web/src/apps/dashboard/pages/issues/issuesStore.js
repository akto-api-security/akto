import {create} from "zustand"
import {createJSONStorage, devtools, persist} from "zustand/middleware"

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

    resetStore: () => {
        set({
            createJiraIssueFieldMetaData: {},
            displayJiraIssueFieldValues: {}
        })
    },  
})

issuesStore = devtools(issuesStore)
issuesStore = persist(issuesStore, {name: 'Akto-jira-custom-fields', storage: createJSONStorage(() => sessionStorage)})
const IssuesStore = create(issuesStore)
export default IssuesStore

