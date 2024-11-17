import { InlineGrid, Text, BlockStack } from '@shopify/polaris'
import "./style.css"
import React from 'react'

function StepperComponent({totalSteps, currentStep, stepperClicked}) {

    const clickFunc = (index) => {
        if(Math.abs(currentStep - index) === 1){
            stepperClicked(index)
        }
    }
    return (
        <BlockStack gap="200">
            <Text variant="bodyMd" as='h3' color='subdued'>Step {currentStep} out of {totalSteps}</Text>
            <InlineGrid columns={totalSteps} gap="100">
                {Array.from({length: totalSteps}).map((_,index) => (
                    <div className={"stepper-box " + (currentStep > index ? 'active' : '')} onClick={() => clickFunc(index + 1)} key={index}/>
                ))}
            </InlineGrid>
        </BlockStack>
    );
}

export default StepperComponent