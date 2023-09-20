import React, { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import api from '../api';
import PersistStore from '../../../../main/PersistStore';
import { Avatar, Box, Button,Frame, HorizontalGrid, HorizontalStack, LegacyCard, Text, TopBar, VerticalStack } from '@shopify/polaris'
import {FlagMajor, CollectionsMajor, ResourcesMajor} from "@shopify/polaris-icons"

function ExportHtml() {
    const params = useParams() ;
    const testingRunSummaryId = params.summaryId
    const moreInfoSections = [
        {
          icon: FlagMajor,
          title: "Impact",
          content: ""
        },
        {
          icon: CollectionsMajor,
          title: "Tags",
          content: ""
        },
        {
            icon: "",
            title: "More Info",
            content: "",
        },
        {
          icon: ResourcesMajor,
          title: "References",
          content: ""
        }
    ]

    const [vulnerableResultsMap, setVulnerableResultsMap] = useState([]) ;
    const [dataToCurlObj, setDataToCurlObj] = useState({});
    const [severitiesCount,setSeveritiesCount] = useState({HIGH: 0, MEDIUM: 0, LOW: 0}) ;

    const subCategoryMap = PersistStore(state => state.subCategoryMap)

    const createVulnerabilityMap = (testingRunResults, sampleDataVsCurlMap) => {
        let categoryVsVulMap = {}
        let sampleDataMsgList = []
        let high = 0
        let medium = 0
        let low = 0
        testingRunResults?.length > 0 && testingRunResults.forEach((testingRun) => {
            let subtype = testingRun?.testSubType
            let subCategory = subCategoryMap?.[subtype]
            let severity = subCategory?.superCategory?.severity?._name
            let severityIndex = 0;
            switch (severity) {
                case 'HIGH': 
                    ++high
                    severityIndex = 2
                    break;
                case 'MEDIUM': 
                    ++medium
                    severityIndex = 1
                    break;
                case 'LOW': 
                    ++low
                    severityIndex = 0
                    break;
                default:
                    break;
            }

            let vulnerabilities = categoryVsVulMap[subtype]
            if (vulnerabilities === undefined) {
                vulnerabilities = JSON.parse(JSON.stringify(subCategory))
            }
            let vulnerableTestingRunResults = vulnerabilities["vulnerableTestingRunResults"]
            if (vulnerableTestingRunResults === undefined) {
                vulnerableTestingRunResults = []
            }
            vulnerableTestingRunResults.push(testingRun)
            vulnerabilities['vulnerableTestingRunResults'] = vulnerableTestingRunResults
            vulnerabilities['severityIndex'] = severityIndex
            categoryVsVulMap[subtype] = vulnerabilities
            let testResults = testingRun?.testResults
            testResults.forEach((testResult) => {
                sampleDataMsgList.push(testResult.message)
                sampleDataMsgList.push(testResult.originalMessage)
            })
        })

        setSeveritiesCount({HIGH: high, MEDIUM: medium, LOW: low});
        let localCopy = vulnerableResultsMap
        Object.keys(categoryVsVulMap).forEach((category) => {
            let obj = categoryVsVulMap[category]
            localCopy.push(obj)
        })

        let compare = function (a, b) {
            let severityA = a[Object.keys(a)[0]]['severityIndex']
            let severityB = b[Object.keys(a)[0]]['severityIndex']
            return severityB - severityA
        }
        localCopy.sort(compare)
        setVulnerableResultsMap(localCopy)
    }

    const fetchVulnerableData = async () => {
        let resultsCount = 0 ;
        let vulnerableTestingRunResults = []
        let sampleDataVsCurlMap = {}

        while(true){
            let testingRunCountsFromDB = 0
            await api.fetchVulnerableTestingRunResults(testingRunSummaryId,resultsCount).then((resp)=>{
                vulnerableTestingRunResults = [...vulnerableTestingRunResults, ...resp.testingRunResults]
                testingRunCountsFromDB = resp.testingRunResults.length
                sampleDataVsCurlMap = {...sampleDataVsCurlMap, ...resp.sampleDataVsCurlMap}
            })
            resultsCount += 50
            if (testingRunCountsFromDB < 50) {
                //EOF: break as no further documents exists
                break
            }
        }
        setDataToCurlObj(sampleDataVsCurlMap)
        createVulnerabilityMap(vulnerableTestingRunResults,sampleDataVsCurlMap)
    }

    useEffect(()=>{
        fetchVulnerableData()
    },[])

    const headerComp = (
        <div className="header-css">
            <Box width="60%">
                <HorizontalStack align="space-between">
                    <Box paddingBlockStart={3}>
                        <Avatar size="medium" shape="square" source="/public/akto_colored.svg" customer name='aktoLogo'/>
                    </Box>
                    <Text variant="headingXl">Akto Vulnerabilities Report</Text> 
                </HorizontalStack>
            </Box>
        </div>
    )
    const headerEditor = (
        <TopBar secondaryMenu={headerComp} />
    )

    const getColor = (item) =>{
        switch(item.severityIndex){
            case 0:
                return "bg-caution"

            case 1 :
                return "bg-warning"

            case 2 :
                return "bg-critical"

            default:
                return "";
        }
    }

    const cardTitleComponent = (item) =>{
        return(
            <Box borderWidth="1" background={getColor(item)}>
                <HorizontalStack>
                <Box borderInlineEndWidth='1' width='35%'>
                    <Box padding={1}>
                        <Text variant="headingMd">Vulnerability</Text>
                    </Box>
                </Box>
                <Box>
                    <Box padding={1}>
                        <Text variant="headingMd">{item?.testName}</Text>
                    </Box>
                </Box>
                </HorizontalStack>
            </Box>
        )
    }

    return (
        <Frame topBar={headerEditor}>
            <div className="html-component" style={{padding: "32px"}}>
                <VerticalStack gap="5">

                    <VerticalStack gap="3">
                        <Text variant="headingLg" fontWeight="medium">Summary of alerts</Text>
                        <Box borderWidth="2" borderRadius="1" width="40%">
                            <HorizontalGrid columns={2}>
                                <div style={{background: "#666666", borderRight: '2px solid white',borderBottom: '2px solid white'}}>
                                    <HorizontalStack align="center">
                                        <Box padding="1">
                                            <Text variant="bodyLg" fontWeight="medium" color="text-inverse">
                                                Severity
                                            </Text>
                                        </Box>
                                    </HorizontalStack>
                                </div>
                                <div style={{background: "#666666",borderBottom: '2px solid white'}}>
                                    <HorizontalStack align="center">
                                        <Box padding="1">
                                            <Text variant="bodyLg" fontWeight="medium" color="text-inverse">
                                                Vulnerable APIs
                                            </Text>
                                        </Box>
                                    </HorizontalStack>
                                </div>
                            </HorizontalGrid>
                            {Object.keys(severitiesCount).map((element, index)=>(
                                <HorizontalGrid columns={2} key={index}>
                                    <div style={{background: "#e8e8e8", borderRight: '2px solid white', borderBottom: (index < 2 ? "2px solid white" : "")}}>
                                        <HorizontalStack align="center">
                                            <Box padding="1">
                                                <Text variant="bodyMd" fontWeight="medium">
                                                    {element}
                                                </Text>
                                            </Box>
                                        </HorizontalStack>
                                    </div>
                                    <div style={{background: "#e8e8e8",borderBottom: (index < 2 ? "2px solid white" : "")}}>
                                        <HorizontalStack align="center">
                                            <Box padding="1">
                                                <Text variant="bodyMd" fontWeight="medium">
                                                    {severitiesCount[element]}
                                                </Text>
                                            </Box>
                                        </HorizontalStack>
                                    </div>
                                </HorizontalGrid>
                            ))}
                        </Box>
                    </VerticalStack>

                    <VerticalStack gap={4}>
                        <Text variant="headingLg" fontWeight="medium">Vulnerabilities details</Text>
                        <VerticalStack gap={3}>
                            {vulnerableResultsMap.map((item,index)=>(
                                <LegacyCard sectioned title={cardTitleComponent(item)} key={index}>
                                    <LegacyCard.Section>

                                    </LegacyCard.Section>
                                </LegacyCard>
                            ))}
                        </VerticalStack>
                    </VerticalStack>

                </VerticalStack>
            </div>
        </Frame>
    )
}

export default ExportHtml