import { Box, Button, Divider,LegacyCard, Text, BlockStack} from '@shopify/polaris'
import React from 'react'
import StepperComponent from '../../../components/shared/StepperComponent'

function OnboardingLayout({stepObj, requestStepChange, currentStep, skipOnboarding, next}) {

    const titleComponent = (
        <BlockStack gap="100">
            <Text variant="headingMd" as="h4">{stepObj.cardTitle}</Text>
        </BlockStack>
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
                    <BlockStack gap="300">
                        {stepObj?.component}
                    </BlockStack>
                </LegacyCard.Section>
                <Divider/>
                <Box padding="200"/>
            </LegacyCard>

            <Button  onClick={() => skipOnboarding()} fullWidth variant="plain">I'm a ninja!! I dont need onboarding.</Button>
        </div>
    );
}

export default OnboardingLayout