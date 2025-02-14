import { useEffect, useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import func from "../../../../../util/func";

const resourceName = {
  singular: "api",
  plural: "apis",
};

const headers = [
  {
    text: "Endpoint",
    value: "api",
    title: "Endpoint",
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

  useEffect(() => {}, []);

  function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 2);
  }

  async function fetchData(sortKey, sortOrder, skip) {
    setLoading(true);
    const sort = { [sortKey]: sortOrder };
    const res = await api.fetchThreatApis(skip, sort);
    let total = res.total;
    let ret = res?.apis?.map((x) => {
      return {
        ...x,
        id: `${x.method}-${x.api}`,
        actorsCount: x.actorsCount,
        requestsCount: x.requestsCount,
        discoveredAt: func.prettifyEpoch(x.discoveredAt),
        api: (
          <GetPrettifyEndpoint method={x.method} url={x.api} isNew={false} />
        ),
      };
    });
    setLoading(false);
    return { value: ret, total: total };
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

export default ThreatApiTable;
