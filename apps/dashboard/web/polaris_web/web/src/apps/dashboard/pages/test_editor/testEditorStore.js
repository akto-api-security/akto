import {create} from "zustand"
import {devtools} from "zustand/middleware"
import convertFunc from "./transform"

let testEditorStore = (set)=>({
    testsObj : null,
    setTestsObj:(testsObj)=>{
        set({testsObj: testsObj})
    },

    selectedTest: null,
    setSelectedTest:(selectedTest)=>{
        set({selectedTest: selectedTest})
    },

    vulnerableRequestsMap: null,
    setVulnerableRequestMap:(vulnerableRequestsMap)=>{
        set({vulnerableRequestsMap: vulnerableRequestsMap})
    },

    defaultRequest: null,
    setDefaultRequest:(defaultRequest)=>{
        set({defaultRequest: defaultRequest})
    },

    currentContent: null,
    setCurrentContent:(currentContent)=>{
        set({currentContent: currentContent})
    },

    contentCache: {},
    hydrateContentCache:(entries)=>{
        set((state) => ({
            contentCache: { ...state.contentCache, ...entries },
        }))
    },
    setContentCacheEntry:(testId, content)=>{
        set((state) => ({
            contentCache: { ...state.contentCache, [testId]: content },
        }))
    },

    contentSearchIndex: {},
    setContentSearchIndex:(contentSearchIndex)=>{
        set({ contentSearchIndex })
    },
    updateContentSearchIndexEntry:(testId, content)=>{
        set((state) => ({
            contentSearchIndex: {
                ...(state.contentSearchIndex || {}),
                [testId]: convertFunc.normalizeSearchTerm(content),
            },
        }))
    },

    selectedRole: null,
    setSelectedRole:(selectedRole)=>{
        set({selectedRole: selectedRole})
    },
})

testEditorStore = devtools(testEditorStore)
const TestEditorStore = create(testEditorStore)

export default TestEditorStore

