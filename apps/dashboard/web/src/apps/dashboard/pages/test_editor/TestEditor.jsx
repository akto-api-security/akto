import { Badge, Button, Card, Frame, Icon, Text } from "@shopify/polaris"
import { ExitMajor } from "@shopify/polaris-icons"
import TestEditorFileExplorer from "./components/TestEditorFileExplorer"
import TestEditorContainer from "./components/TestEditorContainer"
import Store from "../../store"

const TestEditor = () => {

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

    return (
        <Frame>
             <Card>
                <div style={{ display: "grid", gridTemplateColumns: "4vw max-content max-content max-content" }}>
                    <Icon source={ExitMajor} color="base" />
                    <Text variant="headingLg">
                        Test Editor
                    </Text>
                    <Badge status="success">
                        Beta
                    </Badge>
                    <Button>
                        Create custom test
                    </Button>
                </div>
            </Card>

            <div style={{ display: "grid", gridTemplateColumns: "max-content auto", height: "100%"}}>
                <TestEditorFileExplorer />
                <TestEditorContainer />
            </div>
            {toastMarkup}            
        </Frame>
    )
}

export default TestEditor