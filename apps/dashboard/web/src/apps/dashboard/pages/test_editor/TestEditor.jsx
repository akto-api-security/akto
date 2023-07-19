import { Badge, Button, Card, Frame, Icon, Text, Toast, Divider } from "@shopify/polaris"
import { ExitMajor } from "@shopify/polaris-icons"
import TestEditorFileExplorer from "./components/TestEditorFileExplorer"
import Store from "../../store"
import YamlEditor from "./components/YamlEditor"
import SampleApi from "./components/SampleApi"
import { useNavigate } from "react-router-dom"
import testEditorRequests from "./api"
import TestEditorStore from "./testEditorStore"
import convertFunc from "./transform"
import { useEffect } from "react"

const TestEditor = () => {
    const navigate = useNavigate()

    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)

    const setTestsObj = TestEditorStore(state => state.setTestsObj)
    
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
        navigate(-1)
    }

    const fetchAllTests = async () =>{
        await testEditorRequests.fetchAllSubCategories().then((resp)=>{
            const obj = convertFunc.mapCategoryToSubcategory(resp.subCategories)
            setTestsObj(obj)
        })
    }

    useEffect(()=> {
        fetchAllTests()
    },[])

    return (
        <Frame>
                <div style={{ display: "grid", gridTemplateColumns: "4vw max-content max-content auto max-content", alignItems: "center", gap:"5px", height: "10vh", padding: "10px", background: "#ffffff"}}>
                    <div onClick={handleExit}>
                        <Icon source={ExitMajor} color="base" />
                    </div>
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

            <div style={{ display: "grid", gridTemplateColumns: "20vw auto"}}>
                <TestEditorFileExplorer />
                
                <div style={{ display: "grid", gridTemplateColumns: "50% 50%"}}>
                    <YamlEditor />
                    <SampleApi />
                </div>
            </div>
            {toastMarkup}            
        </Frame>
    )
}

export default TestEditor