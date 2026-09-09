import {create} from "zustand"
import {devtools} from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

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

    selectedRole: null,
    setSelectedRole:(selectedRole)=>{
        set({selectedRole: selectedRole})
    },
})

testEditorStore = devtools(testEditorStore, devtoolsOptions("TestEditorStore"))
const TestEditorStore = create(testEditorStore)

export default TestEditorStore

