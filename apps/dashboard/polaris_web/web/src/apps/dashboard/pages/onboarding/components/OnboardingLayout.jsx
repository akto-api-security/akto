import { Box, Button, HorizontalStack, Icon, LegacyCard, Text, VerticalStack} from '@shopify/polaris'
import React from 'react'
import StepperComponent from '../../../components/shared/StepperComponent'

function OnboardingLayout({stepObj, requestStepChange, currentStep, skipOnboarding, next}) {

    const titleComponent = (
        <VerticalStack gap="1">
            <Text variant="headingMd" as="h4">{stepObj.cardTitle}</Text>
        </VerticalStack>
    )

    return (
        <div className={"main-card-onboarding " + (currentStep === 2 ? "expand " : "") + (currentStep === 4 ? "change-margin": "")}>
            <div className='titleContainer'>
                <Text variant="heading3xl" color='subdued' as='h2'>{stepObj.title}</Text>
                <Text variant="headingXl" color='subdued' as='h5'>{stepObj.subtitle}</Text>
                {currentStep < 4 ? <StepperComponent currentStep={currentStep} totalSteps={3} stepperClicked={(index) => requestStepChange(index)}/> : null}
            </div>
            <LegacyCard title={titleComponent}>
                <LegacyCard.Section>
                    <VerticalStack gap="3">
                        {stepObj?.component}
                        <Button onClick={()=> next()} primary fullWidth size='large'>
                            <HorizontalStack gap="1">
                                {stepObj.buttonText}
                                <Box>
                                    <Icon source={stepObj.icon} />
                                </Box>
                            </HorizontalStack>
                        </Button>
                    </VerticalStack>
                </LegacyCard.Section>
            </LegacyCard>

            <Button plain onClick={() => skipOnboarding()} fullWidth>I'm a ninja!! I dont need onboarding.</Button>
        </div>
    )
}

export default OnboardingLayout