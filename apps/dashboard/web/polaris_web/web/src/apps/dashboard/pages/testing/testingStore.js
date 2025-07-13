import {create} from "zustand"
import {devtools} from "zustand/middleware"

let testingStore = (set)=>({
    testRuns: [],
    setTestRuns: (testRuns) => set({ testRuns: testRuns }),
    selectedTestRun: {},
    setSelectedTestRun: (selectedTestRun) => set({ selectedTestRun: selectedTestRun }),
    selectedTestRunResult: {},
    setSelectedTestRunResult: (selectedTestRunResult) => set({ selectedTestRunResult: selectedTestRunResult }),
    authMechanism: null,
    setAuthMechanism: (authMechanism) => set({authMechanism: authMechanism}),
    rerunModal: null,
    setRerunModal: (rerunModal) => set({rerunModal: rerunModal}),
    errorsObject: {},
    setErrorsObject: (errorsObject) => set({errorsObject: errorsObject}),
    testingEndpointsApisList: [],
    setTestingEndpointsApisList: (testingEndpointsApisList) => set({testingEndpointsApisList: testingEndpointsApisList}),
})

testingStore = devtools(testingStore)
const TestingStore = create(testingStore)
export default TestingStore

