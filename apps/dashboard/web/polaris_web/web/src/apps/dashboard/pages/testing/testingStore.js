import {create} from "zustand"
import {devtools} from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

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

testingStore = devtools(testingStore, devtoolsOptions("TestingStore"))
const TestingStore = create(testingStore)
export default TestingStore

