import { ResourceItem, Text } from "@shopify/polaris"

const usersCollectionRenderItem = (item) => {
    const { id, collectionName } = item;

    return (
        <ResourceItem id={id} key={id}>
            <Text variant="bodyMd" fontWeight="semibold" as="h3">{collectionName}</Text>
        </ResourceItem>
    );
}

export { usersCollectionRenderItem }