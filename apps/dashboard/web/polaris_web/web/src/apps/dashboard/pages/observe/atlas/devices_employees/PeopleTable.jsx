import { useCallback, useMemo, useState } from "react";
import { Box, Button, HorizontalStack, Icon, Tabs, Tag, TextField } from "@shopify/polaris";
import { SearchMinor } from "@shopify/polaris-icons";
import func from "@/util/func";
import AgGridTable from "../../../../components/tables/AgGridTable";
import settingRequests from "../../../settings/api";
import {
    AgentCellRenderer, CountCellRenderer, DeviceCellRenderer, EmployeeCellRenderer, GuardrailModeCellRenderer,
    LastSeenCellRenderer, OsCellRenderer, OwnerCellRenderer, RiskCellRenderer, UnapprovedCellRenderer, ViolationsCellRenderer,
} from "./PeopleCellRenderers";
import {
    CATEGORY_LABELS, DEVICE_CHIPS, EMPLOYEE_CHIPS, EMPTY_PEOPLE_FILTERS, HEALTH_LABELS, HIGH_RISK_THRESHOLD, OS_LABELS,
} from "./peopleConstants";

const DEFAULT_COL_DEF = { sortable: true, resizable: false, filter: false, suppressHeaderMenuButton: true, cellStyle: { display: "flex", alignItems: "center" } };
const ROW_HEIGHT = 48;

const EMPLOYEE_COLUMNS = [
    { field: "username", headerName: "Employee", flex: 2, minWidth: 200, cellRenderer: EmployeeCellRenderer },
    { field: "deviceCount", headerName: "Devices", width: 100, cellRenderer: CountCellRenderer },
    { field: "toolCount", headerName: "AI tools", width: 100, cellRenderer: CountCellRenderer },
    { field: "unapprovedTools", headerName: "Unapproved", width: 130, cellRenderer: UnapprovedCellRenderer },
    { field: "personalAccounts", headerName: "Personal accounts", width: 150, cellRenderer: CountCellRenderer },
    { field: "violations", headerName: "Violations (7d)", width: 150, cellRenderer: ViolationsCellRenderer },
    { field: "risk", headerName: "Risk score", width: 190, cellRenderer: RiskCellRenderer },
    { field: "lastActive", headerName: "Last active", width: 150, cellRenderer: LastSeenCellRenderer },
];

const DEVICE_COLUMNS = [
    { field: "name", headerName: "Device", flex: 2, minWidth: 220, cellRenderer: DeviceCellRenderer },
    { field: "os", headerName: "OS", flex: 1.2, minWidth: 170, cellRenderer: OsCellRenderer },
    { field: "owner", headerName: "Owner", flex: 1, minWidth: 140, cellRenderer: OwnerCellRenderer },
    { field: "health", headerName: "Atlas agent", flex: 1.5, minWidth: 220, cellRenderer: AgentCellRenderer },
    { field: "guardrailMode", headerName: "Guardrail mode", width: 150, cellRenderer: GuardrailModeCellRenderer },
    { field: "toolCount", headerName: "AI tools", width: 100, cellRenderer: CountCellRenderer },
    { field: "unapprovedTools", headerName: "Unapproved", width: 130, cellRenderer: UnapprovedCellRenderer },
    { field: "lastHeartbeat", headerName: "Last check-in", width: 150, cellRenderer: LastSeenCellRenderer },
];

function isChipActive(chipKey, filters) {
    return chipKey === "highRisk" ? filters.riskMin >= HIGH_RISK_THRESHOLD : !!filters[chipKey];
}

function toggleChip(chipKey, filters, onFiltersChange) {
    if (chipKey === "highRisk") {
        onFiltersChange({ riskMin: filters.riskMin >= HIGH_RISK_THRESHOLD ? 0 : HIGH_RISK_THRESHOLD, riskMax: 100 });
        return;
    }
    onFiltersChange({ [chipKey]: !filters[chipKey] });
}

