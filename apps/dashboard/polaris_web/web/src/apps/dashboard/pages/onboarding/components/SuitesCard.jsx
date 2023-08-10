import { Box, Card, HorizontalStack, Icon, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { QuestionMarkMinor } from "@shopify/polaris-icons"
import React from 'react'
import OnboardingStore from '../OnboardingStore'

function SuitesCard({cardObj}) {

    const setSelectedTest = OnboardingStore(state => state.setSelectedTestSuite)
    const selectedTest = OnboardingStore(state => state.selectedTestSuite)

    return (
        <div onClick={() => setSelectedTest(cardObj._name)} className={"suite-card " + (selectedTest === cardObj._name ? "active-card" : '')}>
            <Card background="bg-subdued">
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between">
                        <Text variant="headingXl" as='h4' color='subdued'>{cardObj?.name}</Text>
                        <Tooltip content={cardObj?.description}>
                            <Box>
                                <Icon color="subdued" source={QuestionMarkMinor} />
                            </Box>
                        </Tooltip>
                    </HorizontalStack>

                    <Box as="span">
                        <Text variant="headingMd">
                            {cardObj?.tests?.length} Tests
                        </Text>
                    </Box>
                </VerticalStack>
            </Card>
        </div>
    )
}

export default SuitesCard