import { Box, Button, Divider, HorizontalStack, Text } from "@shopify/polaris";
import SampleData from "../../../components/shared/SampleData";
import { ClipboardMinor } from "@shopify/polaris-icons"

function YamlComponent({ dataString, onClickFunc, language, minHeight }) {
    let data = { message: dataString }
    return (
        <Box borderRadius="2" borderWidth="1" borderColor="border-subdued">
            <Box padding={"2"}>
                <HorizontalStack padding="2" align='space-between'>
                    <Text variant="headingSm">{"YAML"}</Text>
                    <Button icon={ClipboardMinor} plain onClick={() => onClickFunc()} />
                </HorizontalStack>
            </Box>
            <Divider />
            <Box padding="05">
                <SampleData data={data} minHeight={minHeight} language={language} editorLanguage={"custom_yaml"} />
            </Box>

        </Box>
    )
}
export default YamlComponent;