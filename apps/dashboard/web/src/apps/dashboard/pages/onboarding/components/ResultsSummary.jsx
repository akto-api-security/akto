import React, { useEffect, useState } from 'react'
import OnboardingStore from '../OnboardingStore'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import api from "../api"
import onFunc from '../transform'
import TestSuitesCard from "./TestSuitesCard"
import { Badge, Button, HorizontalStack, Spinner, Text, VerticalStack } from '@shopify/polaris'
import testingApi from "../../testing/api"
import TestingStore from '../../testing/testingStore'

function ResultsSummary() {

    const severities = ['High' , 'Medium', 'Low']

    const selectedTestSuite = OnboardingStore(state => state.selectedTestSuite)
    const authObj = OnboardingStore(state => state.authObject)
    const selectedCollection = OnboardingStore(state => state.selectedCollection)
    const setTestingRunHexId = OnboardingStore(state => state.setTestingRunHexId)
    const testingRunHexId = OnboardingStore(state => state.testingRunHexId)
    const subCategoryMap = TestingStore(state => state.subCategoryMap)
    const subCategoryFromSourceConfigMap =  TestingStore(state => state.subCategoryFromSourceConfigMap)

    const [loading, setLoading] = useState(false)
    const [testingResults, setTestingResults] = useState([])
    const [fetchTests, setFetchTests] = useState(false)
    const [activeTab, setActiveTab] = useState("High")
    const [countIssues, setCountIssues] = useState({
        "HIGH": 0,
        "MEDIUM": 0,
        "LOW": 0,
    })

    const groupedResults = onFunc.getSeverityObj(testingResults)
    

    const runTest = async() => {
        setLoading(true)
        let authParam = [authObj]
        await api.runTestOnboarding(authParam,selectedCollection,selectedTestSuite).then((resp)=> {
            setTestingRunHexId(resp.testingRunHexId)
            setLoading(false)
        })
    }
    useEffect(()=> {
        runTest()
    },[])

    const fetchTestsSummary = () =>{
        const intervalId = setInterval(async()=> {
            let localCopy = {}
            if(testingRunHexId){
                setFetchTests(true)
                await testingApi.fetchTestingRunResultSummaries(testingRunHexId).then((resp) => {
                    // console.log(resp)
                    localCopy = JSON.parse(JSON.stringify(resp))
                })

                if(localCopy.testingRunResultSummaries && localCopy.testingRunResultSummaries.length > 0){
                    setCountIssues(localCopy.testingRunResultSummaries[0].countIssues)
                    // console.log(localCopy.testingRunResultSummaries[0].countIssues)
                    await testingApi.fetchTestingRunResults(localCopy?.testingRunResultSummaries[0]?.hexId).then((resp)=> {
                        // console.log(resp,resp.testingRunResults)
                        const convertedArr = onFunc.getConvertedTestResults(resp?.testingRunResults,subCategoryMap,subCategoryFromSourceConfigMap)
                        // console.log(convertedArr)
                        setTestingResults(convertedArr)
                    })
                }

                if(localCopy?.testingRun?.state === "COMPLETED"){
                    clearInterval(intervalId)
                    setFetchTests(false)
                }
            }
        },1000)
    }

    useEffect(()=> {
        fetchTestsSummary()
    },[testingRunHexId])

    return (
        loading ? <SpinnerCentered /> :   
        <VerticalStack gap="5">
            {fetchTests ? <div style={{margin : "auto"}}> <Spinner size="small" /> </div> : null}
            <HorizontalStack align="space-around">
                {severities.map((item,index)=> {
                    return(
                        <div className={(item === activeTab ? "header-active": '')} key={index}>
                            <Button plain monochrome onClick={() => setActiveTab(item)} removeUnderline>
                                <HorizontalStack gap="1">
                                    <Text variant = "headingMd" as="h5">{item}</Text>
                                    <Badge status="info">{countIssues[item.toUpperCase()]?.toString()}</Badge>
                                </HorizontalStack>
                            </Button>
                        </div>
                    )
                })}
            </HorizontalStack>

            <VerticalStack gap="3">
                {groupedResults[activeTab]?.items?.map((item,index)=> {
                    return(
                        <TestSuitesCard cardObj={item} key={index} />
                    )
                })}
            </VerticalStack>
            {groupedResults[activeTab]?.truncate ? <Button plain onClick={()=> console.log("hey")}>See More</Button>: null}
        </VerticalStack>
    )
}

export default ResultsSummary