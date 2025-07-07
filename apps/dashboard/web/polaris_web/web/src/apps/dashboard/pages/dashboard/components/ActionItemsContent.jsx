import { VerticalStack, Box, Badge, HorizontalStack, Icon, Avatar } from '@shopify/polaris'
import ActionItemDetails from './ActionItemDetails';
import ActionItemCard from './ActionItemCard';
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
import settingFunctions from '../../settings/module';
import issuesApi from '../../issues/api';
import Store from '../../../store';

const actionItemsHeaders = [
    { title: '', value: 'priority', type: 'text' },
    { title: 'Action Item', value: 'actionItem', type: 'text' },
    { title: 'Team', value: 'team', type: 'text' },
    { title: 'Efforts', value: 'effort', type: 'text' },
    { title: 'Why It Matters', value: 'whyItMatters', type: 'text' },
    { title: 'Action', value: 'actions', type: 'action' }
];

const ACTION_ITEM_TYPES = {
    HIGH_RISK_APIS: 'HIGH_RISK_APIS',
    SENSITIVE_DATA_ENDPOINTS: 'SENSITIVE_DATA_ENDPOINTS',
    UNAUTHENTICATED_APIS: 'UNAUTHENTICATED_APIS',
    THIRD_PARTY_APIS: 'THIRD_PARTY_APIS',
    HIGH_RISK_THIRD_PARTY: 'HIGH_RISK_THIRD_PARTY',
    SHADOW_APIS: 'SHADOW_APIS',
    CRITICAL_SENSITIVE_UNAUTH: 'CRITICAL_SENSITIVE_UNAUTH'
};

const resourceName = {
    singular: 'action item',
    plural: 'action items'
};

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";

