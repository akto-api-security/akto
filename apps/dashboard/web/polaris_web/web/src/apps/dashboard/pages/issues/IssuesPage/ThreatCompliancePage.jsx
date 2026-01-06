import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState, useEffect, useCallback } from "react";
import func from "@/util/func";
import PersistStore from "../../../../main/PersistStore";
import { Button, Popover, Box, Avatar, Text, HorizontalStack, IndexFiltersMode, VerticalStack, Badge, Link } from "@shopify/polaris";
import EmptyScreensLayout from "../../../components/banners/EmptyScreensLayout";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import DateRangeFilter from "../../../components/layouts/DateRangeFilter.jsx";
import { produce } from "immer";
import "./style.css"
import values from "@/util/values";
import { isMCPSecurityCategory, isGenAISecurityCategory, isAgenticSecurityCategory } from "../../../../main/labelHelper";
import threatDetectionApi from "../../threat_detection/api.js"
import SessionStore from "../../../../main/SessionStore"
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import ShowListInBadge from "../../../components/shared/ShowListInBadge";
import { CellType } from "../../../components/tables/rows/GithubRow.js";
import SampleDetails from "../../threat_detection/components/SampleDetails";

const sortOptions = [
    { label: 'Severity', value: 'severity asc', directionLabel: 'Highest', sortKey: 'severity', columnIndex: 1 },
    { label: 'Severity', value: 'severity desc', directionLabel: 'Lowest', sortKey: 'severity', columnIndex: 1 },
    { label: 'Number of endpoints', value: 'numberOfEndpoints asc', directionLabel: 'More', sortKey: 'numberOfEndpoints', columnIndex: 3 },
    { label: 'Number of endpoints', value: 'numberOfEndpoints desc', directionLabel: 'Less', sortKey: 'numberOfEndpoints', columnIndex: 3 },
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

    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[5]
    );

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    };

    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    const headers = [
        {
            title: '',
            type: CellType.COLLAPSIBLE
        },
        {
            title: "Severity",
            text: "Severity",
            value: "severity",
            sortActive: true
        },
        {
            title: "Threat name",
            text: "Threat name",
            value: "issueName",
        },
        {
            title: "Number of endpoints",
            text: "Number of endpoints",
            value: "numberOfEndpoints",
            sortActive: true
        },
        {
            title: "Domains",
            text: "Domains",
            value: "domains"
        },
        {
            title: "Compliance",
            text: "Compliance",
            value: "compliance",
            sortActive: true
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
        jiraTicketUrl: item?.jiraTicketUrl
    });

    const getThreatCollapsibleRow = (urls) => {
        return (
            <tr style={{ background: "#FAFBFB", padding: '0px !important', borderTop: '1px solid #dde0e4' }}>
                <td colSpan={'100%'} style={{ padding: '0px !important' }}>
                    {urls.map((urlObj, index) => {
                        const borderStyle = index < (urls.length - 1) ? { borderBlockEndWidth: 1 } : {}
                        return (
                            <Box padding={"2"} paddingInlineEnd={"4"} paddingInlineStart={"3"} key={index}
                                borderColor="border-subdued" {...borderStyle}>
                                <HorizontalStack gap={4} wrap={false}>
                                    <Link
                                        monochrome
                                        onClick={() => handleThreatClick(urlObj.threatData)}
                                        removeUnderline
                                    >
                                        <GetPrettifyEndpoint
                                            maxWidth="300px"
                                            method={urlObj.method}
                                            url={urlObj.url}
                                            isNew={false}
                                        />
                                    </Link>
                                </HorizontalStack>
                            </Box>
                        )
                    })}
                </td>
            </tr>
        )
    }

    const convertToThreatTableData = (rawData, threatFiltersMapWithTestName) => {
        return rawData.map((threat, idx) => {
            const key = `${threat.id.testSubCategory}|${threat.severity}|${idx}`
            let totalCompliance = (threat.compliance || []).length
            let maxShowCompliance = 2
            let badge = totalCompliance > maxShowCompliance ? <Badge size="extraSmall">+{totalCompliance - maxShowCompliance}</Badge> : null

            return {
                key: key,
                id: threat.urls.map((x) => x.id),
                severity: <div className={`badge-wrapper-${threat.severityType}`}>
                    <Badge size="small" key={idx}>{threat.severity}</Badge>
                </div>,
                issueName: threatFiltersMapWithTestName[threat.issueName]?.testName || threat.issueName,
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
                collapsibleRow: getThreatCollapsibleRow(threat.urls)
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
            let latestAttack = calcFilteredThreatFilterIds(complianceView);
            let hostFilter = [];

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

            const sort = sortKey && sortOrder ? { [sortKey]: sortOrder === 'asc' ? 1 : -1 } : {};
            const successfulFilterValue = Array.isArray(filtersObj?.successfulExploit)
                ? filtersObj?.successfulExploit?.[0]
                : filtersObj?.successfulExploit;
            const successfulBool = (successfulFilterValue === true || successfulFilterValue === 'true') ? true
                : (successfulFilterValue === false || successfulFilterValue === 'false') ? false
                    : undefined;

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
                latestApiOrigRegex
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

                const key = `${item?.filterId}|${threatPolicy.severity || 'HIGH'}`;

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
                        domains: [collectionsMap[item?.apiCollectionId] || "-"],
                        urls: [{
                            method: item?.method,
                            url: item?.url,
                            threatData: createThreatDataObject(item)
                        }],
                        isThreat: true,
                    });
                } else {
                    const existingThreat = uniqueThreatsMap.get(key);
                    const domain = collectionsMap[item?.apiCollectionId] || "-";
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
                if (sortKey === 'numberOfEndpoints') {
                    aValue = a.numberOfEndpoints;
                    bValue = b.numberOfEndpoints;
                } else if (sortKey === 'severity') {
                    const severityOrder = func.getAktoSeverities();
                    aValue = severityOrder.indexOf(a.severityType);
                    bValue = severityOrder.indexOf(b.severityType);
                } else if (sortKey === 'creationTime') {
                    aValue = a.creationTime;
                    bValue = b.creationTime;
                } else {
                    return 0;
                }

                const order = sortOrder === 'asc' ? 1 : -1;
                if (aValue < bValue) return -1 * order;
                if (aValue > bValue) return 1 * order;
                return 0;
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
            },
            {
                key: 'successfulExploit',
                label: 'Successful Exploit',
                title: 'Successful Exploit',
                choices: [
                    { label: 'True', value: 'true' },
                    { label: 'False', value: 'false' }
                ],
                singleSelect: true
            },
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

    const key = startTimestamp + endTimestamp + currentTab + complianceView;

    return (
        <PageWithMultipleCards
            title={
                <HorizontalStack gap={4}>
                    <TitleWithInfo
                        titleText={"Threat Compliance Report"}
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
                (!threatFiltersMap || Object.keys(threatFiltersMap).length === 0) ? [
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
                        sortOptions={sortOptions}
                        disambiguateLabel={disambiguateLabel}
                        loading={loading}
                        fetchData={fetchData}
                        filters={filters}
                        selectable={false}
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