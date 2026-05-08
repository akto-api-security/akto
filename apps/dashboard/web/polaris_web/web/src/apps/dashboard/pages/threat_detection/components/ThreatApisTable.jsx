import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import func from "../../../../../util/func";
import PersistStore from "../../../../main/PersistStore";
import SessionStore from "../../../../main/SessionStore";
import { isMCPSecurityCategory } from "../../../../main/labelHelper";
import { labelMap } from "../../../../main/labelHelperMap";

const resourceName = {
  singular: "api",
  plural: "apis",
};

const headers = [
  {
    text:  labelMap[PersistStore.getState().dashboardCategory]["Endpoint"],
    value: "api",
    title: labelMap[PersistStore.getState().dashboardCategory]["Endpoint"],
  },
  {
    text: "Host",
    value: "host",
    title: "Host",
  },
  {
    text: "Malicious Actors",
    value: "actorsCount",
    title: "Malicious Actors",
  },
  {
    text: "Malicious Requests",
    value: "requestsCount",
    title: "Malicious Requests",
  },
  {
    text: "Discovered",
    title: "Discovered",
    value: "discoveredAt",
    type: CellType.TEXT,
    sortActive: true,
  },
];

const sortOptions = [
  {
    label: "Discovered time",
    value: "discoveredAt asc",
    directionLabel: "Newest",
    sortKey: "discoveredAt",
    columnIndex: 4,
  },
  {
    label: "Discovered time",
    value: "discoveredAt desc",
    directionLabel: "Oldest",
    sortKey: "discoveredAt",
    columnIndex: 4,
  },
];

let filters = [];

function ThreatApiTable({ currDateRange, rowClicked }) {
  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  const [loading, setLoading] = useState(true);

  const threatFiltersMap = SessionStore((state) => state.threatFiltersMap)
  const attackTypeChoices = Object.keys(threatFiltersMap).length === 0 ? [] : Object.entries(threatFiltersMap).map(([key, value]) => {
    return {
      label: value?._id || key,
      value: value?._id || key
    }
  })

  useEffect(() => {
    filters = [
      {
        key: 'latestAttack',
        label: 'Latest attack sub-category',
        type: 'select',
        choices: attackTypeChoices,
        multiple: true
      }
    ]
  }, []);

  function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 2);
  }

  async function fetchData(sortKey, sortOrder, skip, limit, filters) {
    setLoading(true);
    let latestAttack = [];
    if (filters?.latestAttack) {
      latestAttack = filters?.latestAttack;
    }
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchThreatApis(skip, sort, latestAttack);
    let total = res.total;
    let ret = res?.apis?.map((x) => {
      return {
        ...x,
        id:`${x.method}-${x.api}`,
        actorsCount: x.actorsCount,
        requestsCount: x.requestsCount,
        discoveredAt: func.prettifyEpoch(x.discoveredAt),
        host: x.host || "-",
        api: (
          <GetPrettifyEndpoint 
            {...(!isMCPSecurityCategory() && { method: x.method })}
            url={x.api}
            isNew={false}
          />
        ),
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

  const onRowClick = (data) => {
    const tempArr = data?.id?.split("-");
    let url = ""
    if(tempArr.length > 0){
      url = tempArr[1];
    }

    let filtersMap = PersistStore.getState().filtersMap;
    const tempKey = `/dashboard/protection/threat-activity/`
    if(filtersMap !== null && filtersMap.hasOwnProperty(tempKey)){
      delete filtersMap[tempKey];
      PersistStore.getState().setFiltersMap(filtersMap);
    }


    if(url.length > 0){
      const navigateUrl = window.location.origin + "/dashboard/protection/threat-activity?filters=url__" + url;
      window.open(navigateUrl, "_blank")
    }
   
  }

  const key = startTimestamp + endTimestamp;
  return (
    <GithubServerTable
      key={key}
      onRowClick={(data) => onRowClick(data)}
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
      getActions={() => {}}
      hideQueryField={true}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
    />
  );
}

export default ThreatApiTable;
