import { Button, HorizontalStack, Text, VerticalStack, Box, Spinner, Divider, Popover } from "@shopify/polaris"
import {
    CancelMajor
} from '@shopify/polaris-icons';
import "./style.css"

function FlyLayout(props) {

    const { title, show, setShow, components,loading, showDivider} = props
    return (
        <div className={"flyLayout " + (show ? "show" : "")}>
            <div className="innerFlyLayout">
                <Box borderColor="border-subdued" borderWidth="1" background="bg" width="50vw" minHeight="100%">
                    { loading ? <div style={{position: "absolute", right: "25vw" , top: "50vh"}}><Spinner size="large" /></div>:
                    <VerticalStack gap={"5"}>
                        <Box padding={"4"} paddingBlockEnd={"0"}>
                            <HorizontalStack align="space-between">
                                <Text variant="headingMd">
                                    {title}
                                </Text>
                                <Button icon={CancelMajor} onClick={() => { setShow(!show) }} plain></Button>
                            </HorizontalStack>
                        </Box>
                        <Box paddingBlockEnd={"20"}>
                        <VerticalStack>
                        {
                            show ?
                                components.map((component, index) => {
                                    return (
                                        <Box key={index}>                                        
                                            <Box padding={"4"} paddingBlockStart={"0"} paddingBlockEnd={"0"}>
                                                {component}
                                            </Box>
                                            {(showDivider && index !== components.length - 1) ? <Divider /> : null}
                                        </Box>
                                    )
                                })
                                :null
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