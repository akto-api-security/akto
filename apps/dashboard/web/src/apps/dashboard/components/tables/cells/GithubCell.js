import func from '@/util/func';
import {
    Text,
    Badge,
    VerticalStack,
    HorizontalStack,
    Icon,
    Box} from '@shopify/polaris';

function GithubCell(props){
    return (<>
    <HorizontalStack gap="1">
    {
        props?.headers[0]?.icon &&
        <div style={{marginBottom:"auto"}}>
        <Box padding="05">
            <Icon source={props.data[props?.headers[0]?.icon['value']]} color="primary" />
        </Box>
        </div>
    }
    <VerticalStack gap="2">
        <HorizontalStack gap="2" align='start'>
            <Text as="span" variant="headingMd">
                {
                    props?.headers[0]?.name &&
                    props.data[props?.headers[0]?.name['value']]
                }
            </Text>
            {
                props?.headers[1]?.severityList &&
                    props.data[props?.headers[1]?.severityList['value']] ? props.data[props?.headers[1]?.severityList['value']].map((item) =>
                        <Badge key={item.confidence} status={func.getStatus(item)}>{item.count ? item.count: ""} {item.confidence}</Badge>) :
                    []}
        </HorizontalStack>
        <HorizontalStack gap='2' align="start" >
            {
                props?.headers[2]?.details &&
                props?.headers[2]?.details.map((detail) => {
                    return (
                        <HorizontalStack key={detail.value} gap="1">
                            <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                                <Icon source={detail.icon} color="subdued" />
                            </div>
                            <Text as="div" variant="bodySm" color="subdued">
                                {props.data[detail.value]}
                            </Text>
                        </HorizontalStack>
                    )
                })
            }
        </HorizontalStack>
    </VerticalStack>
</HorizontalStack>

    </>)
}

export default GithubCell