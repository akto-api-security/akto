import { Button, HorizontalStack, Text, VerticalStack, Box } from "@shopify/polaris"
import {
    CancelMinor
} from '@shopify/polaris-icons';
import "./style.css"

function FlyLayout(props) {

    const { title, show, setShow, components } = props

    return (
        <div className={"flyLayout " + (show ? "show" : "")}>
            <div className="innerFlyLayout">
            {
                show ?
                <Box borderColor="border-subdued" borderWidth="1" background="bg" padding={"4"} width="50vw" minHeight="100%">
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
                        components.map((component) => {
                            return component
                        })
                        }
                    </VerticalStack>
                    </Box>
                </VerticalStack>
            </Box> :
            <></>
            }
            </div>
        </div>
    )
}

export default FlyLayout