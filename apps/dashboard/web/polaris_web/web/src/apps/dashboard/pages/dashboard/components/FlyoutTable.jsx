import React, { useCallback, useMemo } from 'react';
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import { EmptySearchResult } from "@shopify/polaris";
import LocalStore from '@/apps/main/LocalStorageStore';
import api from '../api';
import { labelMap } from '../../../../main/labelHelperMap';
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import observeApi from '../../observe/api';
import { Box, Badge, Text, Divider } from '@shopify/polaris';
import TooltipText from '@/apps/dashboard/components/shared/TooltipText';
import transform from "@/apps/dashboard/pages/observe/transform";
import func from "@/util/func";
import PersistStore from "@/apps/main/PersistStore";
import { getDashboardCategory, mapLabel } from '../../../../main/labelHelper';

const PAGE_SIZE = 10;

// the list the backend serves for each action item (PostureActionItemsService.Type); the "top sensitive"
// card has no paged list, it is a short top list fetched on its own
const SERVER_ITEM_TYPE = {
    HIGH_RISK_APIS: 'HIGH_RISK',
    SENSITIVE_DATA_ENDPOINTS: 'SENSITIVE',
    UNAUTHENTICATED_APIS: 'UNAUTHENTICATED',
    THIRD_PARTY_APIS: 'THIRD_PARTY',
    HIGH_RISK_THIRD_PARTY: 'HIGH_RISK_THIRD_PARTY',
    SHADOW_APIS: 'SHADOW',
    CRITICAL_SENSITIVE_UNAUTH: 'SENSITIVE_UNAUTHENTICATED',
    ENHANCE_TESTING_COVERAGE: 'NOT_TESTED',
    ENABLE_CONTINUOUS_TESTING: 'ONLY_ONCE_TESTED',
    ADDRESS_MISCONFIGURED_TESTS: 'MISCONFIGURED',
    VULNERABLE_APIS: 'VULNERABLE',
    BROKEN_AUTHENTICATION_ISSUES: 'BROKEN_AUTH',
    FREQUENTLY_VULNERABLE_ENDPOINTS: 'MULTIPLE_ISSUES',
    APIS_WITH_MULTIPLE_ISSUES: 'MULTIPLE_ISSUES',
    VERBOSE_ERROR_MESSAGES: 'VEM',
    MISSING_SECURITY_HEADERS: 'MHH',
};

const ACTION_ITEM_TYPES = {
    HIGH_RISK_APIS: 'HIGH_RISK_APIS',
    SENSITIVE_DATA_ENDPOINTS: 'SENSITIVE_DATA_ENDPOINTS',
    UNAUTHENTICATED_APIS: 'UNAUTHENTICATED_APIS',
    THIRD_PARTY_APIS: 'THIRD_PARTY_APIS',
    HIGH_RISK_THIRD_PARTY: 'HIGH_RISK_THIRD_PARTY',
    SHADOW_APIS: 'SHADOW_APIS',
    CRITICAL_SENSITIVE_UNAUTH: 'CRITICAL_SENSITIVE_UNAUTH',
    ENHANCE_TESTING_COVERAGE: 'ENHANCE_TESTING_COVERAGE',
    ENABLE_CONTINUOUS_TESTING: 'ENABLE_CONTINUOUS_TESTING',
    ADDRESS_MISCONFIGURED_TESTS: 'ADDRESS_MISCONFIGURED_TESTS',
    VULNERABLE_APIS: 'VULNERABLE_APIS',
    BROKEN_AUTHENTICATION_ISSUES: 'BROKEN_AUTHENTICATION_ISSUES',
    FREQUENTLY_VULNERABLE_ENDPOINTS: 'FREQUENTLY_VULNERABLE_ENDPOINTS',
    APIS_WITH_MULTIPLE_ISSUES: 'APIS_WITH_MULTIPLE_ISSUES',
    VERBOSE_ERROR_MESSAGES: 'VERBOSE_ERROR_MESSAGES',
    MISSING_SECURITY_HEADERS: 'MISSING_SECURITY_HEADERS',
    TOP_PUBLIC_EXPOSED_APIS: 'TOP_PUBLIC_EXPOSED_APIS',
};

