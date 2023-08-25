import { VerticalStack, Box, Button, ButtonGroup, HorizontalStack, Icon, Text, Collapsible, Scrollable, DataTable, Badge } from "@shopify/polaris"
import { useCallback, useState } from "react";
import { ChevronDownMinor, ChevronUpMinor } from "@shopify/polaris-icons"
import func from "@/util/func"

function prepareData(data, isHeader) {

    if (data == undefined) {
        return []
    }
    let res = data.filter((x) => isHeader == x.isHeader).map((x, index) => {
        return [(<HorizontalStack gap={"2"} key={index}>
            <Text>
                {x.param.replaceAll("#", ".").replaceAll(".$", "")}
            </Text>
            {
                func.isSubTypeSensitive(x) ?
                    <Badge status="warning">
                        {x.subType.name}
                    </Badge> : null
            }
        </HorizontalStack>), func.prepareValuesTooltip(x)
        ]
    })

    return res;
}

function ApiSingleSchema(props) {
    const { data, title } = props;

    const [open, setOpen] = useState(true);
    const handleToggle = useCallback(() => setOpen((open) => !open), []);
    const [isHeader, setIsHeader] = useState(true)
    let headerCount = 0
    let payloadCount = 0

    data.forEach(element => {
        if(element.isHeader){
            headerCount++
        }else{
            payloadCount++
        }
    });

    return (
        <VerticalStack gap={"2"}>
            <Box background={"bg-subdued"} width="100%" padding={"2"} onClick={handleToggle}>
                <HorizontalStack align="space-between">
                    <Text variant="headingSm">
                        {title}
                    </Text>
                    <Box>
                        <Icon source={open ? ChevronDownMinor : ChevronUpMinor} />
                    </Box>
                </HorizontalStack>
            </Box>
            <Collapsible
                open={open}
                id="basic-collapsible"
                transition={{ duration: '200ms', timingFunction: 'ease-in-out' }}
                expandOnPrint
            >
                <VerticalStack gap={"2"}>
                    <ButtonGroup segmented>
                        <Button size="slim" primarySuccess={isHeader} onClick={() => setIsHeader(true)}> 
                            <HorizontalStack gap="2">
                                <Text variant="bodyMd">Header</Text>
                                <Badge size="small">{headerCount.toString()}</Badge>
                            </HorizontalStack>
                        </Button>
                        <Button size="slim" primarySuccess={!isHeader} onClick={() => setIsHeader(false)}>
                            <HorizontalStack gap="2">
                                <Text variant="bodyMd">Payload</Text>
                                <Badge size="small">{payloadCount.toString()}</Badge>
                            </HorizontalStack>
                        </Button>
                    </ButtonGroup>
                    <Scrollable style={{ height: '25vh' }} focusable>
                        <DataTable
                            headings={[]}
                            columnContentTypes={[
                                'text',
                                'numeric'
                            ]}
                            rows={prepareData(data, isHeader)}
                            increasedTableDensity
                            truncate
                        >
                        </DataTable>
                    </Scrollable>
                </VerticalStack>
            </Collapsible>
        </VerticalStack>
    )
}

function ApiSchema(props) {

    const { data } = props

    let reqData = data.filter((item) => item.responseCode === -1)
    let resData = data.filter((item) => item.responseCode !== -1)

    return (
        <VerticalStack gap="2">
            {
                ['Request', 'Response'].map((type, index) => {
                    return <ApiSingleSchema title={type} key={type} data={index == 0 ? reqData : resData} />
                })
            }

        </VerticalStack>
    )

}

export default ApiSchema