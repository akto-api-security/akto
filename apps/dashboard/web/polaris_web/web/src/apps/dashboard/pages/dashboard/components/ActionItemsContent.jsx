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
import TooltipText from '../../../components/shared/TooltipText'

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

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";

function JiraLogoClickable() {
    const isIntegrated = typeof window !== 'undefined' && window.JIRA_INTEGRATED === true;
    const handleClick = (e) => {
        if (!isIntegrated) {
            e.stopPropagation();
            window.location.href = JIRA_INTEGRATION_URL;
        } else {
            e.stopPropagation(); // Prevent row click if any
        }
    };
    return (
        <span
            style={{ cursor: isIntegrated ? 'default' : 'pointer', display: 'inline-block' }}
            onClick={handleClick}
            title={isIntegrated ? undefined : 'Integrate Jira'}
        >
            <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
        </span>
    );
}

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
                        priority: <Badge status="critical">P1</Badge>,
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip={`${highRiskCount} APIs with risk score more than 3`}
                                    text={`${highRiskCount} APIs with risk score more than 3`}
                                    textProps={{variant: 'bodyMd', fontWeight: 'medium'}}
                                />
                            </Box>
                        ),
                        team: (
                            <Box maxWidth="120px">
                                <TooltipText 
                                    tooltip="Security Team"
                                    text="Security Team"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        effort: (
                            <Box maxWidth="80px">
                                <TooltipText 
                                    tooltip="Medium"
                                    text="Medium"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        whyItMatters: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip="Creates multiple attack vectors for malicious actors"
                                    text="Creates multiple attack vectors for malicious actors"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        displayName: `${highRiskCount} APIs with risk score more than 3`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <JiraLogoClickable />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: highRiskCount
                    },
                    {
                        id: '2',
                        priority: <Badge status="critical">P1</Badge>,
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip={`${sensitiveDataCount} Endpoints exposing PII or confidential information`}
                                    text={`${sensitiveDataCount} Endpoints exposing PII or confidential information`}
                                    textProps={{variant: 'bodyMd', fontWeight: 'medium'}}
                                />
                            </Box>
                        ),
                        team: (
                            <Box maxWidth="120px">
                                <TooltipText 
                                    tooltip="Development"
                                    text="Development"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        effort: (
                            <Box maxWidth="80px">
                                <TooltipText 
                                    tooltip="Medium"
                                    text="Medium"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        whyItMatters: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip="Violates data privacy regulations (GDPR, CCPA) and risks customer trust"
                                    text="Violates data privacy regulations (GDPR, CCPA) and risks customer trust"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        displayName: `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <JiraLogoClickable />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: sensitiveDataCount
                    },
                    {
                        id: '3',
                        priority: <Badge status="critical">P1</Badge>,
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip={`${unauthenticatedCount} APIs lacking proper authentication controls`}
                                    text={`${unauthenticatedCount} APIs lacking proper authentication controls`}
                                    textProps={{variant: 'bodyMd', fontWeight: 'medium'}}
                                />
                            </Box>
                        ),
                        team: (
                            <Box maxWidth="120px">
                                <TooltipText 
                                    tooltip="Security Team"
                                    text="Security Team"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        effort: (
                            <Box maxWidth="80px">
                                <TooltipText 
                                    tooltip="Medium"
                                    text="Medium"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        whyItMatters: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip="Easy target for unauthorized access and data exfiltration"
                                    text="Easy target for unauthorized access and data exfiltration"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        displayName: `${unauthenticatedCount} APIs lacking proper authentication controls`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <JiraLogoClickable />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: unauthenticatedCount
                    },
                    {
                        id: '4',
                        priority: <Badge status="attention">P2</Badge>,
                        priorityComp: <Badge status="attention">P2</Badge>,
                        actionItem: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip={`${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`}
                                    text={`${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`}
                                    textProps={{variant: 'bodyMd', fontWeight: 'medium'}}
                                />
                            </Box>
                        ),
                        team: (
                            <Box maxWidth="120px">
                                <TooltipText 
                                    tooltip="Integration Team"
                                    text="Integration Team"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        effort: (
                            <Box maxWidth="80px">
                                <TooltipText 
                                    tooltip="Low"
                                    text="Low"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        whyItMatters: (
                            <Box maxWidth="300px">
                                <TooltipText 
                                    tooltip="New integrations may introduce unvetted security risks"
                                    text="New integrations may introduce unvetted security risks"
                                    textProps={{variant: 'bodyMd'}}
                                />
                            </Box>
                        ),
                        displayName: `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                        actions: (
                            <VerticalStack align="center">
                                <HorizontalStack gap="2" align="center">
                                    <JiraLogoClickable />
                                </HorizontalStack>
                            </VerticalStack>
                        ),
                        count: Math.max(0, thirdPartyDiff)
                    }
                ];

                const filteredActionItems = dynamicActionItems.filter(item => item.count > -1);
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
            <Box maxWidth="100%" style={{ overflowX: 'hidden' }}>
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