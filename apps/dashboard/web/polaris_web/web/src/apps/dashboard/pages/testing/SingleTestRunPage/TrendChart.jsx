import { useEffect, useReducer, useState, useCallback } from "react";
import api from "../api";
import values from "@/util/values";
import StackedChart from "../../../components/charts/StackedChart";
import {
    Filters,
    LegacyCard,
    ChoiceList,
    Collapsible,
    HorizontalStack, 
    Text,
    Button,
    Box,
    Divider
} from "@shopify/polaris";
import DateRangePicker from "../../../components/layouts/DateRangePicker"
import func from '@/util/func';
import { produce } from "immer"
import "./style.css"
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import SummaryTable from "./SummaryTable";

function TrendChart(props) {

    const { hexId, setSummary, show } = props;
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[2]);
    const [appliedFilters, setAppliedFilters] = useState([]);
    const [testingRunResultSummaries, setTestingRunResultSummaries] = useState([]);
    const [metadataFilterData, setMetadataFilterData] = useState([]);
    const [totalVulnerabilities, setTotalVulnerabilites] = useState(0);
    const [collapsible, setCollapsible] = useState(true)
    const [hideFilter, setHideFilter] = useState(false)

    const dateRangeFilter =
    {
        key: "dateRange",
        label: "Date range",
        filter:
            (<DateRangePicker ranges={values.ranges}
                initialDispatch={currDateRange}
                dispatch={(dateObj) => getDate(dateObj)}
                setPopoverState={() => {
                    setHideFilter(true)
                    setTimeout(() => {
                        setHideFilter(false)
                    }, 10)
                }}
            />),
        pinned: true
    }

    function processChartData(data) {
        let retC = []
        let retH = []
        let retM = []
        let retL = []

        let items = data;

        items = items.filter((x) => {
            let ret = true;
            appliedFilters.forEach((filter) => {
                if (filter.key == "dateRange") {
                    return;
                }
                ret &= filter.value.includes(x?.metadata?.[filter.key])
            })
            return ret
        })

        items.forEach((x) => {
            let ts = x["startTimestamp"] * 1000
            let countIssuesMap = x["countIssues"]
            if(countIssuesMap && Object.keys(countIssuesMap).length > 0){
                if(!countIssuesMap["CRITICAL"]){
                    countIssuesMap["CRITICAL"] = 0;
                }
                retC.push([ts, countIssuesMap["CRITICAL"]])
                retH.push([ts, countIssuesMap["HIGH"]])
                retM.push([ts, countIssuesMap["MEDIUM"]])
                retL.push([ts, countIssuesMap["LOW"]])
            }
        })

        return [
            {
                data: retC,
                color: func.getHexColorForSeverity("CRITICAL"),
                name: "Critical"
            },
            {
                data: retH,
                color: func.getHexColorForSeverity("HIGH"),
                name: "High"
            },
            {
                data: retM,
                color: func.getHexColorForSeverity("MEDIUM"),
                name: "Medium"
            },
            {
                data: retL,
                color: func.getHexColorForSeverity("LOW"),
                name: "Low"
            }
        ]
    }

    function processMetadataFilters(data) {
        let ret = []
        let tmp = data

        Object.keys(tmp).forEach((key) => {
            if (tmp[key].length > 0) {
                ret.push({
                    key: key,
                    label: func.toSentenceCase(key),
                    filter: (
                        <ChoiceList
                            title={func.toSentenceCase(key)}
                            titleHidden
                            choices={tmp[key].map((x) => {
                                return {
                                    label: x,
                                    value: x
                                }
                            })}
                            selected={
                                appliedFilters.filter((localFilter) => { return localFilter.key == key }).length == 1 ?
                                    appliedFilters.filter((localFilter) => { return localFilter.key == key })[0].value : []
                            }
                            onChange={(value) => handleFilterStatusChange(key, value)}
                            allowMultiple={true}
                        />
                    ),
                    pinned: true,
                })
            }
        })

        return ret;
    }

    async function fetchData(dateRange, firstTime) {

        let st = Math.floor(Date.parse(dateRange.since) / 1000);
        let en = Math.floor(Date.parse(dateRange.until) / 1000);

        if (firstTime) {
            st = 0;
            en = 0;
        }

        await api.fetchTestingRunResultSummaries(hexId, st, en).then(async ({ testingRunResultSummaries }) => {
            setTestingRunResultSummaries((prev) => {
                if (func.deepComparison(prev, testingRunResultSummaries)) {
                    return prev;
                }
                return testingRunResultSummaries;
            });

            let count = 0
            testingRunResultSummaries.forEach((ele)=>{
                let obj = (ele?.countIssues && Object.keys(ele.countIssues).length > 0) ? ele.countIssues : {CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0}
                if (!obj.CRITICAL) {
                    obj.CRITICAL = 0;
                }
                count += (obj.CRITICAL + obj.HIGH + obj.MEDIUM + obj.LOW)
            })

            setTotalVulnerabilites(Math.max(count, 0))

            if (firstTime) {

                const result = testingRunResultSummaries.reduce((acc, current) => {
                    const { startTimestamp, endTimestamp } = current;

                    acc.minEndTimestamp = Math.max(acc.minEndTimestamp, endTimestamp);
                    acc.maxStartTimestamp = Math.min(acc.maxStartTimestamp, startTimestamp);

                    return acc;
                }, { minEndTimestamp: values.todayDayEnd / 1000, maxStartTimestamp: new Date().setDate(values.today.getDate() - 6) / 1000 });

                let obj = {
                    since: new Date(result.maxStartTimestamp * 1000),
                    until: new Date(result.minEndTimestamp * 1000),
                };

                handleFilterStatusChange("dateRange", obj)
                dispatchCurrDateRange({ type: "update", period: { period: obj, title: "Custom", alias: "custom" } })
            }

        });
    }

    async function setFilterData(){
        let res = await api.fetchMetadataFilters()
        setMetadataFilterData(res.metadataFilters);
    }

    useEffect(() => {
        fetchData(currDateRange.period, true);
        setFilterData();
    }, [])


    const defaultChartOptions = {
        "legend": {
            enabled: false
        },
    }
    const graphPointClick = ({ point }) => {
        dateClicked(point.options.x)
    }

    const tooltip =  {
        formatter: tooltipFormatter ? tooltipFormatter : undefined,
        shared: true,
    }

    function disambiguateLabel(key, value) {
        switch (key) {
            case "branch":
            case "repository":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            case "dateRange":
                return value.since.toDateString() + " - " + value.until.toDateString();
            default:
                return value;
        }
    }

    const handleRemoveAppliedFilter = (key) => {
        let temp = appliedFilters
        temp = temp.filter((filter) => {
            return filter.key != key
        })
        if (key == "dateRange") {
            getDate({ type: "update", period: values.ranges[2] });
        } else {
            setAppliedFilters(temp);
        }
    }

    const changeAppliedFilters = (key, value) => {
        let temp = appliedFilters
        temp = temp.filter((filter) => {
            return filter.key != key
        })
        if (value.length > 0 || Object.keys(value).length > 0) {
            temp.push({
                key: key,
                label: disambiguateLabel(key, value),
                onRemove: handleRemoveAppliedFilter,
                value: value
            })
        }
        setAppliedFilters(temp);
    };

    const handleFilterStatusChange = (key, value) => {
        changeAppliedFilters(key, value);
    }

    const getDate = (dateObj) => {
        dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })
        let obj = dateObj.period.period;
        fetchData(obj);
        handleFilterStatusChange("dateRange", obj)
    }

    const handleFiltersClearAll = useCallback(() => {
        setAppliedFilters([])
        getDate({ type: "update", period: values.ranges[2] });
    }, []);

    const metadataFilters = processMetadataFilters(metadataFilterData);

    function dateClicked(data) {

        let match = testingRunResultSummaries.filter((x) => {
            return data == x["startTimestamp"] * 1000
        })

        if(match.length > 0) {
            setSummary(match[0])
        }
    }

    function tooltipFormatter(tooltip) {

        let def = tooltip.defaultFormatter.call(this, tooltip);
        let tooltipMetadata = testingRunResultSummaries.reduce((acc, x) => {
            const ts = x["startTimestamp"] * 1000;

            acc[ts] = {
                branch: x?.metadata?.branch,
                repository: x?.metadata?.repository,
            };

            return acc;
        }, {});

        if (tooltipMetadata?.[this.x]) {
            if (tooltipMetadata[this.x]?.branch) {
                def.push(`<span>branch: ${tooltipMetadata[this.x].branch}</span><br/>`)
            }
            if (tooltipMetadata[this.x].repository) {
                def.push(`<span>repository: ${tooltipMetadata[this.x].repository}</span><br/>`)
            }
        }
        return def;
    }

    if(!show){
        return <></>
    }

    const iconSource = collapsible ? ChevronUpMinor : ChevronDownMinor

    return (
        <LegacyCard>
            <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued">Vulnerabilities</Text>}>
                <HorizontalStack align="space-between">
                    <Text fontWeight="semibold" variant="bodyMd">Found {props?.totalVulnerabilities || totalVulnerabilities} vulnerabilities in total</Text>
                    <Button plain monochrome icon={iconSource} onClick={() => setCollapsible(!collapsible)} />
                </HorizontalStack>
                <Collapsible open={collapsible} transition={{duration: '500ms', timingFunction: 'ease-in-out'}}>
                    <Box paddingBlockStart={3}><Divider/></Box>
                    <LegacyCard.Section flush>
                        <div className="filterClass">
                            <Filters
                                hideQueryField={true}
                                filters={[dateRangeFilter, ...metadataFilters]}
                                appliedFilters={appliedFilters}
                                onClearAll={handleFiltersClearAll}
                                hideFilters={hideFilter}
                            />
                        </div>
                    </LegacyCard.Section>
                    <LegacyCard.Section>
                        <StackedChart
                            key={`trend-chart-${hexId}`}
                            type='column'
                            color='#6200EA'
                            areaFillHex="true"
                            height="330"
                            background-color="#ffffff"
                            data={processChartData(testingRunResultSummaries)}
                            testingRunResultSummaries={testingRunResultSummaries}
                            tooltipFormatter={tooltipFormatter}
                            defaultChartOptions={defaultChartOptions}
                            text="true"
                            yAxisTitle="Number of issues"
                            dateClicked={dateClicked}
                            graphPointClick={graphPointClick}
                            tooltip={tooltip}
                            width={50}
                        />
                    </LegacyCard.Section>
                </Collapsible>
            </LegacyCard.Section>
            <LegacyCard.Section>
                <SummaryTable setSummary={setSummary} testingRunResultSummaries={testingRunResultSummaries} />
            </LegacyCard.Section>
        </LegacyCard>
    )

}

export default TrendChart