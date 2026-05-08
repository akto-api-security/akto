import React, { useState } from 'react'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import OnboardingLayout from './OnboardingLayout'
import CollectionSelection from './CollectionSelection'
import OnboardingStore from '../OnboardingStore'
import TestSuites from './TestSuites'
import func from '../../../../../util/func'
import SetConfig from './SetConfig'
import ResultsSummary from './ResultsSummary'
import { useNavigate } from "react-router-dom"
import api from '../api'
import PersistStore from '../../../../main/PersistStore'
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper'

function OnboardingBuilder() {

    const [loading, setLoading] = useState(false)
    const [currentStep, setCurrentStep] = useState(1)
    const apiCollections = PersistStore(state => state.allCollections)
    const selectedTestSuite = OnboardingStore(state => state.selectedTestSuite)
    const authObj = OnboardingStore(state => state.authObject)
    const hexId = OnboardingStore(state => state.testingRunHexId)

    const navigate = useNavigate()

    const canNext = () => {
        if(currentStep === 1){
            return apiCollections.length > 1
        }else if(currentStep === 2){
            return selectedTestSuite ? true : false
        }else{
            return authObj.key.length > 0 && authObj.value.length > 0
        }
    }

    const next = () => {
        if(canNext()){
            if(currentStep === 3){
                func.setToast(true,false, "Test runs scheduled !")
            }else if(currentStep === 4){
                const url = "/dashboard/testing/" + hexId
                navigate(url)
            }
            setCurrentStep(currentStep + 1)
        }
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
            actionText: "Next",
        },
        {
            title: "Select Tests",
            subtitle: "Select tests you wish to run on your API endpoints.",
            buttonText: "Set Config",
            cardTitle: "Select test suites",
            component: <TestSuites />,
            toast: "Please select a test suite.",
            actionText: "Next",
        },
        {
            title: "Set config",
            subtitle: "We have pre-filled token for you!",
            buttonText: mapLabel("Run tests", getDashboardCategory()),
            cardTitle: "Attacker Token",
            toast: "Please fill the above fields.",
            component: <SetConfig />,
            actionText: mapLabel("Run Test", getDashboardCategory()),
        },
        {
            title: mapLabel('Test results', getDashboardCategory()),
            subtitle: "Here are the results for the tests you recently ran",
            buttonText: "See all issues",
            component: <ResultsSummary />,
            actionText: "See all issues",
        },
    ]

    const skipOnboarding = async() => {
        setLoading(true)
        await api.skipOnboarding().then((resp)=>{
            setLoading(false)
            navigate("/dashboard/quick-start")
        })
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