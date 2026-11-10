import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Box, Button, ChoiceList, HorizontalStack, Popover, Tabs, Text } from "@shopify/polaris";
import { CancelSmallMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import AgGridTable from "../../../../components/tables/AgGridTable";
import Pressable from "../../../dashboard/ask/components/Pressable";
import settingRequests from "../../../settings/api";
import {
    AgentCellRenderer, CountCellRenderer, DeviceCellRenderer, EmployeeCellRenderer, GuardrailModeCellRenderer,
    LastSeenCellRenderer, OsCellRenderer, OwnerCellRenderer, RiskCellRenderer, FlagsCellRenderer, TagsCellRenderer, TeamsCellRenderer, UnapprovedCellRenderer, ViolationsCellRenderer,
} from "./PeopleCellRenderers";
import {
    CATEGORY_LABELS, DEVICE_CHIPS, EMPLOYEE_CHIPS, EMPTY_PEOPLE_FILTERS, FILTER_LABELS, FILTER_OPTION_LABELS, LIST_FILTERS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, MODE_LABELS, osLabel, PEOPLE_TABLE_HEIGHT, RISK_MAX, SEARCH_HINTS,
} from "./peopleConstants";
import EditDeviceTagsModal from "../../agentic/EditDeviceTagsModal";
import transform from "./transform";

const DEFAULT_COL_DEF = { sortable: true, resizable: true, filter: false, cellStyle: { display: "flex", alignItems: "center" } };
const ROW_HEIGHT = 48;

const employeeColumns = (dataOptions) => [
    { field: "username", headerName: "Employee", flex: 2, minWidth: 200, cellRenderer: EmployeeCellRenderer },
    { field: "teams", headerName: "Team", flex: 1, minWidth: 140, sortable: false, cellRenderer: TeamsCellRenderer, ...setFilter(dataOptions.teams, (value) => value), filterValueGetter: (params) => params.data?.teams || [] },
    { field: "deviceCount", headerName: "Devices", width: 100, cellRenderer: CountCellRenderer },
    { field: "toolCount", headerName: "AI tools", width: 100, cellRenderer: CountCellRenderer },
    { field: "unapprovedTools", headerName: "Unapproved", width: 130, cellRenderer: UnapprovedCellRenderer },
    { field: "personalAccounts", headerName: "Personal accounts", width: 150, cellRenderer: CountCellRenderer },
    { field: "violations", headerName: "Violations (7d)", width: 150, cellRenderer: ViolationsCellRenderer },
    { field: "riskScore", headerName: "Risk (0-5)", width: 110, cellRenderer: RiskCellRenderer },
    { field: "lastActive", headerName: "Last active", width: 150, cellRenderer: LastSeenCellRenderer },
];

const deviceColumns = (dataOptions) => [
    { field: "name", headerName: "Device", flex: 2, minWidth: 220, cellRenderer: DeviceCellRenderer, checkboxSelection: true, headerCheckboxSelection: true },
    {
        field: "os", headerName: "OS/Browser", flex: 1.2, minWidth: 170, cellRenderer: OsCellRenderer,
        ...setFilter(dataOptions.osBrowser, osBrowserLabel),
        filterValueGetter: (params) => (params.data ? (params.data.extension ? `browser:${params.data.browser || "other"}` : `os:${params.data.os}`) : null),
    },
    { field: "owner", headerName: "Owner", flex: 1, minWidth: 140, cellRenderer: OwnerCellRenderer },
    { field: "hasPersonalAccount", headerName: "Flags", flex: 1.4, minWidth: 260, sortable: false, cellRenderer: FlagsCellRenderer },
    { field: "tags", headerName: "Tags", flex: 1.2, minWidth: 220, sortable: false, cellRenderer: TagsCellRenderer, ...setFilter(dataOptions.tags, (value) => value), filterValueGetter: (params) => (params.data?.tags || []).map((tag) => `${tag.key}=${tag.value}`) },
    { field: "health", headerName: "Atlas agent", flex: 1.5, minWidth: 220, cellRenderer: AgentCellRenderer, ...setFilter(dataOptions.healthStates, (value) => HEALTH_LABELS[value] || value) },
    { field: "guardrailMode", headerName: "Guardrail mode", width: 150, cellRenderer: GuardrailModeCellRenderer, ...setFilter(dataOptions.guardrailModes, (value) => MODE_LABELS[value] || value) },
    { field: "toolCount", headerName: "AI tools", width: 100, cellRenderer: CountCellRenderer },
    { field: "unapprovedTools", headerName: "Unapproved", width: 130, cellRenderer: UnapprovedCellRenderer },
    { field: "violations", headerName: "Violations (7d)", width: 150, cellRenderer: ViolationsCellRenderer },
    { field: "riskScore", headerName: "Risk", width: 90, cellRenderer: RiskCellRenderer },
    { field: "lastHeartbeat", headerName: "Last check-in", width: 150, cellRenderer: LastSeenCellRenderer },
];

function setFilter(values, format) {
    return { filter: "agSetColumnFilter", filterParams: { values: values || [], valueFormatter: (params) => format(params.value) } };
}

function isMonitorOnly(filters) {
    return filters.guardrailModes.length === 1 && filters.guardrailModes[0] === "monitor";
}

function isChipActive(chipKey, filters) {
    if (chipKey === "highRisk") return filters.riskMin >= HIGH_RISK_THRESHOLD;
    if (chipKey === "monitorMode") return isMonitorOnly(filters);
    return !!filters[chipKey];
}

function toggleChip(chipKey, filters, onFiltersChange) {
    if (chipKey === "highRisk") {
        onFiltersChange({ riskMin: filters.riskMin >= HIGH_RISK_THRESHOLD ? 0 : HIGH_RISK_THRESHOLD, riskMax: RISK_MAX });
        return;
    }
    if (chipKey === "monitorMode") {
        onFiltersChange({ guardrailModes: isMonitorOnly(filters) ? [] : ["monitor"] });
        return;
    }
    onFiltersChange({ [chipKey]: !filters[chipKey] });
}

function appliedTags(filters) {
    const tags = [];
    filters.teams.forEach((team) => tags.push({ key: `team-${team}`, label: `Team: ${team}`, clear: { teams: filters.teams.filter((x) => x !== team) } }));
    filters.violationSeverities.forEach((severity) => tags.push({ key: `severity-${severity}`, label: `Violations: ${capitalize(severity)}`, clear: { violationSeverities: filters.violationSeverities.filter((x) => x !== severity) } }));
    filters.tags.forEach((tag) => tags.push({ key: `tag-${tag}`, label: `Tag: ${tag}`, clear: { tags: filters.tags.filter((x) => x !== tag) } }));
    filters.osList.forEach((os) => tags.push({ key: `os-${os}`, label: osLabel(os) || os, clear: { osList: filters.osList.filter((x) => x !== os) } }));
    filters.browserList.forEach((browser) => tags.push({ key: `browser-${browser}`, label: capitalize(browser), clear: { browserList: filters.browserList.filter((x) => x !== browser) } }));
    filters.categories.forEach((category) => tags.push({ key: `cat-${category}`, label: CATEGORY_LABELS[category] || category, clear: { categories: filters.categories.filter((x) => x !== category) } }));
    if (!isMonitorOnly(filters)) {
        filters.guardrailModes.forEach((mode) => tags.push({ key: `mode-${mode}`, label: MODE_LABELS[mode] || mode, clear: { guardrailModes: filters.guardrailModes.filter((x) => x !== mode) } }));
    }
    filters.healthStates.forEach((state) => tags.push({ key: `health-${state}`, label: HEALTH_LABELS[state] || state, clear: { healthStates: filters.healthStates.filter((x) => x !== state) } }));
    const isCustomRiskRange = (filters.riskMin > 0 || filters.riskMax < RISK_MAX)
        && !(filters.riskMin === HIGH_RISK_THRESHOLD && filters.riskMax === RISK_MAX);
    if (isCustomRiskRange) {
        tags.push({ key: "risk", label: `Risk score ${filters.riskMin} to ${filters.riskMax}`, clear: { riskMin: 0, riskMax: RISK_MAX } });
    }
    return tags;
}

function FilterChip({ label, active, onClick }) {
    return (
        <Pressable
            accessibilityLabel={label}
            onClick={onClick}
            background="bg"
            hoverBackground="bg-subdued"
            borderWidth="1"
            borderColor={active ? "border-magic-strong" : "border"}
            borderRadius="2"
            paddingBlockStart="2"
            paddingBlockEnd="2"
            paddingInlineStart="3"
            paddingInlineEnd="3"
        >
            <Box as="span" color={active ? "text-ask-magic" : undefined}>
                <Text as="span" fontWeight={active ? "semibold" : "regular"}>{label}</Text>
            </Box>
        </Pressable>
    );
}

function ListFilter({ field, options, selected, onChange }) {
    const [open, setOpen] = useState(false);
    const label = FILTER_LABELS[field];
    return (
        <Popover
            active={open}
            onClose={() => setOpen(false)}
            activator={<Button disclosure onClick={() => setOpen((current) => !current)}>{selected.length ? `${label} (${selected.length})` : label}</Button>}
        >
            <Box padding="3">
                <ChoiceList allowMultiple title={label} titleHidden choices={options} selected={selected} onChange={(values) => onChange({ [field]: values })} />
            </Box>
        </Popover>
    );
}

const capitalize = (text) => text.charAt(0).toUpperCase() + text.slice(1);

function osBrowserLabel(value) {
    const [type, name] = value.split(":");
    return type === "os" ? (osLabel(name) || name) : capitalize(name);
}

function filterOptions(field, dataOptions) {
    return (dataOptions[field] || []).map((value) => ({ label: field === "osBrowser" ? osBrowserLabel(value) : (FILTER_OPTION_LABELS[field]?.[value] || value), value }));
}

function selectedOsBrowser(filters) {
    return [...filters.osList.map((os) => `os:${os}`), ...filters.browserList.map((browser) => `browser:${browser}`)];
}

function splitOsBrowser(values) {
    const pick = (type) => values.filter((value) => value.startsWith(`${type}:`)).map((value) => value.slice(type.length + 1));
    return { osList: pick("os"), browserList: pick("browser") };
}

const COLUMN_FILTER_FIELDS = {
    employees: { teams: "teams" },
    devices: { osBrowser: "os", healthStates: "health", guardrailModes: "guardrailMode", tags: "tags" },
};

function columnFilterValues(filters) {
    return {
        teams: filters.teams,
        osBrowser: selectedOsBrowser(filters),
        healthStates: filters.healthStates,
        guardrailModes: filters.guardrailModes,
        tags: filters.tags,
    };
}

function modelFromFilters(tab, filters) {
    const values = columnFilterValues(filters);
    const model = {};
    Object.entries(COLUMN_FILTER_FIELDS[tab]).forEach(([key, column]) => {
        if (values[key].length) model[column] = { filterType: "set", values: values[key] };
    });
    return model;
}

function filtersFromModel(tab, model) {
    const picked = (column) => model[column]?.values || [];
    return tab === "employees"
        ? { teams: picked("teams") }
        : { ...splitOsBrowser(picked("os")), healthStates: picked("health"), guardrailModes: picked("guardrailMode"), tags: picked("tags") };
}

const sameValues = (a, b) => JSON.stringify([...a].sort()) === JSON.stringify([...b].sort());

function FilterRow({ tab, filters, onFiltersChange, dataOptions }) {
    const chips = tab === "employees" ? EMPLOYEE_CHIPS : DEVICE_CHIPS;
    const tags = appliedTags(filters);
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {LIST_FILTERS[tab].map((field) => (
                <ListFilter
                    key={field}
                    field={field}
                    options={filterOptions(field, dataOptions)}
                    selected={field === "osBrowser" ? selectedOsBrowser(filters) : filters[field]}
                    onChange={field === "osBrowser" ? (patch) => onFiltersChange(splitOsBrowser(patch.osBrowser)) : onFiltersChange}
                />
            ))}
            {chips.map((chip) => (
                <FilterChip key={chip.key} label={chip.label} active={isChipActive(chip.key, filters)}
                    onClick={() => toggleChip(chip.key, filters, onFiltersChange)} />
            ))}
            {tags.map((tag) => (
                <Button key={tag.key} icon={CancelSmallMinor} accessibilityLabel={`Remove ${tag.label}`} onClick={() => onFiltersChange(tag.clear)}>{tag.label}</Button>
            ))}
            {tags.length > 0 && <Button plain onClick={() => onFiltersChange(EMPTY_PEOPLE_FILTERS)}>Clear filters</Button>}
        </HorizontalStack>
    );
}

