import { Avatar, Box, Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function Steps({currentStep, totalSteps, stepsTextArr}) {

    function CurrentStepComp ({state}) {   
        return(<Button plain monochrome disabled={state}><Avatar shape="round" size="extraSmall" source="/public/steps_icon.svg"/></Button>) 
    }

    const completedStepComponent = (
        <div style={{borderRadius: '50%', backgroundColor: '#ECEBFF', height: '24px', width:'24px'}}>
            <Avatar shape="round" size="extraSmall" source="/public/step_completed_icon.svg"/>
        </div>
    )

    const StepsCardHeader = () => {
        return(
        <Box padding={"2"}>
            <HorizontalStack align="center">
                {Array.from({ length: totalSteps }).map((_, index) => {
                    return(
                        <Box width={index === totalSteps - 1 ? "24px" :"252px"} key={"index " + index}>
                            <HorizontalStack wrap={false} key={index}>
                                {index < currentStep ? completedStepComponent : <CurrentStepComp state={currentStep !== index}/>}
                                {index < totalSteps - 1 ? <Box width="100%" minHeight='1px' borderWidth="2" borderColor={currentStep > index ? "border-primary" : "border-subdued"}></Box> : null}
                            </HorizontalStack>
                        </Box>
                    )}
                )}
            </HorizontalStack>
        </Box>
    )}

    const stepsComp = (
        Array.from({ length: totalSteps }).map((_, index) => {
            return(
                <HorizontalStack key={index}>
                    <Box width='252px' padding={"2"} paddingBlockStart={"0"}>
                        <Text alignment="center" variant="headingMd">{stepsTextArr[index]}</Text>
                    </Box> 
                </HorizontalStack>
            )}
        )
    )

    return (
        <VerticalStack>
            <StepsCardHeader />
            <HorizontalStack align="center">
                {stepsComp}
            </HorizontalStack>
        </VerticalStack>
    )
}

export default Steps