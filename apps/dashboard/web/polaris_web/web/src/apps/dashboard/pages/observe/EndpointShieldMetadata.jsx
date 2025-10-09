import { Text, HorizontalStack, VerticalStack, Box } from "@shopify/polaris"
import { useEffect, useReducer, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import func from "@/util/func"
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import { getMcpEndpointShieldData } from "./dummyData";
import PersistStore from "../../../main/PersistStore";
import { mapLabel } from "../../../main/labelHelper";

const headings = [
    {
        text: "Agent ID",
        value: "agentId",
        title: "Agent ID",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'agentId'
    },
    {
        text: "Device ID",
        value: "deviceId",
        title: "Device ID",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'deviceId'
    },
    {
        text: "Username",
        value: "username",
        title: "Username",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'username'
    },
    {
        text: "Email",
        value: "email",
        title: "Email",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'email'
    },
    {
        title: 'Last Heartbeat',
        text: "Last Heartbeat",
        value: "lastHeartbeatComp",
        sortActive: true,
        sortKey: 'lastHeartbeat',
        type: CellType.TEXT
    },
    {
        title: 'Last Deployed',
        text: "Last Deployed",
        value: "lastDeployedComp",
        sortKey: 'lastDeployed',
        sortActive: true,
        type: CellType.TEXT
    }
]

const sortOptions = [
    { label: 'Agent ID', value: 'agentId desc', directionLabel: 'Z-A', sortKey: 'agentId', columnIndex: 1 },
    { label: 'Agent ID', value: 'agentId asc', directionLabel: 'A-Z', sortKey: 'agentId', columnIndex: 1 },
    { label: 'Device ID', value: 'deviceId desc', directionLabel: 'Z-A', sortKey: 'deviceId', columnIndex: 2 },
    { label: 'Device ID', value: 'deviceId asc', directionLabel: 'A-Z', sortKey: 'deviceId', columnIndex: 2 },
    { label: 'Username', value: 'username desc', directionLabel: 'Z-A', sortKey: 'username', columnIndex: 3 },
    { label: 'Username', value: 'username asc', directionLabel: 'A-Z', sortKey: 'username', columnIndex: 3 },
    { label: 'Email', value: 'email desc', directionLabel: 'Z-A', sortKey: 'email', columnIndex: 4 },
    { label: 'Email', value: 'email asc', directionLabel: 'A-Z', sortKey: 'email', columnIndex: 4 },
    { label: 'Last Heartbeat', value: 'lastHeartbeat desc', directionLabel: 'Newest', sortKey: 'lastHeartbeat', columnIndex: 5 },
    { label: 'Last Heartbeat', value: 'lastHeartbeat asc', directionLabel: 'Oldest', sortKey: 'lastHeartbeat', columnIndex: 5 },
    { label: 'Last Deployed', value: 'lastDeployed desc', directionLabel: 'Newest', sortKey: 'lastDeployed', columnIndex: 6 },
    { label: 'Last Deployed', value: 'lastDeployed asc', directionLabel: 'Oldest', sortKey: 'lastDeployed', columnIndex: 6 },
];

const filters = [
    {
        key: 'username',
        label: 'Username',
        title: 'Username',
        choices: [],
    },
    {
        key: 'email',
        label: 'Email',
        title: 'Email',
        choices: [],
    }
]

const resourceName = {
    singular: 'agent',
    plural: 'agents',
};

const convertDataIntoTableFormat = (agentData) => {
    let temp = {...agentData}
    temp['lastHeartbeatComp'] = func.prettifyEpoch(temp?.lastHeartbeat)
    temp['lastDeployedComp'] = func.prettifyEpoch(temp?.lastDeployed)
    return temp;
}

// Use fixed dummy data from external file
const generateDummyData = () => {
    return getMcpEndpointShieldData();
}

function EndpointShieldMetadata() {
    const [loading, setLoading] = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;

        try {
            // Generate dummy data
            const allDummyData = generateDummyData();

            // Apply filters
            let filteredData = allDummyData.filter(agent => {
                // Date range filter
                if (agent.lastHeartbeat < startTimestamp || agent.lastHeartbeat > endTimestamp) {
                    return false;
                }

                // Username filter
                if (filters.username && filters.username.length > 0 && !filters.username.includes(agent.username)) {
                    return false;
                }

                // Email filter
                if (filters.email && filters.email.length > 0 && !filters.email.includes(agent.email)) {
                    return false;
                }

                // Search query
                if (queryValue) {
                    const searchLower = queryValue.toLowerCase();
                    const matchesSearch =
                        agent.agentId.toLowerCase().includes(searchLower) ||
                        agent.deviceId.toLowerCase().includes(searchLower) ||
                        agent.username.toLowerCase().includes(searchLower) ||
                        agent.email.toLowerCase().includes(searchLower);
                    if (!matchesSearch) return false;
                }

                return true;
            });

            // Apply sorting
            if (sortKey) {
                filteredData.sort((a, b) => {
                    let aVal = a[sortKey];
                    let bVal = b[sortKey];

                    if (typeof aVal === 'string') {
                        aVal = aVal.toLowerCase();
                        bVal = bVal.toLowerCase();
                    }

                    if (sortOrder === 'asc') {
                        return aVal > bVal ? 1 : -1;
                    } else {
                        return aVal < bVal ? 1 : -1;
                    }
                });
            }

            total = filteredData.length;

            // Apply pagination
            const paginatedData = filteredData.slice(skip, skip + limit);

            ret = paginatedData.map(agent => convertDataIntoTableFormat(agent));

        } catch (error) {
            console.error("Error fetching MCP Endpoint Shield metadata:", error)
        }

        setLoading(false);
        return {value: ret, total: total};
    }

    useEffect(() => {
        // Populate filter choices with unique values from dummy data
        const dummyData = generateDummyData();
        const uniqueUsernames = [...new Set(dummyData.map(a => a.username))];
        const uniqueEmails = [...new Set(dummyData.map(a => a.email))];

        filters[0].choices = uniqueUsernames.map(username => ({ label: username, value: username }));
        filters[1].choices = uniqueEmails.map(email => ({ label: email, value: email }));
    }, [])

    const primaryActions = (
        <HorizontalStack gap={"2"}>
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
    )

    return (
        <PageWithMultipleCards
            title={
                <Text as="div" variant="headingLg">
                    {mapLabel("Endpoint Shield", dashboardCategory)}
                </Text>
            }
            backUrl="/dashboard/observe"
            primaryAction={primaryActions}
            components = {[
                <GithubServerTable
                    key={startTimestamp + endTimestamp}
                    headers={headings}
                    resourceName={resourceName}
                    appliedFilters={[]}
                    sortOptions={sortOptions}
                    disambiguateLabel={disambiguateLabel}
                    loading={loading}
                    fetchData={fetchData}
                    filters={filters}
                    hideQueryField={false}
                    useNewRow={true}
                    condensedHeight={true}
                    pageLimit={20}
                    headings={headings}
                />
            ]}
        />
    )
}

export default EndpointShieldMetadata
