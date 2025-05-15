import { EmptyState, LegacyCard, Page } from '@shopify/polaris'
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
    const hasAccess = func.checkUserValidForIntegrations()

    const getMetricsList = async() =>{
        let arr = []
        try {
            // Only fetch traffic metrics if user has access
            if(hasAccess){
                const allMetrics = await settingFunctions.fetchAllMetricNamesAndDescription()
                const trafficMetrics = await settingFunctions.fetchMetricData()
                arr = [...new Set([...trafficMetrics, ...allMetrics])]
            }
        } catch (error) {
        }
        setMetricList(arr)
        return arr;
    }
    const oldMetrics = [
        'INCOMING_PACKETS_MIRRORING',
        'OUTGOING_PACKETS_MIRRORING',
        'OUTGOING_REQUESTS_MIRRORING',
        'TOTAL_REQUESTS_RUNTIME',
        'FILTERED_REQUESTS_RUNTIME'
    ];

    const newMetrics = [
        // Runtime metrics
        'RT_KAFKA_RECORD_COUNT',
        'RT_KAFKA_RECORD_SIZE',
        'RT_KAFKA_LATENCY',
        'KAFKA_RECORDS_LAG_MAX',
        'KAFKA_RECORDS_CONSUMED_RATE',
        'KAFKA_FETCH_AVG_LATENCY',
        'KAFKA_BYTES_CONSUMED_RATE',
        'CYBORG_NEW_API_COUNT',
        'CYBORG_TOTAL_API_COUNT',
        'DELTA_CATALOG_TOTAL_COUNT',
        'DELTA_CATALOG_NEW_COUNT',
        'CYBORG_API_PAYLOAD_SIZE',

        // PostgreSQL metrics
        'PG_SAMPLE_DATA_INSERT_COUNT',
        'PG_SAMPLE_DATA_INSERT_LATENCY',
        'MERGING_JOB_LATENCY',
        'MERGING_JOB_URLS_UPDATED_COUNT',
        'STALE_SAMPLE_DATA_CLEANUP_JOB_LATENCY',
        'STALE_SAMPLE_DATA_DELETED_COUNT',
        'MERGING_JOB_URL_UPDATE_LATENCY',
        'TOTAL_SAMPLE_DATA_COUNT',
        'PG_DATA_SIZE_IN_MB',

        // Testing metrics
        'TESTING_RUN_COUNT',
        'TESTING_RUN_LATENCY',
        'SAMPLE_DATA_FETCH_LATENCY',
        'MULTIPLE_SAMPLE_DATA_FETCH_LATENCY',

        // Cyborg metrics
        'CYBORG_CALL_LATENCY',
        'CYBORG_CALL_COUNT',
        'CYBORG_DATA_SIZE'
    ];

    const names = [...oldMetrics, ...newMetrics];

    const nameMap = new Map(metricsList.map(obj => [obj._name, { description: obj.description, descriptionName: obj.descriptionName }]));
        

    const getOldMetricsData = async(startTime, endTime) => {
        let result = {};
        try {
            const metricData = await settingFunctions.fetchGraphData(groupBy, startTime, endTime, oldMetrics, currentHost);
            for (const [key, countMap] of Object.entries(metricData)) {
                let val = func.convertTrafficMetricsToTrend(countMap);
                result[key] = val;
            }
        } catch (error) {
        }
        return result;
    };

    const getAllMetricsData = async(startTime, endTime, list) => {
        const metricsData = {};
        const currentNameMap = new Map(list.map(obj => [obj._name, { description: obj.description, descriptionName: obj.descriptionName }]));
        const data = await settingFunctions.fetchAllMetricsData(startTime, endTime);
        if (!data) {
            return metricsData;
        }
        for (const metricId of newMetrics) {
            // Filter data for current metricId
            let result = [];    
            const metricData = data.filter(item => item.metricId === metricId);

            if (metricData && metricData.length > 0) {
                const trend = metricData.map(item => ([item.timestamp * 1000,item.value]));
                result.push(
                    { "data": trend, "color": null, "name": currentNameMap.get(metricId)?.descriptionName },
                )

                metricsData[metricId] = result;
            }
        }
        return metricsData;
    };

    const getGraphData = async(startTime, endTime, list) => {
        const [oldMetricsResult, metricsData] = await Promise.all([
            getOldMetricsData(startTime, endTime),
            getAllMetricsData(startTime, endTime, list)
        ]);

        const result = { ...oldMetricsResult, ...metricsData };
        setOrderedResult([]);
        const arr = names.map((name) => ({
            key: name,
            value: result[name]
        }));
        setTimeout(() => {
            setOrderedResult(arr);
        }, 0);
    }

    useEffect(() => {
        const fetchData = async () => {
            const list = await getMetricsList(); // Sourced from state and returns the list
            // Create an up-to-date nameMap for this specific fetch operation            
            setHosts(func.getListOfHosts(apiCollections));            
            // Pass currentNameMap along with startTime and endTime
            await getGraphData(startTime, endTime, list);
        };

        fetchData();
    }, [currDateRange,groupBy]); // Added more dependencies

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

    const defaultChartOptions = function (enableLegends) {
        const options = {
            "plotOptions": {
                series: {
                    events: {
                        // Add legend item click event
                        legendItemClick: function () {
                            var seriesIndex = this.index;
                            var chart = this.chart;
                            var series = chart.series[seriesIndex];

                            chart.series.forEach(function (s) {
                                s.hide(); // Hide all series
                            });
                            series.show(); // Show the selected series

                            return false; // Prevent default legend click behavior
                        }
                    }
                }
            }
        }
        if (enableLegends) {
            options["legend"] = { layout: 'vertical', align: 'right', verticalAlign: 'middle' }
        }
        return options
    }

    const graphContainer = (
        <>
            {/* Original Metrics */}
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => {
                    return element.value && element.value.length > 0 && oldMetrics.includes(element.key)
                }
                    )
                .map((element) => (
                    element.value && element.value.length > 0 ?
                    <LegacyCard.Section key={element.key}>
                        <GraphMetric data={element.value} type='spline' color='#6200EA' areaFillHex="true" height="330"
                            title={nameMap.get(element.key)?.descriptionName} subtitle={nameMap.get(element.key)?.description}
                            defaultChartOptions={defaultChartOptions(true)}
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
                ))}
            {/* Runtime Metrics Section */}
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => newMetrics.slice(0, 12).includes(element.key) && element.value && element.value.length > 0).length > 0 ?
                <LegacyCard.Section>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', marginBottom: '1rem', color: '#202223' }}>Runtime Metrics</h2>
                </LegacyCard.Section> : <div/>
            }
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => newMetrics.slice(0, 12).includes(element.key) && element.value && element.value.length > 0)
                .map((element) => (
                    element.value && element.value.length > 0 ?
                    <LegacyCard.Section key={element.key}>
                        <GraphMetric data={element.value} type='spline' color='#6200EA' areaFillHex="true" height="330"
                            title={nameMap.get(element.key)?.descriptionName} subtitle={nameMap.get(element.key)?.description}
                            defaultChartOptions={defaultChartOptions()}
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
                ))}

            {/* PostgreSQL Metrics Section */}
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => newMetrics.slice(12, 21).includes(element.key) && element.value && element.value.length > 0).length > 0 ?
                <LegacyCard.Section>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', marginBottom: '1rem', color: '#202223' }}>PostgreSQL Metrics</h2>
                </LegacyCard.Section>
                : <div/>
            }
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => newMetrics.slice(12, 21).includes(element.key) && element.value && element.value.length > 0)
                .map((element) => (
                    element.value && element.value.length > 0 ?
                    <LegacyCard.Section key={element.key}>
                        <GraphMetric data={element.value} type='spline' color='#6200EA' areaFillHex="true" height="330"
                            title={nameMap.get(element.key)?.descriptionName} subtitle={nameMap.get(element.key)?.description}
                            defaultChartOptions={defaultChartOptions()}
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
                ))}

            {/* Testing Metrics Section */}
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => newMetrics.slice(21, 25).includes(element.key) && element.value && element.value.length > 0).length > 0 ?
                <LegacyCard.Section>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', marginBottom: '1rem', color: '#202223' }}>Testing Metrics</h2>
                </LegacyCard.Section>
                : <div/>
            }
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element => newMetrics.slice(21, 25).includes(element.key) && element.value && element.value.length > 0)
                .map((element) => (
                    element.value && element.value.length > 0 ?
                    <LegacyCard.Section key={element.key}>
                        <GraphMetric data={element.value} type='spline' color='#6200EA' areaFillHex="true" height="330"
                            title={nameMap.get(element.key)?.descriptionName} subtitle={nameMap.get(element.key)?.description}
                            defaultChartOptions={defaultChartOptions()}
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
                ))}
            {/* Cyborg Metrics Section */}
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element =>  newMetrics.slice(25).includes(element.key) && element.value && element.value.length > 0).length > 0 ?
                <LegacyCard.Section>
                    <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', marginBottom: '1rem', color: '#202223' }}>Cyborg Metrics</h2>
                </LegacyCard.Section>
                : <div/>
            }
            {orderedResult && orderedResult.length > 0 && orderedResult
                .filter(element =>  newMetrics.slice(25).includes(element.key) && element.value && element.value.length > 0)
                .map((element) => (
                    element.value && element.value.length > 0 ?
                    <LegacyCard.Section key={element.key}>
                        <GraphMetric data={element.value} type='spline' color='#6200EA' areaFillHex="true" height="330"
                            title={nameMap.get(element.key)?.descriptionName} subtitle={nameMap.get(element.key)?.description}
                            defaultChartOptions={defaultChartOptions()}
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
                ))}
        </>
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