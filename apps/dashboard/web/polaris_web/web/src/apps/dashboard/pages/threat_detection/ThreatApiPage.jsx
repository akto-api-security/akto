import { useEffect, useReducer, useState } from "react";
import { useSearchParams } from "react-router-dom";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import values from "@/util/values";
import { produce } from "immer";
import func from "@/util/func";
import ThreatApisTable from "./components/ThreatApisTable";
import TopThreatTypeChart from "./components/TopThreatTypeChart";
import ThreatApiSubcategoryCount from "./components/ThreatApiSubcategoryCount";

import api from "./api";
import { HorizontalGrid } from "@shopify/polaris";
import threatDetectionFunc from "./transform";
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";
function ThreatApiPage() {
  const [loading, setLoading] = useState(false);
  const [categoryCount, setCategoryCount] = useState([]);
  const [subCategoryCount, setSubCategoryCount] = useState([]);
  const [searchParams] = useSearchParams();
  const getInitialDateRange = () => {
    const rangeAlias = searchParams.get('range');
    if (rangeAlias) {
      const preset = values.ranges.find((r) => r.alias === rangeAlias);
      if (preset) return preset;
    }
    const sinceParam = searchParams.get('since');
    const untilParam = searchParams.get('until');
    if (sinceParam != null && untilParam != null) {
      const sinceTs = parseInt(sinceParam, 10);
      const untilTs = parseInt(untilParam, 10);
      if (!Number.isNaN(sinceTs) && !Number.isNaN(untilTs)) {
        const sinceDate = new Date(sinceTs * 1000);
        const untilDate = new Date(untilTs * 1000);
        const title = sinceDate.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' }) + " - " + untilDate.toLocaleDateString('en-US', { month: 'short', day: '2-digit', year: 'numeric' });
        return { alias: "custom", title, period: { since: sinceDate, until: untilDate } };
      }
    }
    return values.ranges[3];
  };
  const initialDateRange = getInitialDateRange();
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    initialDateRange
  );

  const ChartComponent = () => {
    return (
      <HorizontalGrid gap={4} columns={2}>
        <TopThreatTypeChart key={"top-threat-types"} data={categoryCount} />
        <ThreatApiSubcategoryCount
          key={"threat-categories"}
          data={subCategoryCount}
        />
      </HorizontalGrid>
    );
  };
  const components = [
    <ChartComponent key={"chart-component"} />,
    <ThreatApisTable
      key={"threat-api-data-table"}
      currDateRange={currDateRange}
    />,
  ];

  useEffect(() => {
    const fetchThreatCategoryCount = async () => {
      setLoading(true);
      const res = await api.fetchThreatCategoryCount();
      const finalObj = threatDetectionFunc.getGraphsData(res);
      setCategoryCount(finalObj.categoryCountRes);
      setSubCategoryCount(finalObj.subCategoryCount);
      setLoading(false);
    };

    fetchThreatCategoryCount();
  }, []);

  return (
    <PageWithMultipleCards
      title={<TitleWithInfo titleText={`${mapLabel("APIs", getDashboardCategory())} under ${mapLabel("Threat", getDashboardCategory())}`}/>}
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

export default ThreatApiPage;
