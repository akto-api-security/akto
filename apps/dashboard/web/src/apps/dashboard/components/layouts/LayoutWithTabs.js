import {LegacyTabs} from "@shopify/polaris"
import { useState } from "react"

export default function LayoutWithTabs(props){

    const [current, setCurrent] = useState(0)
    const setCurrentTab = (selected) => {
        setCurrent(selected)
        props.currTab(props.tabs[selected])
    }

    return(
        <LegacyTabs
            selected={current}
            onSelect={setCurrentTab}
            tabs={props.tabs}
        >
            {props.tabs[current].component }
        </LegacyTabs>
    )
}