import GithubServerTable from "../../../components/tables/GithubServerTable";
import {Text,IndexFiltersMode, LegacyCard, HorizontalStack, Button, Collapsible, HorizontalGrid, Box, Divider} from '@shopify/polaris';
import {
  CircleCancelMajor,
  CalendarMinor,
  ReplayMinor,
  PlayMinor,
  ChevronDownMinor,
  ChevronUpMinor
} from '@shopify/polaris-icons';
import api from "../api";
import { useEffect, useState } from 'react';
import transform from "../transform";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import func from "@/util/func"
import { CellType } from "../../../components/tables/rows/GithubRow";
import ChartypeComponent from "./ChartypeComponent";

/*
  {
    text:"", // req. -> The text to be shown wherever the header is being shown
    value:"", // req. -> which key in the data does this header refer to.
    itemOrder:0, // optional -> needed if all headers are shown differently.
    icon:"", // optional -> needed if a header has a custom icon associated with it.
    showFilter:true, // optional, default -> false
    filterKey:"" // optional, default -> value
    filterLabel:"" // optional, default -> title
    filterValues:[], // optional, default to a list of all data in value key.
    // If value is an object then defaults to object of all keys in the value.
    showSort:false, // optional -> if you want to show show sort on this field, defaults to false. 
    sortKey:"", // optional -> using a different key than "value" for sorting.
    sortLabel: "", // optional, defaults to text -> if you want a different sort label than text.
    sortDirectionLabel: { asc:"", desc:"" }, // optional , defaults to new/old if text/sortLabel contains "time", else high/low
  }
*/

let headers = [
  {
    text:"Test name",
    title: 'Test run name',
    value:"testName",
    itemOrder:1,
  },
  {
    text: "Number of tests",
    title: "Number of tests",
    value: "number_of_tests",
    itemOrder: 3,
    type: CellType.TEXT,
  },
  {
    text:"Severity",
    value: 'severity',
    title: 'Issues',
    filterKey:"severityStatus",
    itemOrder:2,
  },
  {
    text: 'Run time',
    value: 'run_time',
    title: 'Status',
    itemOrder: 3,
    type: CellType.TEXT,
  },
  {
    title: '',
    type: CellType.ACTION,
  }
]

const sortOptions = [
  { label: 'Run time', value: 'endTimestamp asc', directionLabel: 'Newest run', sortKey: 'endTimestamp' },
  { label: 'Run time', value: 'endTimestamp desc', directionLabel: 'Oldest run', sortKey: 'endTimestamp' }
];

const resourceName = {
  singular: 'test run',
  plural: 'test runs',
};

let filters = [
  {
    key: 'severity',
    label: 'Severity',
    title: 'Severity',
    choices: [
      { label: "High", value: "HIGH" }, 
      { label: "Medium", value: "MEDIUM" },
      { label: "Low", value: "LOW" }
    ]
  }
]

function disambiguateLabel(key, value) {
  switch (key) {
    case 'severity':
      return (value).map((val) => `${func.toSentenceCase(val)} severity`).join(', ');
    case "dateRange":
      return value.since.toDateString() + " - " + value.until.toDateString();
    default:
      return value;
  }
}

