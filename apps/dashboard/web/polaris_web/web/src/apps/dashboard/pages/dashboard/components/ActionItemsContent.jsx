import { VerticalStack, Box, Badge, HorizontalStack, Icon, Avatar } from '@shopify/polaris'
import ActionItemDetails from './ActionItemDetails'
import {EmailMajor, ChevronDownMinor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import api from '../api'
import func from '../../../../../util/func'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GridRows from '../../../components/shared/GridRows'

const actionItemsHeaders = [
    {
        title: 'Action Item',
        value: 'actionItem',
        type: 'text',
        maxWidth: '300px'
    },
    {
        title: 'Team',
        value: 'team',
        type: 'text',
        maxWidth: '100px'
    },
    {
        title: 'Why it matters',
        value: 'whyItMatters',
        type: 'text',
        maxWidth: '300px'
    },
    {
        title: 'Actions',
        value: 'actions',
        type: 'action',
        maxWidth: '100px'
    }
];

const resourceName = {
    singular: 'action item',
    plural: 'action items'
};

const sampleActionItems = [
    {
        id: '1',
        priority: 'P1',
        priorityComp: <Badge status="critical">P1</Badge>,
        actionItem: 'Shadow API detected in prod',
        team: 'Security',
        effort: 'High',
        whyItMatters: 'Uncontrolled/unknown attack surface',
        displayName: 'Shadow API detected in prod',
        // assignee: <AssignTaskToUser />, // TODO: Re-enable assignee in future iteration
        actions: <HorizontalStack gap="2"><Icon source={EmailMajor} color="base" /><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" /></HorizontalStack>
    }
];


export const ActionItemsContent = () => {
    function getActions(item) {
        return [{
            items: [
                {
                    content: 'Email',
                    icon: EmailMajor,
                    url: '#',
                    external: true
                },
                {
                    content: item.ticket || 'Create ticket',
                    icon: item.ticket ? undefined : ChevronDownMinor,
                    url: '#',
                    external: true
                }
            ]
        }];
    }

    const handleRowClick = (item) => {
        setSelectedItem(item);
        setShowFlyout(true);
    };

    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [actionItems, setActionItems] = useState(sampleActionItems);

    const fetchData = async () => {
        const endTimestamp = func.timeNow();
        const startTimestamp = func.timeNow() - 3600 * 24 * 7;
        const response = await api.fetchApiStats(startTimestamp, endTimestamp)
        console.log(response);
    }

    useEffect(() => {
        fetchData();
    },[])

    return (
    <VerticalStack gap={"5"}>
        {/* <GridRows items={[{}, {}, {}, {}, {}, {}, {}]} CardComponent={ActionItemCard} columns={4} onButtonClick={handleRowClick}/> */}
        <Box>
            <GithubSimpleTable
                key={"table"}
                data={actionItems}
                resourceName={resourceName}
                headers={actionItemsHeaders}
                headings={actionItemsHeaders}
                useNewRow={true}
                condensedHeight={true}
                hideQueryField={true}
                hidePagination={true}
                hasZebraStriping={true}
                getActions={getActions}
                hasRowActions={true}
                defaultSortField="priority"
                defaultSortDirection="asc"
                renderBadge={(item) => (
                    <Badge status={item.priorityDisplay}>{item.priority}</Badge>
                )}
                onRowClick={handleRowClick}
            />
        </Box>

        <FlyLayout
            show={showFlyout}
            setShow={setShowFlyout}
            title="Action item details"
            components={[<ActionItemDetails item={selectedItem}/>]}
        />
    </VerticalStack>
    )
}