export const ActionItemsContent = () => {
    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [actionItems, setActionItems] = useState([]);
    const [criticalCardData, setCriticalCardData] = useState(null);

    const [modalActive, setModalActive] = useState(false);
    const [projId, setProjId] = useState('');
    const [issueType, setIssueType] = useState('');
    const [issueId, setIssueId] = useState('');
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});

    const [selectedActionItem, setSelectedActionItem] = useState(null);

    const setToastConfig = Store(state => state.setToastConfig);
    const setToast = (isActive, isError, message) => {
        setToastConfig({
            isActive: isActive,
            isError: isError,
            message: message
        });
    };

    const handleJiraIntegration = (actionItem) => {
        const integrated = Boolean(window?.JIRA_INTEGRATED);

        if (!integrated) {
            window.location.href = JIRA_INTEGRATION_URL;
            return;
        }

        setSelectedActionItem(actionItem);
        setIssueId(actionItem.id);

        settingFunctions.fetchJiraIntegration().then((jirIntegration) => {
            if (jirIntegration.projectIdsMap && Object.keys(jirIntegration.projectIdsMap).length > 0) {
                setJiraProjectMaps(jirIntegration.projectIdsMap);
                setProjId(Object.keys(jirIntegration.projectIdsMap)[0]);
            } else {
                setProjId(jirIntegration.projId);
                setIssueType(jirIntegration.issueType);
            }
            setModalActive(true);
        }).catch((error) => {
            setToast(true, true, 'Error fetching Jira integration settings.');
        });
    };

    function JiraLogoClickable({ itemId, item }) {
        const handleJiraClick = (e) => {
            e.stopPropagation();
            const actionItem = item || actionItems.find(ai => ai.id === itemId);

            if (actionItem) {
                handleJiraIntegration(actionItem);
            } else {
                setToast(true, true, 'Action item not found.');
            }
        };

        return (
            <span
                style={{ cursor: 'pointer', display: 'inline-block' }}
                onClick={handleJiraClick}
                title={window?.JIRA_INTEGRATED ? 'Create Jira Ticket' : 'Integrate Jira'}
            >
                <Avatar size="extraSmall" shape="square" source="/public/logo_jira.svg" />
            </span>
        );
    }

    const buildTruncatableCell = (tooltip, text, maxWidth = '400px', fontWeight = 'regular') => (
        <Box style={{ minWidth: 0, flex: 1, maxWidth }}>
            <div style={{
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis'
            }}>
                <TooltipText
                    tooltip={tooltip}
                    text={text}
                    textProps={{ variant: 'bodyMd', fontWeight: fontWeight }}
                />
            </div>
        </Box>
    );

    // Helper function to create action item object
    const createActionItem = (id, priority, title, description, team, effort, count, actionItemType) => ({
        id,
        priority: <Badge status={priority === 'P1' ? 'critical' : 'attention'}>{priority}</Badge>,
        priorityComp: <Badge status={priority === 'P1' ? 'critical' : 'attention'}>{priority}</Badge>,
        actionItem: buildTruncatableCell(title, title, '400px', 'medium'),
        team: buildTruncatableCell(team, team, '200px'),
        effort: buildTruncatableCell(effort, effort, '100px'),
        whyItMatters: buildTruncatableCell(description, description),
        displayName: title,
        title: title,
        description: description,
        actionItemType: actionItemType, // Add this field
        actions: (
            <VerticalStack align="center">
                <HorizontalStack gap="2" align="center">
                    <JiraLogoClickable itemId={id} item={{
                        id,
                        title,
                        description,
                        actionItemType
                    }} />
                </HorizontalStack>
            </VerticalStack>
        ),
        count: count
    });

    function getActions(item) {
        return [{
            items: [
                {
                    content: item.ticket || 'Create ticket',
                    icon: item.ticket ? undefined : ChevronDownMinor,
                    url: '#',
                    external: true,
                    onAction: () => { },
                    customComponent: <JiraLogoClickable itemId={item.id} item={item} />
                }
            ]
        }];
    }

    const fetchData = async () => {
        const endTimestamp = func.timeNow();
        const startTimestamp = func.timeNow() - 3600 * 24 * 7;

        let sensitiveDataCount = 0;
        try {
            const apiStats = await api.fetchApiStats(startTimestamp, endTimestamp);
            const countMapResp = await observeApi.fetchCountMapOfApis();
            const SensitiveAndUnauthenticatedValue = await api.fetchSensitiveAndUnauthenticatedValue();
            const highRiskThirdPartyValue = await api.fetchHighRiskThirdPartyValue();
            const shadowApisValue = await api.fetchShadowApisValue();

            if (countMapResp && typeof countMapResp.totalApisCount === 'number') {
                sensitiveDataCount = countMapResp.totalApisCount;
            }

            if (apiStats && apiStats.apiStatsEnd && apiStats.apiStatsStart) {
                const apiStatsEnd = apiStats.apiStatsEnd;
                const apiStatsStart = apiStats.apiStatsStart;

                const highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
                    .filter(([score]) => parseInt(score) > 3)
                    .reduce((total, [, count]) => total + count, 0);

                const unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;
                const currentThirdParty = apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0;
                const previousThirdParty = apiStatsStart.accessTypeMap?.THIRD_PARTY || 0;
                const thirdPartyDiff = currentThirdParty - previousThirdParty;

                const dynamicActionItems = [
                    createActionItem(
                        '1',
                        'P1',
                        `${highRiskCount} APIs with risk score more than 3`,
                        "Creates multiple attack vectors for malicious actors",
                        "Security Team",
                        "Medium",
                        highRiskCount,
                        ACTION_ITEM_TYPES.HIGH_RISK_APIS
                    ),
                    createActionItem(
                        '2',
                        'P1',
                        `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                        "Violates data privacy regulations (GDPR, CCPA) and risks customer trust",
                        "Development",
                        "Medium",
                        sensitiveDataCount,
                        ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS
                    ),
                    createActionItem(
                        '3',
                        'P1',
                        `${unauthenticatedCount} APIs lacking proper authentication controls`,
                        "Easy target for unauthorized access and data exfiltration",
                        "Security Team",
                        "Medium",
                        unauthenticatedCount,
                        ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS
                    ),
                    createActionItem(
                        '4',
                        'P2',
                        `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                        "New integrations may introduce unvetted security risks",
                        "Integration Team",
                        "Low",
                        Math.max(0, thirdPartyDiff),
                        ACTION_ITEM_TYPES.THIRD_PARTY_APIS
                    ),
                    createActionItem(
                        '5',
                        'P1',
                        `${highRiskThirdPartyValue} External APIs with high risk scores requiring attention`,
                        "Supply chain vulnerabilities that can compromise entire systems",
                        "Security Team",
                        "High",
                        highRiskThirdPartyValue,
                        ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY
                    ),
                    createActionItem(
                        '6',
                        'P2',
                        `${shadowApisValue} Undocumented APIs discovered in the system`,
                        "Unmonitored attack surface with unknown security posture",
                        "API Governance",
                        "High",
                        shadowApisValue,
                        ACTION_ITEM_TYPES.SHADOW_APIS
                    )
                ];

                const filteredActionItems = dynamicActionItems.filter(item => item.count > 0);
                setActionItems(filteredActionItems);

                if (SensitiveAndUnauthenticatedValue > 0) {
                    setCriticalCardData({
                        id: 'p0-critical',
                        priority: 'P0',
                        title: `${SensitiveAndUnauthenticatedValue} APIs returning sensitive data without encryption or proper authorization`,
                        description: 'Potential data breach with regulatory and compliance implications',
                        team: 'Security & Development',
                        effort: 'High',
                        count: SensitiveAndUnauthenticatedValue,
                        actionItemType: ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH
                    });
                } else {
                    setCriticalCardData(null);
                }
            } else {
                setActionItems([]);
            }
        } catch (error) {
            setActionItems([]);
        }
    };

    useEffect(() => {
        fetchData();
    }, []);

    const handleSaveJiraAction = () => {
        if (!selectedActionItem) {
            setToast(true, true, 'No action item selected.');
            return;
        }

        setToast(true, false, 'Please wait while we create your Jira ticket.');
        setModalActive(false);

        const title = selectedActionItem.title || selectedActionItem.displayName || 'Action Item';
        const description = selectedActionItem.description || '';
        const actionItemType = selectedActionItem.actionItemType || '';

        issuesApi.createGeneralJiraTicket({
            title: title,
            description: description,
            projId,
            issueType,
            actionItemType: actionItemType 
        }).then((res) => {
            if (res?.errorMessage) {
                setToast(true, true, res?.errorMessage);
            } else {
                setToast(true, false, 'Jira ticket created and stored successfully.');

                fetchData();
            }
        }).catch((error) => {
            setToast(true, true, 'Error creating Jira ticket.');
        });
    };

    return (
        <VerticalStack gap="5">
            {criticalCardData && (
                <Box maxWidth="300px">
                    <ActionItemCard
                        cardObj={criticalCardData}
                        onButtonClick={handleJiraIntegration}
                    />
                </Box>
            )}

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
                handleSaveAction={handleSaveJiraAction}
                jiraProjectMaps={jiraProjectMaps}
                setProjId={setProjId}
                setIssueType={setIssueType}
                projId={projId}
                issueType={issueType}
                issueId={issueId}
                isAzureModal={false}
            />
        </VerticalStack>
    );
};