function TestRunsPage() {

  const stopTest = (hexId) =>{
    api.stopTest(hexId).then((resp) => {
      func.setToast(true, false, "Test run stopped")
    }).catch((resp) => {
      func.setToast(true, true, "Unable to stop test run")
    });
  }

  const rerunTest = (hexId) =>{
    api.rerunTest(hexId).then((resp) => {
      func.setToast(true, false, "Test re-run")
      setTimeout(() => {
        refreshSummaries();
      }, 2000)
    }).catch((resp) => {
      func.setToast(true, true, "Unable to re-run test")
    });
  }
  

const getActionsList = (hexId) => {
  return [
  {
      content: 'Schedule test',
      icon: CalendarMinor,
      onAction: () => {console.log("schedule test function")},
  },
  {
      content: 'Re-run',
      icon: ReplayMinor,
      onAction: () => {rerunTest(hexId || "")},
  },
  {
      content: 'Add to CI/CD pipeline',
      icon: PlayMinor,
      onAction: () => {window.open('https://docs.akto.io/testing/run-tests-in-cicd', '_blank');},
  },
  {
      content: 'Stop',
      icon: CircleCancelMajor,
      destructive:true,
      onAction: () => {stopTest(hexId || "")},
      disabled: true,
  }
]}

function getActions(item){
  let arr = []
  let section1 = {items:[]}
  let actionsList = getActionsList(item.id);
  if(item['run_type'] === 'One-time'){
    // section1.items.push(actionsList[0])
  }else{
    section1.items.push(actionsList[1])
  }

  if(item['run_type'] === 'CI/CD'){
    // section1.items.push(actionsList[0])
  }else{
    section1.items.push(actionsList[2])
  }
  
  if(item['orderPriority'] === 1 || item['orderPriority'] === 2){
      actionsList[3].disabled = false
  }else{
      actionsList[3].disabled = true
  }

  arr.push(section1)
  let section2 = {items:[]}
  section2.items.push(actionsList[3]);
  arr.push(section2);
  return arr
}

const now = func.timeNow()

const [loading, setLoading] = useState(true);
const [currentTab, setCurrentTab] = useState("oneTime");
const [updateTable, setUpdateTable] = useState(false);
const [countMap, setCountMap] = useState({});
const [selected, setSelected] = useState(0);
const [timeStamp, setTimestamp] = useState({
  startTimestamp: now - func.recencyPeriod,
  endTimestamp: now
})
const [severityCountMap, setSeverityCountMap] = useState({
  HIGH: {text : 0, color: func.getColorForCharts("HIGH")},
  MEDIUM: {text : 0, color: func.getColorForCharts("MEDIUM")},
  LOW: {text : 0, color: func.getColorForCharts("LOW")},
})
const [subCategoryInfo, setSubCategoryInfo] = useState({})
const [collapsible, setCollapsible] = useState(true)

const checkIsTestRunning = (testingRuns) => {
  let val = false
  testingRuns.forEach(element => {
    if(element.orderPriority == 1){
      val = true
    }
  });
  return val ;
}

const refreshSummaries = () =>{
  setTimeout(() => {
    setUpdateTable(!updateTable);
  }, 5000)
}

function processData(testingRuns, latestTestingRunResultSummaries, cicd){
  let testRuns = transform.prepareTestRuns(testingRuns, latestTestingRunResultSummaries, cicd, true);
  if(checkIsTestRunning(testRuns)){
    refreshSummaries();
  }
  return testRuns;
}

  async function fetchTableData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue) {
    setLoading(true);
    let ret = [];
    let total = 0;
    let dateRange = filters['dateRange'] || false;
    delete filters['dateRange']
    let startTimestamp = 0;
    let endTimestamp = func.timeNow()
    if (dateRange) {
      startTimestamp = Math.floor(Date.parse(dateRange.since) / 1000);
      endTimestamp = Math.floor(Date.parse(dateRange.until) / 1000);
      setTimestamp({startTimestamp: startTimestamp, endTimestamp: endTimestamp})
      filters.endTimestamp = [startTimestamp, endTimestamp]
    }

    switch (currentTab) {

      case "cicd":
        await api.fetchTestingDetails(
          0, 0, true, false, sortKey, sortOrder, skip, limit, filters
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries, true);
          total = testingRunsCount;
        });
        break;
      case "scheduled":
        await api.fetchTestingDetails(
          0, 0, false, false, sortKey, sortOrder, skip, limit, filters
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      case "oneTime":
        await api.fetchTestingDetails(
          now - func.recencyPeriod, now, false, false, sortKey, sortOrder, skip, limit, filters
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
      default:
        await api.fetchTestingDetails(
          now - func.recencyPeriod, now, false, true, sortKey, sortOrder, skip, limit, filters
        ).then(({ testingRuns, testingRunsCount, latestTestingRunResultSummaries }) => {
          ret = processData(testingRuns, latestTestingRunResultSummaries);
          total = testingRunsCount;
        });
        break;
    }

    // show the running tests at the top.
    ret = ret.sort((a, b) => (b.orderPriority === 1) ?  1 : ( (a.orderPriority === 1) ? -1 : 0 ));
    // we send the test if any summary of the test is in the filter.
    ret = ret.filter((item) => {
      if(filters.severity && filters.severity.length > 0){
        return item.severityStatus.some(x => filters.severity.includes(x.toUpperCase()))
      } 
      return true
    })

    setLoading(false);
    return { value: ret, total: total };

  }

  const fetchCountsMap = async() => {
    await api.getCountsMap().then((resp)=>{
      setCountMap(resp)
    })
  }

  const fetchSummaryInfo = async()=>{
    await api.getSummaryInfo(timeStamp.startTimestamp, timeStamp.endTimestamp).then((resp)=>{
      setSubCategoryInfo(transform.convertSubIntoSubcategory(resp.subcategoryInfo))
      const copyMap = JSON.parse(JSON.stringify(severityCountMap))
      Object.keys(resp.severityInfo).length > 0 && Object.keys(resp.severityInfo).forEach((key)=>{
        copyMap[key].text = resp?.severityInfo[key]
      })
      setSeverityCountMap(copyMap)
    })
  }

  const tableTabs = [
    {
        content: 'All',
        index: 0,
        badge: countMap['allTestRuns']?.toString(),
        onAction: ()=> {setCurrentTab('All')},
        id: 'All',
    },
    {
      content: 'One time',
      index: 0,
      badge: countMap['oneTime']?.toString(),
      onAction: ()=> {setCurrentTab('oneTime')},
      id: 'oneTime',
    },
    {
      content: 'Recurring',
      index: 0,
      badge: countMap['scheduled']?.toString(),
      onAction: ()=> {setCurrentTab('scheduled')},
      id: 'scheduled',
    },
    {
      content: 'CI/CD',
      index: 0,
      badge: countMap['cicd']?.toString(),
      onAction: ()=> {setCurrentTab('cicd')},
      id: 'cicd',
    },
  ]

  useEffect(()=>{
    fetchCountsMap()
    fetchSummaryInfo()
  },[timeStamp])

  const handleSelectedTab = (selectedIndex) => {
    setLoading(true)
    setSelected(selectedIndex)
    setTimeout(()=>{
        setLoading(false)
    },200)
}

