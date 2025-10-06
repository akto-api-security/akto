import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"

import { Box, Button, Frame, HorizontalGrid, HorizontalStack, TopBar } from "@shopify/polaris"
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
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo"
import LocalStore from "../../../main/LocalStorageStore"
import func from "../../../../util/func"
import transform from "../testing/transform"
import { getDashboardCategory, mapLabel } from "../../../main/labelHelper"

const TestEditor = () => {
    const navigate = useNavigate()

    const setTestsObj = TestEditorStore(state => state.setTestsObj)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)
    const setVulnerableRequestMap = TestEditorStore(state => state.setVulnerableRequestMap)
    const setDefaultRequest = TestEditorStore(state => state.setDefaultRequest)
    const setActive = PersistStore(state => state.setActive)
    const setSubCategoryMap = LocalStore(state => state.setSubCategoryMap)

    const [loading, setLoading] = useState(true)


    const handleExit = () => {
        navigate("/dashboard/test-library/tests")
        setActive('active')
    }

    const fetchVulnerableRequests = async () => {
        let vulnerableRequests = [], promises = []
        const limit = 50
        for (let i = 0; i < 20; i++) {
            promises.push(
                testEditorRequests.fetchVulnerableRequests(i*limit, limit)
            )
        }
        const allResults = await Promise.allSettled(promises);
        for (const result of allResults) {
            if (result.status === "fulfilled"){
              if(result.value.vulnerableRequests && result.value.vulnerableRequests !== undefined && result.value.vulnerableRequests.length > 0){
                vulnerableRequests.push(...result.value.vulnerableRequests)
              }
            }
          }
        return vulnerableRequests
    }

    const fetchSubcategories = async () => {
        const metaDataObj = await transform.getAllSubcategoriesData(false, "testEditor")
        return metaDataObj.subCategories
    }

    const fetchAllTests = async () => {
        const testId = window.location.pathname.split('/').pop();


        const subCategories = await fetchSubcategories()
        let vulnerableRequests = []
        try {
            vulnerableRequests = await fetchVulnerableRequests()
        } catch (err) {
            console.error(err)
        }

        if (subCategories && subCategories.length > 0) {
            try {
                const obj = convertFunc.mapCategoryToSubcategory(subCategories)
                setTestsObj(obj)
    
                const testName = obj.mapIdtoTest[testId]
                const selectedTestObj = {
                    label: testName,
                    value: testId,
                    category: obj.mapTestToData[testName].category,
                    inactive: obj.mapTestToData[testName].inactive
                }
                setSelectedTest(selectedTestObj)
    
                const requestObj = convertFunc.mapVulnerableRequests(vulnerableRequests)
                setVulnerableRequestMap(requestObj)
                const vulnerableRequest = vulnerableRequests?.length > 0 ? vulnerableRequests[0]?.id : {}
                setDefaultRequest(vulnerableRequest)

                let subCategoryMap = {};
                subCategories.forEach((x) => {
                    subCategoryMap[x.name] = x;
                    func.trimContentFromSubCategory(x)
                });
                setSubCategoryMap(subCategoryMap);
    
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
                    <TitleWithInfo docsUrl={"https://docs.akto.io/test-editor/concepts"} tooltipContent={"Test editor playground"} titleText={mapLabel("Test Editor", getDashboardCategory())} />
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
            <Box paddingInlineStart={12}>
                <HorizontalGrid columns={2}>
                    <YamlEditor fetchAllTests={fetchAllTests} />
                    <SampleApi />
                </HorizontalGrid>
            </Box>

        </Frame>
    )
}

export default TestEditor