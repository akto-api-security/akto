import React, { useState } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { Box, HorizontalStack, VerticalStack, Text, Button } from "@shopify/polaris";
import { MobileCancelMajor } from "@shopify/polaris-icons";

// ─── Theme ────────────────────────────────────────────────────────────────────
// Defined once here — all AG Grid consumers import agTableTheme / agTableThemeInner.

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

// Used when the grid sits inside a styled container that provides its own border
// (SearchBar wrapper, flyout panels, etc.) — suppresses the AG Grid border/radius.
export const agTableThemeInner = agTableTheme.withParams({
    wrapperBorder: false,
    wrapperBorderRadius: 0,
});

// ─── SearchBar ────────────────────────────────────────────────────────────────
// Plain <input> (not Polaris TextField) so it blends with the grid container border.

function SearchBar({ value, onChange, placeholder, offset = 0 }) {
    return (
        <div style={{
            padding: "7px 12px",
            paddingLeft: offset > 0 ? offset : 12,
            borderBottom: "1px solid #E1E3E5",
            display: "flex",
            alignItems: "center",
            gap: 8,
            background: "white",
            flexShrink: 0,
        }}>
            {/* Search icon — inline SVG required; no Polaris icon available outside cell renderers */}
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

// ─── BulkActionBar ───────────────────────────────────────────────────────────

function BulkActionBar({ count, bulkActions = [], onClear, noRadius = false }) {
    if (!count) return null;
    return (
        <Box paddingBlockEnd={noRadius ? "0" : "2"}>
            {/* Brand purple background/border (#F5F0FF, #DDD3FA) are outside the Polaris token set */}
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
                            {/* Brand purple #7C3AED badge — no matching Polaris token */}
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

// ─── AgGridTable ─────────────────────────────────────────────────────────────
// Drop-in wrapper standardising theme, defaults, BulkActionBar, and SearchBar.
//
// Key props:
//   height                              — total height in px (default 600); ignored when fillHeight=true
//   fillHeight                          — flex-fill mode: grid expands to fill flex parent (use inside flyouts)
//   noOuterBorder                       — suppress AG Grid wrapper border (uses agTableThemeInner)
//   searchPlaceholder                   — if set, shows a SearchBar above the grid; auto-enables inner theme
//   quickFilter / onQuickFilterChange   — controlled quick-filter text; uncontrolled when omitted
//   bulkActionCount / bulkActions / onClearBulk — BulkActionBar integration
//   gridRef                             — forwarded to AgGridReact
//   All standard AgGridReact props passed through via ...rest

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
    searchOffset = 0,
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
    gridRef,
    animateRows = true,
    suppressCellFocus = true,
    ...rest
}) {
    // Uncontrolled quick-filter state — used when the parent does not pass quickFilter/onQuickFilterChange.
    const [internalQuickFilter, setInternalQuickFilter] = useState("");
    const hasSearch = !!searchPlaceholder;
    const quickFilter = quickFilterProp !== undefined ? quickFilterProp : internalQuickFilter;
    const handleQuickFilterChange = onQuickFilterChange || setInternalQuickFilter;

    // Use the inner theme (no wrapper border/radius) whenever the component provides its own container.
    const theme = (noOuterBorder || fillHeight || hasSearch) ? agTableThemeInner : agTableTheme;

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
            enableBrowserTooltips
            suppressCellFocus={suppressCellFocus}
            rowSelection={rowSelection}
            suppressRowClickSelection={suppressRowClickSelection}
            onRowClicked={onRowClicked}
            onSelectionChanged={onSelectionChanged}
            getRowStyle={getRowStyle}
            pagination={pagination}
            paginationPageSize={paginationPageSize}
            paginationPageSizeSelector={paginationPageSizeSelector}
            sideBar={sideBar}
            treeData={treeData}
            getDataPath={getDataPath}
            autoGroupColumnDef={autoGroupColumnDef}
            groupDefaultExpanded={groupDefaultExpanded}
            quickFilterText={hasSearch ? quickFilter : undefined}
            {...rest}
        />
    );

    // ── Fill-height mode: flex-expand to fill the flex parent (flyout panels) ──
    // Optionally renders a SearchBar above the grid when searchPlaceholder is provided.
    if (fillHeight) {
        return (
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} noRadius />
                {hasSearch && <SearchBar value={quickFilter} onChange={handleQuickFilterChange} placeholder={searchPlaceholder} offset={searchOffset} />}
                <div style={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
                    {gridNode}
                </div>
            </div>
        );
    }

    // ── Search-bar mode: fixed-height outer container with SearchBar above grid ──
    if (hasSearch) {
        return (
            <VerticalStack gap="0">
                <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} />
                <div style={{ height, border: "1px solid #E1E3E5", borderRadius: 8, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                    <SearchBar value={quickFilter} onChange={handleQuickFilterChange} placeholder={searchPlaceholder} />
                    <div style={{ flex: 1, minHeight: 0, overflow: "hidden" }}>
                        {gridNode}
                    </div>
                </div>
            </VerticalStack>
        );
    }

    // ── Default mode: fixed-height div, AG Grid wrapper border visible ──
    return (
        <VerticalStack gap="0">
            <BulkActionBar count={bulkActionCount} bulkActions={bulkActions} onClear={onClearBulk} />
            {/* AG Grid requires an explicit-height container with overflow:hidden
                — Box.minHeight and overflowY are insufficient for the grid to render correctly */}
            <div style={{ height, overflow: "hidden" }}>
                {gridNode}
            </div>
        </VerticalStack>
    );
}
