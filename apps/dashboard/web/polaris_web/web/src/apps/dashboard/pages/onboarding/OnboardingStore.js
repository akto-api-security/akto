import {create} from "zustand"
import {devtools} from "zustand/middleware"
import { devtoolsOptions } from "@/apps/main/devtoolsConfig"

let onboardingStore = (set)=>({
    selectedCollection: null,
    setSelectedCollection:(selectedCollection)=>{
        set({selectedCollection: selectedCollection})
    },

    selectedTestSuite: null,
    setSelectedTestSuite:(selectedTestSuite)=>{
        set({selectedTestSuite: selectedTestSuite})
    },

    authObject: null,
    setAuthObject:(authObject)=>{
        set({authObject: authObject})
    },

    testingRunHexId: null,
    setTestingRunHexId:(testingRunHexId)=>{
        set({testingRunHexId: testingRunHexId})
    },
})

onboardingStore = devtools(onboardingStore, devtoolsOptions("OnboardingStore"))
const OnboardingStore = create(onboardingStore)

export default OnboardingStore