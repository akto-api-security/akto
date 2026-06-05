import React, { useCallback, useEffect, useReducer, useRef, useState } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
import { Box, HorizontalStack, Pagination, Text, VerticalStack, Button } from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";
import PersistStore from "@/apps/main/PersistStore";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);
LicenseManager.setLicenseKey(window.AG_GRID_LICENSE_KEY || "[TRIAL]_this_{AG_Charts_and_AG_Grid}_Enterprise_key_{AG-129492}_is_granted_for_evaluation_only___Use_in_production_is_not_permitted___Please_report_misuse_to_legal@ag-grid.com___For_help_with_purchasing_a_production_key_please_contact_info@ag-grid.com___You_are_granted_a_{Single_Application}_Developer_License_for_one_application_only___All_Front-End_JavaScript_developers_working_on_the_application_would_need_to_be_licensed___This_key_will_deactivate_on_{18 June 2026}____[v3]_[0102]_MTc4MTczNzIwMDAwMA==d27c8a4487e577f42d9980e95824f43c");

export const agTableTheme = themeQuartz.withParams({
    accentColor: "#9642FC",
    borderColor: "#E1E3E5",
    borderRadius: 4,
    browserColorScheme: "light",
    cellTextColor: "#202223",
    columnBorder: false,
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    fontSize: 12,
    foregroundColor: "#202223",
    headerFontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    headerRowBorder: true,
    headerTextColor: "#6D7175",
    iconSize: 16,
    rowBorder: true,
    spacing: 8,
    wrapperBorder: true,
    wrapperBorderRadius: 8,
    headerFontSize: 12,
    headerFontWeight: 500,
    checkboxBorderRadius: 4,
});

export const agTableThemeInner = agTableTheme.withParams({
    wrapperBorder: false,
    wrapperBorderRadius: 0,
});

function SearchBar({ value, onChange, placeholder }) {
    return (
        <div style={{
            padding: "7px 12px",
            borderBottom: "1px solid #E1E3E5",
            display: "flex",
            alignItems: "center",
            gap: 8,
            background: "white",
            flexShrink: 0,
        }}>
            <svg width="16" height="16" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg" style={{ flexShrink: 0 }}>
                <circle cx="9" cy="9" r="7" stroke="#8C9196" strokeWidth="1.75"/>
                <path d="M14.5 14.5L19 19" stroke="#8C9196" strokeWidth="1.75" strokeLinecap="round"/>
            </svg>
            <input
                type="text"
                placeholder={placeholder}
                value={value}
                onChange={e => onChange(e.target.value)}
                style={{
                    border: "none",
                    outline: "none",
                    flex: 1,
                    fontSize: 13,
                    color: "#202223",
                    background: "transparent",
                    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                }}
            />
        </div>
    );
}

function BulkActionBar({ count, bulkActions = [], onClear, noRadius = false }) {
    if (!count) return null;
    return (
        <Box paddingBlockEnd={noRadius ? "0" : "2"}>
            <div style={{
                padding: "8px 14px",
                background: "#F5F0FF",
                border: "1px solid #DDD3FA",
                borderRadius: noRadius ? 0 : 8,
                borderLeft: noRadius ? "none" : undefined,
                borderRight: noRadius ? "none" : undefined,
            }}>
                <VerticalStack gap="2">
                    <HorizontalStack align="space-between" blockAlign="center">
                        <HorizontalStack gap="2" blockAlign="center">
                            <span style={{
                                display: "inline-flex", alignItems: "center", justifyContent: "center",
                                minWidth: 24, height: 24, padding: "0 7px", borderRadius: 12,
                                fontSize: 12, fontWeight: 700,
                                background: "#7C3AED", color: "white",
                            }}>
                                {count}
                            </span>
                            <Text variant="bodySm" color="subdued">
                                {count === 1 ? "row" : "rows"} selected
                            </Text>
                        </HorizontalStack>
                        <Button plain icon={MobileCancelMajor} onClick={onClear} accessibilityLabel="Clear selection" />
                    </HorizontalStack>
                    {bulkActions.length > 0 && (
                        <HorizontalStack gap="2">
                            {bulkActions.map(action => (
                                <Button key={action.label} size="slim" onClick={action.onAction}>
                                    {action.label}
                                </Button>
                            ))}
                        </HorizontalStack>
                    )}
                </VerticalStack>
            </div>
        </Box>
    );
}

