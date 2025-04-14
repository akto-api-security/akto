import { useReducer, useState, useEffect, useRef, useCallback } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import values from "@/util/values";
import { produce } from "immer";
import func from "@/util/func";
import ThreatActorTable from "./components/ThreatActorsTable";
import { ActorDetails } from "./components/ActorDetails";
import ThreatWorldMap from "./components/ThreatWorldMap";
// import ThreatApiSubcategoryCount from "./components/ThreatApiSubcategoryCount";

import api from "./api";
import { HorizontalGrid, VerticalStack } from "@shopify/polaris";
import TopThreatTypeChart from "./components/TopThreatTypeChart";
import threatDetectionFunc from "./transform";
import { ThreatSummary } from "./components/ThreatSummary";
import ThreatActivityTimeline from "./components/ThreatActivityTimeline";
import React from "react";
import ThreatActorFilters from "./components/ThreatActorFilters";

const ChartComponent = ({ mapData, loading, onSubCategoryClick, currDateRange, onCountryClick, appliedFilters }) => {
  console.log("appliedFilters in charts", appliedFilters);
    return (
        <VerticalStack gap={4} columns={2}>
            <HorizontalGrid gap={4} columns={2}>
                <ThreatActivityTimeline
                    onSubCategoryClick={onSubCategoryClick}
                    startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)}
                    endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)}
                    appliedFilters={appliedFilters}
                />
                <ThreatWorldMap
                    data={mapData}
                    style={{
                        width: "100%",
                        marginRight: "auto",
                    }}
                    loading={loading}
                    key={"threat-actor-world-map"}
                    onCountryClick={onCountryClick}
                    appliedFilters={appliedFilters}
                />
            </HorizontalGrid>
        </VerticalStack>
    );
};

const MemoizedChartComponent = React.memo(ChartComponent);

function ThreatActorPage() {
  const [mapData, setMapData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [subCategoryCount, setSubCategoryCount] = useState([]);
  const [actorDetails, setActorDetails] = useState(null);
  const [showActorDetails, setShowActorDetails] = useState(false);

  const initialVal = values.ranges[3];
  const [appliedFilters, setAppliedFilters] = useState([]);
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    initialVal
  );

  const countryFilters = appliedFilters.filter(x => x.key === "country").map(x => x.value) || [];

  useEffect(() => {
    const fetchThreatCategoryCount = async () => {
      setLoading(true);
      const res = await api.fetchThreatCategoryCount();
      const finalObj = threatDetectionFunc.getGraphsData(res);
      // setCategoryCount(finalObj.categoryCountRes);
      setSubCategoryCount(finalObj.subCategoryCount);
      setLoading(false);
    };
    fetchThreatCategoryCount();
  }, []);

  useEffect(() => {
    if (!loading) {
      const fetchActorsPerCountry = async () => {
      setLoading(true);
      const res = await api.getActorsCountPerCounty(countryFilters);
      if (res?.actorsCountPerCountry) {
        setMapData(
          res.actorsCountPerCountry.map((x) => {
            return {
              code: x.country,
              z: 100,
              count: x.count,
            };
          })
        );
      }
      setLoading(false);
    };
    fetchActorsPerCountry();
    }
  }, [appliedFilters]);


  const onSubCategoryClick = (subCategory) => {
    const temp = appliedFilters.filter(x => x.key !== "latestAttack");
    setAppliedFilters([...temp, {key: "latestAttack", value: [subCategory], label: subCategory}]);
  }

  const onCountryClick = (country) => {
    const temp = appliedFilters.filter(x => x.key !== "country");
    setAppliedFilters([...temp, {key: "country", value: [country], label: country}]);
  }

  const onRowClick = (data) => {
    setActorDetails(data);
    setShowActorDetails(true);
  }

  const onFilterChange = (filters) => {
    setAppliedFilters(filters);
  }

  const components = [
    <ThreatSummary startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)} endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)} />,
    <ThreatActorFilters onFilterChange={onFilterChange} appliedFilters={appliedFilters} />,
    <MemoizedChartComponent 
      mapData={mapData}
      loading={loading}
      onSubCategoryClick={onSubCategoryClick}
      onCountryClick={onCountryClick}
      appliedFilters={appliedFilters}
      currDateRange={currDateRange}
    />,
    <ThreatActorTable
      key={"threat-actor-data-table"}
      currDateRange={currDateRange}
      loading={loading}
      handleRowClick={onRowClick}
      appliedFilters={appliedFilters}
    />,
    ...(showActorDetails ? [<ActorDetails actorDetails={actorDetails} setShowActorDetails={setShowActorDetails} />] : [])
  ];

  return (
    <PageWithMultipleCards
      title={<TitleWithInfo titleText={"Threat Actor"} />}
      isFirstPage={true}
      primaryAction={
        <DateRangeFilter
          initialDispatch={currDateRange}
          dispatch={(dateObj) =>
            dispatchCurrDateRange({
              type: "update",
              period: dateObj.period,
              title: dateObj.title,
              alias: dateObj.alias,
            })
          }
        />
      }
      components={components}
    />
  );
}

export default ThreatActorPage;
