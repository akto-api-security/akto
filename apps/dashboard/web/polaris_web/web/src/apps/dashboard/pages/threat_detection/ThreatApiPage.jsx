import { useEffect, useReducer, useState } from "react";
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
function ThreatApiPage() {
  const [loading, setLoading] = useState(false);
  const [categoryCount, setCategoryCount] = useState([]);
  const [subCategoryCount, setSubCategoryCount] = useState([]);
  const initialVal = values.ranges[3];
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    initialVal
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
      if (res?.categoryCounts) {
        const categoryRes = {};
        const subCategoryRes = {};
        for (const cc of res.categoryCounts) {
          if (categoryRes[cc.category]) {
            categoryRes[cc.category] += cc.count;
          } else {
            categoryRes[cc.category] = cc.count;
          }

          if (subCategoryRes[cc.subCategory]) {
            subCategoryRes[cc.subCategory] += cc.count;
          } else {
            subCategoryRes[cc.subCategory] = cc.count;
          }
        }

        setSubCategoryCount(
          Object.keys(subCategoryRes).map((x) => {
            return {
              text: x.replaceAll("_", " "),
              value: subCategoryRes[x],
            };
          })
        );

        setCategoryCount(
          Object.keys(categoryRes).map((x) => {
            return {
              text: x.replaceAll("_", " "),
              value: categoryRes[x],
              color: "#A5B4FC",
            };
          })
        );
      }
      setLoading(false);
    };

    fetchThreatCategoryCount();
  }, []);

  return (
    <PageWithMultipleCards
      title={<TitleWithInfo titleText={"Threat API"} />}
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