function appliedTags(filters) {
    const tags = [];
    filters.osList.forEach((os) => tags.push({ key: `os-${os}`, label: OS_LABELS[os] || os, clear: { osList: filters.osList.filter((x) => x !== os) } }));
    filters.categories.forEach((category) => tags.push({ key: `cat-${category}`, label: CATEGORY_LABELS[category] || category, clear: { categories: filters.categories.filter((x) => x !== category) } }));
    filters.healthStates.forEach((state) => tags.push({ key: `health-${state}`, label: HEALTH_LABELS[state] || state, clear: { healthStates: filters.healthStates.filter((x) => x !== state) } }));
    if (filters.riskMin > 0 || filters.riskMax < 100) {
        tags.push({ key: "risk", label: `Risk ${filters.riskMin}-${filters.riskMax}`, clear: { riskMin: 0, riskMax: 100 } });
    }
    return tags;
}

function SearchRow({ value, onChange, placeholder, tab, filters, onFiltersChange }) {
    const chips = tab === "employees" ? EMPLOYEE_CHIPS : DEVICE_CHIPS;
    return (
        <Box padding="3">
            <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                <Box width="100%">
                    <TextField
                        label={placeholder}
                        labelHidden
                        autoComplete="off"
                        prefix={<Icon source={SearchMinor} />}
                        placeholder={placeholder}
                        value={value}
                        onChange={onChange}
                        clearButton
                        onClearButtonClick={() => onChange("")}
                    />
                </Box>
                {chips.map((chip) => (
                    <Button key={chip.key} pressed={isChipActive(chip.key, filters)} onClick={() => toggleChip(chip.key, filters, onFiltersChange)}>
                        {chip.label}
                    </Button>
                ))}
            </HorizontalStack>
        </Box>
    );
}

function AppliedTags({ filters, onFiltersChange }) {
    const tags = appliedTags(filters);
    return (
        <HorizontalStack gap="2" blockAlign="center">
            {tags.map((tag) => <Tag key={tag.key} onRemove={() => onFiltersChange(tag.clear)}>{tag.label}</Tag>)}
            <Button plain onClick={() => onFiltersChange(EMPTY_PEOPLE_FILTERS)}>Clear filters</Button>
        </HorizontalStack>
    );
}

function PeopleTable({ tab, onTabChange, filters, onFiltersChange, deviceScope, counts, refreshKey, search, onSearchChange, violationsAvailable }) {
    const isEmployees = tab === "employees";
    const hasAppliedFilters = appliedTags(filters).length > 0;
    const [rows, setRows] = useState([]);
    const [loading, setLoading] = useState(true);
    const tabs = [
        { id: "employees", content: "Employees", badge: counts.employees != null ? counts.employees.toLocaleString("en-US") : undefined },
        { id: "devices", content: "Devices", badge: counts.devices != null ? counts.devices.toLocaleString("en-US") : undefined },
    ];

    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const params = {
            deviceScope, skip, limit, sortKey, sortOrder: sortOrder === -1 ? 1 : -1, atlasQuery: searchString,
            atlasOs: filters.osList, toolCategories: filters.categories, healthStates: filters.healthStates,
            hasUnapproved: filters.hasUnapproved, usesPersonal: filters.usesPersonal, agentIssues: filters.agentIssues,
            monitorMode: filters.monitorMode, riskMin: filters.riskMin, riskMax: filters.riskMax,
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
                renderSearch={({ value, onChange }) => (
                    <SearchRow
                        value={value}
                        onChange={onChange}
                        placeholder={isEmployees ? "Search employees or their devices" : "Search devices, owners or OS"}
                        tab={tab}
                        filters={filters}
                        onFiltersChange={onFiltersChange}
                    />
                )}
                searchBelow={hasAppliedFilters ? <AppliedTags filters={filters} onFiltersChange={onFiltersChange} /> : null}
                rowHeight={ROW_HEIGHT}
                noOuterBorder
                sideBar={false}
                context={{ violationsAvailable }}
                getRowStyle={() => ({ cursor: "default" })}
                rowSelection={undefined}
            />
        </Box>
    );
}

export default PeopleTable;
