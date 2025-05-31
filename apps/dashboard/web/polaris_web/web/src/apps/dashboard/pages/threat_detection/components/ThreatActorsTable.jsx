import { useState, useEffect } from "react";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import api from "../api";
import { CellType } from "../../../components/tables/rows/GithubRow";
import GetPrettifyEndpoint from "../../observe/GetPrettifyEndpoint";
import func from "../../../../../util/func";
import PersistStore from "../../../../main/PersistStore";
import observeFunc from "../../observe/transform";
import Store from "../../../store";
import dayjs from "dayjs";
import { flags } from "./flags/index.mjs";
import { Tooltip, Text } from "@shopify/polaris";
import { useSearchParams } from "react-router-dom";
const resourceName = {
  singular: "actor",
  plural: "actors",
};

const headers = [
  {
    text: "Actor Id",
    value: "actor",
    title: "Actor Id",
  },
  {
    text: "Country",
    title: "Country",
    value: "country",
  },
  {
    text: "Actor Ip",
    title: "Actor Ip",
    value: "latestIp",
  },
  {
    text: "Latest API",
    title: "Latest API",
    value: "latestApi",
  },
  {
    text: "Latest Attack",
    title: "Latest Attack",
    value: "latestAttack",
  },
  {
    text: "Access Type",
    title: "Access Type",
    value: "accessType",
  },
  {
    text: "Sensitive Data",
    title: "Sensitive Data",
    value: "sensitiveData",
  },
  {
    text: "Status",
    title: "Status",
    value: "status",
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

function ThreatActorTable({ data, currDateRange, handleRowClick, externalFilter }) {
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

  async function fetchData(sortKey, sortOrder, skip, limit, filters) {
    setLoading(true);
    const sort = { [sortKey]: sortOrder };
    let total = 0;
    let ret = [];
    try {
      const res = await api.fetchThreatActors(skip, sort, filters.latestAttack || [], filters.country || [], startTimestamp, endTimestamp, filters.actorId || []);
      total = res.total;
      if (res?.actors?.length === 0) {
        return { value: [], total: 0 };
      }
      const allEndpoints = res?.actors?.map(x => x.latestApiEndpoint);

      const sensitiveDataResponse = await api.fetchSensitiveParamsForEndpoints(allEndpoints);
      const accessTypesResponse = await api.getAccessTypes(allEndpoints);

      const accessTypesMap = accessTypesResponse.apiInfos.reduce((map, apiInfo) => {
        map[apiInfo.id.url] = apiInfo.apiAccessTypes;
        return map;
      }, {});

      // Store the sensitive data for each endpoint in a map
      const sensitiveDataMap = sensitiveDataResponse.data.endpoints.reduce((map, endpoint) => {
        if (map[endpoint.url] && !map[endpoint.url].includes(endpoint.subType.name)) {
          map[endpoint.url].push(endpoint.subType.name);
        } else {
          map[endpoint.url] = [endpoint.subType.name];
        }
        return map;
      }, {});

      const getAccessType = (accessTypes) => {
       if (!accessTypes || accessTypes.length === 0) {
        return null;
       }
       if (accessTypes.includes("PUBLIC")) {
        return "Public";
       }
       return "Private";
      }

      
      ret = await Promise.all(res?.actors?.map(async x => {
        // Get the sensitive data for the endpoint
        const sensitiveData = sensitiveDataMap[x.latestApiEndpoint] || [];
        const accessTypes = accessTypesMap[x.latestApiEndpoint] || [];
        return {
          ...x,
          actor: x.id ? (
            <Text variant="bodyMd" fontWeight="medium">
              {x.id}
            </Text>
          ) : "-",
          latestIp: x.latestApiIp || "-",
          discoveredAt: x.discoveredAt ? dayjs(x.discoveredAt*1000).format('YYYY-MM-DD, HH:mm:ss A') : "-",
          sensitiveData: sensitiveData && sensitiveData.length > 0 ? observeFunc.prettifySubtypes(sensitiveData, false) : "-",
          latestAttack: x.latestAttack || "-",
          accessType: accessTypes.length > 0 ? getAccessType(accessTypes) : "-",
          status: "Active",
          latestAttack: x.latestAttack || "-",
          country: (
            <Tooltip
              content={x.country || "Unknown"}
            >
              <img
                src={x.country ? (x.country in flags ? flags[x.country] : flags["earth"]) : flags["earth"]}
                alt={x.country}
                style={{ width: '20px', height: '20px', marginRight: '5px' }}
              />
            </Tooltip>
          ),
          latestApi: (
            <GetPrettifyEndpoint
              maxWidth={"300px"}
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

  async function fillFilters() {
    const res = await api.fetchThreatActorFilters();
    const attackTypeChoices = res?.latestAttack.map(x => ({
      label: x,
      value: x
    }));
    const countryChoices = res?.country.map(x => ({
      label: x,
      value: x
    }));
    const actorIdChoices = res?.actorId.map(x => ({
      label: x,
      value: x
    }));
    filters = [
      {
        key: 'actorId',
        label: 'Actor Id',
        type: 'select',
        choices: actorIdChoices,
        multiple: true
      },
      {
        key: 'latestAttack',
        label: 'Latest attack sub-category',
        type: 'select',
        choices: attackTypeChoices,
        multiple: true
      },
      {
        key: 'country',
        label: 'Country',
        type: 'select',
        choices: countryChoices,
        multiple: true
      }
    ]
  }

  useEffect(() => {
    fillFilters();
  }, []);

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
      externalFilter={externalFilter}
    />
  );
}

export default ThreatActorTable;
