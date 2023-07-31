import { useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"

import { Badge, Button, Frame, Text, Toast, HorizontalGrid, Box, Divider } from "@shopify/polaris"
import { ExitMajor } from "@shopify/polaris-icons"

import TestEditorFileExplorer from "./components/TestEditorFileExplorer"
import YamlEditor from "./components/YamlEditor"
import SampleApi from "./components/SampleApi"
import SpinnerCentered from "../../components/progress/SpinnerCentered"

import Store from "../../store"
import TestEditorStore from "./testEditorStore"

import testEditorRequests from "./api"

import convertFunc from "./transform"

const TestEditor = () => {
    const navigate = useNavigate()

    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)
    const setTestsObj = TestEditorStore(state => state.setTestsObj)
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)
    const setVulnerableRequestMap = TestEditorStore(state => state.setVulnerableRequestMap)
    const setDefaultRequest = TestEditorStore(state => state.setDefaultRequest)

    const [loading, setLoading] = useState(true)

    const disableToast = () => {
        setToastConfig({
            isActive: false,
            isError: false,
            message: ""
        })
    }

    const toastMarkup = toastConfig.isActive ? (
        <Toast content={toastConfig.message} error={toastConfig.isError} onDismiss={disableToast} duration={1500} />
    ) : null;

    const handleExit = () => {
        navigate("/dashboard/testing")
    }

    const fetchAllTests = async () => {
        const testId = window.location.pathname.split('/').pop();

        const allSubCategoriesResponse = await testEditorRequests.fetchAllSubCategories()
        if (allSubCategoriesResponse) {
            const obj = convertFunc.mapCategoryToSubcategory(allSubCategoriesResponse.subCategories)
            setTestsObj(obj)

            const testName = obj.mapIdtoTest[testId]
            const selectedTestObj = {
                label: testName,
                value: testId,
                category: obj.mapTestToData[testName].category
            }
            setSelectedTest(selectedTestObj)

            const requestObj = convertFunc.mapVulnerableRequests(allSubCategoriesResponse.vulnerableRequests)
            setVulnerableRequestMap(requestObj)
            setDefaultRequest(allSubCategoriesResponse.vulnerableRequests[0].id)

            setLoading(false)
        }
    }

    useEffect(() => {
        fetchAllTests()
    }, [])

    return (

        <Frame topBar={
            <div style={{ display: "grid", gridTemplateColumns: "4vw max-content max-content auto", alignItems: "center", gap: "5px", height: "7vh", padding: "10px", background: "#ffffff" }}>
                <Button icon={ExitMajor} plain onClick={handleExit} />
                <Text variant="headingLg">
                    Test Editor
                </Text>
                <Badge status="success">
                    Beta
                </Badge>
                <div style={{ textAlign: "right" }}>
                    <Button>
                        Create custom test
                    </Button>
                </div>
            </div>
        }
        navigation={ loading ? <SpinnerCentered />: <TestEditorFileExplorer /> }
        >
            {loading ?
                <SpinnerCentered />
                : 
                    <div style={{ "paddingLeft":"6vh", display: "grid", gridTemplateColumns: "50% 50%" }}>
                        <YamlEditor fetchAllTests={fetchAllTests} />
                        <SampleApi />
                    </div>
            }

            {toastMarkup}
        </Frame>
    )
}

export default TestEditor