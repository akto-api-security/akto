import {useReducer} from "react";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import TitleWithInfo from "../../components/shared/TitleWithInfo";
import FilterComponent from "./components/FilterComponent";
import values from "@/util/values";
import {produce} from "immer"
import func from "@/util/func";
import {HorizontalGrid} from "@shopify/polaris";

function ThreatPolicyPage() {

    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);

    const horizontalComponent = <HorizontalGrid columns={1} gap={2}>
        <FilterComponent key={"filter-component"} excludeCategoryNameEquals={"SuccessfulExploit"} showDelete={true}/>
    </HorizontalGrid>

    const components = [
        horizontalComponent,
    ]

    return <PageWithMultipleCards
        title={
            <TitleWithInfo
                titleText={"Threat Policies"}
                tooltipContent={"Identify malicious requests with Akto's powerful threat detection capabilities"}
            />
        }
        isFirstPage={true}
        primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({
            type: "update",
            period: dateObj.period,
            title: dateObj.title,
            alias: dateObj.alias
        })}/>}
        components={components}
    />
}

export default ThreatPolicyPage;