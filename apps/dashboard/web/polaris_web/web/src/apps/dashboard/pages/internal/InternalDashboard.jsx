import React, { useState } from 'react'
import PageWithMultipleCards from '../../components/layouts/PageWithMultipleCards'
import { Box, HorizontalGrid, LegacyCard, Text, TextField } from '@shopify/polaris'
import Dropdown from '../../components/layouts/Dropdown'

const rolesOptions = [
    {
        label: 'Admin',
        value: 'ADMIN',
    },
    {
        label: 'Security Engineer',
        value: 'MEMBER',
    },
    {
        label: 'Developer',
        value: 'DEVELOPER',
    },
    {
        label: 'Guest',
        value: 'GUEST',
    }
]

function InternalDashboard() {

    const [accountId, setAccountId] = useState()
    const [selectedRole, setSelectedRole] = useState('MEMBER')

    const [accountInfo, setAccountInfo] = useState({
        "org-name": '',
        "hybrid-testing": false,
        "hybrid-runtime": false,
        "domain-name": 'akto'
    })

    const goToAccount = (
        <LegacyCard primaryFooterAction={{content: 'Go to Account', onAction: () => {}} } title="Switch Account" key={"change-account"}>
            <LegacyCard.Section>
                <HorizontalGrid gap={"4"} columns={2}>
                    <TextField value={accountId} onChange={setAccountId} type="number" label="AccountId" placeholder='Enter the account Id' />
                    <Dropdown label="Select your role" menuItems={rolesOptions} id={"roles-dropdown"} selected={setSelectedRole} initial={selectedRole} />    
                    
                </HorizontalGrid>
            </LegacyCard.Section>
        </LegacyCard>
    )

    const knowAccountInfo = ()

    const componentsArr = [goToAccount]

    return (
        <Box padding="8">
            <PageWithMultipleCards
                title={
                    <Text variant='headingLg'>
                        Internal Dashboard
                    </Text>
                }
                components={componentsArr}
                isFirstPage={true}
                divider={true}
            />
        </Box>
    )
}

export default InternalDashboard