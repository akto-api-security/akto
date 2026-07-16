import { useEffect, useState } from "react";
import {
    Box, Card, DataTable, HorizontalGrid, HorizontalStack, Link, Text, VerticalStack, Badge, Spinner
} from "@shopify/polaris";
import api from "../api";
import observeFunc from "../../observe/transform";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import DonutChart from "../../../components/shared/DonutChart";
import ThreatWorldMap from "./ThreatWorldMap";
import { formatCategoryName, getFlagSrc, openThreatActivityPage } from "../utils/threatDashboardUtils";
import { getDashboardCategory, mapLabel, categoryToShortName } from "../../../../main/labelHelper";

const TAB_KEYS = {
    TOTAL_THREATS: 0,
    APIS_UNDER_THREAT: 1,
    THREAT_ACTORS: 2,
};

const STATUS_COLORS = {
    Active: "#E45858",
    "Under Review": "#F5A623",
    Ignored: "#95A5A6",
};

function ThreatTabbedSection({ startTimestamp, endTimestamp }) {
    const [selectedTab, setSelectedTab] = useState(TAB_KEYS.TOTAL_THREATS);
    const [loading, setLoading] = useState(false);

    const [totalThreatsCount, setTotalThreatsCount] = useState(0);
    const [apisUnderThreatCount, setApisUnderThreatCount] = useState(0);
    const [threatActorsCount, setThreatActorsCount] = useState(0);

    const [threatStatusData, setThreatStatusData] = useState({});
    const [threatCategoryData, setThreatCategoryData] = useState([]);
    const [apisData, setApisData] = useState([]);
    const [actorsData, setActorsData] = useState([]);

    useEffect(() => {
        const fetchAll = async () => {
            setLoading(true);
            try {
                const [summaryResp, categoryResp, apisResp, actorsResp] = await Promise.all([
                    api.getDailyThreatActorsCount(startTimestamp, endTimestamp, []),
                    api.fetchThreatCategoryCount(startTimestamp, endTimestamp),
                    api.fetchThreatApis(0, {}, []),
                    api.fetchThreatActors(0, {}, [], [], startTimestamp, endTimestamp, [], []),
                ]);

                let dashboardTopResp = null;
                try {
                    dashboardTopResp = await api.fetchDashboardTopData(startTimestamp, endTimestamp);
                } catch (_) {
                    // Fallback: new API not yet available
                }

                const totalActive = summaryResp?.totalActiveStatus || 0;
                const totalIgnored = summaryResp?.totalIgnoredStatus || 0;
                const totalUnderReview = summaryResp?.totalUnderReviewStatus || 0;
                setTotalThreatsCount(summaryResp?.totalAnalysed || 0);

                setThreatStatusData({
                    Active: {
                        text: totalActive,
                        color: STATUS_COLORS.Active,
                        filterKey: "Active",
                    },
                    "Under Review": {
                        text: totalUnderReview,
                        color: STATUS_COLORS["Under Review"],
                        filterKey: "Under Review",
                    },
                    Ignored: {
                        text: totalIgnored,
                        color: STATUS_COLORS.Ignored,
                        filterKey: "Ignored",
                    },
                });

                if (categoryResp?.categoryCounts) {
                    const subcategoryMap = {};
                    categoryResp.categoryCounts.forEach((item) => {
                        const sub = item.subCategory || item.category || "Unknown";
                        if (!subcategoryMap[sub]) {
                            subcategoryMap[sub] = { rawName: sub, name: formatCategoryName(sub), count: 0 };
                        }
                        subcategoryMap[sub].count += (item.count || 0);
                    });
                    setThreatCategoryData(
                        Object.values(subcategoryMap)
                            .sort((a, b) => b.count - a.count)
                            .slice(0, 5)
                    );
                }

                setApisUnderThreatCount(apisResp?.total || 0);
                setThreatActorsCount(actorsResp?.total || (actorsResp?.actors || []).length);

                // Use dashboard top data for the tabs (top 5 by attack/request count)
                if (dashboardTopResp) {
                    setApisData((dashboardTopResp.dashboardTopApis || []).map((a) => ({
                        api: a.endpoint,
                        method: a.method,
                        host: a.host,
                        requestsCount: a.requestsCount,
                        actorsCount: a.actorsCount,
                    })));
                    setActorsData((dashboardTopResp.dashboardTopActors || []).map((a) => ({
                        id: a.actor,
                        country: a.country,
                        latestAttack: a.latestAttack,
                        attackCount: a.attackCount,
                    })));
                }
            } catch (err) {
                // keep defaults
            } finally {
                setLoading(false);
            }
        };
        fetchAll();
    }, [startTimestamp, endTimestamp]);

    const handleTabChange = (tabIndex) => {
        if (tabIndex === selectedTab) return;
        setSelectedTab(tabIndex);
    };

    const category = getDashboardCategory();
    // "View All" opens a new tab, so pass the category and date range along (see threatDashboardUtils.js).
    const viewAllParams = new URLSearchParams();
    const categoryParam = categoryToShortName[category];
    if (categoryParam) viewAllParams.set("category", categoryParam);
    if (startTimestamp) viewAllParams.set("since", startTimestamp);
    if (endTimestamp) viewAllParams.set("until", endTimestamp);
    const viewAllQuery = viewAllParams.toString() ? `?${viewAllParams.toString()}` : "";
    const tabs = [
        { label: mapLabel("Total Threats", category), count: totalThreatsCount },
        { label: mapLabel("APIs Under Threat", category), count: apisUnderThreatCount },
        { label: mapLabel("Threat Actors", category), count: threatActorsCount },
    ];

    const STATUS_NAME_TO_EVENT_STATUS = {
        "Active": "ACTIVE",
        "Under Review": "UNDER_REVIEW",
        "Ignored": "IGNORED",
    };

    const handleDonutSegmentClick = (segmentName) => {
        const eventStatus = STATUS_NAME_TO_EVENT_STATUS[segmentName] || segmentName.toUpperCase();
        openThreatActivityPage({ eventStatus, startTimestamp, endTimestamp });
    };

    const handleThreatNameClick = (rawName) => {
        openThreatActivityPage({ latestAttack: rawName, startTimestamp, endTimestamp });
    };

    // --- Tab 1: Total Threats ---
    const renderTotalThreatsTab = () => (
        <HorizontalGrid columns={2} gap="4">
            <VerticalStack gap="3">
                <Box>
                    <HorizontalStack align="center">
                        <DonutChart
                            data={threatStatusData}
                            size={180}
                            pieInnerSize="55%"
                            onSegmentClick={handleDonutSegmentClick}
                        />
                    </HorizontalStack>
                </Box>
                <HorizontalStack gap="4" align="center">
                    {Object.entries(threatStatusData).map(([label, item]) => (
                        <HorizontalStack gap="1" blockAlign="center" key={label}>
                            <span style={{ background: item.color, borderRadius: "50%", width: "var(--p-space-2, 0.5rem)", height: "var(--p-space-2, 0.5rem)", display: "inline-block" }} />
                            <Text variant="bodySm">{label}</Text>
                        </HorizontalStack>
                    ))}
                </HorizontalStack>
            </VerticalStack>
            <VerticalStack gap="2">
                <DataTable
                    columnContentTypes={["text", "numeric"]}
                    headings={[
                        <Text variant="headingSm" key="h1">{mapLabel("Threat Name", category)}</Text>,
                        <Text variant="headingSm" key="h2">{mapLabel("APIs Affected", category)}</Text>,
                    ]}
                    rows={threatCategoryData.map((item) => [
                        <div
                            key={item.name}
                            style={{ cursor: "pointer" }}
                            onClick={() => handleThreatNameClick(item.rawName)}
                        >
                            <HorizontalStack gap="2" blockAlign="start" wrap={false}>
                                <span style={{ color: "#E45858", fontSize: "var(--p-font-size-75, 0.75rem)", letterSpacing: "-1px", flexShrink: 0 }}>|||</span>
                                <Text variant="bodyMd">{item.name}</Text>
                            </HorizontalStack>
                        </div>,
                        <Text variant="bodyMd">{observeFunc.formatNumberWithCommas(item.count)}</Text>,
                    ])}
                    hoverable
                    increasedTableDensity
                />
                {threatCategoryData.length > 0 && (
                    <Box paddingBlockStart="2">
                        <Link url={`/dashboard/protection/threat-activity${viewAllQuery}`} removeUnderline target="_blank">
                            View All ({totalThreatsCount})
                        </Link>
                    </Box>
                )}
            </VerticalStack>
        </HorizontalGrid>
    );

    // --- Tab 2: APIs Under Threat ---
    const renderApisTab = () => (
        <VerticalStack gap="2">
            <DataTable
                columnContentTypes={["text", "numeric", "numeric", "text"]}
                headings={[
                    <Text variant="headingSm" key="h1">{mapLabel("API Endpoint", category)}</Text>,
                    <Text variant="headingSm" key="h2">Bad Actors</Text>,
                    <Text variant="headingSm" key="h3">Malicious Requests</Text>,
                    <Text variant="headingSm" key="h4">Host</Text>,
                ]}
                rows={apisData.map((item) => [
                    <div
                        key={item.api}
                        style={{ cursor: "pointer" }}
                        onClick={() => openThreatActivityPage({ url: item.api, startTimestamp, endTimestamp })}
                    >
                        <GetPrettifyEndpoint
                            method={item.method}
                            url={item.api}
                            isNew={false}
                        />
                    </div>,
                    <Text variant="bodyMd">{item.actorsCount || 0}</Text>,
                    <Text variant="bodyMd">{item.requestsCount || 0}</Text>,
                    <Text variant="bodyMd">{item.host || "-"}</Text>,
                ])}
                hoverable
                increasedTableDensity
            />
            {apisData.length > 0 && (
                <Link url={`/dashboard/protection/threat-api${viewAllQuery}`} removeUnderline target="_blank">
                    View All ({apisUnderThreatCount})
                </Link>
            )}
        </VerticalStack>
    );

    // --- Tab 3: Threat Actors ---
    const renderActorsTab = () => (
        <HorizontalGrid columns={2} gap="4">
            <ThreatWorldMap
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
                style={{ width: "100%" }}
                hideTitle={true}
                containerId="threat-tab-world-map"
            />
            <VerticalStack gap="2">
                <DataTable
                    columnContentTypes={["text", "numeric", "text"]}
                    headings={[
                        <Text variant="headingSm" key="h1">{mapLabel("Threat Actor", category)}</Text>,
                        <Text variant="headingSm" key="h2">{mapLabel("Threat Requests", category)}</Text>,
                        <Text variant="headingSm" key="h3">Latest Attack</Text>,
                    ]}
                    rows={actorsData.map((actor) => [
                        <div
                            key={actor.id}
                            style={{ cursor: "pointer" }}
                            onClick={() => openThreatActivityPage({ actor: actor.id, startTimestamp, endTimestamp })}
                        >
                            <HorizontalStack gap="2" blockAlign="center">
                                <img
                                    src={getFlagSrc(actor.country)}
                                    alt={actor.country || ""}
                                    style={{ width: "1.25em", height: "1.25em" }}
                                />
                                <Text variant="bodyMd">{actor.id}</Text>
                            </HorizontalStack>
                        </div>,
                        <Text variant="bodyMd">{observeFunc.formatNumberWithCommas(actor.attackCount || 0)}</Text>,
                        <Text variant="bodyMd">
                            {formatCategoryName(actor.latestAttack) || "-"}
                        </Text>,
                    ])}
                    hoverable
                    increasedTableDensity
                />
                {actorsData.length > 0 && (
                    <Link url={`/dashboard/protection/threat-actor${viewAllQuery}`} removeUnderline target="_blank">
                        View All ({threatActorsCount})
                    </Link>
                )}
            </VerticalStack>
        </HorizontalGrid>
    );

    const tabContent = [renderTotalThreatsTab, renderApisTab, renderActorsTab];

    return (
        <Card padding={5}>
            <VerticalStack gap="4">
                <HorizontalStack gap="0" wrap={true}>
                    {tabs.map((tab, idx) => (
                        <button
                            key={tab.label}
                            onClick={() => handleTabChange(idx)}
                            style={{
                                padding: "var(--p-space-2, 0.5rem) var(--p-space-4, 1rem)",
                                background: "none",
                                border: "none",
                                borderBottom: selectedTab === idx ? "2px solid var(--p-color-bg-interactive, #5C6AC4)" : "2px solid transparent",
                                cursor: "pointer",
                                display: "flex",
                                alignItems: "center",
                                gap: "var(--p-space-2, 0.5rem)",
                            }}
                        >
                            <Text
                                variant="bodyMd"
                                fontWeight={selectedTab === idx ? "semibold" : "regular"}
                                color={selectedTab === idx ? undefined : "subdued"}
                            >
                                {tab.label}
                            </Text>
                            <Badge size="small">{observeFunc.formatNumberWithCommas(tab.count)}</Badge>
                        </button>
                    ))}
                </HorizontalStack>

                <Box>
                    {loading ? (
                        <Box padding="8">
                            <HorizontalStack align="center">
                                <Spinner size="large" />
                            </HorizontalStack>
                        </Box>
                    ) : (
                        tabContent[selectedTab]()
                    )}
                </Box>
            </VerticalStack>
        </Card>
    );
}

export default ThreatTabbedSection;
