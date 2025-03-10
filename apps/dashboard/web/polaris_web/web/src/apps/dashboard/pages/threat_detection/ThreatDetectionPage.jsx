import { useReducer, useState, useEffect } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import SusDataTable from "./components/SusDataTable";
import values from "@/util/values";
import { produce } from "immer"
import func from "@/util/func";
import SampleDetails from "./components/SampleDetails";
import threatDetectionRequests from "./api";
import PersistStore from "../../../main/PersistStore";
import tempFunc from "./dummyData";
import NormalSampleDetails from "./components/NormalSampleDetails";
import { HorizontalGrid, VerticalStack } from "@shopify/polaris";
import TopThreatTypeChart from "./components/TopThreatTypeChart";
import api from "./api";
import threatDetectionFunc from "./transform";
import InfoCard from "../dashboard/new_components/InfoCard";
import BarGraph from "../../components/charts/BarGraph";

const convertToGraphData = (severityMap) => {
    let dataArr = []
    Object.keys(severityMap).forEach((x) => {
        const color = func.getHexColorForSeverity(x)
        let text = func.toSentenceCase(x)
        const value =  severityMap[x]
        dataArr.push({
            text, value, color
        })
    })
    return dataArr
}

function ThreatDetectionPage() {
    const [loading, setLoading] = useState(false);
    const [currentRefId, setCurrentRefId] = useState('')
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [showDetails, setShowDetails] = useState(false);
    const [sampleData, setSampleData] = useState([])
    const [showNewTab, setShowNewTab] = useState(false)
    const [subCategoryCount, setSubCategoryCount] = useState([]);
    const [severityCountMap, setSeverityCountMap] = useState([]);

    const threatFiltersMap = PersistStore((state) => state.threatFiltersMap);

    const rowClicked = async(data) => {
        if(data?.refId === undefined || data?.refId.length === 0){
            const tempData = tempFunc.getSampleDataOfUrl(data.url);
            const sameRow = func.deepComparison(tempData, sampleData);
            if (!sameRow) {
                setSampleData([{"message": JSON.stringify(tempData),  "highlightPaths": []}])
                setShowDetails(true)
            } else {
                setShowDetails(!showDetails)
            }
            setShowNewTab(false)
        }else{
            setShowNewTab(true)
            const sameRow = currentRefId === data?.refId
            if (!sameRow) {
                let rowData = [];
                await threatDetectionRequests.fetchMaliciousRequest(data?.refId).then((res) => {
                    rowData = [...res.maliciousPayloadsResponses]
                }) 
                setRowDataList(rowData)
                setCurrentRefId(data?.refId)
                setShowDetails(true)
                setMoreInfoData({
                    url: data.url,
                    templateId: data.filterId,
                })
            } else {
                setShowDetails(!showDetails)
            }
        }
        
      }

      const fetchCountBySeverity = async () => {
        setLoading(true);
        let severityMap = {
            CRITICAL: 0,
            HIGH: 0,
            MEDIUM: 0,
            LOW: 0,
        }
        const res = await api.fetchCountBySeverity();
        res.categoryCounts.forEach(({ subCategory, count }) => {
            severityMap[subCategory] = count;
        });
        setSeverityCountMap(convertToGraphData(severityMap));
        setLoading(false);
    };

      useEffect(() => {
        const fetchThreatCategoryCount = async () => {
            setLoading(true);
            const res = await api.fetchThreatCategoryCount();
            const finalObj = threatDetectionFunc.getGraphsData(res);
            setSubCategoryCount(finalObj.subCategoryCount);
            setLoading(false);
          };

        fetchThreatCategoryCount();
        fetchCountBySeverity();
      }, []);

      const ChartComponent = () => {
        return (
          <VerticalStack gap={4} columns={2}>
            <HorizontalGrid gap={4} columns={2}>
              <TopThreatTypeChart
                key={"top-threat-types"}
                data={subCategoryCount}
              />
              <InfoCard
                    title={"Threats by severity"}
                    titleToolTip={"Number of APIs per each category"}
                    component={
                        <BarGraph
                            data={severityCountMap}
                            areaFillHex="true"
                            height={"280px"}
                            defaultChartOptions={{
                                "legend": {
                                    enabled: false
                                },
                            }}
                            showYAxis={true}
                            yAxisTitle="Number of APIs"
                            barWidth={52}
                            barGap={12}
                            showGridLines={true}
                        />
                    }
                />
            </HorizontalGrid>
            
          </VerticalStack>
        );
      };


    const components = [
        <ChartComponent />,
        <SusDataTable key={"sus-data-table"}
            currDateRange={currDateRange}
            rowClicked={rowClicked} 
        />,
        !showNewTab ? <NormalSampleDetails
            title={"Attacker payload"}
            showDetails={showDetails}
            setShowDetails={setShowDetails}
            sampleData={sampleData}
            key={"sus-sample-details"}
        /> :  <SampleDetails
                title={"Attacker payload"}
                showDetails={showDetails}
                setShowDetails={setShowDetails}
                data={rowDataList}
                key={"sus-sample-details"}
                moreInfoData={moreInfoData}
                threatFiltersMap={threatFiltersMap}
            />
            

    ]

    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={"API Threat Activity"}
                tooltipContent={"Identify malicious requests with Akto's powerful threat detection capabilities"}
            />
        }
        isFirstPage={true}
        primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
        components={components}
    />
}

export default ThreatDetectionPage;