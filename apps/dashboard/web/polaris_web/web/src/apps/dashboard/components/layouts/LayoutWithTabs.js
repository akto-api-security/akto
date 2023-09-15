import {LegacyTabs} from "@shopify/polaris"
import { useState } from "react"
import SpinnerCentered from "../progress/SpinnerCentered"

export default function LayoutWithTabs(props){

    const [current, setCurrent] = useState(0)
    const [loading, setLoading] = useState(false)
    const setCurrentTab = (selected) => {
        setLoading(true)
        setCurrent(selected)
        setTimeout(() => {
            setLoading(false);
        }, 500)
        props.currTab(props.tabs[selected])
    }

    return(
        <LegacyTabs
            selected={current}
            onSelect={setCurrentTab}
            tabs={props.tabs}
        >
            {loading ? <SpinnerCentered/> : props.tabs[current].component }
        </LegacyTabs>
    )
}