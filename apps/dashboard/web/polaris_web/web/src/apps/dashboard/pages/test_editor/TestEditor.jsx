import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"

import { Badge, Button, Frame, HorizontalStack, Text, TopBar } from "@shopify/polaris"
import { ExitMajor } from "@shopify/polaris-icons"

import TestEditorFileExplorer from "./components/TestEditorFileExplorer"
import YamlEditor from "./components/YamlEditor"
import SampleApi from "./components/SampleApi"
import SpinnerCentered from "../../components/progress/SpinnerCentered"

import TestEditorStore from "./testEditorStore"
import PersistStore from "../../../main/PersistStore"
import testEditorRequests from "./api"

import convertFunc from "./transform"
import { learnMoreObject } from "../../../main/onboardingData"
import LearnPopoverComponent from "../../components/layouts/LearnPopoverComponent"

const TestEditor = () => {
    const navigate = useNavigate()

    const setTestsObj = TestEditorStore(state => state.setTestsObj)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)
    const setVulnerableRequestMap = TestEditorStore(state => state.setVulnerableRequestMap)
    const setDefaultRequest = TestEditorStore(state => state.setDefaultRequest)
    const setActive = PersistStore(state => state.setActive)
    const selectedSampleApi = TestEditorStore(state => state.selectedSampleApi)

    const [loading, setLoading] = useState(true)


    const handleExit = () => {
        navigate("/dashboard/testing")
        setActive('active')
    }

    const fetchAllTests = async () => {
        const testId = window.location.pathname.split('/').pop();

        const allSubCategoriesResponse = await testEditorRequests.fetchAllSubCategories()
        if (allSubCategoriesResponse) {
            try {
                const obj = convertFunc.mapCategoryToSubcategory(allSubCategoriesResponse.subCategories)
                setTestsObj(obj)
    
                const testName = obj.mapIdtoTest[testId]
                const selectedTestObj = {
                    label: testName,
                    value: testId,
                    category: obj.mapTestToData[testName].category,
                    inactive: obj.mapTestToData[testName].inactive
                }
                setSelectedTest(selectedTestObj)
    
                const requestObj = convertFunc.mapVulnerableRequests(allSubCategoriesResponse.vulnerableRequests)
                setVulnerableRequestMap(requestObj)
                const vulnerableRequest = allSubCategoriesResponse?.vulnerableRequests?.length > 0 ? allSubCategoriesResponse?.vulnerableRequests[0]?.id : {}
                setDefaultRequest(vulnerableRequest)
    
                setLoading(false) 
            } catch (error) {
                setLoading(false)
            }
            
        }
    }

    const addCustomTest = (e) => {
        e.stopPropagation()
        console.log("add test")
    }

    const learnMoreObjEditor = learnMoreObject['dashboard_test_editor']

    const headerComp = (
        <div className="header-css">
            <HorizontalStack gap="5">
                <Button onClick={handleExit} icon={ExitMajor} plain/>
                <HorizontalStack gap={"2"}>
                    <Text variant="headingLg" as="h3">Test Editor</Text>
                    <Badge status="success">Beta</Badge>
                </HorizontalStack>
            </HorizontalStack>

            <LearnPopoverComponent learnMoreObj={learnMoreObjEditor} />
        </div>
    )
    

    const headerEditor = (
        <TopBar secondaryMenu={headerComp} />
    )

   


    const defaultId = "REMOVE_TOKENS";

    useEffect(() => {
        const path = window.location.pathname;
        const pathArr = path.split("test-editor")
        if(pathArr[1].length < 2){
            navigate(defaultId)
        }
        fetchAllTests()
    }, [])

    return (
        loading ?
            <SpinnerCentered />
        : 
        <Frame topBar={
            headerEditor
        }
            navigation={ <TestEditorFileExplorer addCustomTest={(e) => addCustomTest(e)}/> }
        >
            
            <div style={{ "paddingLeft":"6vh", display: "grid", gridTemplateColumns: "50% 50%" }}>
                <YamlEditor fetchAllTests={fetchAllTests} />
                <SampleApi />
            </div>
            

        </Frame>
    )
}

export default TestEditor