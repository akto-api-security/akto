import { Box, Button, Divider,LegacyCard, Text, VerticalStack} from '@shopify/polaris'
import React from 'react'
import StepperComponent from '../../../components/shared/StepperComponent'

function OnboardingLayout({stepObj, requestStepChange, currentStep, skipOnboarding, next}) {

    const titleComponent = (
        <VerticalStack gap="1">
            <Text variant="headingMd" as="h4">{stepObj.cardTitle}</Text>
        </VerticalStack>
    )

    return (
        <div className="main-card-onboarding">
            <div className='titleContainer'>
                <Text variant="heading2xl" fontWeight="semibold">{stepObj.title}</Text>
                <Text variant="bodyLg" >{stepObj.subtitle}</Text>
                {currentStep < 4 ? <StepperComponent currentStep={currentStep} totalSteps={3} stepperClicked={(index) => requestStepChange(index)}/> : null}
            </div>
            <LegacyCard title={titleComponent} 
                    primaryFooterAction={{content: stepObj.actionText, onAction: next}}
                    {...(currentStep > 1 && currentStep < 4) ? {secondaryFooterActions: [{content: "Back", onAction: ()=> requestStepChange(currentStep - 1)}]}: null}
            >
                <LegacyCard.Section>
                    <VerticalStack gap="3">
                        {stepObj?.component}
                    </VerticalStack>
                </LegacyCard.Section>
                <Divider/>
                <Box padding="2"/>
            </LegacyCard>

            <Button plain onClick={() => skipOnboarding()} fullWidth>I'm a ninja!! I dont need onboarding.</Button>
        </div>
    )
}

export default OnboardingLayout