import { useEffect, useState, useMemo } from "react"
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
    const setContentSearchIndex = TestEditorStore(state => state.setContentSearchIndex)
    const hydrateContentCache = TestEditorStore(state => state.hydrateContentCache)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)
    const setVulnerableRequestMap = TestEditorStore(state => state.setVulnerableRequestMap)
    const setDefaultRequest = TestEditorStore(state => state.setDefaultRequest)
    const setActive = PersistStore(state => state.setActive)
    const setSubCategoryMap = LocalStore(state => state.setSubCategoryMap)

    const [loading, setLoading] = useState(true)
    const [testsLoaded, setTestsLoaded] = useState(0)

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
        const metaDataObj = await transform.getAllSubcategoriesData(false, "testEditor", setTestsLoaded)
        return metaDataObj.subCategories
    }

    const fetchAllTests = async () => {
        const testId = window.location.pathname.split('/').pop()
        const subCategories = await fetchSubcategories()

        if (subCategories && subCategories.length > 0) {
            try {
                const cacheEntries = {}
                subCategories.forEach((x) => {
                    if (x?.content) {
                        cacheEntries[x.name] = x.content
                    }
                })
                hydrateContentCache(cacheEntries)

                convertFunc.buildContentSearchIndexIdle(subCategories, (index) => {
                    setContentSearchIndex(index)
                })

                subCategories.forEach((x) => func.trimContentFromSubCategory(x))

                const obj = convertFunc.mapCategoryToSubcategory(subCategories)
                setTestsObj(obj)

                const testName = obj.mapIdtoTest[testId]
                if (testName) {
                    setSelectedTest({
                        label: testName,
                        value: testId,
                        category: obj.mapTestToData[testName].category,
                        inactive: obj.mapTestToData[testName].inactive
                    })
                }

                const subCategoryMap = {}
                subCategories.forEach((x) => {
                    subCategoryMap[x.name] = x
                })
                setSubCategoryMap(subCategoryMap)

                setLoading(false)

                fetchVulnerableRequests().then((fetchedRequests) => {
                    const requestObj = convertFunc.mapVulnerableRequests(fetchedRequests)
                    setVulnerableRequestMap(requestObj)
                    const vulnerableRequest = fetchedRequests?.length > 0 ? fetchedRequests[0]?.id : {}
                    setDefaultRequest(vulnerableRequest)
                }).catch((err) => console.error(err))
            } catch (error) {
                setLoading(false)
            }
        }
    }

    const addCustomTest = (e) => {
        e.stopPropagation()
        console.log("add test")
    }

    const learnMoreObjEditor = useMemo(() => {
        const category = getDashboardCategory()
        const categoryKey = category?.toLowerCase().replace(/ /g, '_')
        const pageData = learnMoreObject['dashboard_test_editor']

        if (!pageData) return null

        if (pageData[categoryKey] && typeof pageData[categoryKey] === 'object') {
            const categoryData = pageData[categoryKey]
            return {
                title: categoryData.title,
                description: categoryData.description,
                docsLink: Array.isArray(categoryData.docsLink) ? categoryData.docsLink : [],
                videoLink: Array.isArray(categoryData.videoLink) ? categoryData.videoLink : []
            }
        }

        const hasRootDocs = Array.isArray(pageData.docsLink)
        const hasRootVideos = Array.isArray(pageData.videoLink)

        if (hasRootDocs || hasRootVideos) {
            return {
                title: pageData.title,
                description: pageData.description,
                docsLink: hasRootDocs ? pageData.docsLink : [],
                videoLink: hasRootVideos ? pageData.videoLink : []
            }
        }

        return null
    }, [])

    const headerComp = (
        <div className="header-css">
            <HorizontalStack gap="5">
                <Button onClick={handleExit} icon={ExitMajor} plain/>
                <HorizontalStack gap={"2"}>
                    <TitleWithInfo docsUrl={"https://docs.akto.io/test-editor/concepts"} tooltipContent={"Test editor playground"} titleText={mapLabel("Test Editor", getDashboardCategory())} />
                </HorizontalStack>
            </HorizontalStack>

            {learnMoreObjEditor && (learnMoreObjEditor.docsLink?.length > 0 || learnMoreObjEditor.videoLink?.length > 0) && (
                <LearnPopoverComponent learnMoreObj={learnMoreObjEditor} />
            )}
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
            <SpinnerCentered height="100vh" text={`Loading tests... ${testsLoaded} tests loaded`}/>
        :
        <Frame topBar={headerEditor} navigation={<TestEditorFileExplorer addCustomTest={(e) => addCustomTest(e)}/>}>
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
