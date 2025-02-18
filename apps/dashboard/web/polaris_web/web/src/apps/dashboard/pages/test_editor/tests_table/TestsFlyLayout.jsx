import { Box, Button, HorizontalStack, VerticalStack, Text, Badge, Tooltip } from "@shopify/polaris";
import FlyLayout from "../../../components/layouts/FlyLayout";
import { FileMinor } from "@shopify/polaris-icons"
import { useRef } from "react";
import observeFunc from "../../observe/transform"
import YamlComponent from "./YamlComponent";
import func from "../../../../../util/func";


function TestsFlyLayout({ data, showDetails, setShowDetails }) {

    const TitleComponent = () => {
        return (
            <Box paddingInlineStart={4} paddingInlineEnd={4} paddingBlockEnd={4}>
                <HorizontalStack align="space-between" wrap={false}>
                    <Box width="80%">
                        <VerticalStack gap="2">
                            <div style={{ display: 'flex', gap: '8px' }} className='test-title'>
                                <Text truncate variant="headingSm" alignment="start">{data?.name}</Text>
                                {data?.severityText ? (
                                    <Box className={`badge-wrapper-${data?.severityText.toUpperCase()}`}>
                                        <Badge size="small" status={observeFunc.getColor(data.severityText)}>
                                            {func.toSentenceCase(data?.severityText.replace(/_/g, " "))}
                                        </Badge>
                                    </Box>
                                ) : null}
                            </div>
                            <HorizontalStack align="start" gap="2">
                                <Text color="subdued" variant="bodySm">{data?.category}</Text>
                                <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
                                <Text color="subdued" variant="bodySm">{`By ${data?.author}`}</Text>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                    <Tooltip content="Open in Test Editor">
                    <Button icon={FileMinor} onClick={openEditor}></Button>
                    </Tooltip>
                </HorizontalStack>
            </Box>
        );
    };

    const ref = useRef(null)

    const onClickFunc = () => {
        func.copyToClipboard(data?.content, ref,"Test details copied to clipboard successfully!")
    }

    const openEditor = () => {
        window.open(`${window.location.origin}/dashboard/test-editor/${data?.value}`);
    }

    const currentComponents = [
        <TitleComponent openEditor={openEditor}/>,
        <Box paddingBlockStart={5} paddingInlineEnd={4} paddingInlineStart={4}>

            <YamlComponent onClickFunc={onClickFunc} dataString={data?.content} language="text" minHeight="70vh"></YamlComponent>

        </Box>
    ]

    return <FlyLayout
        title={"Test Details"}
        show={showDetails}
        setShow={setShowDetails}
        components={currentComponents}
        loading={false}
        showDivider={true}
        newComp={true}
        isHandleClose={false}
    />
}



export default TestsFlyLayout;