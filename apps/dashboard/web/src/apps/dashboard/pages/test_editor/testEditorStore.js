import {create} from "zustand"
import {devtools} from "zustand/middleware"

let testEditorStore = (set)=>({
    testsObj : {},
    setTestsObj:(testsObj)=>{
        set({testsObj: testsObj})
    },

    selectedTest: {},
    setSelectedTest:(selectedTest)=>{
        set({selectedTest: selectedTest})
    },
})

testEditorStore = devtools(testEditorStore)
const TestEditorStore = create(testEditorStore)

export default TestEditorStore

