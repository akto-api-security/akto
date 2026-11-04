import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Box, Button, ChoiceList, HorizontalStack, Popover, Tabs, Text } from "@shopify/polaris";
import { CancelSmallMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import AgGridTable from "../../../../components/tables/AgGridTable";
import Pressable from "../../../dashboard/ask/components/Pressable";
import settingRequests from "../../../settings/api";
import {
    AgentCellRenderer, CountCellRenderer, DeviceCellRenderer, EmployeeCellRenderer, GuardrailModeCellRenderer,
    LastSeenCellRenderer, OsCellRenderer, OwnerCellRenderer, RiskCellRenderer, UnapprovedCellRenderer, ViolationsCellRenderer,
} from "./PeopleCellRenderers";
import {
    CATEGORY_LABELS, DEVICE_CHIPS, EMPLOYEE_CHIPS, EMPTY_PEOPLE_FILTERS, FILTER_LABELS, FILTER_OPTION_LABELS, LIST_FILTERS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, MODE_LABELS, OS_LABELS, PEOPLE_TABLE_HEIGHT, RISK_MAX, SEARCH_HINTS,
} from "./peopleConstants";
import transform from "./transform";

const DEFAULT_COL_DEF = { sortable: true, resizable: false, filter: false, suppressHeaderMenuButton: true, cellStyle: { display: "flex", alignItems: "center" } };
const ROW_HEIGHT = 48;

const EMPLOYEE_COLUMNS = [
    { field: "username", headerName: "Employee", flex: 2, minWidth: 200, cellRenderer: EmployeeCellRenderer },
    { field: "deviceCount", headerName: "Devices", width: 100, cellRenderer: CountCellRenderer },
    { field: "toolCount", headerName: "AI tools", width: 100, cellRenderer: CountCellRenderer },
    { field: "unapprovedTools", headerName: "Unapproved", width: 130, cellRenderer: UnapprovedCellRenderer },
    { field: "personalAccounts", headerName: "Personal accounts", width: 150, cellRenderer: CountCellRenderer },
    { field: "violations", headerName: "Violations (7d)", width: 150, cellRenderer: ViolationsCellRenderer },
    { field: "riskScore", headerName: "Risk (0-5)", width: 110, cellRenderer: RiskCellRenderer },
    { field: "lastActive", headerName: "Last active", width: 150, cellRenderer: LastSeenCellRenderer },
];

const DEVICE_COLUMNS = [
    { field: "name", headerName: "Device", flex: 2, minWidth: 220, cellRenderer: DeviceCellRenderer },
    { field: "os", headerName: "OS/Browser", flex: 1.2, minWidth: 170, cellRenderer: OsCellRenderer },
    { field: "owner", headerName: "Owner", flex: 1, minWidth: 140, cellRenderer: OwnerCellRenderer },
    { field: "health", headerName: "Atlas agent", flex: 1.5, minWidth: 220, cellRenderer: AgentCellRenderer },
    { field: "guardrailMode", headerName: "Guardrail mode", width: 150, cellRenderer: GuardrailModeCellRenderer },
    { field: "toolCount", headerName: "AI tools", width: 100, cellRenderer: CountCellRenderer },
    { field: "unapprovedTools", headerName: "Unapproved", width: 130, cellRenderer: UnapprovedCellRenderer },
    { field: "violations", headerName: "Violations (7d)", width: 150, cellRenderer: ViolationsCellRenderer },
    { field: "riskScore", headerName: "Risk", width: 90, cellRenderer: RiskCellRenderer },
    { field: "lastHeartbeat", headerName: "Last check-in", width: 150, cellRenderer: LastSeenCellRenderer },
];

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
    filters.osList.forEach((os) => tags.push({ key: `os-${os}`, label: OS_LABELS[os] || os, clear: { osList: filters.osList.filter((x) => x !== os) } }));
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
    return type === "os" ? (OS_LABELS[name] || name) : capitalize(name);
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

function PeopleTable({ tab, onTabChange, filters, onFiltersChange, deviceScope, counts, refreshKey, search, onSearchChange, violationsAvailable, riskAvailable, onOpen, selected, filterOptions: dataOptions }) {
    const isEmployees = tab === "employees";
    const gridRef = useRef(null);
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
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

    const selectedId = selected?.kind === (isEmployees ? "employee" : "device") ? selected.id : null;
    const getRowStyle = useCallback(({ data }) => {
        const id = isEmployees ? data?.username : data?.key;
        return { cursor: "pointer", background: selectedId && id === selectedId ? "var(--p-color-bg-primary-subdued-hover)" : undefined };
    }, [isEmployees, selectedId]);
    useEffect(() => { gridRef.current?.api?.redrawRows(); }, [selectedId]);

    const gridKey = useMemo(
        () => `${tab}-${deviceScope}-${refreshKey}-${JSON.stringify(filters)}`,
        [tab, deviceScope, refreshKey, filters]
    );

    return (
        <Box background="bg" borderRadius="3" borderWidth="1" borderColor="border-subdued">
            <Tabs tabs={tabs} selected={isEmployees ? 0 : 1} onSelect={(index) => onTabChange(tabs[index].id)} />
            <AgGridTable
                key={gridKey}
                rowData={rows}
                loading={loading}
                columnDefs={isEmployees ? EMPLOYEE_COLUMNS : DEVICE_COLUMNS}
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
                sideBar={false}
                context={{ violationsAvailable, riskAvailable }}
                onRowClicked={(event) => event.data && onOpen(isEmployees ? "employee" : "device", isEmployees ? event.data.username : event.data.key)}
                gridRef={gridRef}
                getRowStyle={getRowStyle}
                rowSelection={undefined}
            />
        </Box>
    );
}

export default PeopleTable;
