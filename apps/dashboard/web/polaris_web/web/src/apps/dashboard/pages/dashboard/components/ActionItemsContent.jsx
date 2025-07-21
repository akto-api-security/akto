import { VerticalStack, Box } from '@shopify/polaris';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import FlyLayout from '../../../components/layouts/FlyLayout';
import GridRows from '../../../components/shared/GridRows';
import JiraTicketCreationModal from '../../../components/shared/JiraTicketCreationModal';
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';
import ActionItemsTable from './ActionItemsTable';
import CriticalActionItemCard from './CriticalActionItemCard';
import issuesApi from '../../issues/api';
import { createActionItem as createActionItemUtil } from '../../../../../util/createActionItem';
import settingsModule from '../../settings/module'; 

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

export const ActionItemsContent = ({ actionItemsData, onCountChange }) => {
    const navigate = useNavigate();
    const [actionItems, setActionItems] = useState([]);
    const [criticalCardData, setCriticalCardData] = useState(null);
    const [modalActive, setModalActive] = useState(false);
    const [projId, setProjId] = useState('');
    const [issueType, setIssueType] = useState('');
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});
    const [selectedActionItem, setSelectedActionItem] = useState(null);
    const [jiraTicketUrlMap, setJiraTicketUrlMap] = useState({});

    const handleJiraIntegration = async (actionItem) => {
        const integrated = window.JIRA_INTEGRATED === 'true'
        if (!integrated) {
            navigate(JIRA_INTEGRATION_URL);
            return;
        }
        setSelectedActionItem(actionItem);
        try {
            const jirIntegration = await settingsModule.fetchJiraIntegration();
            if (jirIntegration.projectIdsMap && Object.keys(jirIntegration.projectIdsMap).length > 0) {
                setJiraProjectMaps(jirIntegration.projectIdsMap);
                setProjId(Object.keys(jirIntegration.projectIdsMap)[0]);
            } else {
                setProjId(jirIntegration.projId);
                setIssueType(jirIntegration.issueType);
            }
            setModalActive(true);
        } catch (e) {
        }
    };

    const createActionItem = (
        id, priority, title, description, team, effort, count, actionItemType
    ) => {
        return createActionItemUtil(
            id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration
        );
    };

    const getActions = (item) => {
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
    };

    useEffect(() => {
        if (!actionItemsData) return;
        const {
            highRiskCount,
            sensitiveDataCount,
            unauthenticatedCount,
            thirdPartyDiff,
            highRiskThirdPartyValue,
            shadowApisValue,
            SensitiveAndUnauthenticatedValue,
            jiraTicketUrlMap: ticketMap
        } = actionItemsData;
        setJiraTicketUrlMap(ticketMap || {});
        
        const items = [
            createActionItem('1', 'P1', `${highRiskCount} APIs with risk score more than 3`,
                "Creates multiple attack vectors for malicious actors", "Security Team", "Medium", highRiskCount, ACTION_ITEM_TYPES.HIGH_RISK_APIS),

            createActionItem('2', 'P1', `${sensitiveDataCount} Endpoints exposing PII or confidential information`,
                "Violates data privacy regulations (GDPR, CCPA) and risks customer trust", "Development", "Medium", sensitiveDataCount, ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS),

            createActionItem('3', 'P1', `${unauthenticatedCount} APIs lacking proper authentication controls`,
                "Easy target for unauthorized access and data exfiltration", "Security Team", "Medium", unauthenticatedCount, ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS),

            createActionItem('4', 'P2', `${Math.max(0, thirdPartyDiff)} Third-party APIs frequently invoked or newly integrated within last 7 days`,
                "New integrations may introduce unvetted security risks", "Integration Team", "Low", Math.max(0, thirdPartyDiff), ACTION_ITEM_TYPES.THIRD_PARTY_APIS),

            createActionItem('5', 'P1', `${highRiskThirdPartyValue} External APIs with high risk scores requiring attention`,
                "Supply chain vulnerabilities that can compromise entire systems", "Security Team", "High", highRiskThirdPartyValue, ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY),

            createActionItem('6', 'P2', `${shadowApisValue} Undocumented APIs discovered in the system`,
                "Unmonitored attack surface with unknown security posture", "API Governance", "High", shadowApisValue, ACTION_ITEM_TYPES.SHADOW_APIS)
        ];

        const filteredItems = items.filter(item => item.count > 0);
        setActionItems(filteredItems);
        let totalCount = filteredItems.length;
        if (SensitiveAndUnauthenticatedValue > 0) {
            totalCount += 1;
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
        if (onCountChange) {
            onCountChange(totalCount);
        }
    }, [actionItemsData, jiraTicketUrlMap, onCountChange]);

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
                // No need to refetch data here as it's passed as a prop
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