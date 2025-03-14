import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import PersistStore from "../../../../main/PersistStore";
import func from "../../../../../util/func";
import { Badge } from "@shopify/polaris";
import dayjs from "dayjs";
const resourceName = {
  singular: "sample",
  plural: "samples",
};

const headers = [
  {
    text: "Severity",
    value: "severityComp",
    title: "Severity",
  },
  {
    text: "Api Endpoint",
    value: "endpointComp",
    title: "Api Endpoint",
  },
  {
    text: "Subcategory",
    value: "subCategory",
    title: "Subcategory",
  },
  {
    text: "Threat Actor",
    value: "actorComp",
    title: "Actor",
    filterKey: 'actor'
  },
  {
    text: "Filter",
    value: "filterId",
    title: "Attack type",
  },
  {
    text: "Collection",
    value: "apiCollectionName",
    title: "Collection",
    maxWidth: "95px",
    type: CellType.TEXT,
  },
  {
    text: "Discovered",
    title: "Detected",
    value: "discoveredTs",
    type: CellType.TEXT,
    sortActive: true,
  },
];

const sortOptions = [
  {
    label: "Discovered time",
    value: "detectedAt asc",
    directionLabel: "Newest",
    sortKey: "detectedAt",
    columnIndex: 5,
  },
  {
    label: "Discovered time",
    value: "detectedAt desc",
    directionLabel: "Oldest",
    sortKey: "detectedAt",
    columnIndex: 5,
  },
];

let filters = [];

function SusDataTable({ currDateRange, rowClicked }) {
  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  const [loading, setLoading] = useState(true);
  const collectionsMap = PersistStore((state) => state.collectionsMap);
  const threatFiltersMap = PersistStore((state) => state.threatFiltersMap);

  const [subCategoryChoices, setSubCategoryChoices] = useState([]);

  async function fetchData(
    sortKey,
    sortOrder,
    skip,
    limit,
    filters,
    filterOperators,
    queryValue
  ) {
    setLoading(true);
    let sourceIpsFilter = [],
      apiCollectionIdsFilter = [],
      matchingUrlFilter = [],
      typeFilter = [],
      subCategoryFilter = [];
    if (filters?.actor) {
      sourceIpsFilter = filters?.actor;
    }
    if (filters?.apiCollectionId) {
      apiCollectionIdsFilter = filters?.apiCollectionId;
    }
    if (filters?.url) {
      matchingUrlFilter = filters?.url;
    }
    if(filters?.type){
      typeFilter = filters?.type
    }
    if(filters?.subCategory){
      subCategoryFilter = filters?.subCategory
    }
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchSuspectSampleData(
      skip,
      sourceIpsFilter,
      apiCollectionIdsFilter,
      matchingUrlFilter,
      typeFilter,
      sort,
      startTimestamp,
      endTimestamp,
      subCategoryFilter
    );
    const distinctSubCategories = Array.from(new Set(res?.maliciousEvents.map((x) => x?.subCategory)));
    setSubCategoryChoices(distinctSubCategories);
    let total = res.total;
    let ret = res?.maliciousEvents.map((x) => {
      const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH"
      return {
        ...x,
        id: x.id,
        subCategory: x?.subCategory,
        actorComp: x?.actor,
        endpointComp: (
          <GetPrettifyEndpoint maxWidth="300px" method={x.method} url={x.url} isNew={false} />
        ),
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: dayjs(x.timestamp).format("DD-MM-YYYY HH:mm:ss"),
        subCategoryComp: x?.subCategory || "-",
        sourceIPComponent: x?.ip || "-",
        type: x?.type || "-",
        severityComp: (<div className={`badge-wrapper-${severity}`}>
                          <Badge size="small">{func.toSentenceCase(severity)}</Badge>
                      </div>
        )
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

  async function fillFilters() {
    const res = await api.fetchFiltersThreatTable();
    let urlChoices = res?.urls
      .map((x) => {
        const url = x || "/"
        return { label: url, value: x };
      });
    let ipChoices = res?.ips.map((x) => {
      return { label: x, value: x };
    });

    let subCategoryChoices = res?.subCategory.map((x) => {
      return { label: x, value: x };
    });

    filters = [
      {
        key: "actor",
        label: "Actor",
        title: "Actor",
        choices: ipChoices,
      },
      {
        key: "url",
        label: "URL",
        title: "URL",
        choices: urlChoices,
      },
      {
        key: "subCategory",
        label: "Subcategory",
        title: "Subcategory",
        choices: subCategoryChoices,
      },
      {
        key: 'type',
        label: "Type",
        title: "Type",
        choices: [
          {label: 'Rule based', value: 'Rule-Based'},
          {label: 'Anomaly', value: 'Anomaly'},
        ],
      }
    ];
  }

  useEffect(() => {
    fillFilters();
  }, []);

  function disambiguateLabel(key, value) {
    switch (key) {
      case "apiCollectionId":
        return func.convertToDisambiguateLabelObj(value, collectionsMap, 2);
      default:
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }
  }

  const key = startTimestamp + endTimestamp;
  return (
    <GithubServerTable
      key={key}
      onRowClick={(data) => rowClicked(data)}
      pageLimit={50}
      headers={headers}
      resourceName={resourceName}
      sortOptions={sortOptions}
      disambiguateLabel={disambiguateLabel}
      loading={loading}
      fetchData={fetchData}
      filters={filters}
      selectable={false}
      hasRowActions={true}
      getActions={() => []}
      hideQueryField={true}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
    />
  );
}

export default SusDataTable;
