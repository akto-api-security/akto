import {create} from "zustand"
import {createJSONStorage, devtools, persist} from "zustand/middleware"

let issuesStore = (set)=>({
    // jira issue fields state 
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
    
    // azure boards work item fields state
    createABWorkItemFieldMetaData: {},
    setCreateABWorkItemFieldMetaData: (createABWorkItemFieldMetaData) => set({ createABWorkItemFieldMetaData: createABWorkItemFieldMetaData }),
    displayABWorkItemFieldValues: {},
    setDisplayABWorkItemFieldValues: (displayABWorkItemFieldValues) => set({ displayABWorkItemFieldValues: displayABWorkItemFieldValues }),
    updateDisplayABWorkItemFieldValues: (fieldReferenceName, value) =>
        set((state) => ({
            displayABWorkItemFieldValues: {
                ...state.displayABWorkItemFieldValues,
                [fieldReferenceName]: value
            }
        })),
    resetStore: () => {
        set({
            createJiraIssueFieldMetaData: {},
            displayJiraIssueFieldValues: {}, 
            
            createABWorkItemFieldMetaData: {},
            displayABWorkItemFieldValues: {},
        })
    },  
})

issuesStore = devtools(issuesStore)
issuesStore = persist(issuesStore, {name: 'Akto-jira-custom-fields', storage: createJSONStorage(() => sessionStorage)})
const IssuesStore = create(issuesStore)
export default IssuesStore

