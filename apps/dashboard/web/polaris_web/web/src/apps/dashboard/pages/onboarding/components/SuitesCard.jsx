import { Box, Card, InlineStack, Icon, RadioButton, Text, Tooltip, VerticalStack } from '@shopify/polaris'
import { QuestionMarkMinor } from "@shopify/polaris-icons"
import React from 'react'
import OnboardingStore from '../OnboardingStore'

function SuitesCard({cardObj}) {

    const setSelectedTest = OnboardingStore(state => state.setSelectedTestSuite)
    const selectedTest = OnboardingStore(state => state.selectedTestSuite)

    return (
        <div onClick={() => setSelectedTest(cardObj._name)}>
            <Card>
                <VerticalStack gap="2">
                    <InlineStack align="space-between">
                        <InlineStack gap="1">
                            <RadioButton checked={selectedTest === cardObj._name} />
                            <Text variant="bodyLg" fontWeight="semibold">{cardObj?.name}</Text>
                        </InlineStack>
                        <Tooltip content={cardObj?.description}>
                            <Box>
                                <Icon color="base" source={QuestionMarkMinor} />
                            </Box>
                        </Tooltip>
                    </InlineStack>

                    <Box as="span" paddingInlineStart="8">
                        <Text variant="bodyMd">
                            {cardObj?.tests?.length} Tests
                        </Text>
                    </Box>
                </VerticalStack>
            </Card>
        </div>
    );
}

export default SuitesCard