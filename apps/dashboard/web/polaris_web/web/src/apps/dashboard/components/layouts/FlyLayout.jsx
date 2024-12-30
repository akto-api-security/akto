import { Button, InlineStack, Text, BlockStack, Box, Spinner, Divider, Scrollable } from "@shopify/polaris"
import { XIcon } from "@shopify/polaris-icons";
import "./style.css"

function FlyLayout(props) {

    const { title, titleComp,  show, setShow, components,loading, showDivider, newComp, handleClose, isHandleClose, width} = props
    const handleExit = () => {
        setShow(!show)
        if(isHandleClose){
            handleClose()
        }
    }
    const divWidth = width || "50vw";
    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{width: divWidth}}>
            <div className="innerFlyLayout">
                <Box borderColor="border-secondary" borderWidth="025" background="bg-surface" width={divWidth} minHeight="100%">
                    { loading ? <div style={{position: "absolute", right: "25vw" , top: "50vh"}}><Spinner size="large" /></div>:
                    <BlockStack gap={"500"}>
                        <Box padding={"400"} paddingBlockEnd={"0"} >
                            <InlineStack align="space-between">
                                {titleComp ? titleComp : 
                                    <Text variant="headingMd">
                                        {title}
                                    </Text>
                                }
                                <Button icon={XIcon} onClick={() => { handleExit()}}  variant="plain"></Button>
                            </InlineStack>
                        </Box>
                        <Scrollable style={{ height: "92vh" }} shadow>
                        <Box paddingBlockEnd={"2000"}>
                        <BlockStack>
                            {
                                show ?
                                    components.map((component, index) => {
                                        return (
                                            <Box key={index}>                                        
                                                {newComp ? <Box>
                                                    {component}
                                                </Box>:
                                                <Box paddingInlineEnd={"400"} paddingInlineStart={"400"}>
                                                    {component}
                                                </Box>
                                                }
                                                {(showDivider && index !== components.length - 1) ? <Divider /> : null}
                                            </Box>
                                        )
                                    })
                                    :null
                            }
                        </BlockStack>
                        </Box>
                        </Scrollable>
                    </BlockStack>
                    }
                </Box>      
            </div>
        </div>
    );
}

export default FlyLayout