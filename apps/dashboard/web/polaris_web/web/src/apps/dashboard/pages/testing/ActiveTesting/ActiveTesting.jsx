import { Badge, Button, HorizontalStack, IndexFiltersMode, Link, Text } from '@shopify/polaris'
import React, { useEffect, useState } from 'react'
import PageWithMultipleCards from '../../../components/layouts/PageWithMultipleCards'
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import GithubSimpleTable from '../../../components/tables/GithubSimpleTable'
import useTable from '../../../components/tables/TableContext'
import func from '@/util/func'
import { useNavigate } from 'react-router-dom'

const headers = [
  {
    title:"Template name",
    text:"Template name",
    value:"templateName",
    sortActive: true,
  },
  {
    title:"Total endpoints",
    text:"Total endpoints",
    value:"totalEndpoints",
  },
  {
    title:"Test coverage",
    text:"Test coverage",
    value:"testRawCoverage",
  },
  {
    title:"Categories covered",
    text:"Categories covered",
    value:"categoriesCovered",
    filterKey:"categoriesCoveredRaw",
  },
  {
    title:"",
    text:"",
    value:"runTestAction"
  },
]

const sortOptions = [
  { label: 'Total endpoints', value: 'totalEndpoints asc', directionLabel: 'Highest', sortKey: 'totalEndpoints', columnIndex: 1},
  { label: 'Total endpoints', value: 'totalEndpoints desc', directionLabel: 'Lowest', sortKey: 'totalEndpoints', columnIndex: 1},
  { label: 'Test coverage', value: 'testRawCoverage asc', directionLabel: 'Highest', sortKey: 'testCoverage', columnIndex: 2},
  { label: 'Test coverage', value: 'testRawCoverage desc', directionLabel: 'Lowest', sortKey: 'testCoverage', columnIndex: 2},
]

const resourceName = {
  singular: 'active testing',
  plural: 'active testing',
}

const ActiveTesting = () => {
  const [loading, setLoading] = useState(false)
  const [templatesData, setTemplatesData] = useState({"allTemplates":[]})
  const [countMap, setCountMap] = useState({})
  const navigate = useNavigate()

  const initialCount = [countMap['allTemplates']]

  const [selectedTab, setSelectedTab] = useState("allTemplates")
  const [selected, setSelected] = useState(0)

  const definedTableTabs = ["All templates"]
  const { tabsInfo } = useTable()
  
  const tableCountObj = func.getTabsCount(definedTableTabs, templatesData, initialCount)
  const tableTabs = func.getTableTabsContent(definedTableTabs, tableCountObj, setSelectedTab, selectedTab, tabsInfo)

  function disambiguateLabel(key, value) {
    return func.convertToDisambiguateLabelObj(value, null, 3)
  }

  const fetchActiveTestingData = () => {
    let temp = {}
    temp.allTemplates = [
      {id: 0, templateName: <Text fontWeight='semibold'>Juice Shop 01</Text>, totalEndpoints: 50, testCoverage: 1, testRawCoverage: "1%", categoriesCovered: <HorizontalStack gap={2}><Badge>BOLA</Badge> <Badge>BUA</Badge></HorizontalStack>, categoriesCoveredRaw: ["BOLA", "BUA"], runTestAction: <Link removeUnderline>Run Test</Link>},
      {id: 1, templateName: <Text fontWeight='semibold'>Juice Shop 02</Text>, totalEndpoints: 120, testCoverage: 2, testRawCoverage: "2%", categoriesCovered: <HorizontalStack gap={2}><Badge>BOLA</Badge> <Badge>Security misconfig</Badge></HorizontalStack>, categoriesCoveredRaw: ["BOLA", "Security misconfig"], runTestAction: <Link removeUnderline>Run Test</Link>},
      {id: 2, templateName: <Text fontWeight='semibold'>Juice Shop BOLA</Text>, totalEndpoints: 400, testCoverage: 18, testRawCoverage: "18%", categoriesCovered: <HorizontalStack gap={2}><Badge>SSRF</Badge> <Badge>CSRF</Badge></HorizontalStack>, categoriesCoveredRaw: ["SSRF", "CSRF"], runTestAction: <Link removeUnderline>Run Test</Link>}
    ]
    setCountMap({allTemplates: temp.allTemplates.length})
    setTemplatesData(temp)
  }

  useEffect(() => {
    fetchActiveTestingData()
  }, [])

  const handleClickOnNewTest = () => {
    navigate("/dashboard/testing/new-active-testing")
  }

  const components = [
    <GithubSimpleTable
      key="active-testing-table"
      data={templatesData[selectedTab]}
      sortOptions={sortOptions}
      filters={[]}
      resourceName={resourceName}
      disambiguateLabel={disambiguateLabel}
      headers={headers}
      headings={headers}
      tableTabs={tableTabs}
      onSelect={(val) => setSelected(val)}
      selected={selected}
      loading={loading}
      useNewRow={true}
      condensedHeight={true}
      hasRowActions={true}
      selectable= {true}
      mode={IndexFiltersMode.Default}
      showFooter={false}
    />
  ]

  return (
    <PageWithMultipleCards
        title={
          <TitleWithInfo
            titleText={"Active Testing"}
            tooltipContent={"See active testing."}
          />
        }
        isFirstPage={true}
        primaryAction = {<Button primary onClick={handleClickOnNewTest}><div data-testid="new_test_role_button">New Test</div></Button>}
        components={components}
    />
  )
}

export default ActiveTesting