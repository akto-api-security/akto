import { VerticalStack, Box, HorizontalStack } from '@shopify/polaris';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import FlyLayout from '../../../components/layouts/FlyLayout';
import JiraTicketCreationModal from '../../../components/shared/JiraTicketCreationModal';
import JiraTicketDisplay from '../../../components/shared/JiraTicketDisplay';
import ActionItemsTable from './ActionItemsTable';
import CriticalActionItemCard from './CriticalActionItemCard';
import issuesApi from '../../issues/api';
import { createActionItem as createActionItemUtil } from '../../../../../util/createActionItem';
import settingsModule from '../../settings/module';
import ActionItemDetails from './ActionItemDetails';
import { fetchAllActionItemsApiInfo } from './actionItemsTransform'; 
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const actionItemsHeaders = [
    { title: '', value: 'priority', type: 'text' },
    { title: 'Action Item', value: 'actionItem', type: 'text', maxWidth: '180px' },
    { title: 'Description', value: 'descriptionCol', type: 'text', maxWidth: '220px' },
    { title: 'Team', value: 'team', type: 'text', maxWidth: '110px' },
    { title: 'Efforts', value: 'effort', type: 'text', maxWidth: '90px' },
    { title: 'Why It Matters', value: 'whyItMatters', type: 'text', maxWidth: '200px' },
    { title: 'Action', value: 'actions', type: 'action', maxWidth: '70px' }
];

const ACTION_ITEM_TYPES = {
    HIGH_RISK_APIS: 'HIGH_RISK_APIS',
    SENSITIVE_DATA_ENDPOINTS: 'SENSITIVE_DATA_ENDPOINTS',
    UNAUTHENTICATED_APIS: 'UNAUTHENTICATED_APIS',
    THIRD_PARTY_APIS: 'THIRD_PARTY_APIS',
    HIGH_RISK_THIRD_PARTY: 'HIGH_RISK_THIRD_PARTY',
    SHADOW_APIS: 'SHADOW_APIS',
    CRITICAL_SENSITIVE_UNAUTH: 'CRITICAL_SENSITIVE_UNAUTH',
    VULNERABLE_APIS: 'VULNERABLE_APIS',
    ENHANCE_TESTING_COVERAGE: 'ENHANCE_TESTING_COVERAGE',
    ENABLE_CONTINUOUS_TESTING: 'ENABLE_CONTINUOUS_TESTING',
    ADDRESS_MISCONFIGURED_TESTS: 'ADDRESS_MISCONFIGURED_TESTS',
    BROKEN_AUTHENTICATION_ISSUES: 'BROKEN_AUTHENTICATION_ISSUES',
    FREQUENTLY_VULNERABLE_ENDPOINTS: 'FREQUENTLY_VULNERABLE_ENDPOINTS',
    BUILD_REMEDIATION_PLAYBOOKS: 'BUILD_REMEDIATION_PLAYBOOKS',
    VERBOSE_ERROR_MESSAGES: 'VERBOSE_ERROR_MESSAGES',
    MISSING_SECURITY_HEADERS: 'MISSING_SECURITY_HEADERS',
    TOP_PUBLIC_EXPOSED_APIS: 'TOP_PUBLIC_EXPOSED_APIS',
};

const JIRA_INTEGRATION_URL = "/dashboard/settings/integrations/jira";

