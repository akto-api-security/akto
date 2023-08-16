import GithubServerTable from "../../../../components/tables/GithubServerTable";
import { useEffect, useState } from "react";
import api from "../../api";
import transform from "../../transform";
import {
    GlobeMinor} from '@shopify/polaris-icons';
import Store from "../../../../store";
import func from "@/util/func"

const headers = [
    {
        text: 'Name',
        value: 'name',
        itemOrder: 1,
    },
    {
        text: 'Type',
        value: 'type',
        icon: GlobeMinor,
        itemOrder: 3
    },
    {
        text: 'Endpoint',
        value: 'endpoint',
        icon: GlobeMinor,
        itemOrder: 3,
        sortKey: 'url',
        showFilterMenu: true
    },
    {
        text: 'Collection',
        value: 'apiCollectionName',
        icon: GlobeMinor,
        itemOrder: 3,
        sortKey: 'apiCollectionId',
        showFilterMenu: true
    },
    {
        text: 'Method',
        value: 'method',
        icon: GlobeMinor,
        itemOrder: 3,
        sortKey: 'method',
        showFilterMenu: true
    },
    {
        text: 'Location',
        value: 'location',
        icon: GlobeMinor,
        itemOrder: 3,
        sortKey: 'isHeader',
        showFilterMenu: true
    },
    {
        text: "Discovered",
        value: 'added',
        icon: GlobeMinor,
        itemOrder: 3,
        sortKey: 'timestamp',
        showFilterMenu: true
    },
    {
        text: 'Values',
        value: 'domain',
        icon: GlobeMinor,
        itemOrder: 3,
        showFilterMenu: true
    }
]

const resourceName = {
    singular: 'API parameter',
    plural: 'API parameters',
};

const sortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp' },
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp' },
];

let filters = [
    {
        key: 'apiCollectionId',
        label: 'Collection',
        title: 'Collection',
        choices: [],
    }
]

function NewParametersTable(props) {

    const { startTimestamp, endTimestamp } = props;
    const [key, setKey] = useState(false);

    useEffect(() => {
        setKey(!key);
    }, [startTimestamp, endTimestamp])

    const allCollections = Store(state => state.allCollections);
    const apiCollectionMap = allCollections.reduce(
        (map, e) => { map[e.id] = e.displayName; return map }, {}
    )

    function disambiguateLabel(key, value) {
        switch (key) {
            case "apiCollectionId": 
                return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 3)
            default:
                return value;
        }
    }

    filters[0].choices = [];
    Object.keys(apiCollectionMap).forEach((key) => {
        filters[0].choices.push({
            label: apiCollectionMap[key],
            value: Number(key)
        })
    });

    const [loading, setLoading] = useState(true);

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
        setLoading(true);
        let ret = [];
        let total = 0;
        await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, false, false).then((res) => {
            ret = res.endpoints.map(transform.prepareEndpointForTable);
            total = res.total;
            setLoading(false);
        })
        return { value: ret, total: total };
    }

    return (
        <GithubServerTable
            key={key}
            pageLimit={50}
            headers={headers}
            resourceName={resourceName}
            sortOptions={sortOptions}
            disambiguateLabel={disambiguateLabel}
            loading={loading}
            fetchData={fetchData}
            filters={filters}
            hideQueryField={true}
        />
    )
}

export default NewParametersTable