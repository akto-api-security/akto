import { useReducer, useState } from "react";
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
function ThreatDetectionPage() {
    const [currentRefId, setCurrentRefId] = useState('')
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [showDetails, setShowDetails] = useState(false);

    const threatFiltersMap = PersistStore((state) => state.filtersMap);

    const rowClicked = async(data) => {
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

    const components = [
        <SusDataTable key={"sus-data-table"}
            currDateRange={currDateRange}
            rowClicked={rowClicked} 
        />,
        <SampleDetails
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