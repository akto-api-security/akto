import { Box, Button, HorizontalStack, Text, VerticalStack, Icon } from '@shopify/polaris'
import React, { useState } from 'react'
import TooltipText from "../../../components/shared/TooltipText"
import { DeleteMajor, ViewMinor, HideMinor } from "@shopify/polaris-icons"

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
                            <LineComponent title={(param.key || '-') + " :"} value={(param.value || '-')} />
                            {param.showHeader !== null ? (
                                <HorizontalStack blockAlign="start" gap={1}>
                                    <Box borderInlineEndWidth='1' borderColor="border-subdued" minHeight='20px'/>
                                    <LineComponent title={"Position :"} value={param.where}/>
                                </HorizontalStack>
                            ) : null}
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
                                <HorizontalStack gap="2" blockAlign="center">
                                    <Box maxWidth='200px'>
                                        <TooltipText tooltip={"Token values"} text={"Token values :"} textProps={{variant:"bodyMd", fontWeight: "medium"}} />
                                    </Box>
                                    <Box
                                        role="button"
                                        tabIndex={0}
                                        aria-label={hideValues ? 'Show token values' : 'Hide token values'}
                                        onClick={() => setHideValues(!hideValues)}
                                        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setHideValues(!hideValues) } }}
                                        style={{ cursor: 'pointer', padding: '4px', lineHeight: 0, display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                                    >
                                        <Icon source={hideValues ? ViewMinor : HideMinor} />
                                    </Box>
                                </HorizontalStack>
                            </VerticalStack>
                            {!hideValues ? (
                                <Box paddingInlineStart={4}>
                                    <ParamsList valuesList={authMechanism.authParams} />
                                </Box>
                            ) : null}
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