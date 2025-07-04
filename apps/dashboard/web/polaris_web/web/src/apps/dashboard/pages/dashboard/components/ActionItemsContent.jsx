import { VerticalStack, Box, Badge, HorizontalStack, Icon, Avatar } from '@shopify/polaris'
import ActionItemDetails from './ActionItemDetails';
import { EmailMajor, ChevronDownMinor, AlertMajor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import api from '../api'
import func from '../../../../../util/func'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GridRows from '../../../components/shared/GridRows'
import observeApi from '../../observe/api'
import TooltipText from '../../../components/shared/TooltipText'
import JiraTicketCreationModal from '../../../components/shared/JiraTicketCreationModal'

const actionItemsHeaders = [
    { title: '', value: 'priority', type: 'text' },
    { title: 'Action Item', value: 'actionItem', type: 'text' },
    { title: 'Team', value: 'team', type: 'text' },
    { title: 'Efforts', value: 'effort', type: 'text' },
    { title: 'Why It Matters', value: 'whyItMatters', type: 'text' },
    { title: 'Action', value: 'actions', type: 'action' }
];

const resourceName = {
    singular: 'action item',
    plural: 'action items'
};

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";

export const ActionItemsContent = () => {
    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [actionItems, setActionItems] = useState([]);

    // Modal-related state
    const [modalActive, setModalActive] = useState(false);
    const [projId, setProjId] = useState('');
    const [issueType, setIssueType] = useState('');
    const [issueId, setIssueId] = useState('');
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});

    const isIntegrated = typeof window !== 'undefined' && window.JIRA_INTEGRATED === true;

    const handleClick = (e) => {
        e.stopPropagation();
        if (!isIntegrated) {
            window.location.href = JIRA_INTEGRATION_URL;
        } else {
            setModalActive(true);
        }
    };

    function JiraLogoClickable() {
        return (
            <span
                style={{ cursor: isIntegrated ? 'pointer' : 'pointer', display: 'inline-block' }}
                onClick={handleClick}
                title={isIntegrated ? 'Create Jira Ticket' : 'Integrate Jira'}
            >
                <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
            </span>
        );
    }

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

    const fetchData = async () => {
        const endTimestamp = func.timeNow();
        const startTimestamp = func.timeNow() - 3600 * 24 * 7;

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
                    .filter(([score]) => parseInt(score) > 3)
                    .reduce((total, [, count]) => total + count, 0);

                const unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;
                const currentThirdParty = apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0;
                const previousThirdParty = apiStatsStart.accessTypeMap?.THIRD_PARTY || 0;
                const thirdPartyDiff = currentThirdParty - previousThirdParty;

                const buildTruncatableCell = (tooltip, text, maxWidth = '400px') => (
                    <Box style={{ minWidth: 0, flex: 1, maxWidth }}>
                        <div style={{
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                        }}>
                            <TooltipText
                                tooltip={tooltip}
                                text={text}
                                textProps={{ variant: 'bodyMd', fontWeight: 'medium' }}
                            />
                        </div>
                    </Box>
                );

                const dynamicActionItems = [
                    {
                        id: '1',
                        priority: <Badge status="critical">P1</Badge>,
                        priorityComp: <Badge status="critical">P1</Badge>,
                        actionItem: buildTruncatableCell(
                            `${highRiskCount} APIs with risk score more than 3`,
                            `${highRiskCount} APIs with risk score more than 3`
                        ),
                        team: buildTruncatableCell("Security Team", "Security Team", '200px'),
                        effort: buildTruncatableCell("Medium", "Medium", '100px'),
                        whyItMatters: buildTruncatableCell(
                            "Creates multiple attack vectors for malicious actors",
                            "Creates multiple attack vectors for malicious actors"
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
                        actionItem: buildTruncatableCell(
                            `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                            `${sensitiveDataCount} Endpoints exposing PII or confidential information`
                        ),
                        team: buildTruncatableCell("Development", "Development", '200px'),
                        effort: buildTruncatableCell("Medium", "Medium", '100px'),
                        whyItMatters: buildTruncatableCell(
                            "Violates data privacy regulations (GDPR, CCPA) and risks customer trust",
                            "Violates data privacy regulations (GDPR, CCPA) and risks customer trust"
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
                        actionItem: buildTruncatableCell(
                            `${unauthenticatedCount} APIs lacking proper authentication controls`,
                            `${unauthenticatedCount} APIs lacking proper authentication controls`
                        ),
                        team: buildTruncatableCell("Security Team", "Security Team", '200px'),
                        effort: buildTruncatableCell("Medium", "Medium", '100px'),
                        whyItMatters: buildTruncatableCell(
                            "Easy target for unauthorized access and data exfiltration",
                            "Easy target for unauthorized access and data exfiltration"
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
                        actionItem: buildTruncatableCell(
                            `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                            `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`
                        ),
                        team: buildTruncatableCell("Integration Team", "Integration Team", '200px'),
                        effort: buildTruncatableCell("Low", "Low", '100px'),
                        whyItMatters: buildTruncatableCell(
                            "New integrations may introduce unvetted security risks",
                            "New integrations may introduce unvetted security risks"
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
        <VerticalStack gap="5">
            <Box maxWidth="100%" style={{ overflowX: 'hidden' }}>
                <GithubSimpleTable
                    key="table"
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
                />
            </Box>

            <JiraTicketCreationModal
                activator={null}
                modalActive={modalActive}
                setModalActive={setModalActive}
                handleSaveAction={() => { }}
                jiraProjectMaps={jiraProjectMaps}
                setProjId={setProjId}
                setIssueType={setIssueType}
                projId={projId}
                issueType={issueType}
                issueId={issueId}
                isAzureModal={false}
            />
            {/* <FlyLayout
                    show={showFlyout}
                    setShow={setShowFlyout}
                    title="Action item details"
                    components={[<ActionItemDetails item={selectedItem} />]}
                /> */}
        </VerticalStack>
    );
};
