import React, { useState } from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { HorizontalStack, Text, TextField, VerticalStack } from '@shopify/polaris'

function InternalDashboard() {

    const [accountId, setAccountId] = useState('')

    const accountsRelatedComp = (
        <VerticalStack key="accounts" gap={"4"}>
            <VerticalStack gap={"2"}>
                <Text color="subdued" variant="headingMd">
                    Go to account
                </Text>
                <HorizontalStack gap={"4"}>
                    <TextField value={accountId} onChange={setAccountId} placeholder='Enter account id here' />

                </HorizontalStack>
            </VerticalStack>
        </VerticalStack>
    )

    return (
        <PageWithMultipleCards
            divider={true}
            components={[]}
            title={
                <Text variant='headingLg' truncate>
                    Debugging life made easy
                </Text>
            }
            isFirstPage={true}
        />
    )
}

export default InternalDashboard