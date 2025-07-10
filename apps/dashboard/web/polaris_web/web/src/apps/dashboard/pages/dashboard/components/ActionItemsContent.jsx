import { VerticalStack, Box } from '@shopify/polaris'
import { ExternalMinor } from '@shopify/polaris-icons'
import { useEffect, useState } from 'react'
import FlyLayout from '../../../components/layouts/FlyLayout'
import GridRows from '../../../components/shared/GridRows'
import JiraTicketCreationModal from '../../../components/shared/JiraTicketCreationModal'
import issuesApi from '../../issues/api';
import ActionItemsTable from './ActionItemsTable';
import CriticalActionItemCard from './CriticalActionItemCard';
import { useNavigate } from 'react-router-dom';
import { handleJiraIntegration as handleJiraIntegrationUtil } from '../../../../../util/handleJiraIntegration'
import { createActionItem as createActionItemUtil } from '../../../../../util/createActionItem';
import { fetchAllData as fetchAllDataUtil } from '../../../../../util/fetchAllData';
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';

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

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";
const PRIORITY_P1 = 'P1';
const PRIORITY_P2 = 'P2';
const PRIORITY_P0 = 'P0';
const CRITICAL_CARD_ID = 'p0-critical';

export const ActionItemsContent = () => {
    const navigate = useNavigate();
    const [actionItems, setActionItems] = useState([]);
    const [criticalCardData, setCriticalCardData] = useState(null);
    const [modalActive, setModalActive] = useState(false);
    const [projId, setProjId] = useState('');
    const [issueType, setIssueType] = useState('');
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});
    const [selectedActionItem, setSelectedActionItem] = useState(null);
    const [jiraTicketUrlMap, setJiraTicketUrlMap] = useState({});
    const [fetchedData, setFetchedData] = useState(null);

    const handleJiraIntegration = (actionItem) => {
        handleJiraIntegrationUtil(
            actionItem,
            navigate,
            setSelectedActionItem,
            setJiraProjectMaps,
            setProjId,
            setIssueType,
            setModalActive
        );
    };

    const createActionItem = (
        id, priority, title, description, team, effort, count, actionItemType
    ) => {
        return createActionItemUtil(
            id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration
        );
    };

    function getActions(item) {
        const actionSource = item?.actionItemObj || item;
        const jiraTicketUrl = jiraTicketUrlMap[actionSource?.actionItemType];
        const jiraKey = jiraTicketUrl?.split('/').pop() || "";

        return [{
            items: [{
                content: (
                    <JiraTicketDisplay
                        jiraTicketUrl={jiraTicketUrl}
                        jiraKey={jiraKey}
                        onButtonClick={() => handleJiraIntegration(actionSource)}
                        ariaLabel={jiraTicketUrl ? `View Jira ticket ${jiraKey}` : 'Create Jira ticket'}
                    />
                ),
                onAction: jiraTicketUrl
                    ? () => window.open(jiraTicketUrl, '_blank')
                    : () => handleJiraIntegration(actionSource),
                accessibilityLabel: jiraTicketUrl ? `View Jira ticket ${jiraKey}` : 'Create Jira ticket',
            }]
        }];
    }


    const fetchAllData = async () => {
        try {
            await fetchAllDataUtil(setJiraTicketUrlMap, setFetchedData, setActionItems);
        } catch (error) {
            console.error('Failed to fetch action items:', error);
        }
    };

    useEffect(() => {
        fetchAllData();
    }, []);

    useEffect(() => {
        if (!fetchedData) return;

        const {
            apiStats,
            countMapResp,
            SensitiveAndUnauthenticatedValue,
            highRiskThirdPartyValue,
            shadowApisValue
        } = fetchedData;

        const sensitiveDataCount = countMapResp?.totalApisCount || 0;

        if (!(apiStats?.apiStatsEnd && apiStats?.apiStatsStart)) {
            setActionItems([]);
            return;
        }

        const { apiStatsEnd, apiStatsStart } = apiStats;

        const highRiskCount = Object.entries(apiStatsEnd.riskScoreMap || {})
            .filter(([score]) => parseInt(score) > 3)
            .reduce((total, [, count]) => total + count, 0);

        const unauthenticatedCount = apiStatsEnd.authTypeMap?.UNAUTHENTICATED || 0;
        const thirdPartyDiff = (apiStatsEnd.accessTypeMap?.THIRD_PARTY || 0) - (apiStatsStart.accessTypeMap?.THIRD_PARTY || 0);

        const items = [
            createActionItem('1', PRIORITY_P1, `${highRiskCount} APIs with risk score more than 3`,
                "Creates multiple attack vectors for malicious actors", "Security Team", "Medium", highRiskCount, ACTION_ITEM_TYPES.HIGH_RISK_APIS),

            createActionItem('2', PRIORITY_P1, `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                "Violates data privacy regulations (GDPR, CCPA) and risks customer trust", "Development", "Medium", sensitiveDataCount, ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS),

            createActionItem('3', PRIORITY_P1, `${unauthenticatedCount} APIs lacking proper authentication controls`,
                "Easy target for unauthorized access and data exfiltration", "Security Team", "Medium", unauthenticatedCount, ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS),

            createActionItem('4', PRIORITY_P2, `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                "New integrations may introduce unvetted security risks", "Integration Team", "Low", Math.max(0, thirdPartyDiff), ACTION_ITEM_TYPES.THIRD_PARTY_APIS),

            createActionItem('5', PRIORITY_P1, `${highRiskThirdPartyValue} External APIs with high risk scores requiring attention`,
                "Supply chain vulnerabilities that can compromise entire systems", "Security Team", "High", highRiskThirdPartyValue, ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY),

            createActionItem('6', PRIORITY_P2, `${shadowApisValue} Undocumented APIs discovered in the system`,
                "Unmonitored attack surface with unknown security posture", "API Governance", "High", shadowApisValue, ACTION_ITEM_TYPES.SHADOW_APIS)
        ];

        setActionItems(items.filter(item => item.count > 0));

        if (SensitiveAndUnauthenticatedValue > 0) {
            setCriticalCardData({
                id: CRITICAL_CARD_ID,
                priority: PRIORITY_P0,
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

    }, [fetchedData, jiraTicketUrlMap]);

    const handleSaveJiraAction = () => {
        if (!selectedActionItem) {
            navigate(JIRA_INTEGRATION_URL);
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
                navigate(JIRA_INTEGRATION_URL);
            } else {
                fetchAllData();
            }
        }).catch(() => {
            navigate(JIRA_INTEGRATION_URL);
        });
    };

    return (
        <VerticalStack gap="5">
            {criticalCardData && (
                <CriticalActionItemCard
                    cardObj={criticalCardData}
                    onButtonClick={handleJiraIntegration}
                    jiraTicketUrlMap={jiraTicketUrlMap}
                />
            )}

            <Box maxWidth="100%" style={{ overflowX: 'hidden' }}>
                <ActionItemsTable
                    data={actionItems}
                    headers={actionItemsHeaders}
                    getActions={getActions}
                    jiraTicketUrlMap={jiraTicketUrlMap}
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
                isAzureModal={false}
            />
        </VerticalStack>
    );
};