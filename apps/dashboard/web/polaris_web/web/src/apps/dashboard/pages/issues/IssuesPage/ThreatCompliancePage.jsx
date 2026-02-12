import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState, useEffect, useCallback } from "react";
import func from "@/util/func";
import PersistStore from "../../../../main/PersistStore";
import { Button, Popover, Box, Avatar, Text, HorizontalStack, IndexFiltersMode, VerticalStack, Badge, Spinner } from "@shopify/polaris";
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter.jsx";
import { produce } from "immer";
import "./style.css"
import values from "@/util/values";
import { isMCPSecurityCategory, isGenAISecurityCategory, isAgenticSecurityCategory, isEndpointSecurityCategory, mapLabel, getDashboardCategory } from "../../../../main/labelHelper";
import threatDetectionApi from "../../threat_detection/api.js"
import SessionStore from "../../../../main/SessionStore"
import ShowListInBadge from "../../../components/shared/ShowListInBadge";
import { CellType } from "../../../components/tables/rows/GithubRow.js";
import SampleDetails from "../../threat_detection/components/SampleDetails";
import useTable from "../../../components/tables/TableContext.js";
import TableStore from "../../../components/tables/TableStore.js";
import transform from "../transform.js";
import useThreatReportDownload from "../../../hooks/useThreatReportDownload";
import { updateThreatFiltersStore } from "../../threat_detection/utils/threatFilters";

const getSortOptions = (category) => [
    { label: mapLabel('Number of endpoints', category), value: 'numberOfEndpoints asc', directionLabel: 'More', sortKey: 'numberOfEndpoints', columnIndex: 3 },
    { label: mapLabel('Number of endpoints', category), value: 'numberOfEndpoints desc', directionLabel: 'Less', sortKey: 'numberOfEndpoints', columnIndex: 3 },
    { label: 'Discovered time', value: 'creationTime asc', directionLabel: 'Newest', sortKey: 'creationTime', columnIndex: 6 },
    { label: 'Discovered time', value: 'creationTime desc', directionLabel: 'Oldest', sortKey: 'creationTime', columnIndex: 6 },
];

const resourceName = {
    singular: 'threat',
    plural: 'threats',
};

const getCompliances = () => {
    const isDemoAccount = func.isDemoAccount();
    const isMCP = isMCPSecurityCategory();
    const isGenAiSecurity = isGenAISecurityCategory();
    const isAgenticSecurity = isAgenticSecurityCategory();

    if (isDemoAccount && (isMCP || isAgenticSecurity || isGenAiSecurity)) {
        return ["OWASP Agentic", "OWASP LLM", "NIST AI Risk Management Framework","MITRE ATLAS","CIS Controls", "CMMC", "CSA CCM", "Cybersecurity Maturity Model Certification (CMMC)", "FISMA", "FedRAMP", "GDPR", "HIPAA", "ISO 27001", "NIST 800-171", "NIST 800-53", "PCI DSS", "SOC 2", "OWASP"];
    }

    return ["CIS Controls", "CMMC", "CSA CCM", "Cybersecurity Maturity Model Certification (CMMC)", "FISMA", "FedRAMP", "GDPR", "HIPAA", "ISO 27001", "NIST 800-171", "NIST 800-53", "PCI DSS", "SOC 2", "OWASP"];
};

const allCompliances = getCompliances();

const initialEventState = {
    currentRefId: '',
    rowDataList: [],
    moreInfoData: {},
    currentEventId: '',
    currentEventStatus: '',
    currentJiraTicketUrl: ''
};

