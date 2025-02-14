import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import PersistStore from "../../../../main/PersistStore";
import func from "../../../../../util/func";

const resourceName = {
  singular: "sample",
  plural: "samples",
};

const headers = [
  {
    text: "Endpoint",
    value: "endpointComp",
    title: "Endpoint",
  },
  {
    text: "Actor",
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
  {
    text: "Source IP",
    title: "Source IP",
    value: "sourceIPComponent",
  },
  {
    text: "Type",
    title: "Type",
    value: "type",
  }
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
      matchingUrlFilter = [];
    if (filters?.actor) {
      sourceIpsFilter = filters?.actor;
    }
    if (filters?.apiCollectionId) {
      apiCollectionIdsFilter = filters?.apiCollectionId;
    }
    if (filters?.url) {
      matchingUrlFilter = filters?.url;
    }
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchSuspectSampleData(
      skip,
      sourceIpsFilter,
      apiCollectionIdsFilter,
      matchingUrlFilter,
      sort,
      startTimestamp,
      endTimestamp
    );
    let total = res.total;
    let ret = res?.maliciousEvents.map((x) => {
      return {
        ...x,
        id: x.id,
        actorComp: x?.actor,
        endpointComp: (
          <GetPrettifyEndpoint method={x.method} url={x.url} isNew={false} />
        ),
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: func.prettifyEpoch(x.timestamp),
        sourceIPComponent: x?.ip || "-",
        type: x?.type || (x?.filterId==="High4XXAlertFilter" ? "Anomaly": "Rule-based")
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
      // {
      //   key: 'type',
      //   label: "Type",
      //   title: "Type",
      //   choices: [
      //     {label: 'Rule based', value: 'Rule-based'},
      //     {label: 'Anomaly', value: 'Anomaly-based'},
      //   ],
      // }
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
