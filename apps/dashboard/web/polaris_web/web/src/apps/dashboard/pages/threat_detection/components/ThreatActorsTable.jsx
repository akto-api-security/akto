import { useState } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import func from "../../../../../util/func";
import PersistStore from "../../../../main/PersistStore";
import observeFunc from "../../observe/transform";
import Store from "../../../store";
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
    text: "Sensitive Data",
    title: "Sensitive Data",
    value: "sensitiveData",
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

function ThreatActorTable({ data, currDateRange, handleRowClick }) {
  const [loading, setLoading] = useState(false);

  const setToastConfig = Store(state => state.setToastConfig)
    const setToast = (isActive, isError, message) => {
        setToastConfig({
          isActive: isActive,
          isError: isError,
          message: message
        })
    }

  const getTimeEpoch = (key) => {
    return Math.floor(Date.parse(currDateRange.period[key]) / 1000);
  };
  const startTimestamp = getTimeEpoch("since");
  const endTimestamp = getTimeEpoch("until");

  function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 2);
  }

  const onRowClick = (data) => {
    handleRowClick(data);
  }

  async function fetchData(sortKey, sortOrder, skip) {
    setLoading(true);
    const sort = { [sortKey]: sortOrder };
    let total = 0;
    let ret = [];
    try {
      const res = await api.fetchThreatActors(skip, sort);
      total = res.total;

      const allEndpoints = res?.actors?.map(x => x.latestApiEndpoint);

      const sensitiveDataResponse = await api.fetchSensitiveParamsForEndpoints(allEndpoints);


      // Store the sensitive data for each endpoint in a map
      const sensitiveDataMap = sensitiveDataResponse.data.endpoints.reduce((map, endpoint) => {
        if (map[endpoint.url] && !map[endpoint.url].includes(endpoint.subType.name)) {
          map[endpoint.url].push(endpoint.subType.name);
        } else {
          map[endpoint.url] = [endpoint.subType.name];
        }
        return map;
      }, {});

      
      ret = await Promise.all(res?.actors?.map(async x => {
        // Get the sensitive data for the endpoint
        const sensitiveData = sensitiveDataMap[x.latestApiEndpoint] || [];
        return {
          ...x,
          actor: x.id,
          latestIp: x.latestApiIp,
          discoveredAt: dayjs(x.discoveredAt).format('YYYY-MM-DD, HH:mm:ss A'),
          sensitiveData: observeFunc.prettifySubtypes(sensitiveData, false),
          latestApi: (
            <GetPrettifyEndpoint
              method={x.latestApiMethod}
              url={x.latestApiEndpoint}
              isNew={false}
            />
          ),
        };
      }));
    } catch (e) {
      setToast(true, true, "Error fetching threat actors");
    } finally {
      setLoading(false);
    }
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
