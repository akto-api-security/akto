import { useReducer, useState } from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import FilterComponent from "./components/FilterComponent";
import SusDataTable from "./components/SusDataTable";
import values from "@/util/values";
import { produce } from "immer"
import func from "@/util/func";
import transform from "../observe/transform";
import SampleDataComponent from "../../components/shared/SampleDataComponent";
import { Box, HorizontalGrid, LegacyCard } from "@shopify/polaris";
function ThreatDetectionPage() {

    const [sampleData, setSampleData] = useState([])
    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const rowClicked = (data) => {
        let sampleData = [data.sample];
        let commonMessages = transform.getCommonSamples(sampleData, [])
        setSampleData(commonMessages)
    }

    const sampleDataComponent = sampleData.length > 0 ? <Box key={"request"}>
        <LegacyCard>
            <SampleDataComponent
                type={"request"}
                sampleData={sampleData[Math.min(0, sampleData.length - 1)]}
                minHeight={"47vh"}
                showDiff={false}
                isNewDiff={false}
            />
        </LegacyCard>
    </Box> : <></>

    const horizontalComponent = <HorizontalGrid columns={sampleData.length > 0 ? 2 : 1} gap={2}>
        <FilterComponent key={"filter-component"} />
        {sampleDataComponent}
    </HorizontalGrid>

    const components = [horizontalComponent,
        <SusDataTable key={"sus-data-table"} currDateRange={currDateRange} rowClicked={rowClicked} />]

    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={"Threat detection"}
                tooltipContent={"Identify malicious requests with Akto's powerful threat detection capabilities"}
            />
        }
        isFirstPage={true}
        primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
        components={components}
    />
}

export default ThreatDetectionPage;