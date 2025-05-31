import { useReducer, useState, useEffect, useRef } from "react";
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

const ChartComponent = ({ mapData, loading, onSubCategoryClick, currDateRange, onCountryClick }) => {
    return (
        <VerticalStack gap={4} columns={2}>
            <HorizontalGrid gap={4} columns={2}>
                <ThreatActivityTimeline
                    onSubCategoryClick={onSubCategoryClick}
                    startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)}
                    endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)}
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

  const initialVal = values.ranges[0];
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    initialVal
  );

  const [externalFilter, setExternalFilter] = useState(null);

  useEffect(() => {
    const fetchActorsPerCountry = async () => {
      setLoading(true);
      const res = await api.getActorsCountPerCounty();
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
    const fetchThreatCategoryCount = async () => {
      setLoading(true);
      const res = await api.fetchThreatCategoryCount();
      const finalObj = threatDetectionFunc.getGraphsData(res);
      // setCategoryCount(finalObj.categoryCountRes);
      setSubCategoryCount(finalObj.subCategoryCount);
      setLoading(false);
    };
    fetchActorsPerCountry();
    fetchThreatCategoryCount();
  }, []);

  const onSubCategoryClick = (subCategory) => {
    setExternalFilter({key: "latestAttack", value: [subCategory]});
  }

  const onCountryClick = (country) => {
    setExternalFilter({key: "country", value: [country]});
  }

  const onRowClick = (data) => {
    setActorDetails(data);
    setShowActorDetails(true);
  }

  const components = [
    <ThreatSummary startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)} endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)} />,
    <MemoizedChartComponent 
      mapData={mapData}
      loading={loading}
      onSubCategoryClick={onSubCategoryClick}
      onCountryClick={onCountryClick}
      currDateRange={currDateRange}
    />,
    <ThreatActorTable
      key={"threat-actor-data-table"}
      currDateRange={currDateRange}
      loading={loading}
      handleRowClick={onRowClick}
      externalFilter={externalFilter}
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
