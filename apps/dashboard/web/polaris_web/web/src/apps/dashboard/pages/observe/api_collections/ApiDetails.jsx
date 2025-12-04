import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs"
import { Box, Button, Popover, Modal, Tooltip, ActionList, VerticalStack, HorizontalStack, Tag, Text } from "@shopify/polaris"
import FlyLayout from "../../../components/layouts/FlyLayout";
import GithubCell from "../../../components/tables/cells/GithubCell";
import ApiGroups from "../../../components/shared/ApiGroups";
import SampleDataList from "../../../components/shared/SampleDataList";
import { useEffect, useState, useRef } from "react";
import api from "../api";
import ApiSchema from "./ApiSchema";
import dashboardFunc from "../../transform";
import AktoGptLayout from "../../../components/aktoGpt/AktoGptLayout";
import func from "@/util/func" 
import transform from "../transform";
import ApiDependency from "./ApiDependency";
import RunTest from "./RunTest";
import PersistStore from "../../../../main/PersistStore";
import gptApi from "../../../components/aktoGpt/api";
import GraphMetric from '../../../components/GraphMetric'
import { HorizontalDotsMinor, FileMinor } from "@shopify/polaris-icons"
import LocalStore from "../../../../main/LocalStorageStore";
import InlineEditableText from "../../../components/shared/InlineEditableText";
import GridRows from "../../../components/shared/GridRows";
import Dropdown from "../../../components/layouts/Dropdown";
import ApiIssuesTab from "./ApiIssuesTab";
import ForbiddenRole from "../../../components/shared/ForbiddenRole";

import Highcharts from 'highcharts';
import HighchartsMore from 'highcharts/highcharts-more';
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

HighchartsMore(Highcharts);

const statsOptions = [
    {label: "15 minutes", value: 15*60},
    {label: "30 minutes", value: 30*60},
    {label: "1 hour", value: 60*60},
    {label: "3 hours", value: 3*60*60},
    {label: "6 hours", value: 6*60*60},
    {label: "12 hours", value: 12*60*60},
    {label: "1 day", value: 24*60*60},
    {label: "7 days", value: 7*24*60*60}
]

const bucketLabelMap = {
    b1: "1-10", b2: "11-50", b3: "51-100", b4: "101-250",
    b5: "251-500", b6: "501-1000", b7: "1001-2500", b8: "2501-5000",
    b9: "5001-10000", b10: "10001-20000", b11: "20001-35000",
    b12: "35001-50000", b13: "50001-100000", b14: "100001+"
};

function TechCard(props){
    const {cardObj} = props;
    return(
        <Tag key={cardObj.id}>
            <Text variant="bodyMd" as="span">{cardObj.name}</Text>
        </Tag> 
    )
}

