import {
    Badge,
    VerticalStack,
    HorizontalStack,
    Icon,
    Box, 
    Text,
    Tooltip,
    Button} from '@shopify/polaris';

import './cell.css'
   
import TooltipText from '../../shared/TooltipText';

function GithubCell(props){

    const {data, headers, getStatus, width, nameWidth, isBadgeClickable, badgeClicked} = props
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
                    isBadgeClickable ? 
                        <Button key={item} onClick={() =>badgeClicked()} plain monochrome>
                            <Badge status={getStatus(item)}>
                                <Text {...header.dataProps}>
                                    {item}
                                </Text>
                            </Badge>
                        </Button>
                        
                    : <Badge key={item} status={getStatus(item)}>
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
                            <div style={{ maxWidth: "1rem", maxHeight: "1rem" }}>
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