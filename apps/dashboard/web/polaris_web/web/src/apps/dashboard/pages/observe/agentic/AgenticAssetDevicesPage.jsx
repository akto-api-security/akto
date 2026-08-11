import React, { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Badge } from "@shopify/polaris";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "@/apps/dashboard/components/tables/GithubServerTable";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import api from "../api";
import func from "@/util/func";
import PersistStore from "../../../../main/PersistStore";
import { fetchAndCacheAgenticTrafficRiskBundle } from "./constants";
import { getRiskStatus } from "./agenticPageBuilders";

// Endpoint/device-wise view for ONE agentic asset — what clicking a row on the legacy Agentic
// Assets page opens instead of navigating to Inventory (which needed the whole account's
// collections just to build a filter). Server-paginated via the same fetchAgenticAssetDevicesPage
// endpoint the new layout's flyout Devices tab already uses, scoped to just this asset's own
// collectionIds — never account-wide. Old-layout look (PageWithMultipleCards + GithubServerTable,
// standard back arrow), not a flyout, per explicit direction.
const HEADERS = [
    { title: "Device", text: "Device", value: "deviceId" },
    { title: "User", text: "User", value: "username" },
    { title: "Services", text: "Services", value: "servicesDisplay" },
    { title: "AI Interactions", text: "AI Interactions", value: "aiInteractionsDisplay", boxWidth: "140px" },
    { title: "Risk score", text: "Risk score", value: "riskScoreComp", numericValue: "riskScore", textValue: "riskScore", sortActive: true, boxWidth: "100px" },
    { title: "Last traffic seen", text: "Last traffic seen", value: "lastTraffic", numericValue: "lastSeenEpoch", sortActive: true, boxWidth: "140px" },
];

const SORT_OPTIONS = [
    { label: "Risk score", value: "riskScore desc", directionLabel: "Highest", sortKey: "riskScore", columnIndex: 5 },
    { label: "Risk score", value: "riskScore asc", directionLabel: "Lowest", sortKey: "riskScore", columnIndex: 5 },
    { label: "User", value: "username asc", directionLabel: "A-Z", sortKey: "username", columnIndex: 2 },
    { label: "User", value: "username desc", directionLabel: "Z-A", sortKey: "username", columnIndex: 2 },
    { label: "Last traffic seen", value: "lastSeenEpoch desc", directionLabel: "Newest", sortKey: "lastSeenEpoch", columnIndex: 6 },
    { label: "Last traffic seen", value: "lastSeenEpoch asc", directionLabel: "Oldest", sortKey: "lastSeenEpoch", columnIndex: 6 },
];

const resourceName = { singular: "device", plural: "devices" };

function shapeDeviceRow(row) {
    const riskScore = row.riskScore || 0;
    return {
        ...row,
        servicesDisplay: (row.services || []).join(", ") || "-",
        aiInteractionsDisplay: row.aiInteractions ? Number(row.aiInteractions).toLocaleString("en-US") : "-",
        riskScoreComp: riskScore ? <Badge status={getRiskStatus(riskScore)} size="small">{riskScore}</Badge> : "-",
        lastTraffic: row.lastSeenEpoch > 0 ? func.prettifyEpoch(row.lastSeenEpoch) : "-",
    };
}

export default function AgenticAssetDevicesPage() {
    const [searchParams] = useSearchParams();
    const groupKey = searchParams.get("groupKey") || "";
    const rowType = searchParams.get("rowType") || "";
    const assetName = searchParams.get("name") || "Asset";
    const assetType = searchParams.get("type") || "";

    // True only until this one asset's own collectionIds resolve (lazy — mirrors the new layout's
    // flyout fetching fetchAgenticAssetDetail once per asset instead of shipping collectionIds on
    // every row of the main grid). Navigating here happens immediately on row-click, before this
    // resolves, so the "loading" feedback lives on this page instead of freezing the previous one.
    const [loading, setLoading] = useState(true);
    const collectionIdsRef = useRef([]);
    const enrichRef = useRef({ trafficMap: {}, riskScoreMap: {}, userAnalysisFlatMap: {}, usernameMap: {} });
    const [refreshKey, setRefreshKey] = useState(0);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        (async () => {
            try {
                const [trafficRiskBundle, detail] = await Promise.all([
                    fetchAndCacheAgenticTrafficRiskBundle({ api, PersistStore }),
                    api.fetchAgenticAssetDetail({ groupKey, rowType }),
                ]);
                if (cancelled) return;
                const { trafficMap = {}, riskScoreMap = {} } = trafficRiskBundle || {};
                enrichRef.current = { trafficMap, riskScoreMap, userAnalysisFlatMap: {}, usernameMap: {} };
                collectionIdsRef.current = detail?.collectionIds || [];
            } catch {
                if (!cancelled) collectionIdsRef.current = [];
            } finally {
                if (!cancelled) { setLoading(false); setRefreshKey((k) => k + 1); }
            }
        })();
        return () => { cancelled = true; };
    }, [groupKey, rowType]);

    const fetchTableData = useCallback(async (sortKey, sortOrder, skip, limit, filtersObj, filterOperators, queryValue) => {
        const { trafficMap, riskScoreMap, userAnalysisFlatMap, usernameMap } = enrichRef.current;
        const mongoSortOrder = sortOrder === -1 ? 1 : -1;
        const res = await api.fetchAgenticAssetDevicesPage({
            apiCollectionIds: collectionIdsRef.current,
            skip, limit, sortKey: sortKey || "riskScore", sortOrder: mongoSortOrder, queryValue,
            trafficMap, riskScoreMap, userAnalysisFlatMap, usernameMap,
        });
        return { value: (res.devices || []).map(shapeDeviceRow), total: res.total || 0 };
    }, []);

    const handleRowClick = useCallback((row) => {
        const deviceId = row.deviceId || row.endpoint;
        if (!deviceId) return;
        window.open(`/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`, "_blank");
    }, []);

    const disambiguateLabel = useCallback((key, value) => func.convertToDisambiguateLabelObj(value, null, 2), []);

    if (loading) {
        return (
            <PageWithMultipleCards
                title={assetName}
                titleMetadata={assetType ? <Badge>{assetType}</Badge> : undefined}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={assetName}
            titleMetadata={assetType ? <Badge>{assetType}</Badge> : undefined}
            components={[
                <GithubServerTable
                    key={`asset-devices-${groupKey}-${rowType}-${refreshKey}`}
                    fetchData={fetchTableData}
                    pageLimit={20}
                    sortOptions={SORT_OPTIONS}
                    resourceName={resourceName}
                    filters={[]}
                    headers={HEADERS}
                    selectable={false}
                    headings={HEADERS}
                    useNewRow={true}
                    condensedHeight={true}
                    disambiguateLabel={disambiguateLabel}
                    onRowClick={handleRowClick}
                    rowClickable={true}
                    supportsNegationFilter={false}
                />,
            ]}
        />
    );
}
