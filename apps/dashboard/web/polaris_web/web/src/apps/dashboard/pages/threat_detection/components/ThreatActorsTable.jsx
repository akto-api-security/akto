import { useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import func from "../../../../../util/func";
import PersistStore from "../../../../main/PersistStore";
import dayjs from "dayjs";
const resourceName = {
  singular: "actor",
  plural: "actors",
};

const headers = [
  {
    text: "Actor",
    value: "actor",
    title: "Actor",
  },
  {
    text: "Latest API",
    title: "Latest API",
    value: "latestApi",
  },
  {
    text: "Detected at",
    title: "Detected at",
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

function ThreatActorTable({ data, currDateRange, rowClicked }) {
  const [loading, setLoading] = useState(false);

  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 2);
  }

  const onRowClick = (data) => {
    const actorIp = data.actor;
    const url = data.latestApiEndpoint

    const tempKey = `/dashboard/protection/threat-activity/`
    let filtersMap = PersistStore.getState().filtersMap;
    if(filtersMap !== null && filtersMap.hasOwnProperty(tempKey)){
      delete filtersMap[tempKey];
      PersistStore.getState().setFiltersMap(filtersMap);
    }


    const filters = `actor__${actorIp}&url__${url}`;
    const navigateUrl = `${window.location.origin}/dashboard/protection/threat-activity?filters=${encodeURIComponent(filters)}`;
    window.open(navigateUrl, "_blank");
  }

  async function fetchData(sortKey, sortOrder, skip) {
    setLoading(true);
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchThreatActors(skip, sort);
    console.log("resta", res);
    let total = res.total;
    let ret = res?.actors?.map((x) => {
      return {
        ...x,
        actor: x.id,
        latestIp: x.latestApiIp,
        discoveredAt: dayjs(x.discoveredAt).format('YYYY-MM-DD, HH:mm:ss A'),
        latestApi: (
          <GetPrettifyEndpoint
            method={x.latestApiMethod}
            url={x.latestApiEndpoint}
            isNew={false}
          />
        ),
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

  const key = startTimestamp + endTimestamp;
  return (
    <GithubServerTable
      onRowClick={(data) => onRowClick(data)}
      key={key}
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
      getActions={() => { }}
      hideQueryField={true}
      headings={headers}
      useNewRow={true}
      condensedHeight={true}
    />
  );
}

export default ThreatActorTable;
