import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useEffect, useState } from "react";
import api from "../api"
import testingApi from "../../testing/api"
import Store from "../../../store";
import TestingStore from "../../testing/testingStore";
import transform from "../transform";
import func from "@/util/func";
import {
    SearchMinor,
    FraudProtectMinor  } from '@shopify/polaris-icons';

const headers = [
    {
        text:"Issue",
        value:"categoryName",
        itemOrder:1
    },
    
    {
        text: "Collection",
        value: "collection",
        itemOrder: 3,
        icon: FraudProtectMinor,
    },
    {
        text: "API Collection ID",
        value: "apiCollectionId",
    },
    {
        text: "Discovered",
        value: "detected_timestamp",
        itemOrder: 3,
        icon: SearchMinor,
    },
    {
        text: "Timestamp",
        value: "timestamp",
    },
    {
        text: "Endpoint",
        value: "url",
        itemOrder: 3,
        icon: SearchMinor,
    },
    {
        text:"Severity",
        value:"severity",
        itemOrder:2
    }
]

const sortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp' },
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp' }
];

let filtersOptions = [
    {
        key: 'apiCollectionId',
        label: 'Collection',
        title: 'Collection',
        choices: [],
    },
    {
        key: 'severity',
        label: 'Severity',
        title: 'Severity',
        choices: [
            { label: "High", value: "HIGH" }, 
            { label: "Medium", value: "MEDIUM" },
            { label: "Low", value: "LOW" }
        ],
    },
    {
        key:"issueCategory",
        label: "Issue category",
        title:"Issue category",
        choices:[]
    }
]

const appliedFilters = [
    {
        key: 'status',
        label: 'Open issues',
        value: ['OPEN'],
        onRemove: () => {}
    }
]

const resourceName = {
    singular: 'Issue',
    plural: 'Issues',
  };

  let promotedBulkActions = (selectedResources) => {
    return [{
        content: 'False positive',
        onAction: () => { console.log("function") }
    }, 
    {
        content: 'Acceptable risk',
        onAction: () => { console.log("function") }
    },
    {
        content: 'No time to fix',
        onAction: () => { console.log("function") }
    }]
}

function IssuesPage(){

    const [loading, setLoading] = useState(true);
    const allCollections = Store(state => state.allCollections);
    const subCategoryMap = TestingStore(state => state.subCategoryMap);
    const subCategoryFromSourceConfigMap = TestingStore(state => state.subCategoryFromSourceConfigMap);
    const setSubCategoryMap = TestingStore(state => state.setSubCategoryMap);
    const setSubCategoryFromSourceConfigMap = TestingStore(state => state.setSubCategoryFromSourceConfigMap);
    const apiCollectionMap = allCollections.reduce(
        (map, e) => {map[e.id] = e.displayName; return map}, {}
    )

    filtersOptions[0].choices=[];
    Object.keys(apiCollectionMap).forEach((key) => { 
        filtersOptions[0].choices.push({
            label:apiCollectionMap[key],
            value:Number(key)
        })
    });
    
    let store = {}
    let result = []
    Object.values(subCategoryMap).forEach((x) => {
        let superCategory = x.superCategory
        if (!store[superCategory.name]) {
            result.push({ "label": superCategory.displayName, "value": superCategory.name })
            store[superCategory.name] = []
        }
        store[superCategory.name].push(x._name);
    })
    filtersOptions[2].choices = [].concat(result)
    let categoryToSubCategories = store

    function disambiguateLabel(key, value) {
        switch (key) {
            case "severity": return (value).map((val) => func.toSentenceCase(val)).join(', ');
            case "issueCategory": 
            case "location":
                return (value).map((val) => val).join(', ');
            case "apiCollectionId": 
                return (value).map((val) => `${apiCollectionMap[val]}`).join(', ');
            case "isRequest":
                return value[0] ? "In request" : "In response"
            case "dateRange":
                return value.since.toDateString() + " - " + value.until.toDateString();
            default:
                return value;
        }
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);

        let c={subCategoryMap:subCategoryMap, subCategoryFromSourceConfigMap:subCategoryFromSourceConfigMap};
        
        if(Object.keys(subCategoryMap) == 0 || Object.keys(subCategoryFromSourceConfigMap) == 0 ){
            let subCategoryMap = {}
            let subCategoryFromSourceConfigMap = {}
            await testingApi.fetchAllSubCategories().then((resp) => {
                resp.subCategories.forEach((x) => {
                    subCategoryMap[x.name] = x
                })
                resp.testSourceConfigs.forEach((x) => {
                    subCategoryFromSourceConfigMap[x.id] = x
                })
            })
            await setSubCategoryMap(subCategoryMap)
            await setSubCategoryFromSourceConfigMap(subCategoryFromSourceConfigMap)
            c.subCategoryMap=subCategoryMap;
            c.subCategoryFromSourceConfigMap=subCategoryFromSourceConfigMap
        }

        let total =0;
        let ret = []
        let filterCollectionsId = filters.apiCollectionId;
        let filterSeverity = filters.severity
        let filterSubCategory = []
        filters?.issueCategory?.forEach((issue) => {
            filterSubCategory = filterSubCategory.concat(categoryToSubCategories[issue])
        })

        await api.fetchIssues(skip, limit,[],filterCollectionsId,filterSeverity,filterSubCategory,0).then((res) => {
            total = res.totalIssuesCount;
            ret = transform.prepareIssues(res, c.subCategoryMap, c.subCategoryFromSourceConfigMap, apiCollectionMap);
            setLoading(false);
        })
        // handle sort here.
        return {value:ret , total:total};
    }

    return (
        <PageWithMultipleCards
            title="Issues"
            components = {[
                <GithubServerTable
                    key="table"
                    headers={headers}
                    resourceName={resourceName} 
                    appliedFilters={appliedFilters}
                    sortOptions={sortOptions}
                    disambiguateLabel={disambiguateLabel}
                    selectable = {true}
                    loading={loading}
                    fetchData={fetchData}
                    filters={filtersOptions}
                    promotedBulkActions={promotedBulkActions}
                    hideQueryField={true}
                    // calenderFilter={true}
                />
            ]}
        />
    )
}

export default IssuesPage