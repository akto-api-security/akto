import func from '@/util/func';
import {
    Text,
    Badge,
    VerticalStack,
    HorizontalStack,
    Icon,
    Box} from '@shopify/polaris';

function GithubCell(props){
    return (
    <HorizontalStack gap="1">
    {
        props?.headers?.filter((header) => {
            return header.itemOrder==0
        }).map((header) => {
            return (
                <div style={{ marginBottom: "auto" }} key={header.value}>
                    <Box padding="05">
                        <Icon source={props.data[header.value]} color="primary" />
                    </Box>
                </div>
            )
        })
    }
    <VerticalStack gap="2">
        <HorizontalStack gap="2" align='start'>
            <Box maxWidth='50vw'>
            <Text as="span" variant="headingMd" truncate={true}>
                {
                    props?.headers?.filter((header) => {
                        return header.itemOrder==1
                    }).map((header) => {
                        return props.data[header.value]
                    }) 
                }
            </Text>
            </Box>
            {
                props?.headers?.filter((header) => {
                    return header.itemOrder==2
                }).map((header) => {
                    return props?.data?.[header?.value]
                    ?.map((item) =>
                    <Badge key={item} status={func.getStatus(item)}>
                        {item}
                    </Badge>
                )}) 
            }
        </HorizontalStack>
        <HorizontalStack gap='2' align="start" >
            {
                props?.headers?.filter((header) => {
                    return header.itemOrder==3
                }).map((header) => {
                    return (
                        <HorizontalStack key={header.value} gap="1">
                            <div style={{ maxWidth: "0.875rem", maxHeight: "0.875rem" }}>
                                <Icon source={header.icon} color="subdued" />
                            </div>
                            <Text as="div" variant="bodySm" color="subdued">
                                {props.data[header.value]}
                            </Text>
                        </HorizontalStack>
                    )
                }) 
            }
        </HorizontalStack>
    </VerticalStack>
</HorizontalStack>
)
}

export default GithubCell