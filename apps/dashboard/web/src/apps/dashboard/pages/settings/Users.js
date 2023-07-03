import { Card, Page, Text } from "@shopify/polaris"

const Users = () => {

    return (
        <Page
            title="Users"
            primaryAction={{
                content: 'Invite user',
            }}
            divider
        >
            <Text variant="headingMd">Team details</Text>
            <Text variant="bodyMd">Find and manage your team permissions here</Text>
            <Card>
                <Text as="h2" variant="bodyMd">
                    Content inside a card
                </Text>
            //Use Resource List
            </Card>
        </Page>
    )
}

export default Users