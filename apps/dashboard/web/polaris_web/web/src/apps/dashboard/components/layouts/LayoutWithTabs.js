import {LegacyTabs} from "@shopify/polaris"
import { useEffect, useState } from "react"
import SpinnerCentered from "../progress/SpinnerCentered"

export default function LayoutWithTabs(props){

    const [current, setCurrent] = useState(0)
    const [loading, setLoading] = useState(false)
    const tabs = !props.disabledTabs ? props.tabs : props.tabs.filter(obj => !props.disabledTabs.includes(obj.id))
    const setCurrentTab = (selected) => {
        if(!props.noLoading){
            setLoading(true)
        }
        setCurrent(selected)
        setTimeout(() => {
            setLoading(false);
        }, 500)
        props.currTab(tabs[selected])
    }

    // Optional: a parent can switch tabs programmatically (e.g. a banner action
    // that opens the Logs tab) by passing selectedTabId; bump selectedTabNonce to
    // re-select the same tab. Without these props behaviour is unchanged.
    useEffect(() => {
        if (!props.selectedTabId) return
        const idx = tabs.findIndex(t => t.id === props.selectedTabId)
        if (idx >= 0) setCurrentTab(idx)
    }, [props.selectedTabId, props.selectedTabNonce])

    return(
        <LegacyTabs
            selected={current}
            onSelect={setCurrentTab}
            tabs={tabs}
        >
            {loading ? <SpinnerCentered/> : (tabs[current]?.component || null) }
        </LegacyTabs>
    )
}