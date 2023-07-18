import { Badge, Button, Card, Divider, Frame, Icon, Text } from "@shopify/polaris"
import { ExitMajor } from "@shopify/polaris-icons"
import TestEditorFileExplorer from "./components/TestEditorFileExplorer"
import Store from "../../store"
import YamlEditor from "./components/YamlEditor"
import SampleApi from "./components/SampleApi"
import { useNavigate } from "react-router-dom"

const TestEditor = () => {
    const navigate = useNavigate()

    const toastConfig = Store(state => state.toastConfig)
    const setToastConfig = Store(state => state.setToastConfig)
    
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

    return (
        <Frame>
             <Card>
                <div style={{ display: "grid", gridTemplateColumns: "4vw max-content max-content auto max-content", alignItems: "center", gap:"5px"}}>
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
            </Card>

            <Divider  />

            <div style={{ display: "grid", gridTemplateColumns: "20vw auto", height: "100%"}}>
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