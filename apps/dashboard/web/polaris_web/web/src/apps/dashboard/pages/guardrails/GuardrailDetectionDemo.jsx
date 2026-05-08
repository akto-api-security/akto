import { useReducer, useState, useEffect, useRef } from "react";
import { Box, EmptySearchResult, HorizontalStack, Popover, ActionList, Button, Icon, Badge, Card, Text, VerticalStack} from '@shopify/polaris';
import {FileMinor, HideMinor, ViewMinor} from '@shopify/polaris-icons';
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import func from "@/util/func";
import values from "@/util/values";
import { produce } from "immer"
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper";
import SessionStore from "../../../main/SessionStore";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { labelMap } from '../../../main/labelHelperMap';
import PersistStore from '@/apps/main/PersistStore';
import { CellType } from "@/apps/dashboard/components/tables/rows/GithubRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import guardRailData from "./dummyData";
import GetPrettifyEndpoint from "@/apps/dashboard/pages/observe/GetPrettifyEndpoint";
import dayjs from "dayjs";
import SampleDetails from "../threat_detection/components/SampleDetails";
import Highcharts from "highcharts";


const resourceName = {
  singular: "sample",
  plural: "samples",
};

const headings = [
  {
    text: "Severity",
    value: "severityComp",
    title: "Severity",
  },
  {
    text: "AI Agent",
    value: "endpointComp",
    title: "AI Agent",
  },
  {
    text: "Actor",
    value: "actorComp",
    title: "Actor",
    filterKey: 'actor'
  },
  {
    text: "Filter",
    value: "filterId",
    title: "Guardrail type",
  },
  {
    text: "Discovered",
    title: "Detected",
    value: "discoveredTs",
    type: CellType.TEXT,
    sortActive: true,
  },
];

const sortOptions = [
  {
    label: "Discovered time",
    value: "detectedAt asc",
    directionLabel: "Newest",
    sortKey: "detectedAt",
    columnIndex: 4,
  },
  {
    label: "Discovered time",
    value: "detectedAt desc",
    directionLabel: "Oldest",
    sortKey: "detectedAt",
    columnIndex: 4,
  },
];


