import {create} from "zustand"
import {devtools} from "zustand/middleware"

let onboardingStore = (set)=>({
    selectedCollection: null,
    setSelectedCollection:(selectedCollection)=>{
        set({selectedCollection: selectedCollection})
    },
})

onboardingStore = devtools(onboardingStore)
const OnboardingStore = create(onboardingStore)

export default OnboardingStore