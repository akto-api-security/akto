import { HorizontalGrid, Text, VerticalStack } from '@shopify/polaris'
import "./style.css"
import React from 'react'

function StepperComponent({totalSteps, currentStep, stepperClicked}) {

    const clickFunc = (index) => {
        if(Math.abs(currentStep - index) === 1){
            stepperClicked(index)
        }
    }
    return (
        <VerticalStack gap="2">
            <Text variant="bodyMd" as='h3' color='subdued'>Step {currentStep} out of {totalSteps}</Text>
            <HorizontalGrid columns={totalSteps} gap="1">
                {Array.from({length: totalSteps}).map((_,index) => (
                    <div className={"stepper-box " + (currentStep > index ? 'active' : '')} onClick={() => clickFunc(index + 1)} key={index}/>
                ))}
            </HorizontalGrid>
        </VerticalStack>
    )
}

export default StepperComponent