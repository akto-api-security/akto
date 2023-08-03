import { Button, HorizontalStack, Card, Text, VerticalStack } from "@shopify/polaris"
import {
    CancelMinor
} from '@shopify/polaris-icons';
import "./style.css"

function FlyLayout(props) {

    const { title, show, setShow, components } = props

    return (
        <div className={"flyLayout " + (show ? "show" : "")}>
            <Card roundedAbove="xs" >
                <VerticalStack gap={"2"}>
                    <HorizontalStack align="space-between">
                        <Text variant="headingMd">
                            {title}
                        </Text>
                        <Button icon={CancelMinor} onClick={() => { setShow(!show) }} plain></Button>
                    </HorizontalStack>
                    <VerticalStack gap="4">
                        {components?.filter((component) => {
                            return component
                        })}
                    </VerticalStack>
                </VerticalStack>
            </Card>
        </div>
    )
}

export default FlyLayout