import { Button, HorizontalStack, Text, VerticalStack, Box, Spinner } from "@shopify/polaris"
import {
    CancelMinor
} from '@shopify/polaris-icons';
import "./style.css"

function FlyLayout(props) {

    const { title, show, setShow, components,loading } = props

    return (
        <div className={"flyLayout " + (show ? "show" : "")}>
            <div className="innerFlyLayout">
                <Box borderColor="border-subdued" borderWidth="1" background="bg" padding={"4"} width="50vw" minHeight="100%">
                    { loading ? <div style={{position: "absolute", right: "25vw" , top: "50vh"}}><Spinner size="large" /></div>:
                    <VerticalStack gap={"5"}>
                        <HorizontalStack align="space-between">
                            <Text variant="headingMd">
                                {title}
                            </Text>
                            <Button icon={CancelMinor} onClick={() => { setShow(!show) }} plain></Button>
                        </HorizontalStack>
                        <Box paddingBlockEnd={"28"}>
                        <VerticalStack gap="4">
                        {
                            show ?
                                components.map((component) => {
                                    return component
                                })
                                :
                                <></>
                        }
                        </VerticalStack>
                        </Box>
                    </VerticalStack>
                    }
                </Box>      
            </div>
        </div>
    )
}

export default FlyLayout