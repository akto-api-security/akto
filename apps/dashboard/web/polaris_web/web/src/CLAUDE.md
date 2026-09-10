# Akto Dashboard Frontend — Developer Guide

Root: `apps/dashboard/web/polaris_web/web/src/`

---

## Directory Layout

```
src/
├── apps/
│   ├── dashboard/
│   │   ├── components/        # Shared UI building blocks
│   │   │   ├── tables/        # GithubSimpleTable, GithubServerTable, rows, cells
│   │   │   ├── shared/        # 58+ reusable components
│   │   │   ├── layouts/       # Page-level layout wrappers
│   │   │   ├── modals/        # Modal implementations
│   │   │   ├── banners/       # Banner/notification UI
│   │   │   ├── charts/        # Chart visualizations
│   │   │   └── progress/      # SpinnerCentered, loaders
│   │   └── pages/             # Feature pages (observe, testing, settings, …)
│   │       ├── observe/
│   │       ├── testing/
│   │       ├── settings/
│   │       ├── threat_detection/
│   │       ├── agent_team/
│   │       └── … (23 modules total)
│   ├── main/
│   │   ├── PersistStore.js    # Zustand + sessionStorage (collections, filters)
│   │   ├── LocalStorageStore.js # Zustand + localStorage (subCategoryMap, categoryMap)
│   │   └── IconPersistStore.js
│   └── signup/
├── util/
│   └── func.js                # Global utility functions (dates, CSV, icons, prettify)
└── services/
    └── IconCacheService.js
```

Public SVG icons live in `apps/dashboard/web/public/` (224+ files, flat structure).

---

## Code Conventions

- **Component files:** `.jsx` for React components, `.js` for non-JSX modules.
- **Naming:** PascalCase for components, camelCase for utilities and hooks.
- **Small focused files:** prefer extracting logic into helpers rather than bloating a component.
- **No raw data in `.js` files** — keep static datasets and fixture objects out of logic files.
- **Comments only for non-obvious WHY** — well-named code documents itself.
- **Utility functions → `util/func.js`** if global; feature-scoped utilities go in the page's own `transform.js`.
- **New SVG icons → `apps/dashboard/web/public/`** (flat, no subdirectory).
- **CSS classes should be small and single-purpose** — avoid large monolithic class blocks.

---

## Hard Constraints

These rules are non-negotiable. Violations will be rejected in review.