export const ActionItemsContent = ({ actionItemsData, onCountChange }) => {
    const navigate = useNavigate();
    const [actionItems, setActionItems] = useState([]);
    const [criticalCardsData, setCriticalCardsData] = useState([]);
    const [modalActive, setModalActive] = useState(false);
    const [projId, setProjId] = useState('');
    const [issueType, setIssueType] = useState('');
    const [jiraProjectMaps, setJiraProjectMaps] = useState({});
    const [selectedActionItem, setSelectedActionItem] = useState(null);
    const [jiraTicketUrlMap, setJiraTicketUrlMap] = useState({});
    const [showFlyout, setShowFlyout] = useState(false);
    const [selectedItem, setSelectedItem] = useState(null);
    const [allApiInfo, setAllApiInfo] = useState(null);
    const [apiInfoLoading, setApiInfoLoading] = useState(false);

    useEffect(() => {
        const fetchApiInfo = async () => {
            setApiInfoLoading(true);
            try {
                const apiData = await fetchAllActionItemsApiInfo();
                setAllApiInfo(apiData);
            } catch (error) {
                console.error('Error fetching API info:', error);
                setAllApiInfo(null);
            } finally {
                setApiInfoLoading(false);
            }
        };

        fetchApiInfo();
    }, []); 

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
            console.error("Error fetching Jira integration settings:", e);
        }
    };

    const createActionItem = (
        id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration, whyItMatters
    ) => {
        return createActionItemUtil(
            id, priority, title, description, team, effort, count, actionItemType, jiraTicketUrlMap, handleJiraIntegration, whyItMatters
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

    const handleCardClick = (item) => {
        setSelectedItem(item);
        setShowFlyout(true);
    };
    
    const handleRowClick = (item) => {
        setSelectedItem(item);
        setShowFlyout(true);
    };

    useEffect(() => {
        if (!actionItemsData) return;
        const {
            highRiskCount,
            sensitiveDataCount,
            unauthenticatedCount,
            thirdPartyDiff,
            highRiskThirdPartyCount,
            shadowApisCount,
            sensitiveAndUnauthenticatedCount,
            jiraTicketUrlMap: ticketMap,
            notTestedApiCount,
            onlyOnceTestedApiCount,
            vulnerableApiCount,
            misConfiguredTestsCount,
            brokenAuthIssuesCount,
            highValueIssuesCount,
            urlsByIssuesTotalCount,
            vemVulnerableApisCount,
            mhhVulnerableApisCount,
        } = actionItemsData;
        setJiraTicketUrlMap(ticketMap || {});

        const items = [
            createActionItem('1', 'P1', `High-risk ${mapLabel("APIs", getDashboardCategory())}`, `${highRiskCount} ${mapLabel("APIs", getDashboardCategory())} with risk score more than 3`, "Security Team", "Medium", highRiskCount, ACTION_ITEM_TYPES.HIGH_RISK_APIS, jiraTicketUrlMap, handleJiraIntegration, "Creates multiple attack vectors for malicious actors"),

            createActionItem('2', 'P1', `${mapLabel("APIs", getDashboardCategory())} returning sensitive data`, `${sensitiveDataCount} ${mapLabel("APIs", getDashboardCategory())} exposing PII or confidential information`, "Development", "Medium", sensitiveDataCount, ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS, jiraTicketUrlMap, handleJiraIntegration, "Violates data privacy regulations (GDPR, CCPA) and risks customer trust"),

            createActionItem('3', 'P1', 'Missing authentication methods', `${unauthenticatedCount} ${mapLabel("APIs", getDashboardCategory())} lacking proper authentication controls`, "Security Team", "Medium", unauthenticatedCount, ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS, jiraTicketUrlMap, handleJiraIntegration, "Easy target for unauthorized access and data exfiltration"),

            createActionItem('4', 'P1', `Recently active third-party ${mapLabel("APIs", getDashboardCategory())}`, `${Math.max(0, thirdPartyDiff)} Third-party ${mapLabel("APIs", getDashboardCategory())} frequently invoked or newly integrated within last 7 days`, "Integration Team", "Low", Math.max(0, thirdPartyDiff), ACTION_ITEM_TYPES.THIRD_PARTY_APIS, jiraTicketUrlMap, handleJiraIntegration, "New integrations may introduce unvetted security risks"),

            createActionItem('5', 'P1', `High-risk third-party ${mapLabel("APIs", getDashboardCategory())}`, `${highRiskThirdPartyCount} External ${mapLabel("APIs", getDashboardCategory())} with high risk scores requiring attention`, "Security Team", "High", highRiskThirdPartyCount, ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY, jiraTicketUrlMap, handleJiraIntegration, "Supply chain vulnerabilities that can compromise entire systems"),

            createActionItem('6', 'P1', `Shadow ${mapLabel("APIs", getDashboardCategory())}`, `${shadowApisCount} Undocumented ${mapLabel("APIs", getDashboardCategory())} discovered in the system`, "API Governance", "High", shadowApisCount, ACTION_ITEM_TYPES.SHADOW_APIS, jiraTicketUrlMap, handleJiraIntegration, "Unmonitored attack surface with unknown security posture"),

            createActionItem('7', 'P1', 'Enhance testing coverage', `${notTestedApiCount} test executions on a single ${mapLabel("API", getDashboardCategory())} indicate the need for broader coverage across other ${mapLabel("APIs", getDashboardCategory())}`, "QA / Security", "Medium", notTestedApiCount, ACTION_ITEM_TYPES.ENHANCE_TESTING_COVERAGE, jiraTicketUrlMap, handleJiraIntegration, `Helps detect issues across all ${mapLabel("APIs", getDashboardCategory())} and prevents blind spots`),

            createActionItem('8', 'P1', 'Enable continuous testing', `${onlyOnceTestedApiCount} ${mapLabel("APIs", getDashboardCategory())} are currently tested only once — consider moving them to scheduled testing`, "QA Team", "Medium", onlyOnceTestedApiCount, ACTION_ITEM_TYPES.ENABLE_CONTINUOUS_TESTING, jiraTicketUrlMap, handleJiraIntegration, `Ensures ongoing protection as ${mapLabel("API", getDashboardCategory())} behavior and usage evolve`),

            createActionItem('9', 'P2', 'Address misconfigured tests', `${misConfiguredTestsCount} misconfigured tests detected during scans`, "QA Team", "Medium", misConfiguredTestsCount, ACTION_ITEM_TYPES.ADDRESS_MISCONFIGURED_TESTS, jiraTicketUrlMap, handleJiraIntegration, "Improves test coverage and reduces missed vulnerabilities"),

            createActionItem('10', 'P1', 'Remediate broken authentication issues', `${brokenAuthIssuesCount} authentication-related issues found across tested APIs`, "Development", "High", brokenAuthIssuesCount, ACTION_ITEM_TYPES.BROKEN_AUTHENTICATION_ISSUES, jiraTicketUrlMap, handleJiraIntegration, "Prevents unauthorized access and ensures proper session handling")
            ,
            createActionItem('11', 'P2', 'Remediate frequently vulnerable endpoints', `${highValueIssuesCount} endpoint surfaced repeatedly in ${mapLabel('test results', getDashboardCategory())} - track and prioritize for remediation`, "Security Team", "Low", highValueIssuesCount, ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS, jiraTicketUrlMap, handleJiraIntegration, "Focuses remediation on APIs with recurring issues"),

            createActionItem('12', 'P2', 'Build remediation playbooks for common issues', `${urlsByIssuesTotalCount} common vulnerability types detected that can benefit from standardized remediation steps`, "Security & DevOps", "Low", urlsByIssuesTotalCount, ACTION_ITEM_TYPES.BUILD_REMEDIATION_PLAYBOOKS, jiraTicketUrlMap, handleJiraIntegration, "Reduces response time and improves fix consistency across teams"),

            createActionItem('13', 'P1', 'Investigate verbose error messages or stack traces', `${vemVulnerableApisCount} APIs leaking debug information in responses.`, "Development", "Medium", vemVulnerableApisCount, ACTION_ITEM_TYPES.VERBOSE_ERROR_MESSAGES, jiraTicketUrlMap, handleJiraIntegration, "Can disclose sensitive implementation details to attackers"),

            createActionItem('14', 'P2', 'Verify security headers on API responses', `${mhhVulnerableApisCount} Ensure headers like HSTS, X-Frame-Options, etc. are present.`, "DevOps / Security", "Low", mhhVulnerableApisCount, ACTION_ITEM_TYPES.MISSING_SECURITY_HEADERS, jiraTicketUrlMap, handleJiraIntegration, "Hardens APIs against clickjacking and other browser-based attacks")
        ];

        const filteredItems = items.filter(item => item.count > 0);
        setActionItems(filteredItems);
        let totalCount = filteredItems.length;
        let criticalCardsDataToSet = [];
        const topPublicExposedCount = 3; 
        
        if (sensitiveAndUnauthenticatedCount > 0) {
            totalCount += 1;
            criticalCardsDataToSet.push({
                id: 'p0-critical',
                priority: 'P0',
                staticTitle: 'Sensitive data without proper controls',
                title: 'Sensitive data without proper controls',
                description: `${sensitiveAndUnauthenticatedCount} APIs returning sensitive data without encryption or proper authorization`,
                team: 'Security & Development',
                effort: 'High',
                count: sensitiveAndUnauthenticatedCount,
                actionItemType: ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH
            });
        }
        
        if (vulnerableApiCount > 0) {
            totalCount += 1;
            criticalCardsDataToSet.push({
                id: 'p0-vulnerable',
                priority: 'P0',
                staticTitle: 'Patch critical vulnerabilities immediately',
                title: 'Patch critical vulnerabilities immediately',
                description: `${vulnerableApiCount} API flagged with critical severity issues that need immediate attention`,
                team: 'Security & Development',
                effort: 'High',
                count: vulnerableApiCount,
                actionItemType: ACTION_ITEM_TYPES.VULNERABLE_APIS
            });
        }

        totalCount += 1;
        criticalCardsDataToSet.push({
            id: 'p0-top-public',
            priority: 'P0',
            staticTitle: 'Top 3 ' + mapLabel('APIs', getDashboardCategory()) + ' with public exposure',
            title: 'Top 3 ' + mapLabel('APIs', getDashboardCategory()) + ' with public exposure',
            description: 'Top 3 ' + mapLabel('APIs', getDashboardCategory()) + ' exposing maximum sensitive data',
            team: 'Security & Development',
            effort: 'High',
            count: topPublicExposedCount,
            whyItMatters: 'Violates data privacy regulations (GDPR, CCPA) and risks customer trust',
            actionItemType: ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS
        });
        
        setCriticalCardsData(criticalCardsDataToSet);
        if (onCountChange) {
            onCountChange(totalCount);
        }
    }, [actionItemsData, jiraTicketUrlMap, onCountChange]);

    const handleSaveJiraAction = (issueId, labels) => {
        if (!selectedActionItem) {
            navigate(JIRA_INTEGRATION_URL);
            return;
        }

        setModalActive(false);
        const { title, displayName, description, actionItemType } = selectedActionItem;
        // Use labels parameter if provided, otherwise fall back to state
        const labelsToUse = labels !== undefined ? labels : labelsText;

        issuesApi.createGeneralJiraTicket({
            title: title || displayName || 'Action Item',
            description: description || '',
            projId,
            issueType,
            actionItemType: actionItemType || '',
            labels: labelsToUse || ''
        }).then((res) => {
            if (res?.errorMessage) {
                navigate(JIRA_INTEGRATION_URL);
            } else {
                // No need to refetch data here as it's passed as a prop
            }
        }).catch((error) => {
            console.error("Error creating Jira ticket:", error);
            navigate(JIRA_INTEGRATION_URL);
        });
    };

    return (
        <VerticalStack gap="5">
            {criticalCardsData.length > 0 && (
                <HorizontalStack gap="4" align="start">
                    {criticalCardsData.map((cardData, index) => (
                        <CriticalActionItemCard
                            key={cardData.id}
                            cardObj={cardData}
                            onButtonClick={handleJiraIntegration}
                            jiraTicketUrlMap={jiraTicketUrlMap}
                            onCardClick={handleCardClick}
                        />
                    ))}
                </HorizontalStack>
            )}

            <Box maxWidth="100%" style={{ overflowX: 'hidden' }}>
                <ActionItemsTable
                    data={actionItems}
                    headers={actionItemsHeaders}
                    getActions={getActions}
                    jiraTicketUrlMap={jiraTicketUrlMap}
                    onRowClick={handleRowClick}
                />
            </Box>

            <FlyLayout
                show={showFlyout}
                setShow={setShowFlyout}
                title="Action item details"
                components={[
                    <ActionItemDetails
                        item={selectedItem}
                        jiraTicketUrlMap={jiraTicketUrlMap}
                        onJiraButtonClick={handleJiraIntegration}
                        allApiInfo={allApiInfo} 
                        apiInfoLoading={apiInfoLoading} 
                    />
                ]}
            />

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