import { Page, LegacyCard, EmptyState } from "@shopify/polaris"
import { useEffect, useReducer, useState } from "react"
import { produce } from "immer"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import Dropdown from "../../../components/layouts/Dropdown"
import settingFunctions from "../module"
import GraphMetric from '../../../components/GraphMetric'
import values from '@/util/values'
import func from "@/util/func"

function TrafficCollectorsMetrics() {

    const [metricsList, setMetricList] = useState([])
    const [orderedResult, setOrderedResult] = useState([])
    const [instanceIds, setInstanceIds] = useState([])
    const [selectedInstanceId, setSelectedInstanceId] = useState("ALL")

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTime = getTimeEpoch('since')
    const endTime = getTimeEpoch('until')

    const tcMetrics = [
        'TC_CPU_USAGE',
        'TC_MEMORY_USAGE',
    ];

    const getMetricsList = async() =>{
        let arr = []
        try {
            const allMetrics = await settingFunctions.fetchAllMetricNamesAndDescription()
            arr = allMetrics
        } catch (error) {
        }
        setMetricList(arr)
        return arr;
    }

    const getTCMetricsData = async(startTime, endTime, list) => {
        const metricsData = {};
        const currentNameMap = new Map(list.map(obj => [obj._name, { description: obj.description, descriptionName: obj.descriptionName }]));
        const data = await settingFunctions.fetchAllMetricsData(startTime, endTime);
        if (!data) {
            return metricsData;
        }

        // Extract unique instanceIds from TC metrics
        const uniqueInstanceIds = new Set();
        data.forEach(item => {
            if ((item.metricId === 'TC_CPU_USAGE' || item.metricId === 'TC_MEMORY_USAGE') && item.instanceId) {
                uniqueInstanceIds.add(item.instanceId);
            }
        });
        const instanceIdsList = [{ label: "All Instances", value: "ALL" }, ...Array.from(uniqueInstanceIds).map(id => ({ label: id, value: id }))];
        setInstanceIds(instanceIdsList);

        for (const metricId of tcMetrics) {
            let result = [];
            const metricData = data.filter(item => item.metricId === metricId);

            if (metricData && metricData.length > 0) {
                const groupedByInstance = {};
                metricData.forEach(item => {
                    const instanceId = item.instanceId || 'unknown';
                    // Apply instance filter
                    if (selectedInstanceId === "ALL" || instanceId === selectedInstanceId) {
                        if (!groupedByInstance[instanceId]) {
                            groupedByInstance[instanceId] = [];
                        }
                        groupedByInstance[instanceId].push([item.timestamp * 1000, item.value]);
                    }
                });

                Object.entries(groupedByInstance).forEach(([instanceId, dataPoints]) => {
                    result.push({
                        "data": dataPoints,
                        "color": null,
                        "name": instanceId
                    });
                });

                metricsData[metricId] = result;
            }
        }
        return metricsData;
    };

    const getGraphData = async(startTime, endTime, list) => {
        const metricsData = await getTCMetricsData(startTime, endTime, list);
        setOrderedResult([]);
        const arr = tcMetrics.map((name) => ({
            key: name,
            value: metricsData[name]
        }));
        setTimeout(() => {
            setOrderedResult(arr);
        }, 0);
    }

    useEffect(() => {
        const fetchData = async () => {
            const list = await getMetricsList();
            await getGraphData(startTime, endTime, list);
        };

        fetchData();
    }, [currDateRange, selectedInstanceId]);

    const defaultChartOptions = function (enableLegends) {
        const options = {
            "plotOptions": {
                series: {
                    events: {
                        legendItemClick: function () {
                            var seriesIndex = this.index;
                            var chart = this.chart;
                            var series = chart.series[seriesIndex];

                            chart.series.forEach(function (s) {
                                s.hide();
                            });
                            series.show();

                            return false;
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

    const nameMap = new Map(metricsList.map(obj => [obj._name, { description: obj.description, descriptionName: obj.descriptionName }]));

    return (
        <Page
            title='Traffic Collectors Metrics'
            divider
            backAction={{
                content: 'Back',
                onAction: () => window.history.back()
            }}
        >
            <LegacyCard >
                <LegacyCard.Section>
                    <LegacyCard.Header title="Traffic Collectors Metrics">
                        <DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>
                        {instanceIds.length > 1 && (
                            <Dropdown
                                menuItems={instanceIds}
                                initial={selectedInstanceId}
                                selected={(val) => setSelectedInstanceId(val)}
                            />
                        )}
                    </LegacyCard.Header>
                </LegacyCard.Section>
                {orderedResult.map((element) => (
                    element.value && element.value.length > 0 ? (
                        <LegacyCard.Section key={element.key}>
                            <GraphMetric
                                data={element.value}
                                type='spline'
                                color='#6200EA'
                                areaFillHex="true"
                                height="330"
                                title={nameMap.get(element.key)?.descriptionName}
                                subtitle={nameMap.get(element.key)?.description}
                                defaultChartOptions={defaultChartOptions(true)}
                                background-color="#000000"
                                text="true"
                                inputMetrics={[]}
                            />
                        </LegacyCard.Section>
                    ) : (
                        <LegacyCard.Section key={element.key}>
                            <EmptyState
                                heading={nameMap.get(element.key)?.descriptionName}
                                footerContent="No Graph Data exist !"
                            >
                                <p>{nameMap.get(element.key)?.description}</p>
                            </EmptyState>
                        </LegacyCard.Section>
                    )
                ))}
            </LegacyCard>
        </Page>
    )
}

export default TrafficCollectorsMetrics
