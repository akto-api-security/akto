import { Box, Button, InlineStack, Text, BlockStack } from '@shopify/polaris'
import React from 'react'
import TooltipText from "../../../components/shared/TooltipText"
import { DeleteIcon } from "@shopify/polaris-icons";

function ParamsCard({dataObj, handleDelete, showEdit}) {
    const authMechanism = dataObj.authMechanism
    const headerConditions = dataObj.headerKVPairs || {}
    const headerKey = Object.keys(headerConditions).length > 0 ? Object.keys(headerConditions)[0] : ''
    const headerValue = headerKey.length > 0 ? headerConditions[headerKey] : ''

    function TitleComponent ({title}){
        return (
            <Text variant="headingMd">{title}</Text>
        )
    }

    function LineComponent({title,value}){
        return(
            <div style={{display: 'flex', gap: '20px', flexWrap: 'nowrap'}}>
                <Box maxWidth='200px'>
                    <TooltipText tooltip={title} text={title} textProps={{variant:"bodyMd", fontWeight: "medium"}} />
                </Box>
                <Text breakWord truncate alignment="start" variant="bodyMd" color="subdued">{value}</Text>
            </div>
        )
    }

    function ParamsList({valuesList}){
        return (
            <BlockStack align="start" gap={1}>
                {valuesList.map((param,index) => {
                    return (
                        <InlineStack blockAlign="start" gap={"4"} key={index}>
                            <LineComponent title={(param.key || '-') + " :"} value={(param.value || '-')} />
                            {param.showHeader !== null ? 
                                <InlineStack blockAlign="start" gap={1}><Box borderInlineEndWidth='1' borderColor="border-subdued" minHeight='20px'/><LineComponent title={"Position :"} value={param.where}/></InlineStack>
                            :null}
                        </InlineStack>
                    );
                })}
            </BlockStack>
        );
    }

    return (
        <Box borderWidth="1" borderRadius='2' padding={2} borderColor="border-subdued" >
            <BlockStack gap={2}>
                <BlockStack gap={3}>
                    {headerKey.length > 0 ? <BlockStack gap={2}>
                        <TitleComponent title={"Api header conditions"} />
                        <Box paddingInlineStart={4}>{headerKey.length > 0 ? <LineComponent title={headerKey + " :"} value={headerValue}/> : <Text variant="headingMd">-</Text>}</Box>
                    </BlockStack> : null}
                    <BlockStack gap={2}>
                        <TitleComponent title={"Token details"} />
                        <Box paddingInlineStart={4}>
                            <BlockStack gap={2}>
                                <LineComponent title={"Token type :"} value={authMechanism.type}/>
                                <LineComponent title={"Token values :"} />
                            </BlockStack>
                            <Box paddingInlineStart={4}>
                                <ParamsList valuesList={authMechanism.authParams} />
                            </Box>
                        </Box>
                    </BlockStack>
                </BlockStack>
                <InlineStack gap={"2"} align="end">
                    <Button size="slim" onClick={handleDelete} icon={DeleteIcon}><div data-testid="delete_button">Delete</div></Button>
                    {authMechanism?.type?.toLowerCase() === 'hardcoded' ? <Button size="slim"  onClick={() => showEdit()} variant="primary">Edit</Button> : null}
                </InlineStack>
            </BlockStack>
        </Box>
    );
}

export default ParamsCard