| Rule | Why |
|------|-----|
| Functional components only — no class components | Hooks don't work in class components; this codebase is 100% functional |
| All UI primitives via Polaris — no raw `<div>/<button>/<input>/<table>` | Breaks design consistency and accessibility |
| No Redux / Recoil / Context for shared state — use Zustand stores | PersistStore and LocalStorageStore are the established patterns |
| All HTTP calls via `util/request.js` — no raw `fetch` or direct `axios` | request.js handles auth interceptors, error normalization, and retries |
| No inline styles in Polaris-based components — use Polaris tokens / CSS classes | Exception: AG Grid cell renderers (grid sandboxes CSS, Polaris tokens don't reach in) |
| No new third-party UI libraries | Adding MUI/Ant/Tailwind creates a parallel design system |

```jsx
// WRONG — raw HTML, class component, no Polaris
class MyPage extends React.Component {
  render() { return <div><button onClick={this.save}>Save</button></div> }
}

// WRONG — direct fetch
const data = await fetch("/api/items").then(r => r.json());

// CORRECT
import { Card, Button } from "@shopify/polaris";
import request from "../../../../util/request";

function MyPage() {
  const save = async () => {
    const res = await request("post", "/api/items", payload);
  };
  return <Card><Button onClick={save}>Save</Button></Card>;
}
```

---

## UI Component System

### Polaris (`@shopify/polaris` ^11.2.1)

The primary design system. Use Polaris components for all layout and UI primitives.

```js
import {
  LegacyCard, Box, HorizontalStack, VerticalStack, Text,
  Button, Badge, Tooltip, Modal, Icon,
  IndexTable, IndexFilters, IndexFiltersMode, Pagination,
  useSetIndexFiltersMode, useIndexResourceState,
  Tabs, ChoiceList, Link
} from '@shopify/polaris';

import { CalendarMinor, SearchMinor } from '@shopify/polaris-icons';
```

### Monaco Editor

Use for displaying YAML, JSON, or raw HTTP text. Do not reinvent code display — reach for Monaco.

---

## Table System

### GithubSimpleTable
`components/tables/GithubSimpleTable.js`

Client-side table. **Filters and sorting run entirely in the browser.** State is managed by `tableReducer` and persisted to sessionStorage via PersistStore.

Key props:

| Prop | Purpose |
|------|---------|
| `data` | Array of row objects |
| `headers` | Column definitions (see header shape below) |
| `pageLimit` | Rows per page (default 100) |
| `sortOptions` | `[{ label, value, directionLabel }]` |
| `resourceName` | `{ singular, plural }` |
| `loading` / `loadingText` | Loading state |
| `selectable` | Enable checkbox selection |
| `promotedBulkActions` | Bulk action buttons |
| `getActions(item)` | Per-row action menu items |
| `hasRowActions` | Show action menu column |
| `onRowClick(id, item)` | Row click handler |
| `filterStateUrl` | URL key for filter persistence in PersistStore |
| `tabs` / `tableTabs` | Tab configuration |
| `useNewRow` | Use GithubRow renderer |
| `condensedHeight` | Compact row height |
| `csvFileName` | Enables CSV export |
| `prettifyPageData(pageData)` | Lazy JSX generation for visible rows only |
| `transformRawData(data)` | Raw → display format transform |
| `emptyStateMarkup` | Custom empty state JSX |
| `calendarFilterKeys` | Keys that get date-range filter treatment |

**Header object shape:**

```js
{
  text: "Column Label",
  value: "dataKey",           // key in data row object
  itemOrder: 1,               // 0=icon, 1=primary text, 2=badge, 3=extra columns
  icon: ClockMinor,           // optional Polaris icon
  filterKey: "filterKey",     // enables filter chip for this column
  showFilter: true,           // show filter in filter bar
  component: (item) => <JSX/> // custom renderer (overrides default)
}
```

### GithubServerTable
`components/tables/GithubServerTable.js`

Server-side paginated table. Inherits all GithubSimpleTable props. Wraps `IndexTable` + `IndexFilters` + `Pagination` from Polaris. Use when the dataset is too large for client-side filtering.

### GithubRow
`components/tables/rows/GithubRow.js`

Renders a single table row. Supports collapsible tree-view rows. Used internally by both table components when `useNewRow={true}`.

### GithubCell
`components/tables/cells/GithubCell.js`

Renders individual cells based on `itemOrder`:
- `0` — icon with optional tooltip
- `1` — primary text / heading with optional custom component
- `2` — badge (optionally clickable)
- `3+` — additional columns

### Choosing the Right Table

| Scenario | Use |
|----------|-----|
| Small-medium dataset, client-side filter+sort | `GithubSimpleTable` |
| Large dataset, server-side pagination | `GithubServerTable` |
| Tree/hierarchical data, row grouping, enterprise grid features | AG Grid (`AgGridReact`) |

---

## AG Grid Tables

Use AG Grid (`ag-grid-react` + `ag-grid-enterprise`) when you need tree data, row grouping, or enterprise features not available in the Polaris `IndexTable`. Reference implementation: `pages/observe/agentic/DeviceEndpoints.jsx`.

### Theme — use this exact configuration for every new AG Grid table

```js
import { themeQuartz } from "ag-grid-enterprise";

const agTableTheme = themeQuartz.withParams({
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

// Use this variant when a SearchBar sits above the grid inside a shared container
const agTableThemeInner = agTableTheme.withParams({
  wrapperBorder: false,
  wrapperBorderRadius: 0,
});
```

Always set `rowHeight={44}` and `headerHeight={40}` to match the design baseline.

### Required module registration (once per app, not per component)

```js
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { AllEnterpriseModule } from "ag-grid-enterprise";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);
```

### Four-layer component structure

Every AG Grid page follows the same four layers:

```
1. Theme + module setup    — file-level constants, registered once
2. Cell renderers          — small focused components (RiskScoreCellRenderer, ViolationsCellRenderer, …)
3. Column definitions      — COL_DEFS array + DEFAULT_COL_DEF, defined outside the component
4. Table section component — owns grid state, SearchBar, BulkActionBar, flyouts
```

Keep each layer in its own clearly separated block (use `// ─── Section name ───` dividers). Do not inline cell renderer logic inside column `cellRenderer` callbacks — always extract to a named component.

### Filter and sort — wire to `tableFunc` + PersistStore

AG Grid tables must use the **same filter and sort persistence** as GithubSimpleTable/GithubServerTable. Import from `components/tables/transform.js` and `apps/main/PersistStore.js`:

```js
import tableFunc from "@/apps/dashboard/components/tables/transform";
import PersistStore from "@/apps/main/PersistStore";

// Inside the TableSection component:
const filtersMap    = PersistStore(state => state.filtersMap);
const setFiltersMap = PersistStore(state => state.setFiltersMap);
const currentPageKey = props?.filterStateUrl || window.location.pathname;
const pageFiltersMap = filtersMap[currentPageKey];

// Restore sort on mount
const [sortSelected, setSortSelected] = useState(
  tableFunc.getInitialSortSelected(sortOptions, pageFiltersMap)
);

// Persist sort on change
const handleSort = (colId, direction) => {
  const sortVal = [`${colId} ${direction}`];
  setSortSelected(sortVal);
  setFiltersMap({
    ...filtersMap,
    [currentPageKey]: { filters: pageFiltersMap?.filters || [], sort: sortVal }
  });
};

// Pass quickFilter text directly to AgGridReact
// quickFilterText={quickFilter}
```

For client-side filter matching use `tableFunc.fetchDataSync` — pass `props.data` and the same `headers` shape (with `filterKey`, `showFilter`) as you would to GithubSimpleTable. This keeps filter logic consistent across both table types.

### SearchBar and BulkActionBar — reuse the pattern from DeviceEndpoints

Extract `SearchBar` and `BulkActionBar` as small self-contained components inside the same file. They are not shared across pages because their button actions differ. Keep them above the grid inside a `flex-column` wrapper:

```jsx
<div style={{ border: "1px solid #E1E3E5", borderRadius: 8, display: "flex", flexDirection: "column" }}>
  <SearchBar value={quickFilter} onChange={setQuickFilter} />
  <div style={{ flex: 1, minHeight: 0, borderRadius: "0 0 8px 8px", overflow: "hidden" }}>
    <AgGridReact theme={agTableThemeInner} quickFilterText={quickFilter} … />
  </div>
</div>
```

The `SearchBar` renders a plain `<input>` (not a Polaris component) inside this container so it blends with the grid border — do **not** use `<TextField>` from Polaris here.

### Standard AgGridReact props

```jsx
<AgGridReact
  theme={agTableThemeInner}
  rowData={data}
  columnDefs={COL_DEFS}
  defaultColDef={DEFAULT_COL_DEF}
  rowHeight={44}
  headerHeight={40}
  animateRows
  suppressCellFocus
  rowSelection="multiple"
  suppressRowClickSelection
  onRowClicked={handleRowClick}
  onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
  pagination
  paginationPageSize={20}
  paginationPageSizeSelector={[20, 50, 100]}
  quickFilterText={quickFilter}
  sideBar={{ toolPanels: ["columns", "filters"] }}
/>
```

For tree data add `treeData`, `getDataPath={d => d.path}`, `groupDefaultExpanded={0}`, and an `autoGroupColumnDef` with `suppressCount: true` and an `innerRenderer` component.

### Cell renderer conventions

- One component per renderer, named `<Concept>CellRenderer`.
- Accept `{ value, data, node }` from AG Grid params — destructure only what you need.
- Return `null` for empty/null values; never return an empty string or `undefined`.
- Use inline styles matching the design tokens in `myTheme` (colors from `#202223`, `#6D7175`, `#E1E3E5`, etc.). Do not introduce new colour values.

```jsx
function RiskScoreCellRenderer({ value }) {
  if (value == null) return null;
  const { bg, color } = getRiskColor(value);
  return (
    <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
      <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center",
        width: 44, height: 24, borderRadius: 12, fontSize: 12, fontWeight: 600,
        background: bg, color }}>
        {value.toFixed(1)}
      </span>
    </div>
  );
}
```

### Badge colours — reuse this palette

```js
// Type badges
const TYPE_STYLES = {
  "AI Agent":   { bg: "#EFF6FF", color: "#1D4ED8", border: "#BFDBFE" },
  "MCP Server": { bg: "#FFFBEB", color: "#92400E", border: "#FDE68A" },
  "LLM":        { bg: "#F0FDF4", color: "#166534", border: "#BBF7D0" },
};

// Severity badges
const SEVERITY_COLORS = {
  critical: { bg: "#DF2909", text: "#FFFBFB" },
  high:     { bg: "#FED3D1", text: "#202223" },
  medium:   { bg: "#FFD79D", text: "#202223" },
  low:      { bg: "#E4E5E7", text: "#202223" },
};
```

---

## Page Module Structure

Each feature page lives in `pages/<feature>/` and typically has:

```
pages/observe/api_collections/
├── ApiCollections.jsx   # Page component
├── api.js               # API calls for this feature
└── transform.js         # API response → UI data format
```

### `api.js`
Contains only API call functions. Each function calls `request()` from `util/request.js` (axios wrapper). Returns raw API response.

```js
import request from "../../../../util/request";

const someApi = {
  fetchItems: (params) => request("post", "/api/someEndpoint", params),
};
export default someApi;
```

### `transform.js`
Converts raw API responses into the format expected by the UI. **No API calls here.** Keeps the page component clean.

```js
const transform = {
  prettifyItems: (rawItems, extraContext) => {
    return rawItems.map(item => ({
      id: item.id,
      name: item.displayName,
      status: item.active ? "Active" : "Inactive",
      timestamp: func.prettifyEpoch(item.updatedAt),
    }));
  }
};
export default transform;
```

---

## State Management

### PersistStore (Zustand + sessionStorage)
`apps/main/PersistStore.js`

Persists across page navigations within a session. Use for:
- `allCollections` — full collections list (source of truth for collections)
- `collectionsMap` — fast id→collection lookup (in-memory only)
- `filtersMap` — per-page active filters (keyed by `filterStateUrl`)
- `tableInitialState` — tab state per table
- `tableSelectedTab` — selected tab per table

```js
import PersistStore from "../../../../main/PersistStore";

const allCollections = PersistStore(state => state.allCollections);
const setFiltersMap = PersistStore(state => state.setFiltersMap);
```

### LocalStorageStore (Zustand + localStorage)
`apps/main/LocalStorageStore.js`

Persists across browser sessions. Use for:
- `subCategoryMap` — subcategory definitions (test categories, vulnerability types)
- `categoryMap` — category metadata

```js
import LocalStorageStore from "../../../../main/LocalStorageStore";

const subCategoryMap = LocalStorageStore(state => state.subCategoryMap);
```

Both stores use Gzip compression via `createGzipStorage()` defined in `PersistStore.js`.

### TableStore (Zustand, in-memory)
`components/tables/TableStore.js`

In-memory only. Tracks per-table row selections and open tree levels. Used internally by the table system.

### tableReducer
`components/tables/tableReducer.js`

Actions: `APPLY_FILTER`, `TABLE_SELECTED_TAB`, `SELECT_ROW_ITEMS`, `OPEN_LEVELS`.
Used internally by the table components. Do not dispatch these directly from page code.

---

## Utility — `util/func.js`

Global helpers. Add generic utilities here, not in page-specific files.

Common exports:

```js
func.prettifyEpoch(epochMs)          // → "2h ago" / "Jan 12"
func.dateRangeReducer(state, action) // Immer reducer for date range state
func.saveToCSV(data, filename)       // Triggers CSV download via file-saver
func.toSentenceCase(str)
func.copyToClipboard(text)
func.getQueryStringFromParams(obj)
```

Icon mappings for common Polaris icons are centralised here — check before importing directly.

---

## Component Lifecycle Pattern

Every page that fetches data follows this exact shape — loading state, error surfaced via toast, success notification on mutations.

```jsx
import func from "../../../../util/func";

function FeaturePage() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);

  // Data fetch — always guard with loading, always show error toast
  useEffect(() => {
    setLoading(true);
    featureApi.fetchItems()
      .then(res => setData(transform.prettifyItems(res.items)))
      .catch(() => func.setToast(true, true, "Failed to load items"))
      .finally(() => setLoading(false));
  }, []);

  // Mutation — optimistic UI is fine; always confirm success or failure
  const handleSave = async (payload) => {
    try {
      await featureApi.saveItem(payload);
      func.setToast(true, false, "Saved successfully");
    } catch {
      func.setToast(true, true, "Save failed");
    }
  };
}
```

`func.setToast(active, isError, message)` — the first arg is always `true` to show, second is `true` for error (red) or `false` for success (green).

### Lazy-loaded routes

Pages are registered in the main router with `React.lazy()` to keep the initial bundle small:

```js
const FeaturePage = React.lazy(() => import("./pages/feature/FeaturePage"));

// In the route tree:
<Route path="/dashboard/feature" element={
  <Suspense fallback={<SpinnerCentered />}>
    <FeaturePage />
  </Suspense>
} />
```

`SpinnerCentered` is the standard fallback — import from `components/progress/SpinnerCentered`.

---

## Typical Page Pattern

```jsx
import React, { useState, useEffect } from "react";
import { ClockMinor } from "@shopify/polaris-icons";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import featureApi from "./api";
import transform from "./transform";
import func from "../../../../util/func";

function FeaturePage() {
  const [data, setData] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    featureApi.fetchItems().then(res => {
      setData(transform.prettifyItems(res.items));
      setLoading(false);
    });
  }, []);

  const headers = [
    { text: "Name",    value: "name",      itemOrder: 1 },
    { text: "Status",  value: "status",    itemOrder: 2 },
    { text: "Updated", value: "timestamp", itemOrder: 3, icon: ClockMinor },
  ];

  const getActions = item => [{
    items: [{ content: "Edit", onAction: () => handleEdit(item) }]
  }];

  return (
    <GithubSimpleTable
      data={data}
      headers={headers}
      resourceName={{ singular: "item", plural: "items" }}
      loading={loading}
      getActions={getActions}
      hasRowActions
      filterStateUrl={"/dashboard/feature/"}
    />
  );
}
```

---

## Icons

- **Polaris icons** (`@shopify/polaris-icons`): for UI chrome (buttons, table cells, status indicators).
- **Custom SVGs** (`apps/dashboard/web/public/`): for integrations, vendors, compliance logos, and feature-specific art. Reference as `/path/to/icon.svg` in `<img src>` or via `IconCacheService`.

When adding a new integration or brand icon, drop the `.svg` into the `public/` directory. Do not inline SVGs in component code.

---

## Shared Components (`components/shared/`)

Before building a new component, check here first. Notable ones:

| Component | Purpose |
|-----------|---------|
| `TooltipText` | Text that shows tooltip on truncation |
| `DropdownSearch` | Searchable dropdown |
| `ChartRenderer` | Generic chart wrapper |
| `DonutChart` | Donut/pie chart |
| `CollectionIcon` | API collection icon |
| `EventStreamComponent` | SSE / streaming UI |
| `FileUpload` / `FileUploadCard` | File upload primitives |
| `JiraTicketCreationModal` | Jira integration modal |
| `MarkdownComponents` | Renders markdown content |

Layout wrappers live in `components/layouts/` — use `PageWithMultipleCards` for standard page chrome.

---

## Checklist Before Writing Code

**Architecture**
- [ ] Functional component only — no class components
- [ ] All UI via Polaris — no raw `<div>/<button>/<input>`
- [ ] HTTP calls via `util/request.js` — no raw `fetch` or `axios`
- [ ] No new state library — use Zustand (PersistStore / LocalStorageStore)
- [ ] No prop drilling past 2 levels — use a store or context

**Structure**
- [ ] Checked `components/shared/` for an existing component before building a new one
- [ ] API calls are in `api.js`, transforms are in `transform.js`, UI is in the component
- [ ] Generic utilities added to `util/func.js`, not inlined in a page
- [ ] New SVGs placed in `public/`, not embedded in JSX
- [ ] No raw static data embedded in `.js` files
- [ ] Page registered with `React.lazy()` in the route tree

**UX**
- [ ] Loading state shown (`loading` prop or `SpinnerCentered`) while data fetches
- [ ] Errors surfaced to user via `func.setToast(true, true, message)` — never swallowed silently
- [ ] Mutations confirm success via `func.setToast(true, false, message)`

**State**
- [ ] Filter/sort state wired to PersistStore via `filterStateUrl` (GithubSimpleTable / AG Grid)
- [ ] `subCategoryMap` / `categoryMap` read from LocalStorageStore, not fetched inline
- [ ] `allCollections` read from PersistStore, not re-fetched per page

**AG Grid specific**
- [ ] Theme uses the standard `agTableTheme` params (accent `#9642FC`, row height 44, header 40)
- [ ] `agTableThemeInner` (no wrapper border) used when SearchBar sits above grid in same container
- [ ] File follows 4-layer structure: theme → cell renderers → col defs → table section component
- [ ] Each cell renderer is a named component — no inline logic in `cellRenderer:` field
- [ ] Sort and filter state persisted via `tableFunc` + PersistStore, same as GithubServerTable
- [ ] Badge colours reuse `TYPE_STYLES` / `SEVERITY_COLORS` palette — no new colour values introduced


