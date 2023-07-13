import { EmptyState, LegacyCard, Page } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import DateRangePicker from '../../../components/layouts/DateRangePicker'
import Dropdown from '../../../components/layouts/Dropdown'

import func from '@/util/func'
import Store from "../../../store"
import "../settings.css"
import settingFunctions from '../module'
import GraphMetric from '../../../components/GraphMetric'

function Metrics() {
    
    const [hosts, setHosts] = useState([])
    const apiCollections = Store(state => state.allCollections)
    const [metricsList, setMetricList] = useState([])
    const [orderedResult, setOrderedResult] = useState([])
    const [startTime, setStartTime] = useState(Math.floor(Date.now() / 1000))
    const [endTime, setEndTime] = useState(Math.floor(Date.now() / 1000))

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
        console.log(names,metricsList)
        let host = null
        const metricData = await settingFunctions.fetchGraphData(groupBy,startTime,endTime,names,host)
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
    },[startTime,endTime,groupBy])

    const handleDate = (dateRange) =>{
        setStartTime(Math.floor(Date.parse(dateRange.since) / 1000))
        setEndTime(Math.floor(Date.parse(dateRange.until) / 1000))
    }

    function changeItems(){
        setMenuItems(hosts)
    }
    const handleChange = (val) =>{
        setGroupBy(val)
        setMenuItems(initialItems)
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
                    title={nameMap.get(element.key).descriptionName} subtitle = {nameMap.get(element.key).description}
                    defaultChartOptions={defaultChartOptions}
                    background-color="#000000"
                    text="true"
                    inputMetrics={[]}
                />
            </LegacyCard.Section>
                :
                <LegacyCard.Section key={element.key}>
                    <EmptyState heading={nameMap.get(element.key).descriptionName} footerContent="No Graph Data exist !">
                        <p>{nameMap.get(element.key).description}</p>
                    </EmptyState>
                </LegacyCard.Section>
        ))
    )

    return (
        <Page title='Metrics' divider>
            <LegacyCard >
                <LegacyCard.Section>
                    <LegacyCard.Header title="Metrics">
                        <DateRangePicker getDate={handleDate}/>
                        <Dropdown menuItems={menuItems} initial= {groupBy} selected={handleChange}
                                    subItems={hosts.length > 0} subContent="Group by Id" subclick={changeItems}
                        />
                    </LegacyCard.Header>
                </LegacyCard.Section>
                {graphContainer}
            </LegacyCard>
        </Page>
    )
}

export default Metrics