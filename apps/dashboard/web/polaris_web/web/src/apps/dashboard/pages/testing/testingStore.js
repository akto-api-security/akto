import {create} from "zustand"
import {devtools} from "zustand/middleware"

const ERRORS_OBJECT= {
    NO_PATH: "No sample data found for the API",
    NO_MESSAGE_WITH_AUTH_TOKEN: "No sample data found for the API which contains the auth token",
    NO_AUTH_MECHANISM: "No authentication mechanism saved",
    API_REQUEST_FAILED: "API request failed",
    SOMETHING_WENT_WRONG: "OOPS! Something went wrong",
    FAILED_TO_CONVERT_TEST_REQUEST_TO_STRING: "Failed to store test",
    INSUFFICIENT_MESSAGES: "Insufficient messages",
    NO_AUTH_TOKEN_FOUND: "No authentication token found",
    FAILED_DOWNLOADING_PAYLOAD_FILES: "Failed downloading payload files",
    FAILED_BUILDING_URL_WITH_DOMAIN: "Failed building URL with domain",
    EXECUTION_FAILED: "Test execution failed",
    INVALID_EXECUTION_BLOCK: "Invalid test execution block in template",
    NO_API_REQUEST: "No test requests created",
    SKIPPING_EXECUTION_BECAUSE_AUTH: "Request API failed authentication check, skipping execution",
    SKIPPING_EXECUTION_BECAUSE_FILTERS: "Request API failed to satisfy api_selection_filters block, skipping execution",
    UNKNOWN_ERROR_OCCURRED: "Unknown error occurred"
}

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
    errorsObject: ERRORS_OBJECT,
    currentTestingRuns: [],
    setCurrentTestingRuns: (currentTestingRuns) => set({currentTestingRuns: currentTestingRuns}),
})

testingStore = devtools(testingStore)
const TestingStore = create(testingStore)
export default TestingStore

