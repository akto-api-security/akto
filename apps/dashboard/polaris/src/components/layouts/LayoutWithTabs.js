import {LegacyCard, LegacyTabs} from "@shopify/polaris"
import { useCallback, useState } from "react"

export default function LayoutWithTabs(props){

    const [current, setCurrent] = useState(0)
    const setCurrentTab = useCallback((selectedTabIndex)=> setCurrent(selectedTabIndex))

    return(
        <LegacyTabs
            selected={current}
            onSelect={setCurrentTab}
            tabs={props.tabs}
        >
            <h1>hey</h1>
        </LegacyTabs>
    )
}