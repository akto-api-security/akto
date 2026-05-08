import React from 'react';
import { HorizontalStack, Link, Tag, Text } from '@shopify/polaris';
import { useNavigate } from 'react-router-dom';
import PersistStore from '../../../main/PersistStore';

/**
 * ApiGroups Component
 * Displays a list of API groups as clickable tags
 * Handles both string names and numeric IDs from collectionIds
 *
 * @param {Object} props - Component props
 * @param {Array<string|number>} props.collectionIds - Array of collection IDs or names
 * @param {Function} props.onGroupClick - Optional callback when a group is clicked
 * @returns {JSX.Element|null} Rendered API groups or null if no groups
 */
function ApiGroups({ collectionIds, onGroupClick }) {
    const apiCollectionMap = PersistStore(state => state.collectionsMap);
    const allCollections = PersistStore(state => state.allCollections);
    const navigate = useNavigate();

    // Return null if no collection IDs provided
    if (!collectionIds || collectionIds.length === 0) {
        return null;
    }

    /**
     * Get API groups from collection IDs
     * @returns {Array<{id: string|number, name: string}>} Array of API group objects
     */
    const getApiGroups = () => {
        const firstItem = collectionIds[0];

        if (typeof firstItem === 'string') {
            // collectionIds contains names directly
            return collectionIds.map((name) => ({
                id: name,
                name: name
            }));
        } else {
            // collectionIds contains IDs, map to names
            return collectionIds
                .filter(id => apiCollectionMap[id])
                .map(id => ({
                    id: id,
                    name: apiCollectionMap[id]
                }));
        }
    };

    /**
     * Handle API group tag click - navigate to the group's collection page
     * @param {string} groupName - Name of the API group
     * @param {string|number} groupId - ID of the API group
     */
    const handleGroupClick = (groupName, groupId) => {
        // Call custom callback if provided
        if (onGroupClick) {
            onGroupClick(groupName, groupId);
        }

        // Find the collection
        const collection = allCollections.find(c =>
            c.id === groupId ||
            c.displayName === groupName ||
            c.name === groupName
        );

        if (collection) {
            navigate(`/dashboard/observe/inventory/${collection.id}`);
        }
    };

    const apiGroups = getApiGroups();

    if (apiGroups.length === 0) {
        return null;
    }

    return (
        <HorizontalStack gap="2" wrap={true} align="start">
            <Text variant="bodyMd" color="subdued">API Groups:</Text>
            {apiGroups.map((group) => (
                <Link
                    key={group.id}
                    removeUnderline
                    onClick={() => handleGroupClick(group.name, group.id)}
                >
                    <Tag>{group.name}</Tag>
                </Link>
            ))}
        </HorizontalStack>
    );
}

export default ApiGroups;