function ApiDetails(props) {
    const { showDetails, setShowDetails, apiDetail, headers, getStatus, isGptActive, collectionIssuesData } = props

    const localCategoryMap = LocalStore.getState().categoryMap
    const localSubCategoryMap = LocalStore.getState().subCategoryMap

    const [sampleData, setSampleData] = useState([])
    const [paramList, setParamList] = useState([])
    const [selectedUrl, setSelectedUrl] = useState({})
    const [prompts, setPrompts] = useState([])
    const [isGptScreenActive, setIsGptScreenActive] = useState(false)
    const [loading, setLoading] = useState(false)
    const [showMoreActions, setShowMoreActions] = useState(false)
    const setSelectedSampleApi = PersistStore(state => state.setSelectedSampleApi)
    const [disabledTabs, setDisabledTabs] = useState([])
    const [description, setDescription] = useState("")
    const [headersWithData, setHeadersWithData] = useState([])
    const [isEditingDescription, setIsEditingDescription] = useState(false)
    const [editableDescription, setEditableDescription] = useState(description)
    const [useLocalSubCategoryData, setUseLocalSubCategoryData] = useState(false)
    const [apiCallStats, setApiCallStats] = useState([]);
    const [apiCallDistribution, setApiCallDistribution] = useState([]);
    const endTs = func.timeNow();
    const [startTime, setStartTime] = useState(endTs - statsOptions[6].value)
    const [hasApiStats, setHasApiStats] = useState(false);
    const [hasApiDistribution, setHasApiDistribution] = useState(false);
    const apiStatsAvailableRef = useRef(false);
    const apiDistributionAvailableRef = useRef(false);
    const [selectedTabId, setSelectedTabId] = useState('values');
    const [showForbidden, setShowForbidden] = useState(false);

    const statusFunc = getStatus ? getStatus : (x) => {
        try {
            if (paramList && paramList.length > 0 &&
                paramList.filter(x => x?.nonSensitiveDataType).map(x => x.subTypeString).includes(x)) {
                return "info"
            }
        } catch (e) {
            return "warning"
        }
        return "warning"
    }

    const standardHeaders = new Set(transform.getStandardHeaderList())

    // const getNiceBinSize = (rawSize) => {
    //     const magnitude = Math.pow(10, Math.floor(Math.log10(rawSize)));
    //     const leading = rawSize / magnitude;
    //     if (leading <= 1) return 1 * magnitude;
    //     if (leading <= 2) return 2 * magnitude;
    //     if (leading <= 5) return 5 * magnitude;
    //     return 10 * magnitude;
    // };

    // // Function to bin data
    // const binData = (rawData, targetBins = 10) => {
    //     if (rawData.length === 0) return [];
    
    //     const sortedData = rawData.sort((a, b) => a[0] - b[0]);
    //     const calls = sortedData.map(point => point[0]);
    
    //     const minCalls = Math.max(0, Math.min(...calls));
    //     const maxCalls = Math.max(...calls);
    //     const range = maxCalls - minCalls;
    
    //     // ✅ Smart bin size
    //     const rawBinSize = Math.ceil(range / targetBins);
    //     const binSize = getNiceBinSize(rawBinSize);
    
    //     const start = Math.floor(minCalls / binSize) * binSize;
    //     const end = Math.ceil(maxCalls / binSize) * binSize;
    
    //     const bins = [];

    //     for (let i = start; i < end; i += binSize) {
    //         bins.push({ range: [i, i + binSize], count: 0 });
    //     }
    //     // for (let i = start; i <= end; i += binSize) {
    //     //     bins.push({ range: [i, i + binSize], count: 0 });
    //     // }
    
    //     rawData.forEach(([calls, users]) => {
    //         const binIndex = Math.floor((calls - start) / binSize);
    //         if (binIndex >= 0 && binIndex < bins.length) {
    //             bins[binIndex].count += users;
    //         }
    //     });
    
    //     return {
    //         data: bins.map(bin => ({
    //             x: (bin.range[0] + bin.range[1]) / 2,
    //             y: bin.count,
    //             binRange: [bin.range[0], bin.range[1]]
    //         })).filter(bin => bin.y > 0),
    //         binSize
    //     };
    // };

    const updateApiCallStatsTabVisibility = () => {
        const hasStats = apiStatsAvailableRef.current;
        const hasDist = apiDistributionAvailableRef.current;
        setDisabledTabs(prev => {
            const updated = prev.filter(tab => tab !== "api-call-stats");
            if (!hasStats && !hasDist) updated.push("api-call-stats");
            return updated;
        });
    };

    const fetchDistributionData = async () => {
        try {
            const { apiCollectionId, endpoint, method } = apiDetail;
            const res = await api.fetchIpLevelApiCallStats(apiCollectionId, endpoint, method, Math.floor(startTime / 60),  Math.floor(endTs / 60));
    
            const bucketStats = res.bucketStats || [];
    
            const sortedBuckets = bucketStats
                .filter(b => b.p25 !== undefined && b.p50 !== undefined && b.p75 !== undefined)
                .sort((a, b) => {
                    const aIndex = parseInt(a.bucketLabel.replace("b", ""), 10);
                    const bIndex = parseInt(b.bucketLabel.replace("b", ""), 10);
                    return aIndex - bIndex;
                });
    
            const categories = sortedBuckets.map(b => bucketLabelMap[b.bucketLabel] || b.bucketLabel);

            // const categories = sortedBuckets.map(b => b.bucketLabel);
    
            const data = sortedBuckets.map(b => [
                b.min ?? b.p25,  // whisker low
                b.p25,
                b.p50,           // median
                b.p75,
                b.max ?? b.p75   // whisker high
            ]);
    
            const boxPlotSeries = [{
                name: "API Call Distribution",
                type: "boxplot",
                data,
                color: "#1E90FF",
                categories
            }];

            const hasData = boxPlotSeries?.[0]?.data?.length > 0;
            apiDistributionAvailableRef.current = hasData;
            setApiCallDistribution(boxPlotSeries);
            setHasApiDistribution(hasData);
            updateApiCallStatsTabVisibility();

        } catch (err) {
            console.error("Error fetching distribution:", err);
            apiDistributionAvailableRef.current = false;
            setApiCallDistribution([]);
            setHasApiDistribution(false);
            updateApiCallStatsTabVisibility(hasApiStats, false);
        }
    };
    
    

    const fetchStats = async (apiCollectionId, endpoint, method) => {
        try {
            setApiCallStats([]); // Clear state before fetching new data
            const res = await api.fetchApiCallStats(apiCollectionId, endpoint, method, startTime, endTs);
            const transformedData = [
                {
                    data: res.result.apiCallStats.sort((a, b) => b.ts - a.ts).map((item) => [item.ts * 60 * 1000, item.count]),
                    color: "",
                    name: mapLabel('Api', getDashboardCategory()) + ' Calls',
                },
            ];

            const hasData = transformedData?.[0]?.data?.length > 0;
            apiStatsAvailableRef.current = hasData;
            setApiCallStats(transformedData);
            setHasApiStats(hasData);
            updateApiCallStatsTabVisibility(hasData, hasApiDistribution);

        } catch (error) {
            console.error("Error fetching API call stats:", error);
            apiStatsAvailableRef.current = false;
            setApiCallStats([]);
            setHasApiStats(false);
            updateApiCallStatsTabVisibility(false, hasApiDistribution);
        }
    };

    const fetchData = async () => {
        if (showDetails) {
            setLoading(true)
            const { apiCollectionId, endpoint, method, description } = apiDetail
            setSelectedUrl({ url: endpoint, method: method })
            
            try {
                await api.checkIfDependencyGraphAvailable(apiCollectionId, endpoint, method).then((resp) => {
                    if (!resp.dependencyGraphExists) {
                        setDisabledTabs(["dependency"])
                    } else {
                        setDisabledTabs([])
                    }
                })
            } catch (error) {
                if (error?.response?.status === 403 || error?.status === 403) {
                    setShowForbidden(true);
                    setLoading(false);
                    return;
                }
            }

            setTimeout(() => {
                setDescription(description == null ? "" : description)
                setEditableDescription(description == null ? "" : description)
            }, 100)
            headers.forEach((header) => {
                if (header.value === "description") {
                    header.action = () => setIsEditingDescription(true)
                }
            })

            let commonMessages = []
            try {
                const res = await api.fetchSampleData(endpoint, apiCollectionId, method)
                try {
                    const resp = await api.fetchSensitiveSampleData(endpoint, apiCollectionId, method)
                    if (resp.sensitiveSampleData && Object.keys(resp.sensitiveSampleData).length > 0) {
                        if (res.sampleDataList.length > 0) {
                            commonMessages = transform.getCommonSamples(res.sampleDataList[0].samples, resp)
                        } else {
                            commonMessages = transform.prepareSampleData(resp, '')
                        }
                    } else {
                        let sensitiveData = []
                        try {
                            const res3 = await api.loadSensitiveParameters(apiCollectionId, endpoint, method)
                            sensitiveData = res3.data.endpoints;
                        } catch (error) {
                            if (error?.response?.status === 403 || error?.status === 403) {
                                setShowForbidden(true);
                                setLoading(false);
                                return;
                            }
                        }
                        let samples = res.sampleDataList.map(x => x.samples)
                        samples = samples.reverse();
                        samples = samples.flat()
                        let newResp = transform.convertSampleDataToSensitiveSampleData(samples, sensitiveData)
                        commonMessages = transform.prepareSampleData(newResp, '')
                    }
                    setSampleData(commonMessages)
                } catch (error) {
                    if (error?.response?.status === 403 || error?.status === 403) {
                        setShowForbidden(true);
                        setLoading(false);
                        return;
                    }
                }
            } catch (error) {
                if (error?.response?.status === 403 || error?.status === 403) {
                    setShowForbidden(true);
                    setLoading(false);
                    return;
                }
            }
            
            setTimeout(() => {
                setLoading(false)
            }, 100)
            const queryPayload = dashboardFunc.getApiPrompts(apiCollectionId, endpoint, method)[0].prepareQuery();
            try{
                if(isGptActive && window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"] && window.STIGG_FEATURE_WISE_ALLOWED["AKTO_GPT_AI"]?.isGranted === true){
                    await gptApi.ask_ai(queryPayload).then((res) => {
                        if (res.response.responses && res.response.responses.length > 0) {
                            const metaHeaderResp = res.response.responses.filter(x => !standardHeaders.has(x.split(" ")[0]))
                            setHeadersWithData(metaHeaderResp)
                        }
                    }
                    ).catch((err) => {
                        console.error("Failed to fetch prompts:", err);
                    })
                }
            }catch (e) {
            }   
            fetchStats(apiCollectionId, endpoint, method)
            fetchDistributionData(); // Fetch distribution data
        }
    }

    const handleSaveDescription = async () => {
        const { apiCollectionId, endpoint, method } = apiDetail;
        
        setIsEditingDescription(false);
        
        if(editableDescription === description) {
            return
        }
        await api.saveEndpointDescription(apiCollectionId, endpoint, method, editableDescription)
            .then(() => {
                setDescription(editableDescription);
                func.setToast(true, false, "Description saved successfully");
            })
            .catch((err) => {
                console.error("Failed to save description:", err);
                func.setToast(true, true, "Failed to save description. Please try again.");
            });
    };

    const runTests = async (testsList) => {
        setIsGptScreenActive(false)
        const apiKeyInfo = {
            apiCollectionId: apiDetail.apiCollectionId,
            url: selectedUrl.url,
            method: selectedUrl.method
        }
        await api.scheduleTestForCustomEndpoints(apiKeyInfo, func.timNow(), false, testsList, "akto_gpt_test", -1, -1)
        func.setToast(true, false, "Triggered tests successfully!")
    }

    useEffect(() => {
        if (
            (localCategoryMap && Object.keys(localCategoryMap).length > 0) &&
            (localSubCategoryMap && Object.keys(localSubCategoryMap).length > 0)
        ) {
            setUseLocalSubCategoryData(true)
        }

        fetchData();
        setHeadersWithData([])
    }, [apiDetail])

    useEffect(() => {
        if(showDetails){
            const { apiCollectionId, endpoint, method } = apiDetail;
            fetchStats(apiCollectionId, endpoint, method);
            fetchDistributionData();
            setApiCallDistribution([]);
        }
    }, [startTime, apiDetail]);

    function displayGPT() {
        setIsGptScreenActive(true)
        let requestObj = { key: "PARAMETER", jsonStr: sampleData[0]?.message, apiCollectionId: Number(apiDetail.apiCollectionId) }
        const activePrompts = dashboardFunc.getPrompts(requestObj)
        setPrompts(activePrompts)
    }

    function isDeMergeAllowed() {
        const { endpoint } = apiDetail
        if (!endpoint || endpoint === undefined) {
            return false;
        }
        return (endpoint.includes("STRING") || endpoint.includes("INTEGER") || endpoint.includes("OBJECT_ID") || endpoint.includes("VERSIONED"))
    }

    const openTest = () => {
        const apiKeyInfo = {
            apiCollectionId: apiDetail["apiCollectionId"],
            url: selectedUrl.url,
            method: {
                "_name": selectedUrl.method
            }
        }
        setSelectedSampleApi(apiKeyInfo)
        const navUrl = window.location.origin + "/dashboard/test-editor/REMOVE_TOKENS"
        window.open(navUrl, "_blank")
    }

    const isDemergingActive = isDeMergeAllowed();

    const defaultChartOptions = (enableLegends) => {
        const options = {
          plotOptions: {
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
                },
              },
            },
          },
        };
        if (enableLegends) {
          options['legend'] = { layout: 'vertical', align: 'right', verticalAlign: 'middle' };
        }
        return options;
    };

    const distributionChartOptions = {
        chart: {
            type: 'column',
            marginTop: 10,
            marginBottom: 70,
            marginRight: 10,
        },
        xAxis: {
            title: {
                text: mapLabel('Api', getDashboardCategory()) + ' Call Frequency',
                style: {
                    fontSize: '12px',
                },
            },
            gridLineWidth: 0,
            labels: {
                style: {
                    fontSize: '12px',
                },
                enabled: true,
            },
            tickmarkPlacement: 'on',
            tickWidth: 1,
            tickLength: 5,
        },
        yAxis: {
            title: {
                text: 'Number of Users',
                style: {
                    fontSize: '12px',
                },
            },
            gridLineWidth: 0,
        },
        plotOptions: {
            column: {
                pointPadding: 0.05,
                groupPadding: 0.1,
                borderWidth: 0,
            },
        },
        tooltip: {
            formatter: function () {
                const binRange = this.point?.binRange || [Math.floor(this.x) - 15, Math.floor(this.x) + 15];
                return `<b>${this.y}</b> users made calls in range <b>${binRange[0]} to ${binRange[1] - 1}</b>`;
            },
        },
        title: { text: null },
        subtitle: { text: null },
        legend: { enabled: false },
    };

    const distributionBoxplotOptions = {
        chart: {
            type: 'boxplot',
            marginTop: 10,
            marginBottom: 70,
            marginRight: 10
        },
        title: { text: null },
        subtitle: { text: null },
        legend: { enabled: false },
        xAxis: {
            categories: apiCallDistribution?.[0]?.categories || [],
            title: {
                text: mapLabel('Api', getDashboardCategory()) + ' Call Count',
                style: { fontSize: '12px' }
            },
            labels: {
                style: { fontSize: '10px' },
                rotation: 0,
                autoRotation: false,
                step: 1,
                staggerLines: 2
            }
        },
        yAxis: {
            title: {
                text: 'User Count',
                style: { fontSize: '12px' }
            },
            gridLineWidth: 0
        },
        tooltip: {
            shared: true,
            useHTML: true,
            headerFormat: '<b>{point.key}</b><br>',
            pointFormat:
                'Min: {point.low}<br>' +
                'P25: {point.q1}<br>' +
                'Median: {point.median}<br>' +
                'P75: {point.q3}<br>' +
                'Max: {point.high}'
        },
        plotOptions: {
            boxplot: {
                fillColor: '#f0f0f0',
                lineWidth: 1,
                medianColor: '#000000',
                medianWidth: 2,
                stemColor: '#999999',
                stemDashStyle: 'dot',
                whiskerColor: '#999999',
                whiskerLength: '50%',
            }
        }
    };
    

    const SchemaTab = {
        id: 'schema',
        content: "Schema",
        component:  <Box paddingBlockStart={"4"}> 
            <ApiSchema
                apiInfo={{
                    apiCollectionId: apiDetail.apiCollectionId,
                    url: apiDetail.endpoint,
                    method: apiDetail.method
                }}
            />
        </Box>
    }
    const ValuesTab = {
        id: 'values',
        content: "Values",
        component: sampleData.length > 0 && <Box paddingBlockStart={"4"}>
            <SampleDataList
                key="Sample values"
                sampleData={sampleData}
                heading={"Sample values"}
                minHeight={"35vh"}
                vertical={true}
                isAPISampleData={true}
                metadata={headersWithData.map(x => x.split(" ")[0])}
            />
        </Box>,
    }
    const DependencyTab = {
        id: 'dependency',
        content: "Dependency Graph",
        component: <Box paddingBlockStart={"2"}>
            <ApiDependency
                apiCollectionId={apiDetail['apiCollectionId']}
                endpoint={apiDetail['endpoint']}
                method={apiDetail['method']}
            />
        </Box>,
    }

    const hasIssues = apiDetail?.severityObj && Object.values(apiDetail.severityObj).some(count => count > 0);

    const IssuesTab = {
        id: 'issues',
        content: 'Issues',
        component: <ApiIssuesTab apiDetail={apiDetail} collectionIssuesData={collectionIssuesData} />,
    };


    const ApiCallStatsTab = {
        id: 'api-call-stats',
        content: mapLabel('Api', getDashboardCategory()) + ' Call Stats',
        component: 
            <Box paddingBlockStart={'4'}>
                <HorizontalStack align="end">
                    <Dropdown
                        menuItems={statsOptions}
                        initial={statsOptions[6].label}
                        selected={(timeInSeconds) => {
                            setStartTime((prev) => {
                                if ((endTs - timeInSeconds) === prev) {
                                    return prev;
                                } else {
                                    return endTs - timeInSeconds;
                                }
                            });
                        }}
                    />
                </HorizontalStack>
                <VerticalStack gap={"4"}>
                    {/* API Call Stats Graph */}
                    {apiCallStats != undefined && apiCallStats.length > 0 && apiCallStats[0]?.data !== undefined && apiCallStats[0]?.data?.length > 0 ? (
                        <GraphMetric
                            key={`stats-${startTime}`}
                            data={apiCallStats}
                            type='spline'
                            color='#6200EA'
                            areaFillHex='true'
                            height='330'
                            title={mapLabel('Api', getDashboardCategory()) + ' Call Count'}
                            subtitle='Number of API calls over time'
                            defaultChartOptions={defaultChartOptions(false)}
                            backgroundColor='#ffffff'
                            text='true'
                            inputMetrics={[]}
                        />
                    ) : null}
                    {/* API Call Distribution Graph */}
                    {apiCallDistribution != undefined && apiCallDistribution.length > 0 && apiCallDistribution[0]?.data !== undefined && apiCallDistribution[0]?.data?.length > 0 ? (
                        <GraphMetric
                            key={`distribution-${startTime}`}
                            data={apiCallDistribution}
                            type='column'
                            color='#1E90FF'
                            height='330'
                            title={undefined}
                            subtitle={undefined}
                            defaultChartOptions={{
                                ...defaultChartOptions(false),
                                ...distributionBoxplotOptions
                            }}                            
                            backgroundColor='#ffffff'
                            text={false}
                            inputMetrics={[]}
                        />
                    ) : (
                        <Box minHeight="330px" />
                    )}
                </VerticalStack>
            </Box>,
    };
    
    const deMergeApis = () => {
        const  { apiCollectionId, endpoint, method } = apiDetail;
        api.deMergeApi(apiCollectionId, endpoint, method).then((resp) => {
            func.setToast(true, false, "De-merging successful!!.")
            window.location.reload()
        })
    }

    let newData = JSON.parse(JSON.stringify(apiDetail))
    newData['copyEndpoint'] = {
        method: apiDetail.method,
        endpoint: apiDetail.endpoint
    }

    try {
        newData['nonSensitiveTags'] = [...new Set(paramList.filter(x => x?.nonSensitiveDataType).map(x => x.subTypeString))]
    } catch (e){
    }
    try {
        newData['sensitiveTags'] = apiDetail?.sensitiveTags && apiDetail?.sensitiveTags.length > 0 ? apiDetail?.sensitiveTags : 
        [...new Set(paramList.filter(x => x?.savedAsSensitive || x?.sensitive).map(x => x.subTypeString))]
    } catch (e){
    }

    let gridData = [];
    try {
        const techValues = [...new Set(headersWithData.filter(x => x.split(" ")[1].length < 50).map(x => x.split(" ")[1]))]
        gridData = techValues.map((x) => {
            return {
                id: x,
                name: x
            }
        })
    } catch (error) {
        
    }

    newData['description'] = (isEditingDescription?<InlineEditableText textValue={editableDescription} setTextValue={setEditableDescription} handleSaveClick={handleSaveDescription} setIsEditing={setIsEditingDescription}  placeholder={"Add a brief description"} maxLength={64}/> : description )

    const headingComp = (
        <VerticalStack gap="4" key="heading">
            <HorizontalStack align="space-between" wrap={false}>
                <VerticalStack gap="3">
                    <HorizontalStack gap={"2"} wrap={false} >
                        <GithubCell
                            width="32vw"
                            data={newData}
                            headers={headers}
                            getStatus={statusFunc}
                        />
                    </HorizontalStack>
                    <ApiGroups
                        collectionIds={apiDetail?.collectionIds}
                        onGroupClick={() => setShowDetails(false)}
                    />
                </VerticalStack>
                <VerticalStack gap="3" align="space-between">
                    <HorizontalStack gap={"1"} wrap={false} >
                    <RunTest
                        apiCollectionId={apiDetail["apiCollectionId"]}
                        endpoints={[apiDetail]}
                        filtered={true}
                        useLocalSubCategoryData={useLocalSubCategoryData}
                        preActivator={false}
                        disabled={window.USER_ROLE === "GUEST"}
                    />
                    <Box>
                        <Tooltip content="Open URL in test editor" dismissOnMouseOut>
                            <Button monochrome onClick={() => openTest()} icon={FileMinor} />
                        </Tooltip>
                    </Box>
                    {
                        isGptActive || isDemergingActive ? <Popover
                            active={showMoreActions}
                            activator={
                                <Tooltip content="More actions" dismissOnMouseOut ><Button plain monochrome icon={HorizontalDotsMinor} onClick={() => setShowMoreActions(!showMoreActions)} /></Tooltip>
                            }
                            autofocusTarget="first-node"
                            onClose={() => setShowMoreActions(false)}
                        >
                            <Popover.Pane fixed>
                                <ActionList
                                    items={[
                                        isGptActive ? { content: "Ask AktoGPT", onAction: displayGPT } : {},
                                        isDemergingActive ? { content: "De-merge", onAction: deMergeApis } : {},
                                    ]}
                                />
                            </Popover.Pane>
                        </Popover> : null
                    }
                </HorizontalStack>
                {headersWithData.length > 0 && 
                    <VerticalStack gap={"1"}>
                        <Text variant="headingSm" color="subdued">Technologies used</Text>
                        <GridRows verticalGap={"2"} horizontalGap={"1"} columns={3} items={gridData.slice(0,Math.min(gridData.length ,12))} CardComponent={TechCard} />
                    </VerticalStack>
                }
            </VerticalStack>
            </HorizontalStack>
        </VerticalStack>
    )

    const components = showForbidden
        ? [<Box padding="4" key="forbidden"><ForbiddenRole /></Box>]
        : [
            headingComp,
            <LayoutWithTabs
                key="tabs"
                tabs={[
                    ValuesTab,
                    SchemaTab,
                    ...(hasIssues ? [IssuesTab] : []),
                    ApiCallStatsTab,
                    DependencyTab
                ]}
                currTab={(tab) => setSelectedTabId(tab.id)}
                disabledTabs={disabledTabs}
            />
        ]

    return (
        <div>
            <FlyLayout
                title={`${mapLabel("API details", getDashboardCategory())}`}
                show={showDetails}
                setShow={setShowDetails}
                components={components}
                loading={loading}
            />
            <Modal large open={isGptScreenActive} onClose={() => setIsGptScreenActive(false)} title="Akto GPT">
                <Modal.Section flush>
                    <AktoGptLayout prompts={prompts} closeModal={() => setIsGptScreenActive(false)} runCustomTests={(tests) => runTests(tests)} />
                </Modal.Section>
            </Modal>
        </div>
    )
}

export default ApiDetails