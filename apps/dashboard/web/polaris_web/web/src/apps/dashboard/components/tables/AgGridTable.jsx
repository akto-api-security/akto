import React, { useCallback, useEffect, useMemo, useReducer, useRef, useState } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
import { Box, HorizontalStack, Pagination, Text, VerticalStack, Button, TextField, Icon } from "@shopify/polaris";
import { MobileCancelMajor, SearchMinor } from "@shopify/polaris-icons";
import PersistStore from "@/apps/main/PersistStore";
import "./rows/row.css";
import { debounce } from 'lodash';
import { AgGridRowRenderer } from "./rows/AgGridRow";
import { CellType } from "./rows/GithubRow";

const AG_GRID_COLUMN_TYPES = {
    [CellType.TEXT]: { cellRenderer: "agGridRow" },
    // CellType.ACTION: wire a dedicated cellRenderer per column — no grid-level preset
    // CellType.COLLAPSIBLE: use AG Grid's built-in treeData/grouping instead
};

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);
LicenseManager.setLicenseKey(window.AG_GRID_LICENSE_KEY);

export const agTableTheme = themeQuartz.withParams({
    accentColor: "#9642FC",
    borderColor: "#E1E3E5",
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


function SearchBar({ value, onChange, placeholder, topRadius = true }) {
    return (
        <Box borderWidth="1" borderColor="border-subdued" borderRadiusStartStart={topRadius ? 2 : 0} borderRadiusStartEnd={topRadius ? 2 : 0} padding={1} borderInlineStartWidth="1" borderBlockStartWidth="1" borderInlineEndWidth="1">
            <div className="ag-grid-search-bar">
            <TextField
                prefix={<Box><Icon source={SearchMinor} /></Box>}
                placeholder={placeholder}
                value={value}
                onChange={onChange}
                borderless
                autoComplete="off"
            />
            </div>
        </Box>
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
                                <Button key={action.label} size="slim" destructive={action.destructive} onClick={action.onAction}>
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
    noOuterBorder = false,
    searchPlaceholder,
    quickFilter: quickFilterProp,
    onQuickFilterChange,
    rowHeight = 44,
    headerHeight = 40,
    rowSelection = "multiple",
    onRowClicked,
    onSelectionChanged,
    getRowStyle,
    bulkActionCount = 0,
    bulkActions = [],
    onClearBulk,
    pagination,
    paginationPageSize = 20,
    paginationPageSizeSelector = [20, 50, 100],
    sideBar = { toolPanels: ["columns", "filters"], defaultToolPanel: null },
    gridRef: gridRefProp,
    animateRows = true,
    suppressCellFocus = true,
    domLayout = "autoHeight",
    height,
    onServerFetch,
    filterStateUrl,
    // Opt-in: use AG Grid's native Server-Side Row Model + built-in pagination footer
    // instead of the custom Polaris Pagination bar. Only affects callers that pass this;
    // MessagesView/SessionsView keep the existing custom-bar behavior untouched.
    serverSideRowModel = false,
    ...rest
}) {
    const hasSearch = !!searchPlaceholder;
    const [searchValue, setSearchValue] = useState("");
    const [debouncedSearchValue, setDebouncedSearchValue] = useState("");
    const theme = noOuterBorder
        ? agTableTheme.withParams({ wrapperBorder: false, wrapperBorderRadius: 0 })
        : agTableTheme;

    const debouncedSetSearch = useRef(
        debounce((val, serverMode) => {
            setDebouncedSearchValue(val);
            if (serverMode) dispatchQuery({ type: "SET_PAGE", page: 0 });
        }, 400)
    ).current;

    useEffect(() => {
        debouncedSetSearch(searchValue, isServerMode);
    }, [searchValue]); // eslint-disable-line react-hooks/exhaustive-deps

    const internalRef = useRef(null);
    const gridRef = gridRefProp || internalRef;

    // ── Server-fetch state ──────────────────────────────────────────────────
    const isServerMode = !!onServerFetch;
    const useSSRM = isServerMode && serverSideRowModel;
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
        if (!isServerMode || useSSRM) return;
        const skip = query.page * paginationPageSize;
        // Use search_after when skip >= 9980 (approaching the 10k from/size hard limit)
        const searchAfterJson = (skip >= 9980 && lastSortValues.current)
            ? JSON.stringify(lastSortValues.current)
            : undefined;
        const result = onServerFetch({ filters: query.filters, sortKey: query.sortKey, sortOrder: query.sortOrder, skip, limit: paginationPageSize, searchAfterJson, searchString: debouncedSearchValue.length >= 3 ? debouncedSearchValue : "" });
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
    }, [query.filters, query.sortKey, query.sortOrder, query.page, debouncedSearchValue]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleFilterChanged = useCallback((e) => {
        if (!isServerMode) return;
        const filters = extractFilterModel(e.api.getFilterModel());
        if (useSSRM) {
            const prev = PersistStore.getState().filtersMap;
            PersistStore.getState().setFiltersMap({ ...prev, [pageKey]: { ...(prev[pageKey] || {}), filters } });
            return;
        }
        dispatchQuery({ type: "SET_FILTERS", filters });
    }, [isServerMode, useSSRM, pageKey]);

    const handleSortChanged = useCallback((e) => {
        if (!isServerMode) return;
        const col = e.api.getColumnState().find(c => c.sort);
        const sortKey = col?.colId || "";
        const sortOrder = col?.sort === "asc" ? -1 : 1;
        if (useSSRM) {
            const prev = PersistStore.getState().filtersMap;
            PersistStore.getState().setFiltersMap({ ...prev, [pageKey]: { ...(prev[pageKey] || {}), sortKey, sortOrder } });
            return;
        }
        dispatchQuery({ type: "SET_SORT", sortKey, sortOrder });
    }, [isServerMode, useSSRM, pageKey]);

    // ── SSRM datasource — AG Grid calls getRows itself on page/sort/filter change ──
    const onServerFetchRef = useRef(onServerFetch);
    onServerFetchRef.current = onServerFetch;

    const searchRef = useRef("");
    useEffect(() => { searchRef.current = debouncedSearchValue; }, [debouncedSearchValue]);

    const firstSearchRun = useRef(true);
    useEffect(() => {
        if (!useSSRM) return;
        if (firstSearchRun.current) { firstSearchRun.current = false; return; }
        gridRef.current?.api?.refreshServerSide({ purge: true });
    }, [debouncedSearchValue]); // eslint-disable-line react-hooks/exhaustive-deps

    const serverSideDatasource = useMemo(() => {
        if (!useSSRM) return undefined;
        return {
            getRows: (params) => {
                const { startRow, endRow, sortModel, filterModel } = params.request;
                const filters = extractFilterModel(filterModel);
                const sortEntry = (sortModel || [])[0];
                const sortKey = sortEntry?.colId || "";
                const sortOrder = sortEntry?.sort === "asc" ? -1 : 1;
                const skip = startRow;
                const limit = (endRow ?? (startRow + paginationPageSize)) - startRow;
                const searchString = searchRef.current.length >= 3 ? searchRef.current : "";
                Promise.resolve(onServerFetchRef.current({ filters, sortKey, sortOrder, skip, limit, searchString }))
                    .then(r => params.success({ rowData: r?.value || [], rowCount: r?.total ?? undefined }))
                    .catch(() => params.fail());
            },
        };
    }, [useSSRM, paginationPageSize]);

    const handleGridReady = useCallback((e) => {
        if (!useSSRM) return;
        const persisted = filtersMap[pageKey] || {};
        if (persisted.filters && Object.keys(persisted.filters).length > 0) {
            const model = {};
            Object.entries(persisted.filters).forEach(([field, values]) => {
                model[field] = { filterType: "set", values };
            });
            e.api.setFilterModel(model);
        }
        if (persisted.sortKey) {
            e.api.applyColumnState({
                state: [{ colId: persisted.sortKey, sort: persisted.sortOrder === -1 ? "desc" : "asc" }],
                defaultState: { sort: null },
            });
        }
    }, [useSSRM, filtersMap, pageKey]);

    // Custom pagination bar — only rendered in the non-SSRM server mode; SSRM uses AG Grid's own footer.
    const serverPaginationBar = isServerMode && !useSSRM && query.total > 0 ? (
        <Box padding="2">
            <HorizontalStack align="center">    
                <Pagination
                    hasPrevious={query.page > 0}
                    onPrevious={() => dispatchQuery({ type: "SET_PAGE", page: query.page - 1 })}
                    hasNext={(query.page + 1) * paginationPageSize < query.total}
                    onNext={() => dispatchQuery({ type: "SET_PAGE", page: query.page + 1 })}
                    label={query.total === 0 ? "No results" : query.page * paginationPageSize + 1 + "-" + Math.min((query.page + 1) * paginationPageSize, query.total) + " of " + query.total}
                />
            </HorizontalStack>
        </Box>
    ) : null;

    // ── Grid node ───────────────────────────────────────────────────────────
    const effectiveDefaultColDef = React.useMemo(() => ({
        enableRowGroup: true,
        enablePivot: true,
        enableValue: true,
        ...defaultColDef,
    }), [defaultColDef]);

    const effectiveSideBar = sideBar;

    const gridNode = (
        <AgGridReact
            ref={gridRef}
            theme={theme}
            rowData={useSSRM ? undefined : rowData}
            columnDefs={columnDefs}
            defaultColDef={effectiveDefaultColDef}
            rowHeight={rowHeight}
            headerHeight={headerHeight}
            animateRows={animateRows}
            suppressCellFocus={suppressCellFocus}
            rowSelection={rowSelection}
            onRowClicked={onRowClicked}
            onSelectionChanged={onSelectionChanged}
            getRowStyle={getRowStyle}
            rowModelType={useSSRM ? "serverSide" : undefined}
            serverSideDatasource={useSSRM ? serverSideDatasource : undefined}
            cacheBlockSize={useSSRM ? paginationPageSize : undefined}
            pagination={useSSRM ? true : pagination}
            paginationPageSize={paginationPageSize}
            paginationPageSizeSelector={paginationPageSizeSelector}
            quickFilterText={isServerMode ? undefined : debouncedSearchValue}
            sideBar={effectiveSideBar}
            treeData={treeData}
            getDataPath={getDataPath}
            autoGroupColumnDef={autoGroupColumnDef}
            groupDefaultExpanded={groupDefaultExpanded}
            components={{ agGridRow: AgGridRowRenderer }}
            columnTypes={AG_GRID_COLUMN_TYPES}
            onFilterChanged={handleFilterChanged}
            onSortChanged={handleSortChanged}
            onGridReady={handleGridReady}
            domLayout={domLayout}
            {...rest}
        />
    );

    return (
        <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
            {hasSearch && <SearchBar value={searchValue} onChange={(val) => {
                setSearchValue(val);
            }} placeholder={searchPlaceholder} topRadius={!noOuterBorder} />}
            <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} noRadius />
            <div style={height ? { height } : { flex: 1, minHeight: 0 }}>
                {gridNode}
            </div>
            {serverPaginationBar}
        </div>
    );
}