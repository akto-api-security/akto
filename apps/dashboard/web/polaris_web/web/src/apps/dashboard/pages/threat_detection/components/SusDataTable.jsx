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
  },
  {
    text: "Filter",
    value: "filterId",
    title: "Threat filter",
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
    title: "Discovered",
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
    title: "",
    type: CellType.ACTION,
  },
];

const sortOptions = [
  {
    label: "Discovered time",
    value: "discovered asc",
    directionLabel: "Newest",
    sortKey: "discovered",
    columnIndex: 3,
  },
  {
    label: "Discovered time",
    value: "discovered desc",
    directionLabel: "Oldest",
    sortKey: "discovered",
    columnIndex: 3,
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
  const allCollections = PersistStore((state) => state.allCollections);

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
    if (filters?.sourceIps) {
      sourceIpsFilter = filters?.sourceIps;
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
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

  async function fillFilters() {
    const res = await api.fetchFiltersThreatTable();
    let apiCollectionFilterChoices = allCollections
      .filter((x) => {
        return x.type !== "API_GROUP";
      })
      .map((x) => {
        return { label: x.displayName, value: x.id };
      });
    let urlChoices = res?.urls
      .filter((x) => {
        return x.length > 0;
      })
      .map((x) => {
        return { label: x, value: x };
      });
    let ipChoices = res?.ips.map((x) => {
      return { label: x, value: x };
    });

    filters = [
      {
        key: "apiCollectionId",
        label: "Collection",
        title: "Collection",
        choices: apiCollectionFilterChoices,
      },
      {
        key: "sourceIps",
        label: "Source IP",
        title: "Source IP",
        choices: ipChoices,
      },
      {
        key: "url",
        label: "URL",
        title: "URL",
        choices: urlChoices,
      },
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

  const getActions = (item) => {
    return [
      {
        items: [
          {
            content: "View in collection",
            onAction: () => {
              window.open(
                `/dashboard/observe/inventory/${item.apiCollectionId}`,
                "_blank"
              );
            },
          },
        ],
      },
    ];
  };

  const key = startTimestamp + endTimestamp;
  return (
    <GithubServerTable
      key={key}
      pageLimit={50}
      headers={headers}
      resourceName={resourceName}
      sortOptions={sortOptions}
      disambiguateLabel={disambiguateLabel}
      loading={loading}
      // onRowClick={(data) => rowClicked(data)} [For now removing on row click functionality]
      fetchData={fetchData}
      filters={filters}
      selectable={false}
      hasRowActions={true}
      getActions={getActions}
      hideQueryField={true}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
    />
  );
}

export default SusDataTable;
