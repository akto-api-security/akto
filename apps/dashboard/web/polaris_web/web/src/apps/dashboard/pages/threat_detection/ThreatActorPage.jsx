import { useReducer, useState, useEffect } from "react";
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

import { HorizontalGrid, VerticalStack } from "@shopify/polaris";
import { ThreatSummary } from "./components/ThreatSummary";
import ThreatActivityTimeline from "./components/ThreatActivityTimeline";
import React from "react";
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";

const ChartComponent = ({ onSubCategoryClick, currDateRange }) => {
    return (
        <VerticalStack gap={4} columns={2}>
            <HorizontalGrid gap={4} columns={2}>
                <ThreatActivityTimeline
                    onSubCategoryClick={onSubCategoryClick}
                    startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)}
                    endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)}
                />
                <ThreatWorldMap
                    startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)}
                    endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)}
                    style={{
                        width: "100%",
                        marginRight: "auto",
                    }}
                    key={"threat-actor-world-map"}
                />
            </HorizontalGrid>
        </VerticalStack>
    );
};

const MemoizedChartComponent = React.memo(ChartComponent);

function ThreatActorPage() {
  const [actorDetails, setActorDetails] = useState(null);
  const [showActorDetails, setShowActorDetails] = useState(false);

  const initialVal = values.ranges[2];
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    initialVal
  );

  useEffect(() => {
  }, []);

  const onSubCategoryClick = (subCategory) => {
    console.log({ subCategory });
  }

  const onRowClick = (data) => {
    setActorDetails(data);
    setShowActorDetails(true);
  }

  const components = [
    <ThreatSummary startTimestamp={parseInt(currDateRange.period.since.getTime()/1000)} endTimestamp={parseInt(currDateRange.period.until.getTime()/1000)} />,
    <MemoizedChartComponent
    key={"threat-actor-chart-component"}
      onSubCategoryClick={onSubCategoryClick}
      currDateRange={currDateRange}
    />,
    <ThreatActorTable
      key={"threat-actor-data-table"}
      currDateRange={currDateRange}
      handleRowClick={onRowClick}
    />,
    ...(showActorDetails ? [<ActorDetails actorDetails={actorDetails} setShowActorDetails={setShowActorDetails} />] : [])
  ];

  return (
    <PageWithMultipleCards
      title={<TitleWithInfo titleText={`${mapLabel("Threat", getDashboardCategory())} Actor`} />}
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