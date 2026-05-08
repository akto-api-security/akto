import {
    Badge,
    VerticalStack,
    HorizontalStack,
    Icon,
    Box, 
    Text,
    Tooltip,
    Button} from '@shopify/polaris';
import { Fragment } from 'react';

import './cell.css'
   
import TooltipText from '../../shared/TooltipText';

function GithubCell(props){

    const {data, headers, getStatus, width, nameWidth, isBadgeClickable, badgeClicked, divWrap} = props
    return (
    <HorizontalStack gap="1" wrap={false}>
    {
        headers?.filter((header) => {
            return header.itemOrder==0
        }).filter((header) => {
            return data[header.value]!=undefined && data[header.value]!="";
        }).map((header, index) => {
            return (
                <div style={{ marginBottom: "auto" }} key={`github-cell-${index}`}>
                    <Box padding="05">
                        {data.iconTooltip ? 
                            <Tooltip content={data?.iconTooltip} dismissOnMouseOut>
                                <div className='big-icon'>
                                    <Icon source={data[header.value]} color={data.iconColor ? data.iconColor : "base"} />
                                </div>
                            </Tooltip>
                            :<Icon source={data[header.value]} color={data.iconColor ? data.iconColor : "base"} />
                        }
                    </Box>
                </div>
            )
        })
    }
    <VerticalStack gap="2" inlineAlign='baseline'>
        <HorizontalStack wrap={divWrap || false} gap="2" align='start'>
            {[
                ...(headers?.filter((header) => {
                    return header.itemOrder == 1
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header, index) => {
                    if(header.component){
                        return (
                            <Box maxWidth={nameWidth || width} key={`order1-header-${index}`}>
                                {header.component(data[header.value])}
                            </Box>
                        )
                    }
                    return (
                        <Box maxWidth={nameWidth || width} key={`order1-header-${index}`}>
                            <div className='order1Title'>
                                <TooltipText
                                    tooltip={data[header.value]}
                                    text = {data[header.value]}
                                    textProps={{variant:"headingMd", ...header.dataProps}}
                                />
                            </div>
                        </Box>
                    )
                }) || []),
                ...(headers?.filter((header) => {
                    return header.itemOrder===2 && header?.alignVertical !== "bottom"
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header, headerIndex) => {
                    return (
                        <Fragment key={`order2-header-${headerIndex}`}>
                            {data?.[header?.value]?.map((item, itemIndex) =>
                                isBadgeClickable ? 
                                    <Button key={`order2-header-${headerIndex}-item-${itemIndex}`} onClick={() =>badgeClicked()} plain monochrome>
                                        <Badge status={getStatus(item)}>
                                            <Text {...header.dataProps}>
                                                {item}
                                            </Text>
                                        </Badge>
                                    </Button>
                                    
                                : <Badge key={`order2-header-${headerIndex}-item-${itemIndex}`} status={getStatus(item)}>
                                    <Text {...header.dataProps}>
                                        {item}
                                    </Text>
                                </Badge>
                            )}
                        </Fragment>
                    )
                }) || [])
            ]}
        </HorizontalStack>
        <Box maxWidth={width}>
            {
                headers?.filter((header) => {
                    return header.itemOrder===2 && header?.alignVertical === "bottom"
                }).map((header, index) => {
                    
                    if(!data?.[header?.value]) {
                        return <div key={`header-component-${index}`}>{header.component({action: header?.action})}</div>
                    }
                    return (
                            <Button key={`header-button-${index}`} plain removeUnderline onClick={header?.action} textAlign="left">
                                <Text as="span" variant="bodyMd" color="subdued" alignment="start">
                                    {data?.[header?.value]}
                                </Text>
                            </Button>
                        )
                    }
                )
            }  
        </Box>
        <Box maxWidth={width}>
        <HorizontalStack gap='2' align="start">
            {
                headers?.filter((header) => {
                    return header.itemOrder==3
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header, index) => {
                    return (
                        <HorizontalStack wrap={false} key={`header-icon-${index}`} gap="1">
                            <div style={{ maxWidth: "1rem", maxHeight: "1rem" }}>
                                <Tooltip content={header.iconTooltip} dismissOnMouseOut>
                                    <Icon source={header.icon} color="subdued" />
                                </Tooltip>
                            </div>
                            <TooltipText
                                tooltip={data[header.value]}
                                text = {data[header.value]}
                                textProps={{variant:"bodySm", color:"subdued", ...header.dataProps}}
                            />
                        </HorizontalStack>
                    )
                }) 
            }
        </HorizontalStack>
        </Box>
        <HorizontalStack gap={"2"}>
        {
            headers?.filter((header) => {
                return header.itemOrder==4
            }).filter((header) => {
                return data[header.value]!=undefined && data[header.value]!="";
            }).map((header, headerIndex) => {
                return (
                    <Fragment key={`order4-header-${headerIndex}`}>
                        {data?.[header?.value]?.map((item, itemIndex) =>
                            isBadgeClickable ? 
                                <div onClick={() =>badgeClicked()} style={{cursor: "pointer"}} key={`order4-header-${headerIndex}-item-${itemIndex}`}>
                                    <Badge status={getStatus(item)}>
                                        <Text {...header.dataProps}>
                                            {item}
                                        </Text>
                                    </Badge>
                                </div>
                                
                            : <Badge key={`order4-header-${headerIndex}-item-${itemIndex}`} status={getStatus(item)}>
                                <Text {...header.dataProps} breakWord>
                                    {item}
                                </Text>
                            </Badge>
                        )}
                    </Fragment>
                )
            }) 
        }
        </HorizontalStack>
    </VerticalStack>
</HorizontalStack>
)
}
export default GithubCell