const getIssueLabel = (actionItemType) => {
    switch (actionItemType) {
        case ACTION_ITEM_TYPES.HIGH_RISK_APIS:
            return 'High Risk APIs';
        case ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS:
            return 'Sensitive Data';
        case ACTION_ITEM_TYPES.UNAUTHENTICATED_APIS:
            return 'Unauthenticated APIs';
        case ACTION_ITEM_TYPES.THIRD_PARTY_APIS:
            return 'Third-Party APIs';
        case ACTION_ITEM_TYPES.HIGH_RISK_THIRD_PARTY:
            return 'High-Risk Third-Party';
        case ACTION_ITEM_TYPES.SHADOW_APIS:
            return 'Shadow APIs';
        case ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH:
            return 'Sensitive, Unauthenticated';
        case ACTION_ITEM_TYPES.ENHANCE_TESTING_COVERAGE:
            return 'Not Tested APIs';
        case ACTION_ITEM_TYPES.ENABLE_CONTINUOUS_TESTING:
            return 'Only Once Tested APIs';
        case ACTION_ITEM_TYPES.ADDRESS_MISCONFIGURED_TESTS:
            return 'Misconfigured Tests';
        case ACTION_ITEM_TYPES.VULNERABLE_APIS:
            return 'Vulnerable APIs';
        case ACTION_ITEM_TYPES.BROKEN_AUTHENTICATION_ISSUES:
            return 'Broken Authentication Issues';
        case ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS:
            return 'Frequently Vulnerable Endpoints';
        case ACTION_ITEM_TYPES.VERBOSE_ERROR_MESSAGES:
            return 'Verbose Error Messages';
        case ACTION_ITEM_TYPES.MISSING_SECURITY_HEADERS:
            return 'Missing Security Headers';
        case ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS:
            return 'Sensitive Data';
        default:
            return '-';
    }
};

const extractHostname = (url) => {
    if (!url || url === '-') return '-';
    try {
        return new URL(url).hostname;
    } catch {
        return url.split('/')[0] || '-';
    }
};

const getAuthTypeDisplay = (authTypes) => {
    if (!authTypes) return '-';
    if (Array.isArray(authTypes)) {
        if (authTypes.length > 0 && Array.isArray(authTypes[0])) {
            return authTypes[0][0] || '-';
        }
        return authTypes[0] || '-';
    }
    return authTypes;
};

