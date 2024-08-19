import React, { useEffect, useState } from 'react'
import OnboardingStore from '../OnboardingStore'
import SpinnerCentered from "../../../components/progress/SpinnerCentered"
import api from "../api"
import onFunc from '../transform'
import TestSuitesCard from "./TestSuitesCard"
import { Badge, Button, ButtonGroup, HorizontalStack, Spinner, Text, VerticalStack } from '@shopify/polaris'
import testingApi from "../../testing/api"
import GridRows from '../../../components/shared/GridRows'
import PersistStore from '../../../../main/PersistStore'
import LocalStore from '../../../../main/LocalStorageStore'

function ResultsSummary() {

    const severities = ['High' , 'Medium', 'Low']

    const selectedTestSuite = OnboardingStore(state => state.selectedTestSuite)
    const authObj = OnboardingStore(state => state.authObject)
    const selectedCollection = OnboardingStore(state => state.selectedCollection)
    const setTestingRunHexId = OnboardingStore(state => state.setTestingRunHexId)
    const testingRunHexId = OnboardingStore(state => state.testingRunHexId)
    const subCategoryMap = LocalStore(state => state.subCategoryMap)
    const subCategoryFromSourceConfigMap =  PersistStore(state => state.subCategoryFromSourceConfigMap)

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
                    localCopy = JSON.parse(JSON.stringify(resp))
                })

                if(localCopy.testingRunResultSummaries && localCopy.testingRunResultSummaries.length > 0){
                    if(Object.keys(localCopy.testingRunResultSummaries[0].countIssues).length > 0){
                        setCountIssues(localCopy.testingRunResultSummaries[0].countIssues)
                    }
                    await testingApi.fetchTestingRunResults(localCopy?.testingRunResultSummaries[0]?.hexId).then((resp)=> {
                        if(resp.testingRunResults && resp.testingRunResults.length > 0){
                            const convertedArr = onFunc.getConvertedTestResults(resp.testingRunResults,subCategoryMap,subCategoryFromSourceConfigMap)
                            setTestingResults(convertedArr)
                        }
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
            <ButtonGroup segmented>
                {severities.map((item,index)=> {
                    return(
                        <Button onClick={()=> setActiveTab(item)} key={index} pressed={item === activeTab}>
                            <div style={{display: "flex", justifyContent: "center", width: "9.4vw"}}>
                                <HorizontalStack gap="2">
                                    <Text variant="bodyLg" fontWeight="medium">{item}</Text>
                                    <Badge>{countIssues[item.toUpperCase()]?.toString()}</Badge>
                                </HorizontalStack>
                            </div>
                        </Button>
                    )
                })}
            </ButtonGroup>
            <GridRows columns={1} items={groupedResults[activeTab]?.items} CardComponent={TestSuitesCard} />
        </VerticalStack>
    )
}

export default ResultsSummary