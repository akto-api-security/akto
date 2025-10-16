import { Box, Button, HorizontalStack, Text, VerticalStack } from '@shopify/polaris'
import React, { useState } from 'react'
import TooltipText from "../../../components/shared/TooltipText"
import { DeleteMajor } from "@shopify/polaris-icons"

function ParamsCard({dataObj, handleDelete, showEdit}) {
    const [hideValues, setHideValues] = useState(true)
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
        return(
            <VerticalStack align="start" gap={1}>
                {valuesList.map((param,index) => {
                    return(
                        <HorizontalStack blockAlign="start" gap={"4"} key={index}>
                            <LineComponent title={(param.key || '-') + " :"} value={(hideValues ? '********' : (param.value || '-'))} />
                            {param.showHeader !== null ? 
                                <HorizontalStack blockAlign="start" gap={1}><Box borderInlineEndWidth='1' borderColor="border-subdued" minHeight='20px'/><LineComponent title={"Position :"} value={param.where}/></HorizontalStack>
                            :null}
                        </HorizontalStack>
                    )
                })}
            </VerticalStack>
        )
    }

    return (
        <Box borderWidth="1" borderRadius='2' padding={2} borderColor="border-subdued" >
            <VerticalStack gap={2}>
                <VerticalStack gap={3}>

                    {headerKey.length > 0 ? <VerticalStack gap={2}>
                        <TitleComponent title={"Api header conditions"} />
                        <Box paddingInlineStart={4}>{headerKey.length > 0 ? <LineComponent title={headerKey + " :"} value={headerValue}/> : <Text variant="headingMd">-</Text>}</Box>
                    </VerticalStack> : null}

                    <VerticalStack gap={2}>
                        <TitleComponent title={"Token details"} />
                        <Box paddingInlineStart={4}>
                            <VerticalStack gap={2}>
                                <LineComponent title={"Token type :"} value={authMechanism.type}/>
                                <HorizontalStack gap="4" blockAlign="center">
                                    <Box maxWidth='200px'>
                                        <TooltipText tooltip={"Token values"} text={"Token values :"} textProps={{variant:"bodyMd", fontWeight: "medium"}} />
                                    </Box>
                                    <Button size="slim" onClick={() => setHideValues(!hideValues)}>
                                        {hideValues ? 'Show' : 'Hide'}
                                    </Button>
                                </HorizontalStack>
                            </VerticalStack>
                            <Box paddingInlineStart={4}>
                                <ParamsList valuesList={authMechanism.authParams} />
                            </Box>
                        </Box>
                    </VerticalStack>

                </VerticalStack>
                <HorizontalStack gap={"2"} align="end">
                    <Button size="slim" onClick={handleDelete} icon={DeleteMajor}><div data-testid="delete_button">Delete</div></Button>
                    <Button size="slim" primary onClick={() => showEdit()}>Edit</Button>
                </HorizontalStack>
            </VerticalStack>
        </Box>
    )
}

export default ParamsCard