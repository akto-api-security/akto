import {
    Badge,
    BlockStack,
    InlineStack,
    Icon,
    Box, 
    Text,
    Tooltip,
    Button} from '@shopify/polaris';

import './cell.css'
   
import TooltipText from '../../shared/TooltipText';

function GithubCell(props){

    const {data, headers, getStatus, width, nameWidth, isBadgeClickable, badgeClicked, divWrap} = props
    return (
        <InlineStack gap="100" wrap={false}>
            {
                headers?.filter((header) => {
                    return header.itemOrder==0
                }).filter((header) => {
                    return data[header.value]!=undefined && data[header.value]!="";
                }).map((header) => {
                    return (
                        <div style={{ marginBottom: "auto" }} key={header.value}>
                            <Box padding="050">
                                {data.iconTooltip ? 
                                    <Tooltip content={data?.iconTooltip} dismissOnMouseOut>
                                        <div className='big-icon'>
                                            <Icon source={data[header.value]} tone={data.iconColor ? data.iconColor : "base"} />
                                        </div>
                                    </Tooltip>
                                    :<Icon source={data[header.value]} tone={data.iconColor ? data.iconColor : "base"} />
                                }
                            </Box>
                        </div>
                    )
                })
            }
            <BlockStack gap="200" inlineAlign='baseline'>
                <InlineStack wrap={divWrap || false} gap="2" align='start'>
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
                                <Button key={item} onClick={() =>badgeClicked()}   variant="monochromePlain">
                                    <Badge tone={getStatus(item)}>
                                        <Text {...header.dataProps}>
                                            {item}
                                        </Text>
                                    </Badge>
                                </Button>
                                
                            : <Badge key={item} tone={getStatus(item)}>
                                <Text {...header.dataProps}>
                                    {item}
                                </Text>
                            </Badge>
                            
                        );}) 
                    }
                </InlineStack>
                <Box maxWidth={width}>
                <InlineStack gap='200' align="start">
                    {
                        headers?.filter((header) => {
                            return header.itemOrder==3
                        }).filter((header) => {
                            return data[header.value]!=undefined && data[header.value]!="";
                        }).map((header) => {
                            return (
                                <InlineStack wrap={false} key={header.value} gap="100">
                                    <div style={{ maxWidth: "1rem", maxHeight: "1rem" }}>
                                        <Tooltip content={header.iconTooltip} dismissOnMouseOut>
                                            <Icon source={header.icon} tone="subdued" />
                                        </Tooltip>
                                    </div>
                                    <TooltipText
                                        tooltip={data[header.value]}
                                        text = {data[header.value]}
                                        textProps={{variant:"bodySm", color:"subdued", ...header.dataProps}}
                                    />
                                </InlineStack>
                            );
                        }) 
                    }
                </InlineStack>
                </Box>
                <InlineStack gap={"200"}>
                    {
                        headers?.filter((header) => {
                            return header.itemOrder==4
                        }).filter((header) => {
                            return data[header.value]!=undefined && data[header.value]!="";
                        }).map((header) => {
                            return data?.[header?.value]
                            ?.map((item) =>
                            isBadgeClickable ? 
                                <div onClick={() =>badgeClicked()} style={{cursor: "pointer"}} key={item}>
                                    <Badge tone={getStatus(item)}>
                                        <Text {...header.dataProps}>
                                            {item}
                                        </Text>
                                    </Badge>
                                </div>
                                
                            : <Badge key={item} tone={getStatus(item)}>
                                <Text {...header.dataProps} breakWord>
                                    {item}
                                </Text>
                            </Badge>
                            
                        )}) 
                    }
                </InlineStack>
            </BlockStack>
        </InlineStack>
    );
}
export default GithubCell