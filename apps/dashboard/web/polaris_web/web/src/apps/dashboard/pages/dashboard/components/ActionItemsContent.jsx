import { VerticalStack, Box, Badge, HorizontalStack, Icon, Avatar, Link, Text, Tag } from '@shopify/polaris'
import ActionItemDetails from './ActionItemDetails';
import ActionItemCard from './ActionItemCard';
import { EmailMajor, ChevronDownMinor, AlertMajor, ExternalMinor } from '@shopify/polaris-icons'
import { useEffect, useState, useMemo } from 'react'
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
    const [adminSettings, setAdminSettings] = useState(null);
    const [jiraTicketUrlMap, setJiraTicketUrlMap] = useState({});

    const [fetchedData, setFetchedData] = useState(null);

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

    const handleCardClick = (actionItem) => {
        setSelectedItem(actionItem);
        setShowFlyout(true);
    };

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

    const JiraCell = ({ actionItemType, actionItemObj }) => {
        const jiraTicketUrl = jiraTicketUrlMap[actionItemType];
        const jiraKey = jiraTicketUrl && jiraTicketUrl.length > 0 ? jiraTicketUrl.split('/').pop() : "";

        if (jiraKey) {
            return (
                <Tag
                    onClick={e => {
                        e.stopPropagation();
                        window.open(jiraTicketUrl, '_blank');
                    }}
                >
                    <HorizontalStack gap="1">
                        <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
                        <Text color="base">{jiraKey}</Text>
                    </HorizontalStack>
                </Tag>
            );
        }

        return (
            <div
                onClick={e => {
                    e.stopPropagation();
                    handleJiraIntegration(actionItemObj);
                }}
                style={{ cursor: 'pointer' }}
                title="Create Jira Ticket"
            >
                <Avatar size="extraSmall" shape="round" source="/public/logo_jira.svg" />
            </div>
        );
    };

    const createActionItem = (id, priority, title, description, team, effort, count, actionItemType) => {
        const actionItemObj = {
            id,
            title,
            description,
            actionItemType,
            team,
            effort,
            count
        };

        return {
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
            actionItemType: actionItemType,
            actions: (
                <HorizontalStack gap="2" align="start">
                    <JiraCell
                        actionItemType={actionItemType}
                        actionItemObj={actionItemObj}
                    />
                    <Box minWidth="16px" />
                </HorizontalStack>
            ),
            count: count,
            actionItemObj: actionItemObj
        };
    };

    function getActions(item) {
        const jiraTicketUrl = jiraTicketUrlMap[item?.actionItemType];
        return [{
            items: [
                {
                    content: jiraTicketUrl ? 'View Jira Ticket' : 'Create Jira Ticket',
                    icon: jiraTicketUrl ? ExternalMinor : undefined,
                    url: jiraTicketUrl ? jiraTicketUrl : undefined,
                    external: Boolean(jiraTicketUrl),
                    onAction: jiraTicketUrl ? undefined : () => handleJiraIntegration(item.actionItemObj || item),
                }
            ]
        }];
    }

    const fetchAllData = async () => {
        const endTimestamp = func.timeNow();
        const startTimestamp = func.timeNow() - 3600 * 24 * 7;
        try {
            const [
                apiStats,
                countMapResp,
                SensitiveAndUnauthenticatedValue,
                highRiskThirdPartyValue,
                shadowApisValue,
                AdminSettings
            ] = await Promise.all([
                api.fetchApiStats(startTimestamp, endTimestamp),
                observeApi.fetchCountMapOfApis(),
                api.fetchSensitiveAndUnauthenticatedValue(),
                api.fetchHighRiskThirdPartyValue(),
                api.fetchShadowApisValue(),
                api.fetchAdminSettings()
            ]);

            setAdminSettings(AdminSettings);
            setJiraTicketUrlMap(AdminSettings?.accountSettings?.jiraTicketUrlMap || {});
            setFetchedData({ apiStats, countMapResp, SensitiveAndUnauthenticatedValue, highRiskThirdPartyValue, shadowApisValue });

        } catch (error) {
            console.error("Error fetching action items data:", error);
            setToast(true, true, 'Failed to fetch action items.');
            setActionItems([]);
        }
    };

    useEffect(() => {
        fetchAllData();
    }, []);

    useEffect(() => {
        if (!fetchedData) return;

        const { apiStats, countMapResp, SensitiveAndUnauthenticatedValue, highRiskThirdPartyValue, shadowApisValue } = fetchedData;

        const sensitiveDataCount = countMapResp?.totalApisCount || 0;

        if (apiStats?.apiStatsEnd && apiStats?.apiStatsStart) {
            const { apiStatsEnd, apiStatsStart } = apiStats;

            const highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
                .filter(([score]) => parseInt(score) > 3)
                .reduce((total, [, count]) => total + count, 0);

            const unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;
            const thirdPartyDiff = (apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0) - (apiStatsStart.accessTypeMap?.THIRD_PARTY || 0);

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


            setActionItems(dynamicActionItems.filter(item => item.count > 0));

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

    }, [fetchedData]);

    const handleSaveJiraAction = () => {
        if (!selectedActionItem) {
            setToast(true, true, 'No action item selected.');
            return;
        }

        setModalActive(false);
        const { title, displayName, description, actionItemType } = selectedActionItem;

        issuesApi.createGeneralJiraTicket({
            title: title || displayName || 'Action Item',
            description: description || '',
            projId,
            issueType,
            actionItemType: actionItemType || ''
        }).then((res) => {
            if (res?.errorMessage) {
                setToast(true, true, res.errorMessage);
            } else {
                setToast(true, false, 'Jira ticket created.');
                fetchAllData();
            }
        }).catch((error) => {
            setToast(true, true, 'Error creating Jira ticket.');
        });
    };

    const ActionItemCardWrapper = ({ cardObj, onButtonClick }) => {
        return (
            <div onClick={() => handleCardClick(cardObj)} style={{ cursor: 'pointer' }}>
                <ActionItemCard
                    cardObj={cardObj}
                    onButtonClick={() => onButtonClick(cardObj)}
                    jiraTicketUrlMap={jiraTicketUrlMap}
                />
            </div>
        );
    };

    return (
        <VerticalStack gap="5">
            {criticalCardData && (
                <Box maxWidth="300px">
                    <ActionItemCardWrapper
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