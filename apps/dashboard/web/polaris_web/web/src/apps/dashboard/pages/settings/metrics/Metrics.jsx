import { Divider, EmptyState, LegacyCard, Page } from '@shopify/polaris'
import React, { useEffect, useReducer, useState } from 'react'
import DateRangeFilter from '../../../components/layouts/DateRangeFilter'
import Dropdown from '../../../components/layouts/Dropdown'
import {produce} from "immer"
import func from '@/util/func'
import "../settings.css"
import settingFunctions from '../module'
import GraphMetric from '../../../components/GraphMetric'
import values from '@/util/values'
import PersistStore from '../../../../main/PersistStore'
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import FlyLayout from '../../../components/layouts/FlyLayout'

function Metrics() {
    
    const [hosts, setHosts] = useState([])
    const apiCollections = PersistStore(state => state.allCollections)
    const [metricsList, setMetricList] = useState([])
    const [orderedResult, setOrderedResult] = useState([])
    const [hostsActive, setHostsActive] = useState(false)
    const [currentHost, setCurrentHost] = useState(null)

    const [runtimeMetricsData, setRuntimeMetricsData] = useState([])
    const [showRuntimeGraph, setShowRuntimeGraph] = useState(false)
    const [graphs, setGraphs] = useState([])
    const [runtimeFilterVal, setRuntimeFilterVal] = useState('last7days')
    const [loading, setLoading] = useState(false)

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

    const runtimeMetricsNameMap = {
        "rt_kafka_record_count": "Kafka Record Count",
        "rt_kafka_record_size": "Kafka Record Size",
        "rt_kafka_latency": "Kafka Latency",
        "rt_kafka_records_lag_max": "Kafka Records Lag Max",
        "rt_kafka_records_consumed_rate": "Kafka Records Consumed Rate",
        "rt_kafka_fetch_avg_latency": "Kafka Fetch Average Latency",
        "rt_kafka_bytes_consumed_rate": "Kafka Bytes Consumed Rate"
    }

    const [menuItems,setMenuItems] =  useState(initialItems)
    const [groupBy, setGroupBy] = useState("ALL")
    const hasAccess = func.checkUserValidForIntegrations()

    const getMetricsList = async() =>{
        let arr = []
        if(hasAccess){
            arr =  await settingFunctions.fetchMetricData()
        }
        setMetricList(arr)
    }
    
    const runtimeFilterOptionsValueMap = {
        "15minutes": Math.floor((Date.now() - (15 * 60 * 1000)) / 1000),
        "30minutes": Math.floor((Date.now() - (30 * 60 * 1000)) / 1000),
        "1hour": Math.floor(Date.now() / 1000) - 3600,
        "6hours": Math.floor(Date.now() / 1000) - 21600,
        "1day": Math.floor((Date.now() - (24 * 60 * 60 * 1000)) / 1000),
        "3days": Math.floor((Date.now() - (3 * 24 * 60 * 60 * 1000)) / 1000),
        "last7days": Math.floor((Date.now() - (7 * 24 * 60 * 60 * 1000)) / 1000)
    }

    const getRuntimeMetrics = async () => {
        const currentEpoch = Math.floor(Date.now() / 1000)
        let runtimeRes = await settingFunctions.fetchRuntimeInstances(runtimeFilterOptionsValueMap[runtimeFilterVal], currentEpoch)

        const uniqueInstanceIds = new Set(runtimeRes.instanceIds)
        const runtimeMetrics = runtimeRes.runtimeMetrics

        const namesArray = Object.keys(runtimeMetricsNameMap)

        const groupedData = Array.from(uniqueInstanceIds).map(instanceId => {
            const instanceData = runtimeMetrics.filter(item => {return item.instanceId === instanceId && namesArray.includes(item.name.toLowerCase())})

            if (!instanceData.length) return null

            const startTime = Math.min(...instanceData.map(item => item.timestamp))
            const heartbeat = Math.max(...instanceData.map(item => item.timestamp))

            const result = {
                id: instanceId,
                startTime: func.prettifyEpoch(startTime),
                heartbeat: func.prettifyEpoch(heartbeat),
                version: instanceData[0].version,
            }

            const latestValuesByName = {};

            instanceData.forEach(item => {
                const name = item.name.toLowerCase();

                if (!latestValuesByName[name] || latestValuesByName[name].timestamp < item.timestamp) {
                    latestValuesByName[name] = {
                        val: item.val,
                        timestamp: item.timestamp
                    };
                }
            });

            Object.keys(latestValuesByName).forEach(name => {
                result[name] = latestValuesByName[name].val;
            });

            return result
        }).filter(item => item !== null)

        setRuntimeMetricsData(groupedData)
    }

    const names = ['INCOMING_PACKETS_MIRRORING','OUTGOING_PACKETS_MIRRORING','OUTGOING_REQUESTS_MIRRORING','TOTAL_REQUESTS_RUNTIME','FILTERED_REQUESTS_RUNTIME']

    const nameMap = new Map(metricsList.map(obj => [obj._name, { description: obj.description, descriptionName: obj.descriptionName }]));
        

    const getGraphData = async(startTime,endTime) =>{
        const metricData = hasAccess ? await settingFunctions.fetchGraphData(groupBy,startTime,endTime,names,currentHost) : []
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

    useEffect(() => {
        getRuntimeMetrics()
    }, [runtimeFilterVal])

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

    const runtimeFilterOptions = [
        { label: '15 Minutes ago', value: '15minutes' },
        { label: '30 Minutes ago', value: '30minutes' },
        { label: '1 hour ago', value: '1hour' },
        { label: '6 hours ago', value: '6hours' },
        { label: '1 Day ago', value: '1day' },
        { label: '3 Days ago', value: '3days' },
        { label: 'Last 7 days', value: 'last7days' }
    ]

    const fillMissingTimestamps = (data) => {
        const sortedData = data.slice().sort((a, b) => a[0] - b[0])
        const smallestTime = sortedData[0][0]
        const largestTime = sortedData[sortedData.length - 1][0]
        
        const result = []
        const timestampMap = new Map()
    
        for (let timestamp = smallestTime; timestamp <= largestTime; timestamp += 60000) {
            timestampMap.set(timestamp, 0)
        }
    
        sortedData.forEach(([timestamp, value]) => {
            timestampMap.set(timestamp, value)
        })
    
        timestampMap.forEach((value, timestamp) => {
            result.push([timestamp, value])
        })
    
        result.sort((a, b) => a[0] - b[0])
        
        return result
    }

    const handleOnRuntimeRowClick = async (data) => {
        setLoading(true)
        const currentEpoch = Math.floor(Date.now() / 1000)
        const instanceId = data.id
        const runtimeMetricsRes = await settingFunctions.fetchRuntimeMetrics(runtimeFilterOptionsValueMap[runtimeFilterVal], currentEpoch, instanceId)
                        
        const valuesByName = getRuntimeValuesByName(runtimeMetricsRes)
        
        setShowRuntimeGraph(true)
        const componentsArray = []

        Object.entries(valuesByName).forEach(([name, values]) => {
            const readableName = runtimeMetricsNameMap[name.toLowerCase()]
            const valuesWithMissingTimestamp = fillMissingTimestamps(values)
            
            const component = runtimeGraphContainer(valuesWithMissingTimestamp, readableName)
            componentsArray.push(<Divider />)
            componentsArray.push(component)
        })
        
        setGraphs(componentsArray)

        setTimeout(() => {
            setLoading(false)
        }, 100);
    }

    const getRuntimeValuesByName = (data) => {
        const valueByName = {};
    
        data.forEach(item => {
            if (!valueByName[item.name]) {
                valueByName[item.name] = [];
            }
            valueByName[item.name].push([(item.timestamp*1000), item.val])
        });
    
        for (const name in valueByName) {
            if (Object.hasOwnProperty.call(valueByName, name)) {
                valueByName[name].sort((a, b) => a.val - b.val);
            }
        }
    
        return valueByName;
    }

    const headers = [
        { title: "Instance ID", text: "Instance ID", value: "id", showFilter: false },
        { title: "Heartbeat", text: "Heartbeat", value: "heartbeat", showFilter: false },
        { title: "Start Time", text: "Start Time", value: "startTime", showFilter: false },
        { title: "Runtime Version", text: "Runtime Version", value: "version", showFilter: false },
        { title: "Kafka Record Count", text: "Kafka Record Count", value: "rt_kafka_record_count", showFilter: false },
        { title: "Kafka Record Size", text: "Kafka Record Size", value: "rt_kafka_record_size", showFilter: false },
        { title: "Kafka Latency", text: "Kafka Latency", value: "rt_kafka_latency", showFilter: false },
        { title: "Kafka Records Lag Max", text: "Kafka Records Lag Max", value: "rt_kafka_records_lag_max", showFilter: false },
        { title: "Kafka Records Consumed Rate", text: "Kafka Records Consumed Rate", value: "rt_kafka_records_consumed_rate", showFilter: false },
        { title: "Kafka Fetch Average Latency", text: "Kafka Records Consumed Rate", value: "rt_kafka_fetch_avg_latency", showFilter: false },
        { title: "Kafka Bytes Consumed Rate", text: "Kafka Bytes Consumed Rate", value: "rt_kafka_bytes_consumed_rate", showFilter: false },
    ]

    const promotedBulkActions = (selectedResources) => {
        const actions = [
            {
                content: `Copy Instance ID${func.addPlurality(selectedResources.length)}`,
                onAction: () => navigator.clipboard.writeText(selectedResources.toString())
            }
        ]

        return actions;
    }

    const runtimeTableContainer = (
        <GithubSimpleTable
            key={"runtime-metrics-container"}
            pageLimit={50}
            data={runtimeMetricsData}
            resourceName={{
                singular: 'metric',
                plural: 'metrics',
            }}
            filters={[]}
            useNewRow={true}
            condensedHeight={true}
            onRowClick={handleOnRuntimeRowClick}
            selectable={true}
            headers={headers}
            headings={headers}
            hideQueryField={true}
            hideContactUs={true}
            promotedBulkActions={promotedBulkActions}
        />
    )

    const processChartData = (data) => {
        return [
            {
                data: data,
                color: "#AEE9D1",
                name: "Runtime Values"
            },
        ]
    }

    const runtimeGraphContainer = (data, title) => (
        <LegacyCard.Section>
            <GraphMetric data={processChartData(data)} type='spline' color='#6200EA' areaFillHex="true" height="330"
                title={title}
                defaultChartOptions={defaultChartOptions}
                background-color="#000000"
                text="true"
                inputMetrics={[]}
            />
        </LegacyCard.Section>
    )

    return (
        <Page fullWidth title='Metrics' divider>
            <LegacyCard>
                <LegacyCard.Section>
                    <LegacyCard.Header title="Traffic Processors">
                        <Dropdown menuItems={runtimeFilterOptions} initial= {runtimeFilterVal} selected={(val) => setRuntimeFilterVal(val)} />
                    </LegacyCard.Header>
                </LegacyCard.Section>
                <Divider />
                { runtimeTableContainer }
            </LegacyCard>

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
          
            <FlyLayout
                title="Runtime Metrics Details"
                show={showRuntimeGraph}
                setShow={setShowRuntimeGraph}
                components={graphs}
                loading={loading}
            />
        </Page>
    )
}

export default Metrics