// One page of an action item's list, a page at a time as it is shown
function FlyoutTable({ actionItemType, count }) {
    const allCollections = PersistStore(state => state.allCollections);
    const dashboardCategory = PersistStore(state => state.dashboardCategory);
    const collectionIdToName = useMemo(() => func.mapCollectionIdToName(allCollections || []), [allCollections]);

    const resourceName = {
        singular: labelMap[dashboardCategory]["API endpoint"],
        plural: labelMap[dashboardCategory]["API endpoints"],
    };

    // sensitive params of the endpoints of this page
    const fetchSensitiveParams = async (urls) => {
        const sensitiveParamsMap = {};
        if (urls.length === 0) return sensitiveParamsMap;
        try {
            const resp = await observeApi.fetchSensitiveParamsForEndpoints(urls);
            resp?.data?.endpoints?.forEach(item => {
                if (!sensitiveParamsMap[item.url]) sensitiveParamsMap[item.url] = [];
                sensitiveParamsMap[item.url].push(item.subTypeString || item.subType?.name);
            });
        } catch (e) {
            console.error('Error fetching sensitive params for flyout page:', e);
        }
        return sensitiveParamsMap;
    };

    const transformApiRow = (api, index, sensitiveParamsMap) => {
        const url = api.id?.url || api.url || '-';
        const method = api.id?.method || api.method || '-';

        const issuesText = getIssueLabel(actionItemType);
        const hostnameText = extractHostname(url);

        const accessTypeText = (Array.isArray(api.apiAccessTypes) && api.apiAccessTypes.length > 0)
            ? api.apiAccessTypes.join(', ')
            : 'No access type';
        const authTypeText = getAuthTypeDisplay(api.allAuthTypesFound || api.actualAuthType);
        const sensitiveTypes = (sensitiveParamsMap[url] && sensitiveParamsMap[url].length > 0)
            ? Array.from(new Set(sensitiveParamsMap[url]))
            : [];
        const sensitiveParamsIcons = transform.prettifySubtypes(sensitiveTypes, false);
        const collectionId = api.apiCollectionId ?? api.id?.apiCollectionId;
        const collectionName = collectionId !== undefined ? (collectionIdToName[collectionId] || collectionId) : '-';

        const baseRow = {
            id: `${actionItemType}-${index}`,
            endpoint: <Box maxWidth="250px">
                    <GetPrettifyEndpoint url={url} method={method} />
                </Box>,
            riskScore: api.riskScore !== null && api.riskScore !== undefined ?
                <Badge status={transform.getStatus(api.riskScore)} size="small">{api.riskScore}</Badge> : '-',
            issues: <Box maxWidth="150px"><TooltipText tooltip={issuesText} text={issuesText} /></Box>,
            hostname: <Box maxWidth="120px"><TooltipText tooltip={hostnameText} text={hostnameText} /></Box>,
            accessType: <Box maxWidth="120px"><TooltipText tooltip={accessTypeText} text={accessTypeText} /></Box>,
            authType: <Box maxWidth="120px"><TooltipText tooltip={authTypeText} text={authTypeText} /></Box>,
            sensitiveParams: <Box maxWidth="200px">{sensitiveParamsIcons}</Box>,
            lastSeen: func.prettifyEpoch(api.lastSeen),
            discoveredAt: func.prettifyEpoch(api.discoveredTimestamp),
            lastTested: func.prettifyEpoch(api.lastTested),
            collection: <Box maxWidth="150px"><TooltipText tooltip={String(collectionName)} text={String(collectionName)} /></Box>,
        };

        if (actionItemType === ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS) {
            const subCategoryMap = LocalStore.getState().subCategoryMap || {};
            const issueLabels = Array.from(new Set((api.issueNames || []).map(n => subCategoryMap?.[n]?.testName || n).filter(Boolean)));
            // collapsible icon on the left, the issues of the api inside
            return {
                ...baseRow,
                name: `${method} ${url}`,
                collapsibleRow: (
                    <tr>
                        <td colSpan={'100%'}>
                            <Box padding="3">
                                {issueLabels.length > 0 ? (
                                    <Box>
                                        {issueLabels.map((label, i) => (
                                            <React.Fragment key={i}>
                                                {i > 0 && <Divider />}
                                                <Box padding="2">
                                                    <Text variant="bodyMd">{label}</Text>
                                                </Box>
                                            </React.Fragment>
                                        ))}
                                    </Box>
                                ) : (
                                    <Text variant="bodyMd" tone="subdued">No issues found</Text>
                                )}
                            </Box>
                        </td>
                    </tr>
                )
            };
        }

        return baseRow;
    };

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit) => {
        try {
            if (actionItemType === ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS) {
                // a short top list, not a paged one
                const resp = await observeApi.getSensitiveInfoForCollections('topSensitive');
                const items = resp?.sensitiveSubtypesInUrl || [];
                const sensitiveParamsMap = items.reduce((acc, item) => {
                    const url = item?.apiInfo?.id?.url || item?.apiInfo?.url;
                    const subTypes = Array.isArray(item?.subTypes) ? Array.from(new Set(item.subTypes)) : [];
                    if (url && subTypes.length > 0) acc[url] = subTypes;
                    return acc;
                }, {});
                return { value: items.map((x, i) => transformApiRow(x.apiInfo || {}, i, sensitiveParamsMap)), total: items.length };
            }

            const resp = await api.fetchPostureActionItems(SERVER_ITEM_TYPE[actionItemType], skip, limit);
            const apis = resp?.rows || [];
            const sensitiveParamsMap = await fetchSensitiveParams(apis.map(a => a.url));
            return {
                value: apis.map((a, i) => transformApiRow(a, skip + i, sensitiveParamsMap)),
                total: resp?.total >= 0 ? resp.total : 0,
            };
        } catch (error) {
            console.error('Error loading action item page:', error);
            return { value: [], total: 0 };
        }
    }, [actionItemType, collectionIdToName]);

    const getHeaders = () => {
        if (actionItemType === ACTION_ITEM_TYPES.FREQUENTLY_VULNERABLE_ENDPOINTS) {
            return [
                { type: CellType.COLLAPSIBLE, value: 'name', text: '', title: '' },
                { text: 'Endpoint', title: 'Endpoint', value: 'endpoint', maxWidth: '300px' },
                { text: 'Risk Score', title: 'Risk Score', value: 'riskScore', isText: CellType.TEXT },
                { text: 'Issues', title: 'Issues', value: 'issues', isText: CellType.TEXT, maxWidth: '150px' },
                { text: 'Hostname', title: 'Hostname', value: 'hostname', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Access Type', title: 'Access Type', value: 'accessType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Auth Type', title: 'Auth Type', value: 'authType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Sensitive Params', title: 'Sensitive Params', value: 'sensitiveParams', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Last Seen', title: 'Last Seen', value: 'lastSeen', isText: CellType.TEXT },
                { text: 'Discovered At', title: 'Discovered At', value: 'discoveredAt', isText: CellType.TEXT },
                { text: 'Last Tested', title: 'Last Tested', value: 'lastTested', isText: CellType.TEXT },
                { text: 'Collection', title: 'Collection', value: 'collection', isText: CellType.TEXT, maxWidth: '150px' },
            ];
        }
        if (actionItemType === ACTION_ITEM_TYPES.SENSITIVE_DATA_ENDPOINTS || actionItemType === ACTION_ITEM_TYPES.CRITICAL_SENSITIVE_UNAUTH || actionItemType === ACTION_ITEM_TYPES.TOP_PUBLIC_EXPOSED_APIS) {
            return [
                { text: mapLabel("Endpoint", getDashboardCategory()), title: mapLabel("Endpoint", getDashboardCategory()), value: 'endpoint', maxWidth: '300px' },
                { text: 'Sensitive Params', title: 'Sensitive Params', value: 'sensitiveParams', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Risk Score', title: 'Risk Score', value: 'riskScore', isText: CellType.TEXT },
                { text: 'Issues', title: 'Issues', value: 'issues', isText: CellType.TEXT, maxWidth: '150px' },
                { text: 'Hostname', title: 'Hostname', value: 'hostname', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Access Type', title: 'Access Type', value: 'accessType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Auth Type', title: 'Auth Type', value: 'authType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Last Seen', title: 'Last Seen', value: 'lastSeen', isText: CellType.TEXT },
                { text: 'Discovered At', title: 'Discovered At', value: 'discoveredAt', isText: CellType.TEXT },
                { text: 'Last Tested', title: 'Last Tested', value: 'lastTested', isText: CellType.TEXT },
                { text: 'Collection', title: 'Collection', value: 'collection', isText: CellType.TEXT, maxWidth: '150px' },
            ];
        } else {
            return [
                { text: mapLabel("Endpoint", getDashboardCategory()), title: mapLabel("Endpoint", getDashboardCategory()), value: 'endpoint', maxWidth: '300px' },
                { text: 'Risk Score', title: 'Risk Score', value: 'riskScore', isText: CellType.TEXT },
                { text: 'Issues', title: 'Issues', value: 'issues', isText: CellType.TEXT, maxWidth: '150px' },
                { text: 'Hostname', title: 'Hostname', value: 'hostname', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Access Type', title: 'Access Type', value: 'accessType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Auth Type', title: 'Auth Type', value: 'authType', isText: CellType.TEXT, maxWidth: '120px' },
                { text: 'Sensitive Params', title: 'Sensitive Params', value: 'sensitiveParams', isText: CellType.TEXT, maxWidth: '200px' },
                { text: 'Last Seen', title: 'Last Seen', value: 'lastSeen', isText: CellType.TEXT },
                { text: 'Discovered At', title: 'Discovered At', value: 'discoveredAt', isText: CellType.TEXT },
                { text: 'Last Tested', title: 'Last Tested', value: 'lastTested', isText: CellType.TEXT },
                { text: 'Collection', title: 'Collection', value: 'collection', isText: CellType.TEXT, maxWidth: '150px' },
            ];
        }
    };

    const headers = getHeaders();

    return (
        <GithubServerTable
            key={actionItemType}
            resourceName={resourceName}
            headers={headers}
            headings={headers}
            fetchData={fetchData}
            pageLimit={PAGE_SIZE}
            hidePageSizeSelector
            sortOptions={[]}
            filters={[]}
            selectable={false}
            useNewRow={true}
            condensedHeight={true}
            hideQueryField={true}
            skipUrlFilters
            emptyStateMarkup={<EmptySearchResult
                title={'No ' + mapLabel('APIs', getDashboardCategory()) + ' found'}
                description={'No ' + mapLabel('APIs', getDashboardCategory()) + ' found for this action item type.'}
                withIllustration
            />}
        />
    );
}

export default FlyoutTable;
