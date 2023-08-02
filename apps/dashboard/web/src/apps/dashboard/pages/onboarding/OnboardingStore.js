import {create} from "zustand"
import {devtools} from "zustand/middleware"

let onboardingStore = (set)=>({
    selectedCollection: null,
    setSelectedCollection:(selectedCollection)=>{
        set({selectedCollection: selectedCollection})
    },

    selectedTestSuite: null,
    setSelectedTestSuite:(selectedTestSuite)=>{
        set({selectedTestSuite: selectedTestSuite})
    },
})

onboardingStore = devtools(onboardingStore)
const OnboardingStore = create(onboardingStore)

export default OnboardingStore