import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import PersistStore from "../../../../main/PersistStore";
import func from "../../../../../util/func";
import { Badge, IndexFiltersMode } from "@shopify/polaris";
import dayjs from "dayjs";
import SessionStore from "../../../../main/SessionStore";
import { labelMap } from "../../../../main/labelHelperMap";
import { formatActorId } from "../utils/formatUtils";
import useTable from "../../../components/tables/TableContext";

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
    text: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
    value: "endpointComp",
    title: labelMap[PersistStore.getState().dashboardCategory]["API endpoint"],
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
  const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);
  const [currentTab, setCurrentTab] = useState('events');
  const [selected, setSelected] = useState(0)

  const tableTabs = [
    {
      content: 'Events',
      onAction: () => { setCurrentTab('events') },
      id: 'events',
      index: 0 
    },
    {
      content: 'Active',
      onAction: () => { setCurrentTab('active') },
      id: 'active',
      index: 0 
    },
    {
      content: 'Triaged',
      onAction: () => { setCurrentTab('triaged') },
      id: 'triaged',
      index: 1 
    }
  ]

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
  }



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
      latestAttack = [];
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
    if(filters?.latestAttack){
      latestAttack = filters?.latestAttack
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
      latestAttack,
      50,
      currentTab.toUpperCase(),
    );
//    setSubCategoryChoices(distinctSubCategories);
    let total = res.total;
    let ret = res?.maliciousEvents.map((x) => {
      const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH"
      return {
        ...x,
        id: x.id,
        actorComp: formatActorId(x.actor),
        endpointComp: (
          <GetPrettifyEndpoint 
            maxWidth="300px" 
            method={x.method}
            url={x.url} 
            isNew={false} 
          />
        ),
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: dayjs(x.timestamp*1000).format("DD-MM-YYYY HH:mm:ss"),
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

  const attackTypeChoices = Object.keys(threatFiltersMap).length === 0 ? [] : Object.entries(threatFiltersMap).map(([key, value]) => {
    return {
      label: value?._id || key,
      value: value?._id || key
    }
  })
  

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
      {
        key: 'type',
        label: "Type",
        title: "Type",
        choices: [
          {label: 'Rule based', value: 'Rule-Based'},
          {label: 'Anomaly', value: 'Anomaly'},
        ],
      },
      {
        key: 'latestAttack',
        label: 'Latest attack sub-category',
        type: 'select',
        choices: attackTypeChoices,
        multiple: true
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
      selectable={true}

      headings={headers}
      useNewRow={true}
      condensedHeight={true}
      tableTabs={tableTabs}
      selected={selected}
      onSelect={handleSelectedTab}
      mode={IndexFiltersMode.Default}
      hideQueryField={true}
    />
  );
}

export default SusDataTable;