function GuardrailDetectionDemo() {

    const initialVal = values.ranges[3]
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), initialVal);
    const [moreActions, setMoreActions] = useState(false);
    const [showDetails, setShowDetails] = useState(false);
    const [sampleData, setSampleData] = useState([])
    const [showNewTab, setShowNewTab] = useState(false)
    const [rowDataList, setRowDataList] = useState([])
    const [moreInfoData, setMoreInfoData] = useState({})
    
    const collectionsMap = PersistStore((state) => state.collectionsMap);
    const threatFiltersMap = SessionStore((state) => state.threatFiltersMap);

    const data = guardRailData.guardRailDummyData.maliciousEvents.map((x) => {
      const severity = threatFiltersMap[x?.filterId]?.severity || "HIGH"
      return {
        ...x,
        id: x.id,
        actorComp: x.actor,
        endpointComp: x.url,
        apiCollectionName: collectionsMap[x.apiCollectionId] || "-",
        discoveredTs: dayjs(x.timestamp*1000).format("DD-MM-YYYY HH:mm:ss"),
        sourceIPComponent: x?.ip || "-",
        type: x?.type || "-",
        severityComp:  (
          <div className={`badge-wrapper-${severity}`}>
              <Badge size="small">{severity}</Badge>
          </div>
        )
        
      };
    });


    const emptyStateMarkup = (
        <EmptySearchResult
          title={'No guardrail activity found'}
          withIllustration
        />
      );


    const rowClicked = async(data) => {
        const tempData = {"orig": JSON.stringify(guardRailData.sampleDataMap[data.url])};
        setShowNewTab(true)
        const sameRow = false
        if (!sameRow) {
            let rowData = [tempData];
            setRowDataList(rowData)
            setShowDetails(true)
            setMoreInfoData({
                url: data.url,
                method: data.method,
                apiCollectionId: data.apiCollectionId,
                templateId: data.filterId,
            })
        } else {
            setShowDetails(!showDetails)
        }

      }

      const containerRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const chart = Highcharts.chart(containerRef.current, {
      chart: { type: "spline", spacing: [8, 8, 8, 8] },
      title: { text: null },
      credits: { enabled: false },
      exporting: { enabled: false },
      legend: { enabled: false },

      xAxis: {
        // show every 2nd month tick -> Jan, Mar, May, Jul, Sep, Nov, Dec
        categories: [
          "Jan","Feb","Mar","Apr","May","Jun",
          "Jul","Aug","Sep","Oct","Nov","Dec"
        ],
        tickInterval: 2,
        tickLength: 0,
        title: { text: "Time" }
      },

      yAxis: {
        title: { text: "Detection rate" },
        min: 25,           // since we start around 27%
        max: 100,
        tickPositions: [25, 50, 75, 100],
        labels: { format: "{value}%" },
        gridLineColor: "#eee"
      },

      tooltip: {
        shared: true,
        valueSuffix: "%",
        headerFormat: '<span style="font-size:11px">{point.key}</span><br/>'
      },

      plotOptions: {
        series: {
          lineWidth: 2,
          marker: { enabled: false }
        }
      },

      series: [{
        name: "Detection rate",
        color: "#F69292",
        // wavy values starting at ~27% and trending upward
        data: [27, 32, 29, 40, 50, 47, 62, 70, 65, 78, 85, 100]
      }]
    });

    return () => chart?.destroy();
  }, []);


  const ref = useRef(null);

  useEffect(() => {
    if (!ref.current) return;

    const chart = Highcharts.chart(ref.current, {
      chart: { type: "spline", spacing: [8, 8, 8, 8] },
      title: { text: null },
      credits: { enabled: false },
      exporting: { enabled: false },

      xAxis: {
        title: { text: null },
        min: 0,
        max: 90,
        tickInterval: 15,    // 0, 15, 30, 45, 60, 75, 90
        labels: { format: "{value}" }
      },

      yAxis: {
        title: { text: null },
        gridLineColor: "#eee"
      },

      legend: {
        align: "center",
        verticalAlign: "top",
        symbolRadius: 6
      },

      tooltip: {
        shared: true,
        headerFormat: "<span style='font-size:11px'>T={point.key}</span><br/>",
        pointFormat:
          "<span style='color:{series.color}'>●</span> {series.name}: <b>{point.y}</b><br/>"
      },

      plotOptions: {
        series: {
          lineWidth: 3,
          marker: { enabled: false },
          states: { hover: { lineWidthPlus: 0 } }
        },
        spline: {
          // soft area fill like your mock
          fillOpacity: 0.1
        }
      },

      series: [
        {
          name: "Safe",
          color: "#56D97B",
          pointStart: 0,
          pointInterval: 5, // one point every 5 units → aligns with 0..90 ticks
          // wavy upward trend with a spike ~55–60 and stronger finish
          data: [12, 14, 13, 18, 22, 20, 27, 25, 34, 32, 46, 42, 58, 50, 66, 62, 74, 70, 95]
        },
        {
          name: "Unsafe",
          color: "#F69292",
          pointStart: 0,
          pointInterval: 5,
          // gentle rise, small dip around 25–30, steady climb after
          data: [7, 8, 8, 9, 10, 11, 10, 9, 12, 14, 16, 17, 18, 20, 21, 22, 24, 26, 32]
        }
      ]
    });

    return () => chart.destroy();
  }, []);

  const chartRef = useRef(null); // ✅ renamed ref

  useEffect(() => {
    if (!chartRef.current) return;

    // Count occurrences of each guardrail type from the actual data
    const guardrailCounts = {};
    guardRailData.guardRailDummyData.maliciousEvents.forEach(event => {
      const filterId = event.filterId;
      guardrailCounts[filterId] = (guardrailCounts[filterId] || 0) + 1;
    });

    // Create dynamic multipliers for more realistic visualization
    const multipliers = {
      "PII": 8500,
      "Sensitive data guardrail": 4200,
      "Word mask": 6300,
      "Injection guardrail": 3100,
      "Competitor analysis": 2800,
      "Bias Check": 1950
    };

    // Create chart data with dynamic values
    const colors = ["#E63B2E", "#F14A3E", "#F37474", "#F9A6A6", "#FF8C82", "#FFB3BA"];
    const data = Object.keys(guardrailCounts).map((key, index) => ({
      name: key,
      y: guardrailCounts[key] * (multipliers[key] || Math.floor(Math.random() * 5000 + 1000)),
      color: colors[index % colors.length]
    }));

    const total = data.reduce((sum, d) => sum + d.y, 0);

    const chart = Highcharts.chart(chartRef.current, {
      chart: { 
        type: "pie", 
        spacing: [8, 8, 8, 8],
        events: {
          render: function() {
            const centerX = this.plotLeft + (this.plotWidth / 2);
            const centerY = this.plotTop + (this.plotHeight / 2);
            
            if (!this.customLabel) {
              this.customLabel = this.renderer.text(
                `<div style="text-align: center">
                  <div style="font-size:12px;color:#666">Total Violations</div>
                  <div style="font-size:20px;font-weight:600">${Highcharts.numberFormat(total, 0)}</div>
                </div>`,
                0,
                0,
                true
              )
              .css({
                width: '100px',
                textAlign: 'center'
              })
              .add();
            }
            
            this.customLabel.attr({
              x: centerX - 50,
              y: centerY - 10
            });
          }
        }
      },
      title: { text: null },
      credits: { enabled: false },
      exporting: { enabled: false },

      plotOptions: {
        pie: {
          size: "80%",
          innerSize: "65%",
          dataLabels: { enabled: false },
          showInLegend: true
        }
      },

      legend: {
        align: "right",
        verticalAlign: "middle",
        layout: "vertical",
        symbolRadius: 6
      },

      tooltip: {
        pointFormat: "<b>{point.y:,.0f}</b> violations<br/>({point.percentage:.1f}% of total)",
        backgroundColor: 'rgba(255, 255, 255, 0.95)',
        borderColor: '#ccc',
        borderRadius: 4
      },

      series: [
        {
          name: "Requests",
          colorByPoint: true,
          data
        }
      ]
    });

    return () => chart?.destroy();
  }, []);

    const components =  [
        <Box paddingBlockEnd="4">
            <HorizontalStack gap="4" wrap={false}>
                <Box width="33.33%">
                    <Card>
                        <VerticalStack gap="3">
                            <Text variant="headingMd" as="h3">Total detection rate</Text>
                            <div ref={containerRef} style={{ height: 240, width: "100%" }} />
                        </VerticalStack>
                    </Card>
                </Box>
                <Box width="33.33%">
                    <Card>
                        <VerticalStack gap="3">
                            <Text variant="headingMd" as="h3">Requests over time</Text>
                            <div ref={ref} style={{ height: 240, width: "100%" }} />
                        </VerticalStack>
                    </Card>
                </Box>
                <Box width="33.33%">
                    <Card>
                        <VerticalStack gap="3">
                            <Text variant="headingMd" as="h3">Violations by guardrail type</Text>
                            <div ref={chartRef} style={{ height: 240, width: "100%" }} />
                        </VerticalStack>
                    </Card>
                </Box>
            </HorizontalStack>
        </Box>,
        <GithubSimpleTable
            resourceName={resourceName}
            useNewRow={true}
            headers={headings}
            headings={headings}
            data={data}
            hideQueryField={true}
            sortOptions={sortOptions}
            emptyStateMarkup={emptyStateMarkup}   
            onRowClick={rowClicked}    
            rowClickable={true} 
            hardCodedKey={true}
            pageSize={20}
        ></GithubSimpleTable>,
         <SampleDetails
                title={"Attacker payload"}
                showDetails={showDetails}
                setShowDetails={setShowDetails}
                data={rowDataList}
                key={"sus-sample-details"}
                moreInfoData={moreInfoData}
                threatFiltersMap={threatFiltersMap}
            />
    ]

    const secondaryActionsComp = (
        <HorizontalStack gap={2}>
            <Popover
                active={moreActions}
                activator={(
                    <Button onClick={() => setMoreActions(!moreActions)} disclosure removeUnderline>
                        More Actions
                    </Button>
                )}
                autofocusTarget="first-node"
                onClose={() => { setMoreActions(false) }}
            >
                <Popover.Pane fixed>
                    <ActionList
                        actionRole="menuitem"
                        sections={
                            [
                                {
                                    title: 'Export',
                                    items: [
                                        {
                                            content: 'Export',
                                            onAction: () => {},
                                            prefix: <Box><Icon source={FileMinor} /></Box>
                                        }
                                    ]
                                },
                            ]
                        }
                    />
                </Popover.Pane>
            </Popover>
        </HorizontalStack>
    )

    return <PageWithMultipleCards
            title={
                <TitleWithInfo
                    titleText={mapLabel("Guardrail Activity", getDashboardCategory())}
                    tooltipContent={"Identify malicious requests with Akto's powerful guardrailing capabilities"}
                />
            }
            isFirstPage={true}
            primaryAction={<DateRangeFilter initialDispatch={currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })} />}
            components={components}
            secondaryActions={secondaryActionsComp}
        />
}

export default GuardrailDetectionDemo;