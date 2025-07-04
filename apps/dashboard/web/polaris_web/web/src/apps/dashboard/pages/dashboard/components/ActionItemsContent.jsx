import { VerticalStack, Box, Badge, HorizontalStack, Icon, Avatar } from '@shopify/polaris'
import ActionItemDetails from './ActionItemDetails'
import { EmailMajor, ChevronDownMinor, AlertMajor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import api from '../api'
import func from '../../../../../util/func'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GridRows from '../../../components/shared/GridRows'
import observeApi from '../../observe/api'

const actionItemsHeaders = [
    {
        title: '', 
        value: 'priority',
        type: 'text',
        maxWidth: '60px'
    },
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
        maxWidth: '120px'
    },
    {
        title: 'Efforts',
        value: 'effort',
        type: 'text',
        maxWidth: '80px'
    },
    {
        title: 'Why It Matters',
        value: 'whyItMatters',
        type: 'text',
        maxWidth: '300px'
    },
    {
        title: 'Action',
        value: 'actions',
        type: 'action',
        maxWidth: '100px'
    }
];

const resourceName = {
    singular: 'action item',
    plural: 'action items'
};

const PriorityIcon = ({ priority }) => {
    const getIconStyle = () => {
        switch (priority) {
            case 'P1':
                return {
                    backgroundColor: '#FED3D1',
                    color: '#000000',
                    borderRadius: '12px',
                    width: '32px',
                    height: '20px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '12px',
                    fontWeight: 'normal'
                };
            case 'P2':
                return {
                    backgroundColor: '#FFD79D',
                    color: '#000000',
                    borderRadius: '12px',
                    width: '32px',
                    height: '20px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '12px',
                    fontWeight: 'normal'
                };
            case 'P3':
                return {
                    backgroundColor: '#E5E5E5',
                    color: '#000000',
                    borderRadius: '12px',
                    width: '32px',
                    height: '20px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '12px',
                    fontWeight: 'normal'
                };
        }
    };

    return (
        <div style={getIconStyle()}>
            {priority}
        </div>
    );
};

export const ActionItemsContent = () => {
    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [actionItems, setActionItems] = useState([]);

    function getActions(item) {
        return [{
            items: [
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
            console.log('API Stats Response:', response);
            const countMapResp = await observeApi.fetchCountMapOfApis();
            console.log('Count Map Response:', countMapResp);
            if (countMapResp && typeof countMapResp.totalApisCount === 'number') {
                sensitiveDataCount = countMapResp.totalApisCount;
            }

            if (response && response.apiStatsEnd && response.apiStatsStart) {
                const apiStatsEnd = response.apiStatsEnd;
                const apiStatsStart = response.apiStatsStart;

                const highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
                    .filter(([score]) => parseInt(score) > 3)
                    .reduce((total, [, count]) => total + count, 0);

                const unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;

                const currentThirdParty = apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0;
                const previousThirdParty = apiStatsStart.accessTypeMap?.THIRD_PARTY || 0;
                const thirdPartyDiff = currentThirdParty - previousThirdParty;

                const dynamicActionItems = [
                    {
                        id: '1',
                        priority: <PriorityIcon priority="P1" />,
                        priorityComp: <PriorityIcon priority="P1" />,
                        actionItem: `${highRiskCount} APIs with risk score more than 3`,
                        team: 'Security Team',
                        effort: 'Medium',
                        whyItMatters: 'Creates multiple attack vectors for malicious actors',
                        displayName: `${highRiskCount} APIs with risk score more than 3`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: highRiskCount
                    },
                    {
                        id: '2',
                        priority: <PriorityIcon priority="P1" />,
                        priorityComp: <PriorityIcon priority="P1" />,
                        actionItem: `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                        team: 'Development',
                        effort: 'Medium',
                        whyItMatters: 'Violates data privacy regulations (GDPR, CCPA) and risks customer trust',
                        displayName: `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: sensitiveDataCount
                    },
                    {
                        id: '3',
                        priority: <PriorityIcon priority="P1" />,
                        priorityComp: <PriorityIcon priority="P1" />,
                        actionItem: `${unauthenticatedCount} APIs lacking proper authentication controls`,
                        team: 'Security Team',
                        effort: 'Medium',
                        whyItMatters: 'Easy target for unauthorized access and data exfiltration',
                        displayName: `${unauthenticatedCount} APIs lacking proper authentication controls`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: unauthenticatedCount
                    },
                    {
                        id: '4',
                        priority: <PriorityIcon priority="P2" />,
                        priorityComp: <PriorityIcon priority="P2" />,
                        actionItem: `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                        team: 'Integration Team',
                        effort: 'Low',
                        whyItMatters: 'New integrations may introduce unvetted security risks',
                        displayName: `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: Math.max(0, thirdPartyDiff)
                    }
                ];

                const filteredActionItems = dynamicActionItems.filter(item => item.count > 0);
                setActionItems(filteredActionItems);
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
                    emptyStateMessage="No action items found"
                    // onRowClick={handleRowClick}
                />
            </Box>

            {/* <FlyLayout
                show={showFlyout}
                setShow={setShowFlyout}
                title="Action item details"
                components={[<ActionItemDetails item={selectedItem} />]}
            /> */}
        </VerticalStack>
    );
};