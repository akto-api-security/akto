import { Avatar, Box, Button, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'

function Steps({currentStep, totalSteps, stepObj}) {

    const CurrentStepComp = (state) =>{       
        return(<Button plain monochrome disabled={state}><Avatar shape="round" size="extraSmall" source="/public/steps_icon.svg"/></Button>) 
    }

    const completedStepComponent = (
        <div style={{borderRadius: '50%', backgroundColor: '#ECEBFF', height: '24px', width:'24px'}}>
            <Avatar shape="round" size="extraSmall" source="/public/step_completed_icon.svg"/>
        </div>
    )

    const StepsCardHeader= () => {
        <Box padding={"2"}>
            <VerticalStack>
                
            </VerticalStack>
        </Box>
    }

    const stepsComp = (
        Array.from({ length: totalSteps }).map((_, index) => {
            return(
                <HorizontalStack key={index}>
                   {currentStepComp}
                </HorizontalStack>
            )}
        )
    )

    return (
        <HorizontalGrid columns={totalSteps}>
           {stepsComp}
        </HorizontalGrid>
    )
}

export default Steps