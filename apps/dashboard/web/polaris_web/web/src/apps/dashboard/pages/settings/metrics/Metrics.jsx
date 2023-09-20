import { EmptyState, LegacyCard, Page } from '@shopify/polaris'
import React, { useEffect, useReducer, useState } from 'react'
import DateRangeFilter from '../../../components/layouts/DateRangeFilter'
import Dropdown from '../../../components/layouts/Dropdown'
import {produce} from "immer"
import func from '@/util/func'
import Store from "../../../store"
import "../settings.css"
import settingFunctions from '../module'
import GraphMetric from '../../../components/GraphMetric'
import values from '@/util/values'
import PersistStore from '../../../../main/PersistStore'

function Metrics() {
    
    const [hosts, setHosts] = useState([])
    const apiCollections = PersistStore(state => state.allCollections)
    const [metricsList, setMetricList] = useState([])
    const [orderedResult, setOrderedResult] = useState([])
    const [hostsActive, setHostsActive] = useState(false)
    const [currentHost, setCurrentHost] = useState(null)

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTime = getTimeEpoch("since")
    const endTime = getTimeEpoch("until")

    const initialItems = [
        { label: "All", value: "ALL" },
        { label: "Group by Host", value: "HOST" },
        { label: "Group by Target group", value: "VXLANID" },
    ]

    const [menuItems,setMenuItems] =  useState(initialItems)
    const [groupBy, setGroupBy] = useState("ALL")

    const getMetricsList = async() =>{
        let arr = await settingFunctions.fetchMetricData()
        setMetricList(arr)
    }
    const names = ['INCOMING_PACKETS_MIRRORING','OUTGOING_PACKETS_MIRRORING','OUTGOING_REQUESTS_MIRRORING','TOTAL_REQUESTS_RUNTIME','FILTERED_REQUESTS_RUNTIME']

    const nameMap = new Map(metricsList.map(obj => [obj._name, { description: obj.description, descriptionName: obj.descriptionName }]));
        

    const getGraphData = async(startTime,endTime) =>{
        const metricData = await settingFunctions.fetchGraphData(groupBy,startTime,endTime,names,currentHost)
        let result = {}
        for (const [key, countMap] of Object.entries(metricData)) {
            let val = func.convertTrafficMetricsToTrend(countMap)
            result[key] =val
        }
        setOrderedResult([])
        const arr = names.map((name)=>{
            return{
                key: name,
                value: result[name]
            }
        })
        setTimeout(() => {
            setOrderedResult(arr)
        }, 0);
    }
    useEffect(()=>{
        getMetricsList()
        setHosts(func.getListOfHosts(apiCollections))
    },[])

    useEffect(()=>{
        getGraphData(startTime,endTime)
    },[currDateRange,groupBy])

    function changeItems(){
        setMenuItems(hosts)
        setHostsActive(true)
    }
    const handleChange = (val) =>{
        if(hostsActive){
            setGroupBy("IP")
            setCurrentHost(val)
        }else{
            setGroupBy(val)
            setCurrentHost(null)
        }
        setMenuItems(initialItems)
        setHostsActive(false)
    }

    const defaultChartOptions = {
        "legend": {
            layout: 'vertical', align: 'right', verticalAlign: 'middle'
        },
        "plotOptions": {
            series: {
                events: {
                    // Add legend item click event
                    legendItemClick: function() {
                        var seriesIndex = this.index;
                        var chart = this.chart;
                        var series = chart.series[seriesIndex]; 

                        chart.series.forEach(function(s) {
                            s.hide(); // Hide all series
                        });
                        series.show(); // Show the selected series

                        return false; // Prevent default legend click behavior
                    }
                }
            }
        }
    }

    const graphContainer = (
        orderedResult && orderedResult.length > 0 && orderedResult.map((element)=>(
            element.value && element.value.length > 0 ? 
            <LegacyCard.Section key={element.key}>
                <GraphMetric data={element.value}  type='spline' color='#6200EA' areaFillHex="true" height="330"
                    title={nameMap.get(element.key)?.descriptionName} subtitle = {nameMap.get(element.key)?.description}
                    defaultChartOptions={defaultChartOptions}
                    background-color="#000000"
                    text="true"
                    inputMetrics={[]}
                />
            </LegacyCard.Section>
                :
                <LegacyCard.Section key={element.key}>
                    <EmptyState heading={nameMap.get(element.key)?.descriptionName} footerContent="No Graph Data exist !">
                        <p>{nameMap.get(element.key)?.description}</p>
                    </EmptyState>
                </LegacyCard.Section>
        ))
    )

    return (
        <Page title='Metrics' divider>
            <LegacyCard >
                <LegacyCard.Section>
                    <LegacyCard.Header title="Metrics">
                        <DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>
                        <Dropdown menuItems={menuItems} initial= {groupBy} selected={handleChange}
                                    subItems={hosts.length > 0} subContent="Group by Id" subClick={changeItems}
                        />
                    </LegacyCard.Header>
                </LegacyCard.Section>
                {graphContainer}
            </LegacyCard>
        </Page>
    )
}

export default Metrics