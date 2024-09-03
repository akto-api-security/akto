import React from 'react'
import {Text, ButtonGroup, Button, HorizontalStack, VerticalStack, LegacyCard, Box } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'


function Help() {
    const titleComp = (
        <LegacyCard key={"titleComp"} sectioned={true}>
            <HorizontalStack align="space-between" gap="2" blockAlign="center">
                <VerticalStack align="space-between" gap="4">
                    <Text variant="headingMd">Need Assistance?</Text>
                    <Box width="450px">
                        <Text variant="bodyMd">Our team is here to help you. Book a call with our support experts or contact us directly for prompt assistance.</Text>
                    </Box>
                    <ButtonGroup>
                        <Button onClick={() => window.open("mailto:support@akto.io", "_blank")}>Mail us</Button>
                        <Button plain onClick={() => window.Intercom && window.Intercom('show')}>Contact us</Button>
                    </ButtonGroup>
                </VerticalStack>
                <VerticalStack gap="4">
                    <img src="/public/chat_major_help.svg" alt="Help" width="100" height="100" />
                </VerticalStack>
            </HorizontalStack>
        </LegacyCard>
    )

    const components = [titleComp]
  return (
    <PageWithMultipleCards
            components={components}
            title={
                <Text variant='headingLg' truncate>
                    Help and Support
                </Text>
            }
            isFirstPage={true}
            divider={true}
        />
  )
}

export default Help