const iconSource = collapsible ? ChevronUpMinor : ChevronDownMinor
const SummaryCardComponent = () =>{
  let totalVulnerabilites = severityCountMap?.HIGH?.text + severityCountMap?.MEDIUM?.text +  severityCountMap?.LOW?.text 
  return(
    <LegacyCard>
      <LegacyCard.Section title={<Text fontWeight="regular" variant="bodySm" color="subdued">Vulnerabilities</Text>}>
        <HorizontalStack align="space-between">
          <Text fontWeight="semibold" variant="bodyMd">Found {totalVulnerabilites} vulnerabilities in total</Text>
          <Button plain monochrome icon={iconSource} onClick={() => setCollapsible(!collapsible)} />
        </HorizontalStack>
        <Collapsible open={collapsible} transition={{duration: '500ms', timingFunction: 'ease-in-out'}}>
          <LegacyCard.Subsection>
            <Box paddingBlockStart={3}><Divider/></Box>
            <HorizontalGrid columns={2} gap={6}>
              <ChartypeComponent data={subCategoryInfo} title={"Categories"}/>
              <ChartypeComponent data={severityCountMap} reverse={true} title={"Severity"} charTitle={totalVulnerabilites} chartSubtitle={"Total Vulnerabilities"}/>
            </HorizontalGrid>

          </LegacyCard.Subsection>
        </Collapsible>
      </LegacyCard.Section>
    </LegacyCard>
  )
}

const coreTable = (
<GithubServerTable
    key={currentTab + updateTable}
    pageLimit={50}
    fetchData={fetchTableData}
    sortOptions={sortOptions} 
    resourceName={resourceName} 
    filters={filters}
    hideQueryField={true}
    calenderFilter={true}
    calenderLabel={"Last run"}
    disambiguateLabel={disambiguateLabel} 
    headers={headers}
    getActions = {getActions}
    hasRowActions={true}
    loading={loading}
    getStatus={func.getTestResultStatus}
    tableTabs={tableTabs}
    onSelect={handleSelectedTab}
    selected={selected}
    mode={IndexFiltersMode.Default}
    headings={headers}
    useNewRow={true}
    condensedHeight={true}
  />   
)

const components = [<SummaryCardComponent key={"summary"}/>, coreTable]
  return (
    <PageWithMultipleCards
    title={<Text variant="headingLg" fontWeight="semibold">Test results</Text>}
    isFirstPage={true}
    components={components}
    />
  );
}

export default TestRunsPage