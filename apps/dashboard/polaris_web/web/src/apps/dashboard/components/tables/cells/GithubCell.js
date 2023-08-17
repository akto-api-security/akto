import {
    Badge,
    VerticalStack,
    HorizontalStack,
    Icon,
    Box, 
    Text,
    Tooltip} from '@shopify/polaris';

import './cell.css'
   
import TooltipText from '../../shared/TooltipText';

function GithubCell(props){

    const {data, headers, getStatus, width, nameWidth} = props
    return (
    <HorizontalStack gap="1">
    {
        headers?.filter((header) => {
            return header.itemOrder==0
        }).filter((header) => {
            return data[header.value]!=undefined && data[header.value]!="";
        }).map((header) => {
            return (
                <div style={{ marginBottom: "auto" }} key={header.value}>
                    <Box padding="05">
                        {data.iconTooltip ? 
                            <Tooltip content={data?.iconTooltip} dismissOnMouseOut>
                                <Icon source={data[header.value]} color={data.iconColor ? data.iconColor : "base"} />
                            </Tooltip>
                            :<Icon source={data[header.value]} color={data.iconColor ? data.iconColor : "base"} />
                        }
                    </Box>
                </div>
            )
        })
    }
    <VerticalStack gap="2" inlineAlign='baseline'>
        <HorizontalStack gap="2" align='start'>
            {
                headers?.filter((header) => {
                    return header.itemOrder == 1
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header) => {
                    if(header.component){
                        return (
                            <Box maxWidth={nameWidth || width} key={header.value}>
                                {header.component(data[header.value])}
                            </Box>
                        )
                    }
                    return (
                        <Box maxWidth={nameWidth || width} key={header.value}>
                            <div className='order1Title'>
                                <TooltipText
                                    tooltip={data[header.value]}
                                    text = {data[header.value]}
                                    textProps={{variant:"headingMd", ...header.dataProps}}
                                />
                            </div>
                        </Box>
                    )
                })
            }
            {
                headers?.filter((header) => {
                    return header.itemOrder==2
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header) => {
                    return data?.[header?.value]
                    ?.map((item) =>
                    <Badge key={item} status={getStatus(item)}>
                        <Text {...header.dataProps}>
                            {item}
                        </Text>
                    </Badge>
                )}) 
            }
        </HorizontalStack>
        <Box maxWidth={width}>
        <HorizontalStack gap='2' align="start">
            {
                headers?.filter((header) => {
                    return header.itemOrder==3
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header) => {
                    return (
                        <HorizontalStack key={header.value} gap="1">
                            <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                                <Icon source={header.icon} color="subdued" />
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
    </VerticalStack>
</HorizontalStack>
)
}

export default GithubCell