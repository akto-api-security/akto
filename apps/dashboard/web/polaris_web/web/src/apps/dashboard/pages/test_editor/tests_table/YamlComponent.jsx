import { Box, Button, Divider, HorizontalStack, LegacyCard, VerticalStack } from "@shopify/polaris";
import SampleData from "../../../components/shared/SampleData";
import { ClipboardMinor } from "@shopify/polaris-icons"

function YamlComponent({ dataString, onClickFunc, title, toolTipContent, language, minHeight }) {
    let data = { message: dataString }
    // const onClickFunc = () => {
    //     console.log("clicked")
    // }

    // return (
    //     <LegacyCard>
    //         <LegacyCard.Section flush>
    //             <Box padding={"2"}>
    //                 <HorizontalStack padding="2" align='space-between'>
    //                     {"yaml"}
    //                     <Button icon={ClipboardMinor} plain  onClick={() => onClickFunc()}/>
    //                 </HorizontalStack>
    //             </Box>
    //         </LegacyCard.Section>
    //         <LegacyCard.Section flush>
    //             <SampleData data={data} minHeight={minHeight } language={language} />
    //         </LegacyCard.Section>
    //     </LegacyCard>
    // )
    return (
        <Box padding="05" borderRadius="2" borderWidth="1" borderColor="border-subdued">

            <Box padding={"2"}>
                <HorizontalStack padding="2" align='space-between'>
                    {"yaml"}
                    <Button icon={ClipboardMinor} plain onClick={() => onClickFunc()} />
                </HorizontalStack>
            </Box>
            <Divider />
            <SampleData data={data} minHeight={minHeight} language={language} />

        </Box>
    )
}
export default YamlComponent;