import { Avatar, Card, Page, ResourceItem, ResourceList, Text } from "@shopify/polaris"

const Users = () => {

    return (
        <div style={{ height: "100vh" }}>
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
                    <ResourceList
                        resourceName={{ singular: 'customer', plural: 'customers' }}
                        items={[
                            {
                                id: '105',
                                name: 'Mae Jemison',
                                location: 'Decatur, USA',
                            },
                            {
                                id: '205',
                                url: '#',
                                name: 'Ellen Ochoa',
                                location: 'Los Angeles, USA',
                            },
                        ]}
                        renderItem={(item) => {
                            const { id, url, name, location } = item;
                            const media = <Avatar customer size="medium" name={name} />;

                            return (
                                <ResourceItem
                                    id={id}
                                    url={url}
                                    media={media}
                                    accessibilityLabel={`View details for ${name}`}
                                >
                                    <Text variant="bodyMd" fontWeight="bold" as="h3">
                                        {name}
                                    </Text>
                                    <div>{location}</div>
                                </ResourceItem>
                            );
                        }}
                        showHeader
                        totalItemsCount={50}
                    />
                </Card>
            </Page>
        </div>

    )
}

export default Users