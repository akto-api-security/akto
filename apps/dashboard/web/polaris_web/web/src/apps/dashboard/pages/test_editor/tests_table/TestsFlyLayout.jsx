import { Box, Button, HorizontalStack, VerticalStack, Text, Badge, Tooltip } from "@shopify/polaris";
import FlyLayout from "../../../components/layouts/FlyLayout";
import { HorizontalDotsMinor, FileMinor } from "@shopify/polaris-icons"
import JsonComponent from "../../quick_start/components/shared/JsonComponent";
import { useRef, useState } from "react";
import observeFunc from "../../observe/transform"
import YamlComponent from "./YamlComponent";
import func from "../../../../../util/func";


function TestsFlyLayout({ data, showDetails, setShowDetails }) {

    function TitleComponent({openEditor}) {
        return (
            // <div style={{ maxWidth:"100%", display: 'flex', justifyContent: "space-between", gap: "24px", padding: "16px", paddingTop: '0px' }}>
            // <VerticalStack gap={"2"}>
            //     <Box width="60%">
            //         <div style={{ display: 'flex', gap: '4px'}} className='test-title'>
            //             <Text truncate variant="headingSm" alignment="start" breakWord>{data?.tests}</Text>
            //             {(data?.severityText && data?.severityText?.length > 0) ? <Box className={`badge-wrapper-${data?.severityText?.toUpperCase()}`}><Badge size="small" status={observeFunc.getColor(data?.severityText)}>{data?.severityText}</Badge></Box> : null}
            //         </div>
            //     </Box>
            //     <Box width="60%">
            //     <HorizontalStack gap={"2"}>
            //         <Text color="subdued" variant="bodySm">{data?.category}</Text>
            //         <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
            //         <Text color="subdued" variant="bodySm">{data?.author}</Text>
            //     </HorizontalStack>
            //     </Box>
            // </VerticalStack>
            //     <HorizontalStack gap={2} wrap={false}>
            //         <Button icon={FileMinor} ></Button>
            //     </HorizontalStack>
            // </div>
            <Box paddingInlineStart={4} paddingInlineEnd={4} paddingBlockEnd={4}>
                <HorizontalStack align="space-between" wrap={false}>
                    <Box width="80%">
                        <VerticalStack gap={"2"}>
                            <div style={{ display: 'flex', gap: '4px' }} className='test-title'>
                                <Text truncate variant="headingSm" alignment="start" breakWord>{data?.tests}</Text>
                                {(data?.severityText && data?.severityText?.length > 0) ? <Box className={`badge-wrapper-${data?.severityText?.toUpperCase()}`}><Badge size="small" status={observeFunc.getColor(data?.severityText)}>{data?.severityText}</Badge></Box> : null}
                            </div>
                            <HorizontalStack align="start" gap={"2"}>
                                <Text color="subdued" variant="bodySm">{data?.category}</Text>
                                <Box width="1px" borderColor="border-subdued" borderInlineStartWidth="1" minHeight='16px' />
                                <Text color="subdued" variant="bodySm">{`By ${data?.author}`}</Text>
                            </HorizontalStack>
                        </VerticalStack>
                    </Box>
                    <Button icon={FileMinor} onClick={()=>openEditor()} ></Button>
                </HorizontalStack>
            </Box>
        )
    }
    const ref = useRef(null)

    const onClickFunc = () => {
        func.copyToClipboard(data?.content, ref)
    }

    const openEditor = () => {
        window.open(`${window.location.origin}/dashboard/test-editor/${data.value}`);
    }

    const currentComponents = [
        <TitleComponent openEditor={openEditor}/>,
        <Box paddingBlockStart={5} paddingBlockEnd={5} paddingInlineEnd={4} paddingInlineStart={4}>

            <YamlComponent onClickFunc={onClickFunc} dataString={data?.content} language="text" minHeight="76vh"></YamlComponent>

        </Box>
    ]

    return <FlyLayout
        titleComp={<Text fontWeight="medium" variant="headingSm">{"Test Details"}</Text>}
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