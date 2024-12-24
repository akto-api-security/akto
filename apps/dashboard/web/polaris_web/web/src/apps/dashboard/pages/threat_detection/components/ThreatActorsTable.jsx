import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import PersistStore from "../../../../main/PersistStore";
import func from "../../../../../util/func";

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
    text: "Latest IP",
    title: "Latest IP",
    value: "latestIp",
  },
  {
    text: "Latest API",
    title: "Latest API",
    value: "latestApi",
  },
  {
    text: "Discovered",
    title: "Discovered",
    value: "discovered",
    type: CellType.TEXT,
    sortActive: true,
  },
];

const sortOptions = [];

let filters = [];

function ThreatActorTable({ currDateRange, rowClicked }) {
  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  const [loading, setLoading] = useState(true);
  const collectionsMap = PersistStore((state) => state.collectionsMap);
  const allCollections = PersistStore((state) => state.allCollections);

  async function fetchData(sortKey, sortOrder, skip) {
    setLoading(true);
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchThreatActors(skip);
    let total = res.total;
    let ret = res?.actors.map((x) => {
      return {
        ...x,
        actor: x.id,
        latestIp: x?.latestApiIp,
        latestApi: (
          <GetPrettifyEndpoint
            method={x.latestApiMethod}
            url={x.latestApiEndpoint}
            isNew={false}
          />
        ),
        discovered: func.prettifyEpoch(x.discoveredAt),
      };
    });
    setLoading(false);
    return { value: ret, total: total };
  }

  useEffect(() => {}, []);

  function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 2);
  }

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

export default ThreatActorTable;
