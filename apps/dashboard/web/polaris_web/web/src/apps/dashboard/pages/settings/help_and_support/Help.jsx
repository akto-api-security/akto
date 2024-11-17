import React from 'react'
import {Text, ButtonGroup, Button, InlineStack, BlockStack, LegacyCard, Box } from '@shopify/polaris'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'


function Help() {
    const titleComp = (
        <LegacyCard key={"titleComp"} sectioned={true}>
            <InlineStack align="space-between" gap="200" blockAlign="center">
                <BlockStack align="space-between" gap="400">
                    <Text variant="headingMd">Need Assistance?</Text>
                    <Box width="450px">
                        <Text variant="bodyMd">Our team is here to help you. Book a call with our support experts or contact us directly for prompt assistance.</Text>
                    </Box>
                    <ButtonGroup>
                        <Button onClick={() => window.open("mailto:support@akto.io", "_blank")}>Mail us</Button>
                        <Button

                            onClick={() => window.Intercom && window.Intercom('show')}
                            variant="plain">Contact us</Button>
                    </ButtonGroup>
                </BlockStack>
                <BlockStack gap="400">
                    <img src="/public/chat_major_help.svg" alt="Help" width="100" height="100" />
                </BlockStack>
            </InlineStack>
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
