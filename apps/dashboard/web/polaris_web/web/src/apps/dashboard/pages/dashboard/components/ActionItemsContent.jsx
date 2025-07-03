import { VerticalStack, Box, Badge, HorizontalStack, Icon, Avatar } from '@shopify/polaris'
import ActionItemDetails from './ActionItemDetails'
import { EmailMajor, ChevronDownMinor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import api from '../api'
import func from '../../../../../util/func'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GridRows from '../../../components/shared/GridRows'
import observeApi from '../../observe/api'

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

export const ActionItemsContent = () => {
    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [actionItems, setActionItems] = useState([]);

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

    const fetchData = async () => {
        const endTimestamp = func.timeNow();
        const startTimestamp = func.timeNow() - 3600 * 24 * 7; // 7 days ago

        let sensitiveDataCount = 0;
        try {
            const response = await api.fetchApiStats(startTimestamp, endTimestamp);
            const countMapResp = await observeApi.fetchCountMapOfApis();
            if (countMapResp && typeof countMapResp.totalApisCount === 'number') {
                sensitiveDataCount = countMapResp.totalApisCount;
            }

            if (response && response.apiStatsEnd && response.apiStatsStart) {
                const apiStatsEnd = response.apiStatsEnd;
                const apiStatsStart = response.apiStatsStart;

                const highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
                    .filter(([score]) => parseInt(score) == 3)
                    .reduce((total, [, count]) => total + count, 0);

                const unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;

                const currentThirdParty = apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0;
                const previousThirdParty = apiStatsStart.accessTypeMap?.THIRD_PARTY || 0;
                const thirdPartyDiff = currentThirdParty - previousThirdParty;

                const dynamicActionItems = [
                    {
                        id: '1',
                        priority: 'P1',
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: `High-risk APIs - ${highRiskCount}`,
                        team: 'Security',
                        effort: 'High',
                        whyItMatters: 'High-risk APIs can expose sensitive data and create vulnerabilities that attackers can exploit to gain unauthorized access',
                        displayName: `High-risk APIs - ${highRiskCount}`,
                        actions: <HorizontalStack gap="2"><Icon source={EmailMajor} color="base" /><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" /></HorizontalStack>
                    },
                    {
                        id: '2',
                        priority: 'P1',
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: `Sensitive data APIs - ${sensitiveDataCount}`,
                        team: 'Security',
                        effort: 'High',
                        whyItMatters: 'APIs exposing sensitive data without proper protection can lead to data breaches and compliance violations',
                        displayName: `Sensitive data APIs - ${sensitiveDataCount}`,
                        actions: <HorizontalStack gap="2"><Icon source={EmailMajor} color="base" /><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" /></HorizontalStack>
                    },
                    {
                        id: '3',
                        priority: 'P1',
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: `Unauthenticated APIs - ${unauthenticatedCount}`,
                        team: 'Security',
                        effort: 'High',
                        whyItMatters: 'APIs without proper authentication are vulnerable to unauthorized access and potential security breaches',
                        displayName: `Unauthenticated APIs - ${unauthenticatedCount}`,
                        actions: <HorizontalStack gap="2"><Icon source={EmailMajor} color="base" /><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" /></HorizontalStack>
                    },
                    {
                        id: '4',
                        priority: 'P1',
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: `New third-party APIs (last 7 days) - ${Math.max(0, thirdPartyDiff)}`,
                        team: 'Security',
                        effort: 'High',
                        whyItMatters: 'Third-party APIs can introduce external dependencies and security risks that need monitoring and assessment',
                        displayName: `New third-party APIs (last 7 days) - ${Math.max(0, thirdPartyDiff)}`,
                        actions: <HorizontalStack gap="2"><Icon source={EmailMajor} color="base" /><Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" /></HorizontalStack>
                    }
                ];

                setActionItems(dynamicActionItems);
            } else {
                console.error('Invalid API response structure');
                setActionItems([]);
            }
        } catch (error) {
            console.error('Error fetching API stats:', error);
            setActionItems([]);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    return (
        <VerticalStack gap={"5"}>
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
                components={[<ActionItemDetails item={selectedItem} />]}
            />
        </VerticalStack>
    );
};