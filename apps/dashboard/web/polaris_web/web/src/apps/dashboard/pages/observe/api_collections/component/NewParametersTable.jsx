import GithubServerTable from "../../../../components/tables/GithubServerTable";
import { useEffect, useState } from "react";
import api from "../../api";
import transform from "../../transform";
import {
    GlobeMinor, PasskeyMinor, DynamicSourceMinor, LinkMinor, LocationsMinor, ClockMinor, PriceLookupMinor} from '@shopify/polaris-icons';
import Store from "../../../../store";
import func from "@/util/func"
import PersistStore from "../../../../../main/PersistStore";

const headers = [
    {
        text: 'Name',
        value: 'name',
        itemOrder: 1,
    },
    {
        text: 'Type',
        value: 'subType',
        icon: PasskeyMinor,
        itemOrder: 3
    },
    {
        text: 'Endpoint',
        value: 'endpoint',
        icon: LinkMinor,
        itemOrder: 3,
        sortKey: 'url',
        showFilterMenu: true
    },
    {
        text: 'Collection',
        value: 'apiCollectionName',
        icon: DynamicSourceMinor,
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
        icon: LocationsMinor,
        itemOrder: 3,
        sortKey: 'isHeader',
        showFilterMenu: true
    },
    {
        text: "Discovered",
        value: 'added',
        icon: ClockMinor,
        itemOrder: 3,
        sortKey: 'timestamp',
        showFilterMenu: true
    },
    {
        text: 'Values',
        value: 'domain',
        icon: PriceLookupMinor,
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
    },
    {
        key: 'method',
        label: 'Method',
        title: 'Method',
        choices: [
            { label: "GET",value: "GET"},
            { label: "POST",value: "POST"},
            { label: "PUT",value: "PUT"},
            { label: "PATCH",value: "PATCH"},
            { label: "DELETE",value: "DELETE"},
            { label: "OPTIONS",value: "OPTIONS"},
            { label: "HEAD",value: "HEAD"},
        ]
    },
    {
        key: 'subType',
        label: 'Type',
        title: 'Type',
        choices: []
    },
    {
        key:'location',
        label:'Location',
        title:'Location',
        choices:[
            {label:"Header", value:"header"},
            {label:"Payload", value:"payload"},
            {label:"URL param", value:"urlParam"}
        ],
    }
]

function NewParametersTable(props) {

    const { startTimestamp, endTimestamp, handleRowClick } = props;
    const [key, setKey] = useState(false);

    useEffect(() => {
        setKey(!key);
    }, [startTimestamp, endTimestamp])

    const allCollections = PersistStore(state => state.allCollections);
    const dataTypeNames = Store(state => state.dataTypeNames);
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

    filters[2].choices = dataTypeNames.map((x) => {
        return {
            label:x,
            value:x
        }
    })

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

    const handleRow = (data) => {
        handleRowClick(data,headers)
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
            onRowClick={(data) => handleRow(data)}
            fetchData={fetchData}
            filters={filters}
            hideQueryField={true}
        />
    )
}

export default NewParametersTable