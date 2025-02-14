import { useReducer, useState, useEffect } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import values from "@/util/values";
import { produce } from "immer";
import func from "@/util/func";
import ThreatActorTable from "./components/ThreatActorsTable";
import ThreatWorldMap from "./components/ThreatWorldMap";
// import ThreatApiSubcategoryCount from "./components/ThreatApiSubcategoryCount";

import api from "./api";
import { HorizontalGrid, VerticalStack } from "@shopify/polaris";
import TopThreatTypeChart from "./components/TopThreatTypeChart";
import threatDetectionFunc from "./transform";
function ThreatActorPage() {
  const [mapData, setMapData] = useState([]);
  const [loading, setLoading] = useState(false);
  const [subCategoryCount, setSubCategoryCount] = useState([]);
  // const [categoryCount, setCategoryCount] = useState([]);
  const initialVal = values.ranges[3];
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    initialVal
  );

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

  const ChartComponent = () => {
    return (
      <VerticalStack gap={4} columns={2}>
        <HorizontalGrid gap={4} columns={2}>
          <TopThreatTypeChart
            key={"top-threat-types"}
            data={subCategoryCount}
          />
          <ThreatWorldMap
            data={mapData}
            style={{
              width: "100%",
              marginRight: "auto",
            }}
            loading={loading}
            key={"threat-actor-world-map"}
          />
        </HorizontalGrid>
        
      </VerticalStack>
    );
  };

  const components = [
    <ChartComponent />,
    <ThreatActorTable
      key={"threat-actor-data-table"}
      currDateRange={currDateRange}
      loading={loading}
    />,
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
