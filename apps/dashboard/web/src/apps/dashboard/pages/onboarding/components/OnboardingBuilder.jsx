import React, { useState } from 'react'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import OnboardingLayout from './OnboardingLayout'
import CollectionSelection from './CollectionSelection'
import OnboardingStore from '../OnboardingStore'
import {ArrowRightMinor, PlayMajor} from "@shopify/polaris-icons"
import Store from '../../../store'
import TestSuites from './TestSuites'
import func from '../../../../../util/func'

function OnboardingBuilder() {

    const [loading, setLoading] = useState(false)
    const [currentStep, setCurrentStep] = useState(1)
    const apiCollections = Store(state => state.allCollections)
    const selectedTestSuite = OnboardingStore(state => state.selectedTestSuite)

    const canNext = () => {
        if(currentStep === 1){
            return apiCollections.length > 1
        }else if(currentStep === 2){
            return selectedTestSuite ? true : false
        }else{
            return false
        }
    }

    const next = () => {
        if(currentStep < 3 && canNext())
            setCurrentStep(currentStep + 1)
        else{
            func.setToast(true, false, componentsArr[currentStep - 1].toast)
        }
    }

    const componentsArr = [
        {
            title: "Welcome to Akto",
            subtitle: "Add API collection you want to test. Here we have an existing API collection for you.",
            buttonText: "Select tests",
            cardTitle: "API Collections",
            component: <CollectionSelection />,
            toast: "Please select a collection to go to next step.",
            icon: ArrowRightMinor,
        },
        {
            title: "Select Tests",
            subtitle: "Select tests you wish to run on your API endpoints.",
            buttonText: "Set Config",
            cardTitle: "Select test suites",
            component: <TestSuites />,
            toast: "Please select a test suite.",
            icon: ArrowRightMinor,
        },
        {
            title: "Set config",
            subtitle: "We have pre-filled token for you!",
            buttonText: "Run tests",
            cardTitle: "Attacker Token",
            icon: PlayMajor,
            toast: "Please fill the above fields."
        },
        {
            title: "Test results",
            subtitle: "Here are the results for the tests you recently ran",
            buttonText: "See all issues",
        },
    ]

    const skipOnboarding = () => {
        console.log("skipOnboarding")
    }


    const changeStep = (index) => {
        if(index < currentStep || canNext()){
            setCurrentStep(index)
        }
    }

    return (
        loading ? <SpinnerCentered />
            : <OnboardingLayout 
                stepObj={componentsArr[currentStep - 1]} 
                requestStepChange={(index) => changeStep(index)} 
                currentStep={currentStep}
                skipOnboarding={() => skipOnboarding()}
                next={next}
            />
    )
}

export default OnboardingBuilder