function ThreatCompliancePage() {
    const [loading, setLoading] = useState(true);
    const [moreActions, setMoreActions] = useState(false);
    const [complianceView, setComplianceView] = useState('SOC 2');
    const [currentTab, setCurrentTab] = useState('active');
    const [selected, setSelected] = useState(0);
    const [showDetails, setShowDetails] = useState(false);
    const [eventState, setEventState] = useState(initialEventState);
    const [detailsLoading, setDetailsLoading] = useState(false);
    const [tableKey, setTableKey] = useState(false);
    const [currentAppliedFilters, setCurrentAppliedFilters] = useState({});
    const [threatFiltersLoading, setThreatFiltersLoading] = useState(true);

    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);
    const setThreatFiltersMap = SessionStore((state) => state.setThreatFiltersMap);

    const { tabsInfo, selectItems } = useTable();
    const dashboardCategory = getDashboardCategory();

    // Fetch threat filters data on mount
    useEffect(() => {
        const fetchThreatFiltersData = async () => {
            setThreatFiltersLoading(true);
            try {
                // Fetch threat filter templates
                const resp = await threatDetectionApi.fetchFilterYamlTemplate();
                const templates = Array.isArray(resp?.templates) ? resp.templates : [];
                
                if (templates.length === 0) {
                    setThreatFiltersLoading(false);
                    return;
                }

                // Update threat filters store
                updateThreatFiltersStore(templates);

                // Fetch and merge compliance info
                const complianceResp = await threatDetectionApi.fetchThreatComplianceInfos();
                if (complianceResp?.threatComplianceInfos && Array.isArray(complianceResp.threatComplianceInfos)) {
                    const threatComplianceMap = {};
                    complianceResp.threatComplianceInfos.forEach((compliance) => {
                        threatComplianceMap[compliance._id] = compliance;
                    });

                    const currentThreatFiltersMap = SessionStore.getState().threatFiltersMap || {};
                    const updatedThreatFiltersMap = { ...currentThreatFiltersMap };

                    Object.keys(updatedThreatFiltersMap).forEach((filterId) => {
                        const complianceKey = `threat_compliance/${filterId}.conf`;
                        const compliance = threatComplianceMap[complianceKey];

                        if (compliance) {
                            updatedThreatFiltersMap[filterId] = {
                                ...updatedThreatFiltersMap[filterId],
                                compliance: {
                                    mapComplianceToListClauses: compliance.mapComplianceToListClauses
                                }
                            };
                        }
                    });

                    setThreatFiltersMap(updatedThreatFiltersMap);
                }
            } catch (e) {
                console.error(`Failed to fetch threat filters data: ${e?.message}`);
            } finally {
                setThreatFiltersLoading(false);
            }
        };

        fetchThreatFiltersData();
    }, []);

    const resetResourcesSelected = () => {
        TableStore.getState().setSelectedItems([])
        selectItems([])
        setTableKey(!tableKey)
    };

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    };

    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    const { downloadThreatReport } = useThreatReportDownload({
        startTimestamp,
        endTimestamp,
        additionalFilters: currentAppliedFilters
    });

    // Only show session context features for Agentic Security (Argus) and Endpoint Security (Atlas), not for API Security
    const showSessionContext = isAgenticSecurityCategory() || isEndpointSecurityCategory();

    const headers = [
        {
            title: '',
            type: CellType.COLLAPSIBLE
        },
        {
            title: "Severity",
            text: "Severity",
            value: "severity"
        },
        {
            title: "Threat name",
            text: "Threat name",
            value: "issueName",
        },
        ...(showSessionContext ? [{
            title: "Detection Type",
            text: "Detection Type",
            value: "detectionType"
        }] : []),
        {
            title: "Detection Type",
            text: "Detection Type",
            value: "detectionType"
        },
        {
            title: mapLabel("Number of endpoints", dashboardCategory),
            text: mapLabel("Number of endpoints", dashboardCategory),
            value: "numberOfEndpoints",
            sortActive: true
        },
        {
            title: mapLabel("Domains", dashboardCategory),
            text: mapLabel("Domains", dashboardCategory),
            value: "domains"
        },
        {
            title: "Compliance",
            text: "Compliance",
            value: "compliance"
        },
        {
            title: "Discovered",
            text: "Discovered",
            value: "creationTime",
            sortActive: true
        },
        {
            value: 'collectionIds'
        },
    ];

    const tableTabs = [
        {
            content: 'Active',
            onAction: () => { setCurrentTab('active') },
            id: 'active',
            index: 0
        },
        {
            content: 'Under Review',
            onAction: () => { setCurrentTab('under_review') },
            id: 'under_review',
            index: 1
        },
        {
            content: 'Ignored',
            onAction: () => { setCurrentTab('ignored') },
            id: 'ignored',
            index: 2
        }
    ];

    const handleSelectedTab = (selectedIndex) => {
        setLoading(true);
        setSelected(selectedIndex);
        setTimeout(() => {
            setLoading(false);
        }, 200);
    };

    function calcFilteredThreatFilterIds(complianceView) {
        let ret = Object.entries(threatFiltersMap || {})
            .filter(([_, v]) => {
                return !!v.compliance?.mapComplianceToListClauses[complianceView]
            })
            .map(([k, _]) => k);
        return ret;
    }

    const handleDetailsVisibility = useCallback((visible) => {
        setShowDetails(visible);
        if (!visible) {
            setEventState(initialEventState);
            setDetailsLoading(false);
        }
    }, []);

    const handleThreatClick = useCallback(async (threatData) => {
        if (!threatData?.refId || !threatData?.eventType || !threatData?.actor || !threatData?.filterId) {
            return;
        }

        setShowDetails(true);
        setDetailsLoading(true);

        try {
            const payloadResponse = await threatDetectionApi.fetchMaliciousRequest(
                threatData.refId,
                threatData.eventType,
                threatData.actor,
                threatData.filterId
            );

            const maliciousPayloads = payloadResponse?.maliciousPayloadsResponses || [];

            setEventState({
                currentRefId: threatData.refId,
                rowDataList: maliciousPayloads,
                moreInfoData: {
                    url: threatData.url || '',
                    method: threatData.method || '',
                    apiCollectionId: threatData.apiCollectionId,
                    templateId: threatData.filterId,
                    sessionContext: threatData.sessionContext || ''
                },
                currentEventId: threatData.eventId || '',
                currentEventStatus: threatData.status || '',
                currentJiraTicketUrl: threatData.jiraTicketUrl || ''
            });
        } catch (error) {
            console.error('Error fetching threat details:', error);
            func.setToast(true, true, 'Failed to load threat details. Please try again.');
            handleDetailsVisibility(false);
        } finally {
            setDetailsLoading(false);
        }
    }, [handleDetailsVisibility]);

    const handleStatusUpdate = (newStatus) => {
        setEventState(prev => ({ ...prev, currentEventStatus: newStatus }));
    };

    const createThreatDataObject = (item) => ({
        refId: item?.refId,
        eventType: item?.eventType,
        actor: item?.actor,
        filterId: item?.filterId,
        url: item?.url,
        method: item?.method,
        apiCollectionId: item?.apiCollectionId,
        status: item?.status,
        eventId: item?.id,
        jiraTicketUrl: item?.jiraTicketUrl,
        sessionContext: item?.sessionContext || ''
    });


    const convertToThreatTableData = (rawData, threatFiltersMapWithTestName) => {
        return rawData.map((threat, idx) => {
            const key = `${threat.id.testSubCategory}|${threat.severity}|${idx}`
            let totalCompliance = (threat.compliance || []).length
            let maxShowCompliance = 2
            let badge = totalCompliance > maxShowCompliance ? <Badge size="extraSmall">+{totalCompliance - maxShowCompliance}</Badge> : null

            // Extract detection type from metadata
            const detectionType = threat.detectionType || 'SINGLE_PROMPT';
            const isSessionBased = detectionType === 'SESSION_CONTEXT';

            return {
                key: key,
                id: threat.urls.map((urlObj) => JSON.stringify({ eventId: urlObj.threatData?.eventId || "" })),
                severity: <div className={`badge-wrapper-${threat.severityType}`}>
                    <Badge size="small" key={idx}>{threat.severity}</Badge>
                </div>,
                issueName: threatFiltersMapWithTestName[threat.issueName]?.testName || threat.issueName,
                ...(showSessionContext && {
                    detectionType: (
                        <Badge status={isSessionBased ? 'info' : 'default'}>
                            {isSessionBased ? 'Session' : 'Single Prompt'}
                        </Badge>
                    )
                }),
                numberOfEndpoints: threat.numberOfEndpoints,
                domains: (
                    <ShowListInBadge
                        itemsArr={threat.domains}
                        maxItems={1}
                        maxWidth={"250px"}
                        status={"new"}
                        itemWidth={"200px"}
                    />
                ),
                compliance: totalCompliance > 0 ? (
                    <HorizontalStack wrap={false} gap={1}>
                        {threat.compliance.slice(0, maxShowCompliance).map((x, i) =>
                            <Avatar key={i} source={func.getComplianceIcon(x)} shape="square" size="extraSmall" />
                        )}
                        <Box>{badge}</Box>
                    </HorizontalStack>
                ) : (
                    <Text color="subdued">-</Text>
                ),
                creationTime: func.prettifyEpoch(threat.creationTime),
                collapsibleRow: transform.getThreatCollapsibleRow(threat.urls.map(urlObj => ({
                    method: urlObj.method,
                    url: urlObj.url,
                    threatData: urlObj.threatData
                })), handleThreatClick)
            }
        })
    }

    async function fetchData(
        sortKey,
        sortOrder,
        skip,
        _limit,
        filtersObj,
        _filterOperators,
        queryValue
    ) {
        setLoading(true);

        try {
            let sourceIpsFilter = [];
            let apiCollectionIdsFilter = [];
            let matchingUrlFilter = [];
            let typeFilter = [];
            let latestAttack = [];
            let hostFilter = [];
            let severityFilter = [];
            let detectionTypeFilter = [];

            let latestApiOrigRegex = queryValue.length > 3 ? queryValue : "";

            if (filtersObj?.actor) {
                sourceIpsFilter = filtersObj?.actor;
            }
            if (filtersObj?.apiCollectionId) {
                apiCollectionIdsFilter = filtersObj?.apiCollectionId;
            }
            if (filtersObj?.url) {
                matchingUrlFilter = filtersObj?.url;
            }
            if (filtersObj?.type) {
                typeFilter = filtersObj?.type;
            }
            if (filtersObj?.host) {
                hostFilter = filtersObj?.host;
            }
            if (filtersObj?.severity) {
                severityFilter = filtersObj?.severity;
            }
            if (filtersObj?.detectionType) {
                detectionTypeFilter = filtersObj?.detectionType;
            }

            // Update current applied filters for report export
            const appliedFilters = {};
            if (sourceIpsFilter.length > 0) appliedFilters.actor = sourceIpsFilter;
            if (matchingUrlFilter.length > 0) appliedFilters.url = matchingUrlFilter;
            if (hostFilter.length > 0) appliedFilters.host = hostFilter;
            if (typeFilter.length > 0) appliedFilters.type = typeFilter;
            if (latestAttack.length > 0) appliedFilters.latestAttack = latestAttack;
            if (severityFilter.length > 0) appliedFilters.severity = severityFilter;
            setCurrentAppliedFilters(appliedFilters);

            const sort = sortKey && sortOrder ? { [sortKey]: sortOrder === -1 ? 1 : -1 } : {};
            const successfulBool = true;

            const res = await threatDetectionApi.fetchSuspectSampleData(
                skip,
                sourceIpsFilter,
                apiCollectionIdsFilter,
                matchingUrlFilter,
                typeFilter,
                sort,
                startTimestamp,
                endTimestamp,
                latestAttack,
                50,
                currentTab.toUpperCase(),
                successfulBool,
                'THREAT',
                hostFilter,
                latestApiOrigRegex,
                [],
                true
            );

            const total = res?.total || 0;
            const uniqueThreatsMap = new Map();

            (res?.maliciousEvents || []).forEach(item => {
                const threatPolicy = threatFiltersMap[item?.filterId];
                if (!threatPolicy) return;

                const complianceData = threatPolicy?.compliance?.mapComplianceToListClauses || {};
                const availableCompliances = Object.keys(complianceData);
                const complianceViewUpper = complianceView.toUpperCase();
                const hasCompliance = availableCompliances.some(c => {
                    const cUpper = c.toUpperCase();
                    return cUpper === complianceViewUpper ||
                           cUpper.includes(complianceViewUpper) ||
                           complianceViewUpper.includes(cUpper);
                });

                if (!hasCompliance) return;

                if (severityFilter.length > 0 && !severityFilter.includes(threatPolicy.severity)) {
                    return;
                }

                // Parse sessionContext for detection type filtering
                let sessionData = {};
                try {
                    if (item?.sessionContext) {
                        sessionData = typeof item.sessionContext === 'string'
                            ? JSON.parse(item.sessionContext)
                            : item.sessionContext;
                    }
                } catch (e) {
                    console.error('[ThreatCompliancePage] Error parsing sessionContext:', e);
                }

                const itemDetectionType = sessionData?.detectionType || 'SINGLE_PROMPT';
                if (detectionTypeFilter.length > 0 && !detectionTypeFilter.includes(itemDetectionType)) {
                    return;
                }

                const key = `${item?.filterId}|${threatPolicy.severity || 'HIGH'}`;

                // Get domain from collectionsMap, fall back to host field, then "-"
                const getDomain = (item) => {
                    if (item?.apiCollectionId && collectionsMap[item.apiCollectionId]) {
                        return collectionsMap[item.apiCollectionId];
                    }
                    if (item?.host) {
                        return item.host;
                    }
                    return "-";
                };

                if (!uniqueThreatsMap.has(key)) {
                    uniqueThreatsMap.set(key, {
                        id: { testSubCategory: item?.filterId },
                        severity: func.toSentenceCase(threatPolicy.severity || 'HIGH'),
                        compliance: Object.keys(complianceData),
                        severityType: threatPolicy.severity || 'HIGH',
                        issueName: item?.filterId,
                        category: 'Threat',
                        numberOfEndpoints: 1,
                        creationTime: item?.timestamp || Math.floor(Date.now() / 1000),
                        domains: [getDomain(item)],
                        urls: [{
                            method: item?.method,
                            url: item?.url,
                            threatData: createThreatDataObject(item)
                        }],
                        isThreat: true,
                        sessionContext: sessionData,
                        detectionType: sessionData?.detectionType || 'SINGLE_PROMPT'
                    });
                } else {
                    const existingThreat = uniqueThreatsMap.get(key);
                    const domain = getDomain(item);
                    if (!existingThreat.domains.includes(domain)) {
                        existingThreat.domains.push(domain);
                    }
                    existingThreat.urls.push({
                        method: item?.method,
                        url: item?.url,
                        threatData: createThreatDataObject(item)
                    });
                    existingThreat.numberOfEndpoints += 1;
                    if (item?.timestamp && item.timestamp > existingThreat.creationTime) {
                        existingThreat.creationTime = item.timestamp;
                    }
                }
            });

            let threatItem = Array.from(uniqueThreatsMap.values());
            const sortedThreatItem = threatItem.sort((a, b) => {
                let aValue, bValue;
                let order;

                if (sortKey === 'numberOfEndpoints') {
                    aValue = a.numberOfEndpoints;
                    bValue = b.numberOfEndpoints;
                    order = sortOrder === -1 ? 1 : -1;
                    if (aValue !== bValue) {
                        return aValue < bValue ? -1 * order : 1 * order;
                    }
                } else if (sortKey === 'creationTime') {
                    aValue = a.creationTime;
                    bValue = b.creationTime;
                    order = sortOrder === -1 ? 1 : -1;
                    if (aValue !== bValue) {
                        return aValue < bValue ? -1 * order : 1 * order;
                    }
                } else {
                    const severityOrder = func.getAktoSeverities();
                    aValue = severityOrder.indexOf(a.severityType);
                    bValue = severityOrder.indexOf(b.severityType);

                    if (aValue !== bValue) {
                        return aValue < bValue ? -1 : 1;
                    }

                    return b.creationTime - a.creationTime;
                }

                return b.creationTime - a.creationTime;
            });

            const threatFiltersMapWithTestName = Object.fromEntries(
                Object.entries(threatFiltersMap || {}).map(([key, value]) => [
                    key,
                    { ...value, testName: value.name, superCategory: { shortName: 'Threat' } }
                ])
            );

            const threatTableData = convertToThreatTableData(sortedThreatItem, threatFiltersMapWithTestName);

            setLoading(false);
            return { value: threatTableData, total: threatTableData.length > 0 ? total : 0 };
        } catch (error) {
            console.error("Error fetching threat data:", error);
            setLoading(false);
            return { value: [], total: 0 };
        }
    }

    async function fillFilters() {
        const res = await threatDetectionApi.fetchFiltersThreatTable();
        let urlChoices = (res?.urls || []).map((x) => {
            const url = x || "/";
            return { label: url, value: x };
        });
        let ipChoices = (res?.ips || []).map((x) => {
            return { label: x, value: x };
        });

        let hostChoices = [];
        if (res?.hosts && Array.isArray(res.hosts) && res.hosts.length > 0) {
            hostChoices = res.hosts
                .filter(host => host && host.trim() !== '' && host !== '-')
                .map(x => ({ label: x, value: x }));
        }

        return [
            {
                key: 'severity',
                label: 'Severity',
                title: 'Severity',
                choices: [
                    { label: 'Critical', value: 'CRITICAL' },
                    { label: 'High', value: 'HIGH' },
                    { label: 'Medium', value: 'MEDIUM' },
                    { label: 'Low', value: 'LOW' }
                ]
            },
            ...(showSessionContext ? [{
                key: 'detectionType',
                label: 'Detection Type',
                title: 'Detection Type',
                choices: [
                    { label: 'Session Context', value: 'SESSION_CONTEXT' },
                    { label: 'Single Prompt', value: 'SINGLE_PROMPT' },
                ]
            }] : []),
            {
                key: 'detectionType',
                label: 'Detection Type',
                title: 'Detection Type',
                choices: [
                    { label: 'Session Context', value: 'SESSION_CONTEXT' },
                    { label: 'Single Prompt', value: 'SINGLE_PROMPT' },
                ]
            },
            {
                key: "actor",
                label: "Actor",
                title: "Actor",
                choices: ipChoices,
            },
            {
                key: "url",
                label: "URL",
                title: "URL",
                choices: urlChoices,
            },
            {
                key: 'host',
                label: "Host",
                title: "Host",
                choices: hostChoices,
            },
            {
                key: 'type',
                label: "Type",
                title: "Type",
                choices: [
                    { label: 'Rule based', value: 'Rule-Based' },
                    { label: 'Anomaly', value: 'Anomaly' },
                ],
            }
        ];
    }

    const [filters, setFilters] = useState([]);

    useEffect(() => {
        fillFilters().then(f => setFilters(f));
    }, [threatFiltersMap]);

    function disambiguateLabel(key, value) {
        switch (key) {
            case "apiCollectionId":
                return func.convertToDisambiguateLabelObj(value, collectionsMap, 2);
            default:
                return func.convertToDisambiguateLabelObj(value, null, 2);
        }
    }

    const onSelectCompliance = (compliance) => {
        setComplianceView(compliance);
        setMoreActions(false);
    };

    const infoItems = [
        {
            title: "Threat Detection",
            description: "View all detected threats mapped to compliance standards.",
        },
        {
            title: "Compliance Mapping",
            description: "See which threats are mapped to specific compliance requirements.",
        }
    ];

    const handleBulkOperation = async (selectedIds, operation, newState = null) => {
        const actionLabels = {
            ignore: { ing: 'ignoring', ed: 'ignored' },
            delete: { ing: 'deleting', ed: 'deleted' },
            markForReview: { ing: 'marking for review', ed: 'marked for review' },
            removeFromReview: { ing: 'removing from review', ed: 'removed from review' }
        };

        const label = actionLabels[operation];

        if (!selectedIds || selectedIds.length === 0) {
            func.setToast(true, true, 'No events selected');
            return;
        }

        let eventIds = [];
        selectedIds.forEach(id => {
            try {
                const parsed = JSON.parse(id);
                if (parsed.eventId) {
                    eventIds.push(parsed.eventId);
                } else if (Array.isArray(parsed)) {
                    parsed.forEach(item => {
                        if (item.eventId) eventIds.push(item.eventId);
                    });
                }
            } catch (e) {
                console.error('Error parsing ID:', e);
            }
        });

        if (eventIds.length === 0) {
            func.setToast(true, true, 'No valid events selected');
            return;
        }

        try {
            let response;
            if (operation === 'delete') {
                response = await threatDetectionApi.deleteMaliciousEvents({ eventIds });
            } else {
                response = await threatDetectionApi.updateMaliciousEventStatus({ eventIds, status: newState });
            }

            const isSuccess = operation === 'delete' ? response?.deleteSuccess : response?.updateSuccess;
            const count = operation === 'delete' ? response?.deletedCount : response?.updatedCount;

            if (isSuccess) {
                func.setToast(true, false, `${count || eventIds.length} event${eventIds.length === 1 ? '' : 's'} ${label.ed} successfully`);
                resetResourcesSelected();
                setLoading(true);
                setTimeout(() => setLoading(false), 500);
            } else {
                func.setToast(true, true, `Failed to ${operation} events`);
            }
        } catch (error) {
            func.setToast(true, true, `Error ${label.ing} events`);
        }
    };

    const promotedBulkActions = (selectedResources) => {
        if (!selectedResources || selectedResources.length === 0) return [];

        let items = [];
        if (Array.isArray(selectedResources)) {
            selectedResources.forEach(resource => {
                if (typeof resource === 'string') {
                    items.push(resource);
                } else if (Array.isArray(resource)) {
                    items.push(...resource);
                }
            });
        }

        const eventCount = items.length;
        const eventText = `${eventCount} selected event${eventCount === 1 ? '' : 's'}`;

        const createAction = (label, actionType, includeWarning = false) => {
            const warningText = includeWarning
                ? '\n\nNote: Future events matching these URL and Attack Type combinations will be automatically blocked.'
                : '';

            return {
                content: `${label} ${eventText}`,
                onAction: () => {
                    const message = actionType === 'delete'
                        ? `Are you sure you want to permanently delete ${eventText}? This action cannot be undone.`
                        : `Are you sure you want to ${label.toLowerCase()} ${eventText}?${warningText}`;

                    const handlers = {
                        markForReview: () => handleBulkOperation(items, 'markForReview', 'UNDER_REVIEW'),
                        ignore: () => handleBulkOperation(items, 'ignore', 'IGNORED'),
                        removeFromReview: () => handleBulkOperation(items, 'removeFromReview', 'ACTIVE'),
                        reactivate: () => handleBulkOperation(items, 'removeFromReview', 'ACTIVE'),
                        delete: () => handleBulkOperation(items, 'delete')
                    };

                    func.showConfirmationModal(message, label, handlers[actionType]);
                }
            };
        };

        const actions = [];
        const tabActions = {
            'active': [
                { label: 'Mark for Review', type: 'markForReview' },
                { label: 'Ignore', type: 'ignore', warning: true }
            ],
            'under_review': [
                { label: 'Remove from Review', type: 'removeFromReview' },
                { label: 'Ignore', type: 'ignore', warning: true }
            ],
            'ignored': [
                { label: 'Reactivate', type: 'reactivate' }
            ]
        };

        const currentTabActions = tabActions[currentTab] || [];
        currentTabActions.forEach(({ label, type, warning }) => {
            actions.push(createAction(label, type, warning));
        });

        actions.push(createAction('Delete', 'delete'));

        return actions;
    }

    const threatFiltersCount = threatFiltersMap ? Object.keys(threatFiltersMap).length : 0;
    const key = startTimestamp + endTimestamp + currentTab + complianceView + threatFiltersCount;

    return (
        <PageWithMultipleCards
            title={
                <HorizontalStack gap={4}>
                    <TitleWithInfo
                        titleText={"Threat"}
                        tooltipContent={"View detected threats mapped to compliance standards such as OWASP, PCI-DSS, SOC 2, and more."}
                    />
                    <Popover
                        active={moreActions}
                        activator={(
                            <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                                <Box>
                                    <HorizontalStack gap={2}>
                                        <Avatar source={func.getComplianceIcon(complianceView)} shape="square" size="extraSmall" />
                                        <Text>{complianceView}</Text>
                                    </HorizontalStack>
                                </Box>
                            </Button>
                        )}
                        autofocusTarget="first-node"
                        onClose={() => { setMoreActions(false) }}
                        preferredAlignment="right"
                    >
                        <Popover.Pane fixed>
                            <Popover.Section>
                                <VerticalStack gap={"2"}>
                                    {allCompliances.map((compliance, idx) => (
                                        <Button
                                            key={idx}
                                            textAlign="left"
                                            plain
                                            onClick={() => onSelectCompliance(compliance)}
                                            removeUnderline
                                        >
                                            <Box>
                                                <HorizontalStack gap={2}>
                                                    <Avatar source={func.getComplianceIcon(compliance)} shape="square" size="extraSmall" />
                                                    <Text>{compliance}</Text>
                                                </HorizontalStack>
                                            </Box>
                                        </Button>
                                    ))}
                                </VerticalStack>
                            </Popover.Section>
                        </Popover.Pane>
                    </Popover>
                </HorizontalStack>
            }
            isFirstPage={true}
            components={
                threatFiltersLoading ? [
                    <Box key="loading" padding="10">
                        <HorizontalStack align="center">
                            <Spinner size="large" />
                        </HorizontalStack>
                    </Box>
                ] : (!threatFiltersMap || Object.keys(threatFiltersMap).length === 0) ? [
                    <EmptyScreensLayout
                        key="emptyScreen"
                        iconSrc={"/public/alert_hexagon.svg"}
                        headingText={"No threats yet!"}
                        description={"There are currently no threats detected in your APIs."}
                        infoItems={infoItems}
                        infoTitle={"Threat Compliance"}
                    />
                ] : [
                    <GithubServerTable
                        key={key}
                        onRowClick={() => { }}
                        pageLimit={50}
                        headers={headers}
                        resourceName={resourceName}
                        sortOptions={getSortOptions(dashboardCategory)}
                        disambiguateLabel={disambiguateLabel}
                        loading={loading}
                        fetchData={fetchData}
                        filters={filters}
                        selectable={true}
                        promotedBulkActions={promotedBulkActions}
                        isMultipleItemsSelected={true}
                        headings={headers}
                        useNewRow={true}
                        condensedHeight={true}
                        tableTabs={tableTabs}
                        selected={selected}
                        onSelect={handleSelectedTab}
                        mode={IndexFiltersMode.Default}
                        hideQueryField={true}
                    />,
                    <SampleDetails
                        key={`threat-sample-details-${eventState.currentRefId || 'default'}`}
                        title={"Attacker payload"}
                        showDetails={showDetails}
                        setShowDetails={handleDetailsVisibility}
                        data={eventState.rowDataList}
                        moreInfoData={eventState.moreInfoData}
                        threatFiltersMap={threatFiltersMap}
                        eventId={eventState.currentEventId}
                        eventStatus={eventState.currentEventStatus}
                        onStatusUpdate={handleStatusUpdate}
                        jiraTicketUrl={eventState.currentJiraTicketUrl}
                        loading={detailsLoading}
                    />
                ]
            }
            secondaryActions={
                <HorizontalStack gap={2}>
                    <Button primary onClick={downloadThreatReport}>
                        Export Threat Report
                    </Button>
                    <DateRangeFilter
                        initialDispatch={currDateRange}
                        dispatch={(dateObj) => dispatchCurrDateRange({
                            type: "update",
                            period: dateObj.period,
                            title: dateObj.title,
                            alias: dateObj.alias
                        })}
                    />
                </HorizontalStack>
            }
        />
    );
}

export default ThreatCompliancePage