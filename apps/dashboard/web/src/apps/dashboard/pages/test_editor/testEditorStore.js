import {create} from "zustand"
import {devtools} from "zustand/middleware"

let testEditorStore = (set)=>({
    allSubCategories: null,
    setAllSubCategories: (allSubCategories) => { set({ allSubCategories: allSubCategories }) },
    testsObj : null,
    setTestsObj:(testsObj)=>{
        set({testsObj: testsObj})
    },

    selectedTest: null,
    setSelectedTest:(selectedTest)=>{
        set({selectedTest: selectedTest})
    },
})

testEditorStore = devtools(testEditorStore)
const TestEditorStore = create(testEditorStore)

export default TestEditorStore

