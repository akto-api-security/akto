import { useReducer, useState } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import SusDataTable from "./components/SusDataTable";
import values from "@/util/values";
import { produce } from "immer"
import func from "@/util/func";
import transform from "../observe/transform";
function ThreatDetectionPage() {

    const [sampleData, setSampleData] = useState([])
    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [showDetails, setShowDetails] = useState(false);
    const rowClicked = (data) => {
        let tmp = [data.sample];
        let commonMessages = transform.getCommonSamples(tmp, [])
        setSampleData(commonMessages)
        const sameRow = func.deepComparison(commonMessages, sampleData);
        if (!sameRow) {
            setShowDetails(true)
        } else {
            setShowDetails(!showDetails)
        }
    }

    const components = [
        <SusDataTable key={"sus-data-table"}
            currDateRange={currDateRange}
            rowClicked={rowClicked} />,
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