import GithubSimpleTable from '../../../../components/tables/GithubSimpleTable';
import {
    ClockMinor,
    LockMinor,
    GlobeMinor,
    FraudProtectMinor
} from '@shopify/polaris-icons';
import StyledEndpoint from "./StyledEndpoint";
import func from "@/util/func"


const resourceName = {
    singular: 'API endpoint',
    plural: 'API endpoints',
};

const headers = [
    {
        text: 'Endpoint',
        value: 'parameterisedEndpoint',
        itemOrder: 1,
        component: StyledEndpoint
    },
    {
        text: 'Collection',
        value: 'apiCollectionName',
        itemOrder: 3,
        icon: FraudProtectMinor,
        showFilter: true
    },
    {
        text: 'Method',
        value: 'method',
        showFilter: true
    },
    {
        text: 'Tags',
        value: 'tags',
        itemCell: 3,
        showFilter: true
    },
    {
        text: 'Sensitive Params',
        value: 'sensitiveTags',
        itemOrder: 2,
        showFilter: true
    },
    {
        text: 'Access Type',
        value: 'access_type',
        icon: GlobeMinor,
        itemOrder: 3,
        showFilter: true
    },
    {
        text: 'Auth Type',
        value: 'auth_type',
        icon: LockMinor,
        itemOrder: 3,
        showFilter: true
    },
    {
        text: 'Last Seen',
        value: 'last_seen',
        icon: ClockMinor,
        itemOrder: 3
    }
]

function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 2)
}

const sortOptions = [
    { label: 'Method', value: 'method asc', directionLabel: 'A-Z', sortKey: 'method' },
    { label: 'Method', value: 'method desc', directionLabel: 'Z-A', sortKey: 'method' },
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'endpoint' },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'endpoint' },
    { label: 'Auth Type', value: 'auth_type asc', directionLabel: 'A-Z', sortKey: 'auth_type' },
    { label: 'Auth Type', value: 'auth_type desc', directionLabel: 'Z-A', sortKey: 'auth_type' },
    { label: 'Access Type', value: 'access_type asc', directionLabel: 'A-Z', sortKey: 'access_type' },
    { label: 'Access Type', value: 'access_type desc', directionLabel: 'Z-A', sortKey: 'access_type' },
];

function NewEndpointsTable(props){

    const {loading, newEndpoints,handleRowClick} =props;
    const handleRow = (data) => {
        handleRowClick(data,headers)
    }

    return (
        <GithubSimpleTable
        key="endpointsTable"
        pageLimit={50}
        resourceName={resourceName}
        data={newEndpoints}
        sortOptions={sortOptions}
        filters={[]}
        disambiguateLabel={disambiguateLabel}
        onRowClick={(data) => handleRow(data)}
        headers={headers}
        loading={loading}
        getStatus={() => { return "warning" }}
    />
    )

}

export default NewEndpointsTable;