function PeopleTable({ tab, onTabChange, filters, onFiltersChange, deviceScope, counts, search, onSearchChange, violationsAvailable, riskAvailable, onOpen, selected, filterOptions: dataOptions }) {
    const isEmployees = tab === "employees";
    const gridRef = useRef(null);
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const [selectedRows, setSelectedRows] = useState([]);
    const [tagNames, setTagNames] = useState(null);
    const [refreshKey, setRefreshKey] = useState(0);
    const tabs = [
        { id: "employees", content: "Employees", badge: counts.employees != null ? counts.employees.toLocaleString("en-US") : undefined },
        { id: "devices", content: "Devices", badge: counts.devices != null ? counts.devices.toLocaleString("en-US") : undefined },
    ];

    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const params = {
            ...transform.peopleParams(deviceScope, filters, searchString),
            skip, limit, sortKey, sortOrder: sortOrder === -1 ? 1 : -1,
        };
        const request = isEmployees ? settingRequests.fetchAtlasEmployees(params) : settingRequests.fetchAtlasDevices(params);
        setLoading(true);
        return request
            .then((response) => {
                const value = response?.atlasRows || [];
                setRows(value);
                return { value, total: response?.atlasTotal || 0 };
            })
            .catch(() => {
                func.setToast(true, true, `Failed to load ${isEmployees ? "employees" : "devices"}`);
                setRows([]);
                return { value: [], total: 0 };
            })
            .finally(() => setLoading(false));
    }, [deviceScope, filters, isEmployees]);

    const columnDefs = useMemo(() => (isEmployees ? employeeColumns(dataOptions) : deviceColumns(dataOptions)), [isEmployees, dataOptions]);
    const initialState = useMemo(() => ({ filter: { filterModel: modelFromFilters(tab, filters) } }), [tab, filters]);
    const onColumnFilterChanged = useCallback((event) => {
        const patch = filtersFromModel(tab, event.api.getFilterModel());
        if (Object.keys(patch).every((key) => sameValues(patch[key], filters[key]))) return;
        onFiltersChange(patch);
    }, [tab, filters, onFiltersChange]);

    const selectedId = selected?.kind === (isEmployees ? "employee" : "device") ? selected.id : null;
    const getRowStyle = useCallback(({ data }) => {
        const id = isEmployees ? data?.username : data?.key;
        return { cursor: "pointer", background: selectedId && id === selectedId ? "var(--p-color-bg-primary-subdued-hover)" : undefined };
    }, [isEmployees, selectedId]);
    useEffect(() => { gridRef.current?.api?.redrawRows(); }, [selectedId]);

    const gridKey = useMemo(
        () => `${tab}-${deviceScope}-${JSON.stringify(filters)}-${refreshKey}`,
        [tab, deviceScope, filters, refreshKey]
    );
    useEffect(() => { setSelectedRows([]); }, [gridKey]);

    const openTagModal = () => {
        const owners = [...new Set(selectedRows.map((row) => row.owner).filter(Boolean))];
        if (owners.length === 0) {
            func.setToast(true, true, "The selected devices have no owner to tag");
            return;
        }
        setTagNames(owners);
    };

    return (
        <Box background="bg" borderRadius="3" borderWidth="1" borderColor="border-subdued">
            <Tabs tabs={tabs} selected={isEmployees ? 0 : 1} onSelect={(index) => onTabChange(tabs[index].id)} />
            <AgGridTable
                key={gridKey}
                rowData={rows}
                loading={loading}
                columnDefs={columnDefs}
                initialState={initialState}
                onFilterChanged={onColumnFilterChanged}
                defaultColDef={DEFAULT_COL_DEF}
                onServerFetch={onServerFetch}
                filterStateUrl={`/dashboard/observe/devices-employees/${tab}`}
                searchValue={search}
                onSearchChange={onSearchChange}
                searchPlaceholder={SEARCH_HINTS[tab]}
                searchBelow={<FilterRow tab={tab} filters={filters} onFiltersChange={onFiltersChange} dataOptions={dataOptions} />}
                height={PEOPLE_TABLE_HEIGHT}
                domLayout="normal"
                rowHeight={ROW_HEIGHT}
                noOuterBorder
                context={{ violationsAvailable, riskAvailable }}
                onRowClicked={(event) => event.data && onOpen(isEmployees ? "employee" : "device", isEmployees ? event.data.username : event.data.key)}
                gridRef={gridRef}
                getRowStyle={getRowStyle}
                rowSelection={isEmployees ? undefined : "multiple"}
                suppressRowClickSelection
                onSelectionChanged={(event) => setSelectedRows(event.api.getSelectedRows())}
                bulkActionCount={selectedRows.length}
                bulkActions={isEmployees ? [] : [{ content: "Edit device tags", onAction: openTagModal, requires: "api/bulkUpdateUserDeviceTag" }]}
                onClearBulk={() => gridRef.current?.api?.deselectAll()}
            />
            {tagNames && (
                <EditDeviceTagsModal
                    usernames={tagNames}
                    rows={selectedRows.map((row) => ({ id: row.owner, groupName: row.owner, tags: row.tags }))}
                    onClose={() => setTagNames(null)}
                    onSaved={() => { setTagNames(null); setRefreshKey((key) => key + 1); }}
                />
            )}
        </Box>
    );
}

export default PeopleTable;
