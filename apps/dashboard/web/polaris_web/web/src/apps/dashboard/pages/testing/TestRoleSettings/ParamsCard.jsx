import { Box, Button, HorizontalGrid, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React from 'react'
import TooltipText from "../../../components/shared/TooltipText"
import { DeleteMajor } from "@shopify/polaris-icons"

function ParamsCard({dataObj, handleDelete}) {
    const authMechanism = dataObj.authMechanism
    const headerConditions = dataObj.headerKVPairs
    const headerKey = Object.keys(headerConditions).length > 0 ? Object.keys(headerConditions)[0] : ''
    const headerValue = headerKey.length > 0 ? headerConditions[headerKey] : ''

    function TitleComponent ({title}){
        return (
            <Text variant="headingMd">{title}</Text>
        )
    }

    function LineComponent({title,value}){
        return(
            <HorizontalStack gap={5}>
                <Box maxWidth='200px'>
                    <TooltipText tooltip={title} text={title} textProps={{variant:"bodyMd", fontWeight: "medium"}} />
                </Box>
                <Text variant="bodyMd" color="subdued">{value}</Text>
            </HorizontalStack>
        )
    }

    function ParamsList({valuesList}){
        return(
            <VerticalStack gap={1}>
                {valuesList.map((param,index) => {
                    return(
                        <HorizontalGrid key={index} columns={2}>
                            <LineComponent title={(param.key || '-') + " :"} value={(param.value || '-')} />
                            {param.showHeader !== null ? 
                                <HorizontalStack gap={1}><Box borderInlineEndWidth='1' borderColor="border-subdued" minHeight='20px'/><LineComponent title={"Position :"} value={param.where}/></HorizontalStack>
                            :null}
                        </HorizontalGrid>
                    )
                })}
            </VerticalStack>
        )
    }

    return (
        <Box borderWidth="1" borderRadius='2' padding={2} borderColor="border-subdued" >
            <VerticalStack gap={2}>
                <VerticalStack gap={3}>

                    <VerticalStack gap={2}>
                        <TitleComponent title={"Api header conditions"} />
                        <Box paddingInlineStart={4}>{headerKey.length > 0 ? <LineComponent title={headerKey + " :"} value={headerValue}/> : <Text variant="headingMd">-</Text>}</Box>
                    </VerticalStack>

                    <VerticalStack gap={2}>
                        <TitleComponent title={"Token details"} />
                        <Box paddingInlineStart={4}>
                            <VerticalStack gap={2}>
                                <LineComponent title={"Token type :"} value={authMechanism.type}/>
                                <LineComponent title={"Token values :"} />
                            </VerticalStack>
                            <Box paddingInlineStart={4}>
                                <ParamsList valuesList={authMechanism.authParams} />
                            </Box>
                        </Box>
                    </VerticalStack>

                </VerticalStack>
                <HorizontalStack align="end">
                    <Button size="slim" onClick={handleDelete} icon={DeleteMajor}>Delete</Button>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    )
}

export default ParamsCard