// ── Server-fetch query reducer ────────────────────────────────────────────────
// Used only when onServerFetch is provided. Owns filter/sort/page state,
// persisted to PersistStore.filtersMap keyed by filterStateUrl or pathname.

function extractFilterModel(model) {
    const out = {};
    Object.entries(model || {}).forEach(([field, state]) => {
        if (state.filterType === "set" && Array.isArray(state.values)) {
            out[field] = state.values;
        } else if (state.filterType === "text" && state.filter) {
            out[field] = [state.filter];
        }
    });
    return out;
}

const QUERY_INIT = { filters: {}, sortKey: "", sortOrder: 1, page: 0, total: 0 };

function queryReducer(state, action) {
    switch (action.type) {
        case "SET_FILTERS": return { ...state, filters: action.filters, page: 0 };
        case "SET_SORT":    return { ...state, sortKey: action.sortKey, sortOrder: action.sortOrder, page: 0 };
        case "SET_PAGE":    return { ...state, page: action.page };
        case "SET_TOTAL":   return { ...state, total: action.total };
        default:            return state;
    }
}

// ── AgGridTable ───────────────────────────────────────────────────────────────

export default function AgGridTable({
    rowData,
    columnDefs,
    defaultColDef,
    treeData,
    getDataPath,
    autoGroupColumnDef,
    groupDefaultExpanded,
    height = 600,
    fillHeight = false,
    noOuterBorder = false,
    searchPlaceholder,
    quickFilter: quickFilterProp,
    onQuickFilterChange,
    rowHeight = 44,
    headerHeight = 40,
    rowSelection = "multiple",
    suppressRowClickSelection = true,
    onRowClicked,
    onSelectionChanged,
    getRowStyle,
    bulkActionCount = 0,
    bulkActions = [],
    onClearBulk,
    pagination = true,
    paginationPageSize = 20,
    paginationPageSizeSelector = [20, 50, 100],
    sideBar = { toolPanels: ["columns", "filters"] },
    gridRef: gridRefProp,
    animateRows = true,
    suppressCellFocus = true,
    onServerFetch,
    filterStateUrl,
    ...rest
}) {
    const [internalQuickFilter, setInternalQuickFilter] = useState("");
    const hasSearch = !!searchPlaceholder;
    const quickFilter = quickFilterProp !== undefined ? quickFilterProp : internalQuickFilter;
    const handleQuickFilterChange = onQuickFilterChange || setInternalQuickFilter;
    const theme = (noOuterBorder || fillHeight || hasSearch) ? agTableThemeInner : agTableTheme;

    const internalRef = useRef(null);
    const gridRef = gridRefProp || internalRef;

    // ── Server-fetch state ──────────────────────────────────────────────────
    const isServerMode = !!onServerFetch;
    const pageKey = filterStateUrl || (window.location.pathname + "/ag-grid");

    const filtersMap    = PersistStore(s => s.filtersMap);
    const setFiltersMap = PersistStore(s => s.setFiltersMap);

    const persisted = filtersMap[pageKey] || {};
    const [query, dispatchQuery] = useReducer(
        queryReducer,
        isServerMode
            ? { ...QUERY_INIT, filters: persisted.filters || {}, sortKey: persisted.sortKey || "", sortOrder: persisted.sortOrder ?? 1 }
            : QUERY_INIT
    );

    // Persist filter/sort to PersistStore (not page) on change
    const prevPersistKey = useRef(null);
    useEffect(() => {
        if (!isServerMode) return;
        const key = JSON.stringify({ f: query.filters, sk: query.sortKey, so: query.sortOrder });
        if (key === prevPersistKey.current) return;
        prevPersistKey.current = key;
        setFiltersMap({ ...filtersMap, [pageKey]: { filters: query.filters, sortKey: query.sortKey, sortOrder: query.sortOrder } });
    });

    // Tracks the last page's final sort values for search_after deep pagination
    const lastSortValues = useRef(null);

    // Call onServerFetch whenever query changes; callback returns { value, total }
    useEffect(() => {
        if (!isServerMode) return;
        const skip = query.page * paginationPageSize;
        // Use search_after when skip >= 9980 (approaching the 10k from/size hard limit)
        const searchAfterJson = (skip >= 9980 && lastSortValues.current)
            ? JSON.stringify(lastSortValues.current)
            : undefined;
        const result = onServerFetch({ filters: query.filters, sortKey: query.sortKey, sortOrder: query.sortOrder, skip, limit: paginationPageSize, searchAfterJson });
        if (result && typeof result.then === "function") {
            result.then(r => {
                if (r?.total !== undefined) dispatchQuery({ type: "SET_TOTAL", total: r.total });
                // Store the sort values of the last returned row for the next deep page
                const rows = r?.value || [];
                if (rows.length > 0) {
                    const last = rows[rows.length - 1];
                    lastSortValues.current = last._sortValues || null;
                }
            });
        }
    }, [query.filters, query.sortKey, query.sortOrder, query.page]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleFilterChanged = useCallback((e) => {
        if (!isServerMode) return;
        dispatchQuery({ type: "SET_FILTERS", filters: extractFilterModel(e.api.getFilterModel()) });
    }, [isServerMode]);

    const handleSortChanged = useCallback((e) => {
        if (!isServerMode) return;
        const col = e.api.getColumnState().find(c => c.sort);
        dispatchQuery({ type: "SET_SORT", sortKey: col?.colId || "", sortOrder: col?.sort === "asc" ? -1 : 1 });
    }, [isServerMode]);

    // Custom pagination bar — only rendered in server mode; replaces AG Grid's built-in pagination.
    const serverPaginationBar = isServerMode && query.total > 0 ? (
        <Box padding="3" borderBlockStartWidth="025" borderColor="border" background="bg-surface">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text variant="bodySm" tone="subdued">
                    {query.total === 0 ? "No results" : `${query.page * paginationPageSize + 1}–${Math.min((query.page + 1) * paginationPageSize, query.total)} of ${query.total}`}
                </Text>
                <Pagination
                    hasPrevious={query.page > 0}
                    onPrevious={() => dispatchQuery({ type: "SET_PAGE", page: query.page - 1 })}
                    hasNext={(query.page + 1) * paginationPageSize < query.total}
                    onNext={() => dispatchQuery({ type: "SET_PAGE", page: query.page + 1 })}
                />
            </HorizontalStack>
        </Box>
    ) : null;

    // ── Grid node ───────────────────────────────────────────────────────────
    const gridNode = (
        <AgGridReact
            ref={gridRef}
            theme={theme}
            rowData={rowData}
            columnDefs={columnDefs}
            defaultColDef={defaultColDef}
            rowHeight={rowHeight}
            headerHeight={headerHeight}
            animateRows={animateRows}
            suppressCellFocus={suppressCellFocus}
            rowSelection={rowSelection}
            suppressRowClickSelection={suppressRowClickSelection}
            onRowClicked={onRowClicked}
            onSelectionChanged={onSelectionChanged}
            getRowStyle={getRowStyle}
            pagination={isServerMode ? false : pagination}
            paginationPageSize={paginationPageSize}
            paginationPageSizeSelector={paginationPageSizeSelector}
            sideBar={sideBar}
            treeData={treeData}
            getDataPath={getDataPath}
            autoGroupColumnDef={autoGroupColumnDef}
            groupDefaultExpanded={groupDefaultExpanded}
            quickFilterText={hasSearch ? quickFilter : undefined}
            onFilterChanged={isServerMode ? handleFilterChanged : undefined}
            onSortChanged={isServerMode ? handleSortChanged : undefined}
            {...rest}
        />
    );

    if (fillHeight) {
        return (
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} noRadius />
                {hasSearch && <SearchBar value={quickFilter} onChange={handleQuickFilterChange} placeholder={searchPlaceholder} />}
                <div style={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
                    {gridNode}
                </div>
                {serverPaginationBar}
            </div>
        );
    }

    if (hasSearch) {
        return (
            <VerticalStack gap="0">
                <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} />
                <div style={{ height, border: "1px solid #E1E3E5", borderRadius: 8, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                    <SearchBar value={quickFilter} onChange={handleQuickFilterChange} placeholder={searchPlaceholder} />
                    <div style={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
                        {gridNode}
                    </div>
                    {serverPaginationBar}
                </div>
            </VerticalStack>
        );
    }

    return (
        <VerticalStack gap="0">
            <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} />
            <div style={{ height, overflow: "hidden" }}>
                {gridNode}
            </div>
            {serverPaginationBar}
        </VerticalStack>
    );
}
