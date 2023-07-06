import {create} from "zustand"
import {devtools} from "zustand/middleware"

let testingStore = (set)=>({
    testRuns: [],
    setTestRuns: (testRuns) => set({ testRuns: testRuns }),
    selectedTestRun: {},
    setSelectedTestRun: (selectedTestRun) => set({ selectedTestRun: selectedTestRun }),
    subCategoryMap: {},
    setSubCategoryMap: (subCategoryMap) => set({subCategoryMap: subCategoryMap}),
    subCategoryFromSourceConfigMap: {},
    setSubCategoryFromSourceConfigMap: (subCategoryFromSourceConfigMap) => set({subCategoryFromSourceConfigMap: subCategoryFromSourceConfigMap}),
})

testingStore = devtools(testingStore)
const TestingStore = create(testingStore)
export default TestingStore

