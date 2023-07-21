import { Badge, Button, Card, Frame, Icon, Text, Toast, Divider } from "@shopify/polaris"
import { ExitMajor } from "@shopify/polaris-icons"
import TestEditorFileExplorer from "./components/TestEditorFileExplorer"
import Store from "../../store"
import YamlEditor from "./components/YamlEditor"
import SampleApi from "./components/SampleApi"
import { useNavigate, useParams } from "react-router-dom"
import testEditorRequests from "./api"
import TestEditorStore from "./testEditorStore"
import convertFunc from "./transform"
import { useEffect, useState } from "react"
import SpinnerCentered from "../../components/progress/SpinnerCentered"

const TestEditor = () => {
    const navigate = useNavigate()
    const { testId } = useParams()

    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)
    const setTestsObj = TestEditorStore(state => state.setTestsObj)  
    const setSelectedTest = TestEditorStore(state => state.setSelectedTest)

    const [ loading, setLoading ] = useState(true)
    
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

    const fetchAllTests = async () =>{
        setLoading(true)

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
            setLoading(false)
        }
    }

    useEffect(()=> {
        fetchAllTests()
    },[])

    return (
        <Frame>
                <div style={{ display: "grid", gridTemplateColumns: "4vw max-content max-content auto max-content", alignItems: "center", gap:"5px", height: "10vh", padding: "10px", background: "#ffffff"}}>
                    <Button icon={ExitMajor} plain onClick={handleExit} />
                    <Text variant="headingLg">
                        Test Editor
                    </Text>
                    <Badge status="success">
                        Beta
                    </Badge>
                    <div></div>
                    <Button>
                        Create custom test
                    </Button>
                </div>

            <Divider  />

            { loading ? 
                <SpinnerCentered />
            :   <div style={{ display: "grid", gridTemplateColumns: "max-content auto"}}>
                    <TestEditorFileExplorer />
                    
                    <div style={{ display: "grid", gridTemplateColumns: "50% 50%"}}>
                        <YamlEditor />
                        <SampleApi />
                    </div>
                </div>
            }
           
            {toastMarkup}            
        </Frame>
    )
}

export default TestEditor