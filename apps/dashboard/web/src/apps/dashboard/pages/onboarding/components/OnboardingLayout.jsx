import { Button, LegacyCard, Text, VerticalStack} from '@shopify/polaris'
import React from 'react'
import StepperComponent from '../../../components/shared/StepperComponent'

function OnboardingLayout({stepObj, requestStepChange, currentStep, skipOnboarding}) {

    const titleComponent = (
        <VerticalStack gap="1">
            <Text variant="headingMd" as="h4">{stepObj.cardTitle}</Text>
        </VerticalStack>
    )

    return (
        <div className='main-card-onboarding'>
            <div className='titleContainer'>
                <Text variant="heading3xl" color='subdued' as='h2'>{stepObj.title}</Text>
                <Text variant="headingXl" color='subdued' as='h5'>{stepObj.subtitle}</Text>
                <StepperComponent currentStep={currentStep} totalSteps={3} stepperClicked={(index) => requestStepChange(index)}/>
            </div>
            <LegacyCard title={titleComponent}>
                {stepObj?.component}
            </LegacyCard>

            <Button plain onClick={() => skipOnboarding()} fullWidth>I'm a ninja!! I dont need onboarding.</Button>
        </div>
    )
}

export default OnboardingLayout